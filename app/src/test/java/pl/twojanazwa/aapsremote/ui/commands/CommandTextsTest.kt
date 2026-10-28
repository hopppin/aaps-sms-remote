package pl.twojanazwa.aapsremote.ui.commands

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.CarbsAmountError
import pl.twojanazwa.aapsremote.commands.CarbsLimits

class CommandTextsTest {
    private val strings: Map<String, String> by lazy {
        val xml = File("src/main/res/values/strings.xml").readText()
        Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun assertDistinctNonZero(ids: List<Int>) {
        ids.forEach { assertNotEquals(0, it) }
        assertEquals(ids.size, ids.toSet().size, "identyfikatory muszą być różne")
    }

    @Test
    fun `CMDUI-25 kazda wartosc CommandMessage ma niezerowy rozny identyfikator`() {
        assertDistinctNonZero(CommandMessage.entries.map { it.textRes() })
    }

    @Test
    fun `CMDUI-25 kazda wartosc CarbsAmountError ma niezerowy rozny identyfikator`() {
        assertDistinctNonZero(CarbsAmountError.entries.map { it.textRes() })
    }

    @Test
    fun `CMDUI-25 ABOVE_MAX podaje MAX_GRAMS jako jedyny argument, BELOW_MIN i EMPTY podaja MIN i MAX`() {
        assertEquals(listOf(CarbsLimits.MAX_GRAMS), CarbsAmountError.ABOVE_MAX.textArgs())
        assertEquals(listOf(CarbsLimits.MIN_GRAMS, CarbsLimits.MAX_GRAMS), CarbsAmountError.BELOW_MIN.textArgs())
        assertEquals(listOf(CarbsLimits.MIN_GRAMS, CarbsLimits.MAX_GRAMS), CarbsAmountError.EMPTY.textArgs())
    }

    @Test
    fun `CMDUI-25 numer z banerem ma niezerowy rozny identyfikator, CONFIGURED daje null`() {
        assertNull(NumberReadiness.CONFIGURED.bannerRes())
        assertDistinctNonZero(
            listOf(NumberReadiness.LOADING, NumberReadiness.NOT_CONFIGURED, NumberReadiness.LOAD_FAILED)
                .map { it.bannerRes()!! },
        )
    }

    @Test
    fun `CMDUI-25 sekret z banerem ma niezerowy rozny identyfikator, UNKNOWN i PRESENT daja null`() {
        assertNull(SecretReadiness.UNKNOWN.bannerRes())
        assertNull(SecretReadiness.PRESENT.bannerRes())
        assertDistinctNonZero(
            listOf(SecretReadiness.ABSENT, SecretReadiness.INVALIDATED).map { it.bannerRes()!! },
        )
    }

    @Test
    fun `CMDUI-25 wspolne teksty ze Spec 008 sa ponownie uzyte, nie kopiowane`() {
        assertEquals(R.string.msg_command_active, CommandMessage.COMMAND_ACTIVE.textRes())
        assertEquals(R.string.sms_denied, CommandMessage.SMS_PERMISSIONS_MISSING.textRes())
        assertEquals(R.string.secret_msg_locked, CommandMessage.SECRET_LOCKED.textRes())
    }

    @Test
    fun `CMDUI-25 klucze commands niepuste z tabeli design`() {
        val keys = listOf(
            "commands_bg", "commands_bg_hint", "commands_pump", "commands_pump_hint",
            "commands_carbs_title", "commands_carbs_label", "commands_carbs_quick", "commands_carbs_next",
            "commands_carbs_error_empty", "commands_carbs_error_invalid_characters",
            "commands_carbs_error_below_min", "commands_carbs_error_above_max",
            "commands_large_title", "commands_large_text", "commands_large_confirm", "commands_large_dismiss",
            "commands_summary_title", "commands_summary_text", "commands_pin_label", "commands_send",
            "commands_open_settings",
            "commands_banner_number_loading", "commands_banner_number_not_configured",
            "commands_banner_number_load_failed", "commands_banner_secret_absent",
            "commands_banner_secret_invalidated",
            "commands_msg_number_loading", "commands_msg_number_missing", "commands_msg_secret_absent",
            "commands_msg_secret_invalidated", "commands_msg_secret_unavailable",
            "commands_msg_start_failed", "commands_msg_action_settings",
        )
        keys.forEach { assertTrue(!strings[it].isNullOrBlank(), "Brak niepustego tekstu: $it") }
    }

    @Test
    fun `CMDUI-25 klucze z argumentem zawieraja procent 1 d`() {
        listOf(
            "commands_carbs_quick", "commands_carbs_error_empty", "commands_carbs_error_below_min",
            "commands_carbs_error_above_max", "commands_large_title", "commands_large_text",
            "commands_large_confirm", "commands_summary_title", "commands_summary_text",
        ).forEach { assertTrue(strings[it]!!.contains("%1\$d"), "Brak %1\$d: $it") }
    }

    @Test
    fun `CMDUI-25 commands_carbs_error_empty zawiera procent 1 d i procent 2 d`() {
        val text = strings["commands_carbs_error_empty"]!!
        assertTrue(text.contains("%1\$d"))
        assertTrue(text.contains("%2\$d"))
    }

    @Test
    fun `CMDUI-25 tresci wiazace ze spec`() {
        assertEquals("Podaj ilość węglowodanów (%1\$d–%2\$d g).", strings["commands_carbs_error_empty"])
        assertEquals("Na pewno %1\$d g?", strings["commands_large_title"])
        assertEquals("Wysłać CARBS %1\$d g do AAPS?", strings["commands_summary_title"])
    }
}
