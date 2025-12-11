package database

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object CacheTable : LongIdTable("cache_entries") {
    val endpoint = varchar("endpoint", 255)
    val requestHash = varchar("request_hash", 64).uniqueIndex()
    val requestJson = text("request_json")
    val responseJson = text("response_json")
    val createdAt = long("created_at")
}

object CacheDatabase {
    private var database: Database? = null
    
    // Bump this filename when cached-response JSON schemas change, to avoid replaying old cached JSON.
    const val DEFAULT_DB_PATH: String = "./data/cache-v2.db"
    
    fun initialize(dbPath: String = DEFAULT_DB_PATH) {
        // Ensure data directory exists
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()
        
        // Connect to SQLite database
        database = Database.connect(
            "jdbc:sqlite:$dbPath",
            driver = "org.sqlite.JDBC"
        )
        
        // Create table if it doesn't exist
        transaction(database!!) {
            SchemaUtils.create(CacheTable)
        }
    }
    
    fun getDatabase(): Database {
        return database ?: throw IllegalStateException("Database not initialized. Call initialize() first.")
    }
}

