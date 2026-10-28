package pl.twojanazwa.aapsremote.ui.history

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistorySourcesTest {
    private val ui = "src/main/java/pl/twojanazwa/aapsremote/ui"

    @Test
    fun `HISTUI-01 brak zaslepki i klucza, a AppScaffold wywoluje HistoryScreen dla trasy history`() {
        assertFalse(File("$ui/history/HistoryPlaceholderScreen.kt").exists())
        assertFalse(File("src/main/res/values/strings.xml").readText().contains("placeholder_history_text"))
        val scaffold = File("$ui/navigation/AppScaffold.kt").readText()
        assertTrue(Regex("""AppDestination\.HISTORY\.route\)\s*\{\s*HistoryScreen\(\)""").containsMatchIn(scaffold))
    }

    @Test
    fun `HISTUI-13 HistoryScreen zawiera SecureWindowEffect(true)`() {
        assertTrue(File("$ui/history/HistoryScreen.kt").readText().contains("SecureWindowEffect(true)"))
    }

    @Test
    fun `HISTUI-14 Compose w ui history bez literalow tekstowych widocznych dla uzytkownika`() {
        listOf("HistoryScreen.kt", "HistoryList.kt").forEach { name ->
            val src = File("$ui/history/$name").readText()
            assertFalse(Regex("""Text\(\s*"""").containsMatchIn(src), name)
            assertFalse(Regex("""\btext\s*=\s*"""").containsMatchIn(src), name)
        }
    }
}
