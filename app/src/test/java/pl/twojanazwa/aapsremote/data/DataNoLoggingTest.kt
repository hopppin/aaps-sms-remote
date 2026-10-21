package pl.twojanazwa.aapsremote.data

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Katalog roboczy testów to moduł `app`. */
class DataNoLoggingTest {

    private val forbidden = listOf("android.util.Log", "println(", "System.out", "System.err")

    @Test
    fun `DATA-17 zrodla data i data-platform nie wywoluja API logowania`() {
        val root = File("src/main/java/pl/twojanazwa/aapsremote/data")
        assertTrue(root.isDirectory, "Brak katalogu: ${root.path}")
        val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(
            sources.any { it.path.replace('\\', '/').contains("/platform/") },
            "Nie znaleziono źródeł data.platform",
        )
        val violations = sources.flatMap { file ->
            val text = file.readText()
            forbidden.filter { text.contains(it) }.map { "${file.name}: $it" }
        }
        assertTrue(violations.isEmpty(), "Użycie API logowania: $violations")
    }
}
