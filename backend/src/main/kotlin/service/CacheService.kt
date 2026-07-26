package service

import database.CacheDatabase
import database.CacheTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest

class CacheService {

    private val logger = LoggerFactory.getLogger(CacheService::class.java)

    // Hard cap on cached entries; oldest are evicted past this. Override with CACHE_MAX_ENTRIES.
    private val maxEntries: Long = System.getenv("CACHE_MAX_ENTRIES")?.toLongOrNull() ?: 50_000L

    /**
     * Generate SHA-256 hash of endpoint + request JSON for cache key
     */
    private fun generateCacheKey(endpoint: String, requestJson: String): String {
        val input = "$endpoint:$requestJson"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Retrieve cached response if it exists
     * @return Cached response JSON string, or null if not found
     */
    fun getCachedResponse(endpoint: String, requestJson: String): String? {
        return try {
            val requestHash = generateCacheKey(endpoint, requestJson)
            transaction(CacheDatabase.getDatabase()) {
                CacheTable
                    .selectAll().where { CacheTable.requestHash eq requestHash }
                    .firstOrNull()
                    ?.get(CacheTable.responseJson)
            }
        } catch (e: Exception) {
            // Log error but don't fail the request
            logger.error("Cache lookup failed for endpoint $endpoint", e)
            null
        }
    }

    /**
     * Store response in cache, then evict oldest entries past [maxEntries].
     */
    fun storeCachedResponse(endpoint: String, requestJson: String, responseJson: String) {
        try {
            // Defense in depth: never persist oversized cache keys even if a caller skipped validation.
            if (requestJson.toByteArray(Charsets.UTF_8).size > 64 * 1024) {
                logger.warn("Refusing to cache oversized request for {}", endpoint)
                return
            }
            val requestHash = generateCacheKey(endpoint, requestJson)
            val createdAt = System.currentTimeMillis()

            transaction(CacheDatabase.getDatabase()) {
                // Use INSERT OR REPLACE semantics by updating if the key exists.
                val existing = CacheTable.selectAll().where { CacheTable.requestHash eq requestHash }.firstOrNull()
                if (existing != null) {
                    CacheTable.update({ CacheTable.requestHash eq requestHash }) {
                        it[CacheTable.responseJson] = responseJson
                        it[CacheTable.createdAt] = createdAt
                    }
                } else {
                    CacheTable.insert {
                        it[CacheTable.endpoint] = endpoint
                        it[CacheTable.requestHash] = requestHash
                        // Do not store the full request body — hash is sufficient for lookup and
                        // avoids bloating SQLite with large (but still capped) JSON payloads.
                        it[CacheTable.requestJson] = ""
                        it[CacheTable.responseJson] = responseJson
                        it[CacheTable.createdAt] = createdAt
                    }
                }
            }
            logger.debug("Stored cache entry for {}", endpoint)
            limitCacheSize(maxEntries)
        } catch (e: Exception) {
            // Log error but don't fail the request
            logger.error("Failed to store cache entry for endpoint $endpoint", e)
        }
    }

    /**
     * Get cache statistics
     */
    data class CacheStats(
        val totalEntries: Long,
        val totalSizeBytes: Long,
        val entriesByEndpoint: Map<String, Long>,
        val oldestEntryTimestamp: Long?,
        val newestEntryTimestamp: Long?
    )

    fun getCacheStats(): CacheStats {
        return try {
            transaction(CacheDatabase.getDatabase()) {
                val totalEntries = CacheTable.selectAll().count().toLong()

                val entriesByEndpoint = CacheTable
                    .select(CacheTable.endpoint, CacheTable.id.count())
                    .groupBy(CacheTable.endpoint)
                    .associate { it[CacheTable.endpoint] to it[CacheTable.id.count()] }

                val timestamps = CacheTable
                    .select(CacheTable.createdAt)
                    .mapNotNull { it[CacheTable.createdAt] }

                val oldestTimestamp = timestamps.minOrNull()
                val newestTimestamp = timestamps.maxOrNull()

                // Estimate total size by reading all entries (for small-medium caches)
                // For very large caches, this could be slow - consider sampling
                val totalSize = if (totalEntries < 100000) {
                    CacheTable
                        .selectAll()
                        .sumOf {
                            (it[CacheTable.requestJson]?.length?.toLong() ?: 0L) +
                            (it[CacheTable.responseJson]?.length?.toLong() ?: 0L)
                        }
                } else {
                    // For large caches, estimate based on sample
                    val sampleSize = 1000
                    val sampleAvg = CacheTable
                        .selectAll()
                        .limit(sampleSize)
                        .map {
                            (it[CacheTable.requestJson]?.length ?: 0) +
                            (it[CacheTable.responseJson]?.length ?: 0)
                        }
                        .average()
                    (sampleAvg * totalEntries).toLong()
                }

                CacheStats(
                    totalEntries = totalEntries,
                    totalSizeBytes = totalSize,
                    entriesByEndpoint = entriesByEndpoint,
                    oldestEntryTimestamp = oldestTimestamp,
                    newestEntryTimestamp = newestTimestamp
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to compute cache stats", e)
            CacheStats(0, 0, emptyMap(), null, null)
        }
    }

    /**
     * Get database file size on disk
     */
    fun getDatabaseFileSize(): Long {
        return try {
            val dbFile = File(CacheDatabase.DEFAULT_DB_PATH)
            if (dbFile.exists()) dbFile.length() else 0L
        } catch (e: Exception) {
            logger.error("Failed to read database file size", e)
            0L
        }
    }

    /**
     * Clean up old cache entries (older than specified days)
     * Returns number of entries deleted
     */
    fun cleanupOldEntries(olderThanDays: Int): Int {
        return try {
            val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24L * 60L * 60L * 1000L)
            transaction(CacheDatabase.getDatabase()) {
                val deleted = CacheTable.deleteWhere { CacheTable.createdAt less cutoffTime }
                if (deleted > 0) logger.info("Cleaned up {} cache entries older than {} days", deleted, olderThanDays)
                deleted
            }
        } catch (e: Exception) {
            logger.error("Failed to clean up old cache entries", e)
            0
        }
    }

    /**
     * Limit cache size by removing oldest entries when limit is exceeded
     * Returns number of entries deleted
     */
    fun limitCacheSize(maxEntries: Long): Int {
        return try {
            transaction(CacheDatabase.getDatabase()) {
                val currentCount = CacheTable.selectAll().count()
                if (currentCount <= maxEntries) {
                    0
                } else {
                    val toDelete = (currentCount - maxEntries).toInt()
                    // Get IDs of oldest entries to delete
                    val oldestIds = CacheTable
                        .select(CacheTable.id)
                        .orderBy(CacheTable.createdAt to SortOrder.ASC)
                        .limit(toDelete)
                        .map { it[CacheTable.id].value }

                    if (oldestIds.isNotEmpty()) {
                        val deleted = CacheTable.deleteWhere { CacheTable.id inList oldestIds }
                        logger.info("Evicted {} oldest cache entries (cap {})", deleted, maxEntries)
                        deleted
                    } else {
                        0
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to limit cache size", e)
            0
        }
    }
}
