package i18n

import java.io.InputStream

/**
 * Android platform implementation: Load resources from assets/classpath
 */
internal actual object ResourceLoader {
    actual fun loadResource(path: String): String? {
        return try {
            val inputStream: InputStream? = this::class.java.classLoader.getResourceAsStream(path)
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




