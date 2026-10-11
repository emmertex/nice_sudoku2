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
    const val DEFAULT_DB_PATH: String = "./data/cache-v3.db"
    
    fun initialize(dbPath: String = DEFAULT_DB_PATH) {
        // Ensure data directory exists
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()
        
        // Connect to SQLite database
        database = Database.connect(
            "jdbc:sqlite:$dbPath",
            driver = "org.sqlite.JDBC",
            setupConnection = { conn ->
                conn.createStatement().use { st ->
                    // WAL lets readers proceed during writes; busy_timeout waits instead of failing with SQLITE_BUSY.
                    st.execute("PRAGMA journal_mode=WAL")
                    st.execute("PRAGMA busy_timeout=5000")
                    st.execute("PRAGMA synchronous=NORMAL")
                }
            }
        )
        
        // Create table if it doesn't exist
        transaction(database!!) {
            SchemaUtils.create(CacheTable)
            // Eviction orders by created_at; without this it's a full scan + sort.
            exec("CREATE INDEX IF NOT EXISTS cache_entries_created_at ON cache_entries (created_at)")
        }
    }
    
    fun getDatabase(): Database {
        return database ?: throw IllegalStateException("Database not initialized. Call initialize() first.")
    }
}

