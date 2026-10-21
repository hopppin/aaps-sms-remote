package pl.twojanazwa.aapsremote.ui.settings.number

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NumberTextsTest {
    @Test
    fun `UI-20 kazdy NumberMessage ma niezerowy i unikalny zasob`() {
        val ids = NumberMessage.entries.map { it.textRes() }
        assertTrue(ids.all { it != 0 })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `UI-20 strings xml ma niepuste teksty numeru i blokady`() {
        val xml = File("src/main/res/values/strings.xml").readText()
        val strings = Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
            .findAll(xml).associate { it.groupValues[1] to it.groupValues[2] }
        listOf(
            "settings_command_active", "msg_command_active", "number_section_title", "number_loading",
            "number_not_configured", "number_load_failed", "number_action_set", "number_action_change",
            "number_field_label", "number_error_invalid", "number_clear_title", "number_clear_text",
            "number_msg_saved", "number_msg_save_failed", "number_msg_cleared", "number_msg_clear_failed",
        ).forEach { assertTrue(!strings[it].isNullOrBlank(), "Brak niepustego tekstu: $it") }
    }
}
