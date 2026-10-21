package pl.twojanazwa.aapsremote.ui.navigation

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationStringsTest {
    private val strings: Map<String, String> by lazy {
        val xml = File("src/main/res/values/strings.xml").readText()
        Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun assertNotBlank(key: String) {
        assertTrue(!strings[key].isNullOrBlank(), "Brak niepustego tekstu: $key")
    }

    @Test
    fun `UI-03 zaslepki Polecenia i Historia maja niepuste teksty`() {
        assertNotBlank("placeholder_commands_text")
        assertNotBlank("placeholder_history_text")
    }

    @Test
    fun `UI-20 teksty nawigacji i wspolnych przyciskow sa niepuste`() {
        listOf(
            "nav_commands", "nav_history", "nav_settings",
            "action_save", "action_cancel", "action_delete", "action_open_app_settings",
        ).forEach(::assertNotBlank)
    }
}
