package i18n

import java.io.InputStream

/**
 * JVM platform implementation: Load resources from classpath
 */
internal actual object ResourceLoader {
    actual fun loadResource(path: String): String? {
        // ClassLoader.getResourceAsStream expects path without leading slash
        val resourcePath = if (path.startsWith("/")) path.substring(1) else path
        
        return try {
            val inputStream: InputStream? = this::class.java.classLoader.getResourceAsStream(resourcePath)
            inputStream?.use { stream ->
                stream.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

