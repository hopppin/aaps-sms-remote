package pl.twojanazwa.aapsremote.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandId
import pl.twojanazwa.aapsremote.data.HistoryFixtures.T0
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.UnknownReason

class HistoryCodecDecodeTest {

    // Niezalezna tabela kodow (literaly z design.md); kompletnosc sprawdzana wzgledem entries.
    private val commandCodes = mapOf(CommandId.BG to "BG", CommandId.PUMP to "PUMP", CommandId.CARBS to "CARBS", CommandId.BOLUS to "BOLUS")

    private val failureCodes = mapOf(
        FailureReason.SEND_FAILED to "SEND_FAILED",
        FailureReason.UNRECOGNIZED_REPLY to "UNRECOGNIZED_REPLY",
        FailureReason.AMOUNT_MISMATCH to "AMOUNT_MISMATCH",
        FailureReason.UNEXPECTED_REPLY to "UNEXPECTED_REPLY",
        FailureReason.WRONG_CODE to "WRONG_CODE",
        FailureReason.CODE_REQUEST_EXPIRED to "CODE_REQUEST_EXPIRED",
        FailureReason.CODE_PREPARATION_TIMEOUT to "CODE_PREPARATION_TIMEOUT",
        FailureReason.INTERNAL_ERROR to "INTERNAL_ERROR",
    )

    private val unknownCodes = mapOf(
        UnknownReason.CONFIRMATION_TIMEOUT to "CONFIRMATION_TIMEOUT",
        UnknownReason.CANCELLED_AFTER_CODE to "CANCELLED_AFTER_CODE",
        UnknownReason.CODE_SEND_FAILED to "CODE_SEND_FAILED",
        UnknownReason.UNRECOGNIZED_REPLY to "UNRECOGNIZED_REPLY",
        UnknownReason.AMOUNT_MISMATCH to "AMOUNT_MISMATCH",
        UnknownReason.UNEXPECTED_REPLY to "UNEXPECTED_REPLY",
        UnknownReason.INTERNAL_ERROR to "INTERNAL_ERROR",
    )

    private val phaseCodes = mapOf(
        SessionPhase.NOT_STARTED to "NOT_STARTED",
        SessionPhase.SENDING_COMMAND to "SENDING_COMMAND",
        SessionPhase.AWAITING_REPLY to "AWAITING_REPLY",
        SessionPhase.AWAITING_CODE_REQUEST to "AWAITING_CODE_REQUEST",
        SessionPhase.PREPARING_CODE to "PREPARING_CODE",
        SessionPhase.SENDING_CODE to "SENDING_CODE",
        SessionPhase.AWAITING_CONFIRMATION to "AWAITING_CONFIRMATION",
        SessionPhase.FINISHED to "FINISHED",
    )

    private fun row(
        command: String = "BG",
        carbs: Int? = null,
        outcome: String = "COMPLETED",
        reason: String? = null,
        text: String? = "tekst",
    ) = HistoryRow(7, command, carbs, outcome, reason, T0, T0 + 1000, T0 + 2000, T0 + 3000, text, 3)

    // ---- Kompletnosc tabeli kodow wzgledem wartosci wyliczeniowych ----

    @Test
    fun `DATA-09 tabela kodow obejmuje wszystkie wartosci enumow`() {
        assertEquals(CommandId.entries.toSet(), commandCodes.keys)
        assertEquals(FailureReason.entries.toSet(), failureCodes.keys)
        assertEquals(UnknownReason.entries.toSet(), unknownCodes.keys)
        assertEquals(SessionPhase.entries.toSet(), phaseCodes.keys)
        assertEquals(4, commandCodes.size)
        assertEquals(8, failureCodes.size)
        assertEquals(7, unknownCodes.size)
        assertEquals(8, phaseCodes.size)
    }

    @ParameterizedTest
    @EnumSource(CommandId::class)
    fun `DATA-09 polecenie round-trip i tekst z tabeli`(id: CommandId) {
        val command = when (id) {
            CommandId.BG -> Command.Bg
            CommandId.PUMP -> Command.Pump
            CommandId.CARBS -> HistoryFixtures.carbs20
            CommandId.BOLUS -> HistoryFixtures.bolus
        }
        val row = HistoryCodec.toRow(HistoryFixtures.withOutcome(SessionOutcome.Cancelled).copy(command = command))
        assertEquals(commandCodes.getValue(id), row.command)
        val expected = when (id) {
            CommandId.BG -> HistoryCommand.BG
            CommandId.PUMP -> HistoryCommand.PUMP
            CommandId.CARBS -> HistoryCommand.CARBS
            CommandId.BOLUS -> HistoryCommand.UNRECOGNIZED // do zad. 4 spec 012
        }
        assertEquals(expected, HistoryCodec.toEntry(row).command)
    }

    @ParameterizedTest
    @EnumSource(FailureReason::class)
    fun `DATA-09 powod FAILED round-trip i tekst z tabeli`(reason: FailureReason) {
        val row = HistoryCodec.toRow(HistoryFixtures.withOutcome(SessionOutcome.Failed(reason, null)))
        assertEquals("FAILED", row.outcome)
        assertEquals(failureCodes.getValue(reason), row.reason)
        assertEquals(HistoryOutcome.Failed(reason), HistoryCodec.toEntry(row).outcome)
    }

