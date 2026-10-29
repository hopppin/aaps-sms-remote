package pl.twojanazwa.aapsremote.data

import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandId
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionResult
import pl.twojanazwa.aapsremote.session.UnknownReason

/**
 * DATA-07, DATA-09. Czysta funkcja, nigdy nie rzuca.
 * Kody tekstowe to jawne literały (bez Enum.name, valueOf i ordinal): zmiana nazwy stałej
 * w kodzie nie zmienia zapisanych danych.
 */
object HistoryCodec {

    /** Nie czyta Completed.reply ani treści unexpectedMessages (DATA-08). */
    fun toRow(result: SessionResult): HistoryRow {
        val outcome = result.outcome
        val kind: String
        val reason: String?
        val text: String?
        when (outcome) {
            is SessionOutcome.Completed -> { kind = "COMPLETED"; reason = null; text = outcome.text }
            is SessionOutcome.CarbsConfirmed -> { kind = "CARBS_CONFIRMED"; reason = null; text = outcome.text }
            is SessionOutcome.BolusConfirmed -> { kind = "BOLUS_CONFIRMED"; reason = null; text = outcome.text }
            is SessionOutcome.Failed -> { kind = "FAILED"; reason = encode(outcome.reason); text = outcome.text }
            is SessionOutcome.TimedOut -> { kind = "TIMED_OUT"; reason = encode(outcome.phase); text = null }
            is SessionOutcome.Unknown -> { kind = "UNKNOWN"; reason = encode(outcome.reason); text = outcome.text }
            SessionOutcome.Cancelled -> { kind = "CANCELLED"; reason = null; text = null }
        }
        return HistoryRow(
            id = 0,
            command = encode(result.command.id),
            carbsGrams = (result.command as? Command.Carbs)?.grams,
            bolusCentiUnits = (result.command as? Command.Bolus)?.centiUnits,
            outcome = kind,
            reason = reason,
            startedAtEpochMillis = result.startedAtEpochMillis,
            commandSentAtEpochMillis = result.commandSentAtEpochMillis,
            codeSentAtEpochMillis = result.codeSentAtEpochMillis,
            finishedAtEpochMillis = result.finishedAtEpochMillis,
            replyText = text,
            unexpectedMessageCount = result.unexpectedMessages.size,
        )
    }

    /** Nigdy nie rzuca: nieznane kody daja UNRECOGNIZED / Unrecognized / powod = brak (DATA-09). */
    fun toEntry(row: HistoryRow): HistoryEntry {
        val command = COMMANDS[row.command] ?: HistoryCommand.UNRECOGNIZED
        val reason = row.reason
        val outcome: HistoryOutcome = when (row.outcome) {
            "COMPLETED" -> HistoryOutcome.Completed
            "CARBS_CONFIRMED" -> HistoryOutcome.CarbsConfirmed
            "BOLUS_CONFIRMED" -> HistoryOutcome.BolusConfirmed
            "FAILED" -> HistoryOutcome.Failed(reason?.let { FAILURE_REASONS[it] })
            "TIMED_OUT" -> HistoryOutcome.TimedOut(reason?.let { PHASES[it] })
            "UNKNOWN" -> HistoryOutcome.Unknown(reason?.let { UNKNOWN_REASONS[it] })
            "CANCELLED" -> HistoryOutcome.Cancelled
            else -> HistoryOutcome.Unrecognized
        }
        return HistoryEntry(
            id = row.id,
            command = command,
            carbsGrams = if (command == HistoryCommand.CARBS) row.carbsGrams else null,
            bolusCentiUnits = if (command == HistoryCommand.BOLUS) row.bolusCentiUnits else null,
            outcome = outcome,
            startedAtEpochMillis = row.startedAtEpochMillis,
            commandSentAtEpochMillis = row.commandSentAtEpochMillis,
            codeSentAtEpochMillis = row.codeSentAtEpochMillis,
            finishedAtEpochMillis = row.finishedAtEpochMillis,
            replyText = row.replyText,
            unexpectedMessageCount = row.unexpectedMessageCount,
        )
    }

    // Mapy dekodowania: te same literaly co w encode (bez valueOf i Enum.name).
    private val COMMANDS: Map<String, HistoryCommand> = mapOf(
        "BG" to HistoryCommand.BG,
        "PUMP" to HistoryCommand.PUMP,
        "CARBS" to HistoryCommand.CARBS,
        "BOLUS" to HistoryCommand.BOLUS,
    )

