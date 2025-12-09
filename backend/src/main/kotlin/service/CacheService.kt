package service

import database.CacheDatabase
import database.CacheTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
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
}

