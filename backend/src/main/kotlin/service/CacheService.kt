package service

import database.CacheDatabase
import database.CacheTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.security.MessageDigest

class CacheService {
    
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
            println("[CACHE-SERVICE] Looking up cache for endpoint: $endpoint")
            println("[CACHE-SERVICE] Request JSON length: ${requestJson.length}, first 100 chars: ${requestJson.take(100)}")
            println("[CACHE-SERVICE] Generated hash: ${requestHash.take(16)}...")
            
            val result = transaction(CacheDatabase.getDatabase()) {
                val found = CacheTable
                    .select { CacheTable.requestHash eq requestHash }
                    .firstOrNull()
                
                if (found != null) {
                    println("[CACHE-SERVICE] Cache entry FOUND in database")
                    found.get(CacheTable.responseJson)
                } else {
                    println("[CACHE-SERVICE] Cache entry NOT FOUND in database")
                    null
                }
            }
            
            if (result != null) {
                println("[CACHE-SERVICE] Returning cached response (length: ${result.length})")
            } else {
                println("[CACHE-SERVICE] No cached response available")
            }
            
            result
        } catch (e: Exception) {
            // Log error but don't fail the request
            println("[CACHE-SERVICE] ERROR during cache lookup: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Store response in cache
     */
    fun storeCachedResponse(endpoint: String, requestJson: String, responseJson: String) {
        try {
            val requestHash = generateCacheKey(endpoint, requestJson)
            val createdAt = System.currentTimeMillis()
            
            println("[CACHE-SERVICE] Storing response in cache for endpoint: $endpoint")
            println("[CACHE-SERVICE] Hash: ${requestHash.take(16)}..., Response length: ${responseJson.length}")
            
            transaction(CacheDatabase.getDatabase()) {
                // Use INSERT OR REPLACE semantics by deleting first if exists
                val existing = CacheTable.select { CacheTable.requestHash eq requestHash }.firstOrNull()
                if (existing != null) {
                    println("[CACHE-SERVICE] Updating existing cache entry")
                    // Update existing entry
                    CacheTable.update({ CacheTable.requestHash eq requestHash }) {
                        it[CacheTable.responseJson] = responseJson
                        it[CacheTable.createdAt] = createdAt
                    }
                } else {
                    println("[CACHE-SERVICE] Inserting new cache entry")
                    // Insert new entry
                    CacheTable.insert {
                        it[CacheTable.endpoint] = endpoint
                        it[CacheTable.requestHash] = requestHash
                        it[CacheTable.requestJson] = requestJson
                        it[CacheTable.responseJson] = responseJson
                        it[CacheTable.createdAt] = createdAt
                    }
                }
            }
            println("[CACHE-SERVICE] Successfully stored in cache")
        } catch (e: Exception) {
            // Log error but don't fail the request
            println("[CACHE-SERVICE] ERROR storing in cache: ${e.message}")
            e.printStackTrace()
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
                    .slice(CacheTable.endpoint, CacheTable.id.count())
                    .selectAll()
                    .groupBy(CacheTable.endpoint)
                    .associate { it[CacheTable.endpoint] to it[CacheTable.id.count()] }
                
                val timestamps = CacheTable
                    .slice(CacheTable.createdAt)
                    .selectAll()
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
            println("[CACHE-SERVICE] ERROR getting cache stats: ${e.message}")
            CacheStats(0, 0, emptyMap(), null, null)
        }
    }
    
    /**
     * Get database file size on disk
     */
    fun getDatabaseFileSize(): Long {
        return try {
            val dbFile = File("./data/cache.db")
            if (dbFile.exists()) dbFile.length() else 0L
        } catch (e: Exception) {
            println("[CACHE-SERVICE] ERROR getting database file size: ${e.message}")
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
                // Get IDs of entries to delete
                val idsToDelete = CacheTable
                    .slice(CacheTable.id)
                    .select { CacheTable.createdAt less cutoffTime }
                    .map { it[CacheTable.id].value }
                
                var deleted = 0
                idsToDelete.forEach { id ->
                    CacheTable.deleteWhere { CacheTable.id eq id }
                    deleted++
                }
                println("[CACHE-SERVICE] Cleaned up $deleted old cache entries (older than $olderThanDays days)")
                deleted
            }
        } catch (e: Exception) {
            println("[CACHE-SERVICE] ERROR cleaning up old entries: ${e.message}")
            e.printStackTrace()
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
                        .slice(CacheTable.id)
                        .selectAll()
                        .orderBy(CacheTable.createdAt to SortOrder.ASC)
                        .limit(toDelete)
                        .map { it[CacheTable.id].value }
                    
                    if (oldestIds.isNotEmpty()) {
                        val deleted = CacheTable.deleteWhere { CacheTable.id inList oldestIds }
                        println("[CACHE-SERVICE] Limited cache to $maxEntries entries, deleted $deleted oldest entries")
                        deleted
                    } else {
                        0
                    }
                }
            }
        } catch (e: Exception) {
            println("[CACHE-SERVICE] ERROR limiting cache size: ${e.message}")
            e.printStackTrace()
            0
        }
    }
}

