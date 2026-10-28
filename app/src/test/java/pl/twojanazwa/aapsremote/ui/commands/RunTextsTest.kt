package pl.twojanazwa.aapsremote.ui.commands

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.CommandId

class RunTextsTest {
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
    fun `CMDUI-25 kazda wartosc ResultKind ma niezerowy rozny identyfikator`() {
        assertDistinctNonZero(ResultKind.entries.map { it.titleRes() })
    }

    @Test
    fun `CMDUI-25 kazda wartosc ResultMessage ma niezerowy rozny identyfikator`() {
        assertDistinctNonZero(ResultMessage.entries.map { it.textRes() })
    }

    @Test
    fun `CMDUI-25 kazda wartosc ProgressStepId ma niezerowy rozny identyfikator`() {
        assertDistinctNonZero(ProgressStepId.entries.map { it.textRes() })
    }

    @Test
    fun `CMDUI-25 klucze run i result sa niepuste`() {
        val keys = listOf(
            "run_title_bg", "run_title_pump", "run_title_carbs",
            "run_step_send_command", "run_step_await_reply", "run_step_await_code_request",
            "run_step_prepare_code", "run_step_send_code", "run_step_await_confirmation",
            "run_step_status_done", "run_step_status_current", "run_step_status_pending",
            "run_foreground_hint", "run_unexpected", "run_cancel", "run_stop_waiting",
            "run_stop_title", "run_stop_text", "run_stop_dismiss",
            "run_exit_title", "run_exit_text", "run_exit_stay", "run_exit_leave",
            "result_kind_success", "result_kind_failure", "result_kind_no_response",
            "result_kind_unknown", "result_kind_cancelled",
            "result_bg_reply", "result_pump_reply", "result_carbs_confirmed", "result_details_missing",
            "result_send_failed",
            "result_unrecognized_reply", "result_amount_mismatch", "result_unexpected_reply",
            "result_wrong_code", "result_code_request_expired", "result_code_preparation_timeout",
            "result_internal_error", "result_no_response", "result_send_timeout",
            "result_unknown_confirmation_timeout", "result_unknown_cancelled_after_code",
            "result_unknown_code_send_failed", "result_unknown_unrecognized_reply",
            "result_unknown_amount_mismatch", "result_unknown_unexpected_reply",
            "result_unknown_internal_error", "result_cancelled", "result_reply_label",
            "result_code_not_sent", "result_unknown_warning", "result_close",
        )
        keys.forEach { assertTrue(!strings[it].isNullOrBlank(), "Brak niepustego tekstu: $it") }
    }

    @Test
    fun `CMDUI-25 klucze z argumentem zawieraja procent 1 d`() {
        listOf(
            "run_title_carbs", "run_step_await_reply", "run_step_await_code_request",
            "run_step_await_confirmation", "run_unexpected", "result_carbs_confirmed",
            "result_no_response", "result_send_timeout", "result_unknown_confirmation_timeout",
        ).forEach { assertTrue(strings[it]!!.contains("%1\$d"), "Brak %1\$d: $it") }
    }

    @Test
    fun `CMDUI-25 tresci wiazace ze spec`() {
        assertEquals("AAPS potwierdził wprowadzenie %1\$d g", strings["result_carbs_confirmed"])
        assertEquals("Nieznany – sprawdź w AAPS", strings["result_kind_unknown"])
        assertEquals(
            "Nie ponawiaj polecenia, zanim nie sprawdzisz w AAPS, czy zostało wykonane.",
            strings["result_unknown_warning"],
        )
    }

    @Test
    fun `CMDUI-25 zasob ma procent 1 d dokladnie gdy ResultMapper podaje messageArg`() {
        val byMessage = ResultMapperTest().resultSamples().map { ResultMapper.map(it) }.associateBy { it.message }
        assertEquals(ResultMessage.entries.toSet(), byMessage.keys)
        val keyPattern = Regex("""R\.string\.(\w+)""")
        val source = File("src/main/java/pl/twojanazwa/aapsremote/ui/commands/RunTexts.kt").readText()
        ResultMessage.entries.forEach { message ->
            val line = source.lines().single { it.trim().startsWith("ResultMessage.${message.name} ->") }
            val key = keyPattern.find(line)!!.groupValues[1]
            val hasArg = strings[key]!!.contains("%1\$d")
            assertEquals(byMessage.getValue(message).messageArg != null, hasArg, "$message / $key")
            assertEquals(message in ResultMapperTest.MESSAGES_WITH_ARG, hasArg, "$message / $key")
        }
    }

    @Test
    fun `CMDUI-25 result_details_missing jest niepusty i bez argumentu`() {
        assertEquals("Brak szczegółów wyniku.", strings["result_details_missing"])
    }

    @Test
    fun `CMDUI-25 trzy zmienione tresci o wysylaniu kodu wg spec`() {
        assertEquals(
            "Kod mógł już dotrzeć do AAPS. AAPS może wykonać polecenie mimo to. " +
                "Wynik będzie nieznany – sprawdź w AAPS.",
            strings["run_stop_text"],
        )
        assertEquals(
            "Kod przekazany do wysłania, ale AAPS nie potwierdził w ciągu %1\$d min.",
            strings["result_unknown_confirmation_timeout"],
        )
        assertEquals(
            "Przestano czekać po przekazaniu kodu do wysłania.",
            strings["result_unknown_cancelled_after_code"],
        )
    }

    @Test
    fun `CMDUI-17 kazdy StepStatus ma niezerowy i rozny opis stanu`() {
        assertDistinctNonZero(StepStatus.entries.map { it.descriptionRes() })
    }

    @Test
    fun `CMDUI-17 kazdy StepStatus wskazuje wlasciwy zasob opisu stanu`() {
        assertEquals(R.string.run_step_status_done, StepStatus.DONE.descriptionRes())
        assertEquals(R.string.run_step_status_current, StepStatus.CURRENT.descriptionRes())
        assertEquals(R.string.run_step_status_pending, StepStatus.PENDING.descriptionRes())
    }

    @Test
    fun `CMDUI-17 opisy stanu krokow to wykonany, w toku, oczekuje`() {
        assertEquals("wykonany", strings["run_step_status_done"])
        assertEquals("w toku", strings["run_step_status_current"])
        assertEquals("oczekuje", strings["run_step_status_pending"])
    }

    @Test
    fun `CMDUI-16 kazdy CommandId ma niezerowy i rozny naglowek przebiegu i wyniku`() {
        assertDistinctNonZero(CommandId.entries.map { it.titleRes() })
    }
}