    @ParameterizedTest
    @EnumSource(UnknownReason::class)
    fun `DATA-09 powod UNKNOWN round-trip i tekst z tabeli`(reason: UnknownReason) {
        val row = HistoryCodec.toRow(HistoryFixtures.withOutcome(SessionOutcome.Unknown(reason, null)))
        assertEquals("UNKNOWN", row.outcome)
        assertEquals(unknownCodes.getValue(reason), row.reason)
        assertEquals(HistoryOutcome.Unknown(reason), HistoryCodec.toEntry(row).outcome)
    }

    @ParameterizedTest
    @EnumSource(SessionPhase::class)
    fun `DATA-09 faza TIMED_OUT round-trip i tekst z tabeli`(phase: SessionPhase) {
        val row = HistoryCodec.toRow(HistoryFixtures.withOutcome(SessionOutcome.TimedOut(phase)))
        assertEquals("TIMED_OUT", row.outcome)
        assertEquals(phaseCodes.getValue(phase), row.reason)
        assertEquals(HistoryOutcome.TimedOut(phase), HistoryCodec.toEntry(row).outcome)
    }

    @Test
    fun `DATA-09 rodzaje wyniku round-trip i tekst z tabeli`() {
        val cases = listOf(
            Triple(HistoryFixtures.bgCompleted(), "COMPLETED", HistoryOutcome.Completed),
            Triple(HistoryFixtures.carbsConfirmed(), "CARBS_CONFIRMED", HistoryOutcome.CarbsConfirmed),
            Triple(HistoryFixtures.sendFailed(), "FAILED", HistoryOutcome.Failed(FailureReason.SEND_FAILED)),
            Triple(HistoryFixtures.timedOut(), "TIMED_OUT", HistoryOutcome.TimedOut(SessionPhase.AWAITING_REPLY)),
            Triple(
                HistoryFixtures.confirmationTimeout(), "UNKNOWN",
                HistoryOutcome.Unknown(UnknownReason.CONFIRMATION_TIMEOUT),
            ),
            Triple(HistoryFixtures.cancelledNoTimes(), "CANCELLED", HistoryOutcome.Cancelled),
        )
        assertEquals(6, cases.size)
        for ((result, text, outcome) in cases) {
            val row = HistoryCodec.toRow(result)
            assertEquals(text, row.outcome)
            assertEquals(outcome, HistoryCodec.toEntry(row).outcome)
        }
    }

    // ---- Wartosci nieznane ----

    @Test
    fun `DATA-09 nieznane polecenie daje UNRECOGNIZED bez ilosci`() {
        val entry = HistoryCodec.toEntry(row(command = "TBR", carbs = 20))
        assertEquals(HistoryCommand.UNRECOGNIZED, entry.command)
        assertNull(entry.carbsGrams)
    }

    @Test
    fun `DATA-09 nieznany rodzaj daje Unrecognized i brak powodu`() {
        val entry = HistoryCodec.toEntry(row(outcome = "PARTIAL", reason = "SEND_FAILED"))
        assertEquals(HistoryOutcome.Unrecognized, entry.outcome)
    }

    @Test
    fun `DATA-09 nieznany powod FAILED daje brak powodu i zachowuje pola`() {
        val entry = HistoryCodec.toEntry(row(outcome = "FAILED", reason = "NO_SIGNAL"))
        assertEquals(HistoryOutcome.Failed(null), entry.outcome)
        assertEquals(7L, entry.id)
        assertEquals(T0, entry.startedAtEpochMillis)
        assertEquals(T0 + 1000, entry.commandSentAtEpochMillis)
        assertEquals(T0 + 2000, entry.codeSentAtEpochMillis)
        assertEquals(T0 + 3000, entry.finishedAtEpochMillis)
        assertEquals("tekst", entry.replyText)
        assertEquals(3, entry.unexpectedMessageCount)
    }

    @Test
    fun `DATA-09 nieznany albo brakujacy powod UNKNOWN i TIMED_OUT daje brak powodu`() {
        assertEquals(HistoryOutcome.Unknown(null), HistoryCodec.toEntry(row(outcome = "UNKNOWN", reason = "NO_SIGNAL")).outcome)
        assertEquals(HistoryOutcome.Unknown(null), HistoryCodec.toEntry(row(outcome = "UNKNOWN", reason = null)).outcome)
        assertEquals(HistoryOutcome.TimedOut(null), HistoryCodec.toEntry(row(outcome = "TIMED_OUT", reason = "NO_SIGNAL")).outcome)
        assertEquals(HistoryOutcome.TimedOut(null), HistoryCodec.toEntry(row(outcome = "TIMED_OUT", reason = null)).outcome)
        assertEquals(HistoryOutcome.Failed(null), HistoryCodec.toEntry(row(outcome = "FAILED", reason = null)).outcome)
    }

