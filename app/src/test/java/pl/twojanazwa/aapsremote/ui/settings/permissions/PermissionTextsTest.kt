package pl.twojanazwa.aapsremote.ui.settings.permissions

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.ui.permissions.PermissionDenial
import pl.twojanazwa.aapsremote.ui.permissions.SmsPermissionsUiState

class PermissionTextsTest {
    private val strings: Map<String, String> by lazy {
        val xml = File("src/main/res/values/strings.xml").readText()
        Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun `UI-20 odwzorowanie brakujacych uprawnien daje niezerowe rozne identyfikatory`() {
        val both = PermissionTexts.missing(SmsPermissionsUiState(canSend = false, canReceive = false))
        assertEquals(listOf(R.string.sms_permission_send, R.string.sms_permission_receive), both)
        assertTrue(both.all { it != 0 })
        assertNotEquals(both[0], both[1])
        assertEquals(
            listOf(R.string.sms_permission_receive),
            PermissionTexts.missing(SmsPermissionsUiState(canSend = true, canReceive = false)),
        )
        assertTrue(PermissionTexts.missing(SmsPermissionsUiState(true, true)).isEmpty())
    }

    @Test
    fun `UI-20 odwzorowanie odmowy daje tekst tylko przy odmowie`() {
        assertNull(PermissionTexts.denial(PermissionDenial.NONE))
        assertEquals(R.string.sms_denied, PermissionTexts.denial(PermissionDenial.DENIED))
        assertEquals(R.string.sms_denied, PermissionTexts.denial(PermissionDenial.PERMANENTLY_DENIED))
    }

    @Test
    fun `UI-18 przycisk ustawien tylko przy trwalej odmowie`() {
        assertFalse(PermissionTexts.showOpenSettings(PermissionDenial.NONE))
        assertFalse(PermissionTexts.showOpenSettings(PermissionDenial.DENIED))
        assertTrue(PermissionTexts.showOpenSettings(PermissionDenial.PERMANENTLY_DENIED))
    }

    @Test
    fun `UI-20 strings xml ma niepusty tekst dla kluczy sms`() {
        listOf(
            "sms_section_title", "sms_all_granted", "sms_missing_label", "sms_permission_send",
            "sms_permission_receive", "sms_action_request", "sms_denied",
        ).forEach { assertTrue(!strings[it].isNullOrBlank(), "Brak tekstu: $it") }
    }
}