    private val FAILURE_REASONS: Map<String, FailureReason> = mapOf(
        "SEND_FAILED" to FailureReason.SEND_FAILED,
        "UNRECOGNIZED_REPLY" to FailureReason.UNRECOGNIZED_REPLY,
        "AMOUNT_MISMATCH" to FailureReason.AMOUNT_MISMATCH,
        "UNEXPECTED_REPLY" to FailureReason.UNEXPECTED_REPLY,
        "WRONG_CODE" to FailureReason.WRONG_CODE,
        "CODE_REQUEST_EXPIRED" to FailureReason.CODE_REQUEST_EXPIRED,
        "CODE_PREPARATION_TIMEOUT" to FailureReason.CODE_PREPARATION_TIMEOUT,
        "INTERNAL_ERROR" to FailureReason.INTERNAL_ERROR,
    )

    private val UNKNOWN_REASONS: Map<String, UnknownReason> = mapOf(
        "CONFIRMATION_TIMEOUT" to UnknownReason.CONFIRMATION_TIMEOUT,
        "CANCELLED_AFTER_CODE" to UnknownReason.CANCELLED_AFTER_CODE,
        "CODE_SEND_FAILED" to UnknownReason.CODE_SEND_FAILED,
        "UNRECOGNIZED_REPLY" to UnknownReason.UNRECOGNIZED_REPLY,
        "AMOUNT_MISMATCH" to UnknownReason.AMOUNT_MISMATCH,
        "UNEXPECTED_REPLY" to UnknownReason.UNEXPECTED_REPLY,
        "INTERNAL_ERROR" to UnknownReason.INTERNAL_ERROR,
    )

    private val PHASES: Map<String, SessionPhase> = mapOf(
        "NOT_STARTED" to SessionPhase.NOT_STARTED,
        "SENDING_COMMAND" to SessionPhase.SENDING_COMMAND,
        "AWAITING_REPLY" to SessionPhase.AWAITING_REPLY,
        "AWAITING_CODE_REQUEST" to SessionPhase.AWAITING_CODE_REQUEST,
        "PREPARING_CODE" to SessionPhase.PREPARING_CODE,
        "SENDING_CODE" to SessionPhase.SENDING_CODE,
        "AWAITING_CONFIRMATION" to SessionPhase.AWAITING_CONFIRMATION,
        "FINISHED" to SessionPhase.FINISHED,
    )

    private fun encode(id: CommandId): String = when (id) {
        CommandId.BG -> "BG"
        CommandId.PUMP -> "PUMP"
        CommandId.CARBS -> "CARBS"
        CommandId.BOLUS -> "BOLUS"
    }

    private fun encode(reason: FailureReason): String = when (reason) {
        FailureReason.SEND_FAILED -> "SEND_FAILED"
        FailureReason.UNRECOGNIZED_REPLY -> "UNRECOGNIZED_REPLY"
        FailureReason.AMOUNT_MISMATCH -> "AMOUNT_MISMATCH"
        FailureReason.UNEXPECTED_REPLY -> "UNEXPECTED_REPLY"
        FailureReason.WRONG_CODE -> "WRONG_CODE"
        FailureReason.CODE_REQUEST_EXPIRED -> "CODE_REQUEST_EXPIRED"
        FailureReason.CODE_PREPARATION_TIMEOUT -> "CODE_PREPARATION_TIMEOUT"
        FailureReason.INTERNAL_ERROR -> "INTERNAL_ERROR"
    }

    private fun encode(reason: UnknownReason): String = when (reason) {
        UnknownReason.CONFIRMATION_TIMEOUT -> "CONFIRMATION_TIMEOUT"
        UnknownReason.CANCELLED_AFTER_CODE -> "CANCELLED_AFTER_CODE"
        UnknownReason.CODE_SEND_FAILED -> "CODE_SEND_FAILED"
        UnknownReason.UNRECOGNIZED_REPLY -> "UNRECOGNIZED_REPLY"
        UnknownReason.AMOUNT_MISMATCH -> "AMOUNT_MISMATCH"
        UnknownReason.UNEXPECTED_REPLY -> "UNEXPECTED_REPLY"
        UnknownReason.INTERNAL_ERROR -> "INTERNAL_ERROR"
    }

    private fun encode(phase: SessionPhase): String = when (phase) {
        SessionPhase.NOT_STARTED -> "NOT_STARTED"
        SessionPhase.SENDING_COMMAND -> "SENDING_COMMAND"
        SessionPhase.AWAITING_REPLY -> "AWAITING_REPLY"
        SessionPhase.AWAITING_CODE_REQUEST -> "AWAITING_CODE_REQUEST"
        SessionPhase.PREPARING_CODE -> "PREPARING_CODE"
        SessionPhase.SENDING_CODE -> "SENDING_CODE"
        SessionPhase.AWAITING_CONFIRMATION -> "AWAITING_CONFIRMATION"
        SessionPhase.FINISHED -> "FINISHED"
    }
}
