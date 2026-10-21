package pl.twojanazwa.aapsremote.ui.settings.secret

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecretTextsTest {
    @Test
    fun `UI-20 kazdy SecretMessage ma niezerowy i unikalny zasob`() {
        val ids = SecretMessage.entries.map { it.textRes() }
        assertTrue(ids.all { it != 0 })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `UI-07 kazdy SecretDisplay ma niezerowy i unikalny zasob`() {
        val ids = SecretDisplay.entries.map { it.textRes() }
        assertTrue(ids.all { it != 0 })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `UI-20 strings xml ma niepuste teksty sekretu`() {
        val xml = File("src/main/res/values/strings.xml").readText()
        val strings = Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
            .findAll(xml).associate { it.groupValues[1] to it.groupValues[2] }
        listOf(
            "secret_section_title", "secret_checking", "secret_absent", "secret_present", "secret_invalidated",
            "secret_action_scan", "secret_action_paste", "secret_action_delete", "secret_replace_title",
            "secret_replace_text", "secret_replace_confirm", "secret_delete_title", "secret_delete_text",
            "secret_paste_title", "secret_paste_label", "secret_msg_saved", "secret_msg_locked",
            "secret_msg_key_generation_failed", "secret_msg_save_failed", "secret_msg_not_totp_qr",
            "secret_msg_unsupported_parameters", "secret_msg_invalid_secret", "secret_msg_deleted",
            "secret_msg_delete_failed", "secret_msg_camera_unavailable",
        ).forEach { assertTrue(!strings[it].isNullOrBlank(), "Brak niepustego tekstu: $it") }
    }
}