    @Test
    fun `DATA-09 powod dekodowany mapa wlasciwa dla rodzaju`() {
        assertEquals(
            HistoryOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY),
            HistoryCodec.toEntry(row(outcome = "FAILED", reason = "UNRECOGNIZED_REPLY")).outcome,
        )
        assertEquals(
            HistoryOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY),
            HistoryCodec.toEntry(row(outcome = "UNKNOWN", reason = "UNRECOGNIZED_REPLY")).outcome,
        )
        // powod z innej grupy nie jest rozpoznawany
        assertEquals(
            HistoryOutcome.Failed(null),
            HistoryCodec.toEntry(row(outcome = "FAILED", reason = "CONFIRMATION_TIMEOUT")).outcome,
        )
    }

    @Test
    fun `DATA-09 powod przy COMPLETED CARBS_CONFIRMED i CANCELLED jest pominiety`() {
        assertEquals(HistoryOutcome.Completed, HistoryCodec.toEntry(row(outcome = "COMPLETED", reason = "WRONG_CODE")).outcome)
        assertEquals(
            HistoryOutcome.CarbsConfirmed,
            HistoryCodec.toEntry(row(outcome = "CARBS_CONFIRMED", reason = "WRONG_CODE")).outcome,
        )
        assertEquals(HistoryOutcome.Cancelled, HistoryCodec.toEntry(row(outcome = "CANCELLED", reason = "WRONG_CODE")).outcome)
    }

    @Test
    fun `DATA-09 ilosc przy poleceniu innym niz CARBS jest pominieta`() {
        assertNull(HistoryCodec.toEntry(row(command = "BG", carbs = 20)).carbsGrams)
        assertNull(HistoryCodec.toEntry(row(command = "PUMP", carbs = 20)).carbsGrams)
        assertEquals(20, HistoryCodec.toEntry(row(command = "CARBS", carbs = 20)).carbsGrams)
    }

    // ---- DATA-07: wpis ----

    @Test
    fun `DATA-07 tresc 2000 znakow przechodzi przez wiersz i wpis bez zmian`() {
        val text = "a".repeat(2000)
        val result = HistoryFixtures.withOutcome(SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, text))
        val entry = HistoryCodec.toEntry(HistoryCodec.toRow(result))
        assertEquals(2000, entry.replyText?.length)
        assertEquals(text, entry.replyText)
    }

    @Test
    fun `DATA-07 wpis BG Completed`() {
        val e = HistoryCodec.toEntry(HistoryCodec.toRow(HistoryFixtures.bgCompleted()))
        assertEquals(
            HistoryEntry(
                0, HistoryCommand.BG, null, HistoryOutcome.Completed, T0, T0 + 1000, null, T0 + 5000,
                AapsSamples.bg, 0,
            ),
            e,
        )
    }

    @Test
    fun `DATA-07 wpis CARBS 20 CarbsConfirmed`() {
        val e = HistoryCodec.toEntry(HistoryCodec.toRow(HistoryFixtures.carbsConfirmed()))
        assertEquals(
            HistoryEntry(
                0, HistoryCommand.CARBS, 20, HistoryOutcome.CarbsConfirmed, T0, T0 + 1000, T0 + 61000,
                T0 + 120000, AapsSamples.carbsDone, 2,
            ),
            e,
        )
    }

    @Test
    fun `DATA-07 wpisy Failed Unknown TimedOut i Cancelled`() {
        fun entry(r: pl.twojanazwa.aapsremote.session.SessionResult) = HistoryCodec.toEntry(HistoryCodec.toRow(r))
        val wrong = entry(HistoryFixtures.wrongCode())
        assertEquals(HistoryOutcome.Failed(FailureReason.WRONG_CODE), wrong.outcome)
        assertEquals(AapsSamples.wrongCode, wrong.replyText)
        val send = entry(HistoryFixtures.sendFailed())
        assertEquals(HistoryOutcome.Failed(FailureReason.SEND_FAILED), send.outcome)
        assertNull(send.replyText)
        val timeout = entry(HistoryFixtures.confirmationTimeout())
        assertEquals(HistoryOutcome.Unknown(UnknownReason.CONFIRMATION_TIMEOUT), timeout.outcome)
        assertNull(timeout.replyText)
        val mismatch = entry(HistoryFixtures.unknownMismatch())
        assertEquals(HistoryOutcome.Unknown(UnknownReason.AMOUNT_MISMATCH), mismatch.outcome)
        assertEquals(AapsSamples.carbsCodeRequest, mismatch.replyText)
        val timed = entry(HistoryFixtures.timedOut())
        assertEquals(HistoryOutcome.TimedOut(SessionPhase.AWAITING_REPLY), timed.outcome)
        assertNull(timed.replyText)
        val cancelled = entry(HistoryFixtures.cancelledNoTimes())
        assertEquals(HistoryOutcome.Cancelled, cancelled.outcome)
        assertNull(cancelled.startedAtEpochMillis)
        assertNull(cancelled.commandSentAtEpochMillis)
        assertNull(cancelled.codeSentAtEpochMillis)
        assertNull(cancelled.finishedAtEpochMillis)
    }
}
