package pl.twojanazwa.aapsremote.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Katalog roboczy testów to moduł `app`. */
class UiNoLoggingTest {

    private val forbidden = listOf(
        Regex("""\bandroid\s*\.\s*util\s*\.\s*Log\b"""),
        Regex("""\bLog\s*\.\s*[a-z]+\s*\("""),
        Regex("""\b(?:print|println)\s*\("""),
        Regex("""\bSystem\s*\.\s*(?:out|err)\b"""),
    )

    @Test
    fun `CMDUI-26 zrodla ui-commands i ui-platform nie wywoluja API logowania`() {
        val base = "src/main/java/pl/twojanazwa/aapsremote/ui"
        val sources = listOf("commands", "platform").flatMap { dir ->
            val root = File("$base/$dir")
            assertTrue(root.isDirectory, "Brak katalogu: ${root.path}")
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue(sources.any { it.name == "CurrentRun.kt" }, "Brak CurrentRun.kt")
        assertTrue(sources.any { it.name == "CommandsModule.kt" }, "Brak CommandsModule.kt")
        val violations = sources.flatMap { file ->
            val text = file.readText()
            forbidden.filter { it.containsMatchIn(text) }.map { "${file.name}: ${it.pattern}" }
        }
        assertTrue(violations.isEmpty(), "Użycie API logowania: $violations")
    }

    @Test
    fun `HISTUI-15 zrodla ui-history nie wywoluja API logowania`() {
        val root = File("src/main/java/pl/twojanazwa/aapsremote/ui/history")
        assertTrue(root.isDirectory, "Brak katalogu: ${root.path}")
        val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.any { it.name == "HistoryEntryMapper.kt" }, "Brak HistoryEntryMapper.kt")
        val violations = sources.flatMap { file ->
            val text = file.readText()
            forbidden.filter { it.containsMatchIn(text) }.map { "${file.name}: ${it.pattern}" }
        }
        assertTrue(violations.isEmpty(), "Użycie API logowania: $violations")
    }

    @Test
    fun `UI-21zrodla ui i session-platform nie wywoluja API logowania`() {
        val base = "src/main/java/pl/twojanazwa/aapsremote"
        val roots = listOf(File("$base/ui"), File("$base/session/platform"))
        roots.forEach { assertTrue(it.isDirectory, "Brak katalogu: ${it.path}") }
        val sources = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue(
            sources.any { it.name == "SessionModule.kt" },
            "Nie znaleziono źródeł session.platform",
        )
        val violations = sources.flatMap { file ->
            val text = file.readText()
            forbidden.filter { it.containsMatchIn(text) }.map { "${file.name}: ${it.pattern}" }
        }
        assertTrue(violations.isEmpty(), "Użycie API logowania: $violations")
    }
}
