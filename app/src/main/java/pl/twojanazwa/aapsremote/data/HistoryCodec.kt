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
            is SessionOutcome.Failed -> { kind = "FAILED"; reason = encode(outcome.reason); text = outcome.text }
            is SessionOutcome.TimedOut -> { kind = "TIMED_OUT"; reason = encode(outcome.phase); text = null }
            is SessionOutcome.Unknown -> { kind = "UNKNOWN"; reason = encode(outcome.reason); text = outcome.text }
            SessionOutcome.Cancelled -> { kind = "CANCELLED"; reason = null; text = null }
        }
        return HistoryRow(
            id = 0,
            command = encode(result.command.id),
            carbsGrams = (result.command as? Command.Carbs)?.grams,
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

    private fun encode(id: CommandId): String = when (id) {
        CommandId.BG -> "BG"
        CommandId.PUMP -> "PUMP"
        CommandId.CARBS -> "CARBS"
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
