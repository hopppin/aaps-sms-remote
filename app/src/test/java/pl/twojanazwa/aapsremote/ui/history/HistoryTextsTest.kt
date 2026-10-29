package pl.twojanazwa.aapsremote.ui.history

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.ui.commands.ResultKind
import pl.twojanazwa.aapsremote.ui.commands.ResultMessage
import pl.twojanazwa.aapsremote.ui.commands.textRes
import pl.twojanazwa.aapsremote.ui.commands.titleRes

class HistoryTextsTest {
    private val strings: Map<String, String> by lazy {
        val xml = File("src/main/res/values/strings.xml").readText()
        Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private val historyKeys = listOf(
        "history_limit_info", "history_loading", "history_empty", "history_unavailable",
        "history_retry", "history_command_carbs", "history_command_carbs_no_amount",
        "history_command_unrecognized", "history_kind_unrecognized", "history_result_details_missing",
        "history_details_title", "history_time_started", "history_time_command_sent",
        "history_time_code_sent", "history_time_finished", "history_time_none", "history_clear",
        "history_clear_title", "history_clear_text", "history_clear_confirm",
        "history_msg_cleared", "history_msg_clear_failed", "history_result_profile_list_reply",
    )

    private fun assertDistinctNonZero(ids: List<Int>) {
        ids.forEach { assertNotEquals(0, it) }
        assertEquals(ids.size, ids.toSet().size, "identyfikatory muszą być różne")
    }

    private fun view(kind: ResultKind?, message: ResultMessage?) =
        HistoryResultView(kind, message, null, codeNotSent = false, unknownWarning = false)

    @Test
    fun `HISTUI-14 kazda etykieta polecenia ma niezerowy rozny identyfikator`() {
        assertDistinctNonZero(HistoryCommandLabel.entries.map { it.textRes() })
        assertEquals(R.string.commands_bg, HistoryCommandLabel.BG.textRes())
        assertEquals(R.string.commands_pump, HistoryCommandLabel.PUMP.textRes())
    }

    @Test
    fun `HISTUI-14 kazda etykieta czasu ma niezerowy rozny identyfikator`() {
        assertDistinctNonZero(HistoryTimeLabel.entries.map { it.textRes() })
    }

    @Test
    fun `HISTUI-14 kazdy komunikat historii ma niezerowy rozny identyfikator`() {
        assertDistinctNonZero(HistoryMessage.entries.map { it.textRes() })
    }

    @Test
    fun `HISTUI-14 tytul wyniku dla kind null i kazdego rodzaju jest niezerowy i rozny`() {
        val ids = ResultKind.entries.map { view(it, null).titleRes() } + view(null, null).titleRes()
        assertDistinctNonZero(ids)
        assertEquals(R.string.history_kind_unrecognized, view(null, null).titleRes())
        ResultKind.entries.forEach { assertEquals(it.titleRes(), view(it, null).titleRes()) }
    }

    @Test
    fun `HISTUI-14 komunikat wyniku dla message null i kazdego komunikatu jest niezerowy i rozny`() {
        val ids = ResultMessage.entries.map { view(null, it).messageRes() } + view(null, null).messageRes()
        assertDistinctNonZero(ids)
        assertEquals(R.string.history_result_details_missing, view(null, null).messageRes())
        ResultMessage.entries.filter { it != ResultMessage.PROFILE_LIST_REPLY }
            .forEach { assertEquals(it.textRes(), view(null, it).messageRes()) }
    }

    @Test
    fun `PROF-17 historia PROFILE LIST ma neutralny tekst bez Wybierz profil`() {
        val id = view(ResultKind.SUCCESS, ResultMessage.PROFILE_LIST_REPLY).messageRes()
        assertEquals(R.string.history_result_profile_list_reply, id)
        assertEquals("Odpowiedź AAPS na PROFILE LIST.", strings["history_result_profile_list_reply"])
        assertFalse(strings.getValue("history_result_profile_list_reply").contains("Wybierz"))
        assertNotEquals(R.string.result_profile_list_reply, id)
    }

    @Test
    fun `HISTUI-14 kazdy nowy klucz history ma niepusty tekst`() {
        assertEquals(23, historyKeys.size)
        historyKeys.forEach { assertTrue(!strings[it].isNullOrBlank(), "Brak niepustego tekstu: $it") }
    }

    @Test
    fun `HISTUI-14 klucze z argumentem zawieraja placeholder d`() {
        listOf("history_limit_info", "history_command_carbs", "history_clear_text").forEach {
            assertTrue(strings.getValue(it).contains("%1\$d"), "Brak %1\$d: $it")
        }
    }

    @Test
    fun `HISTUI-14 zadna tresc history nie jest kopia tresci result`() {
        val results = strings.filterKeys { it.startsWith("result_") }.values.toSet()
        strings.filterKeys { it.startsWith("history_") }.forEach { (key, text) ->
            assertTrue(text !in results, "Kopia tresci result_*: $key")
        }
    }

    @Test
    fun `HISTUI-14 tresci zgodne ze specyfikacja`() {
        assertEquals(
            "Brak poleceń w historii. Wynik każdego polecenia pojawi się tutaj po jego zakończeniu.",
            strings["history_empty"],
        )
        assertEquals("Nie udało się wczytać historii.", strings["history_unavailable"])
        assertEquals(
            "Wszystkie wpisy (%1\$d) zostaną usunięte. Tej operacji nie można cofnąć.",
            strings["history_clear_text"],
        )
        assertEquals("Historia wyczyszczona.", strings["history_msg_cleared"])
        assertEquals("Nie udało się wyczyścić historii. Spróbuj ponownie.", strings["history_msg_clear_failed"])
        assertEquals("Aplikacja przechowuje %1\$d ostatnich poleceń.", strings["history_limit_info"])
        assertEquals("CARBS %1\$d g", strings["history_command_carbs"])
        assertEquals(
            "Brak szczegółów wyniku (wpis z innej wersji aplikacji).",
            strings["history_result_details_missing"],
        )
    }
}
