package pl.twojanazwa.aapsremote.ui.commands

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class CommandsSourcesTest {
    private val commandsDirectory = File("src/main/java/pl/twojanazwa/aapsremote/ui/commands")
    private val strings = File("src/main/res/values/strings.xml").readText()

    @Test
    fun `CMDUI-01 zrodla nie zawieraja zaslepki ani jej tekstu`() {
        assertFalse(File(commandsDirectory, "CommandsPlaceholderScreen.kt").exists())
        assertFalse("placeholder_commands_text" in strings)
    }

    @Test
    fun `CMDUI-01 ekran polecen nie zapisuje pola przez stan Compose`() {
        val sources = commandsDirectory.listFiles()
            .orEmpty()
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse("rememberSaveable" in sources)
        assertFalse("SavedStateHandle" in sources)
    }
}
