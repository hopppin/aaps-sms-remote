package pl.twojanazwa.aapsremote.session

import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.parser.AapsReply

enum class SessionPhase {
    NOT_STARTED, SENDING_COMMAND, AWAITING_REPLY, AWAITING_CODE_REQUEST,
    PREPARING_CODE, SENDING_CODE, AWAITING_CONFIRMATION, FINISHED,
}

/** result != null wtedy i tylko wtedy, gdy phase == FINISHED. */
data class SessionState(
    val phase: SessionPhase,
    val unexpectedMessages: List<IncomingSms>,
    val result: SessionResult?,
) {
    override fun toString(): String =
        "SessionState(phase=$phase, unexpected=${unexpectedMessages.size}, result=$result)"
}

data class SessionResult(
    val command: Command,
    /** null tylko, gdy zegar nie zwrócił w sesji żadnej poprawnej wartości (SESSION-19). */
    val startedAtEpochMillis: Long?,
    val commandSentAtEpochMillis: Long?,
    val codeSentAtEpochMillis: Long?,
    /** Odczyt przy zakończeniu albo ostatni poprawny odczyt, gdy zegar rzucił (SESSION-19). */
    val finishedAtEpochMillis: Long?,
    val outcome: SessionOutcome,
    val unexpectedMessages: List<IncomingSms>,
) {
    override fun toString(): String =
        "SessionResult(command=$command, outcome=$outcome, unexpected=${unexpectedMessages.size})"
}

sealed interface SessionOutcome {
    /** BG/PUMP. reply to AapsReply.BgReport albo AapsReply.PumpStatus. */
    data class Completed(val reply: AapsReply, val text: String) : SessionOutcome {
        override fun toString(): String = "Completed(***)"
    }

    /** „AAPS potwierdził wprowadzenie X g” – nie dowód wykonania polecenia z aplikacji (A5). */
    data class CarbsConfirmed(val grams: Int, val text: String) : SessionOutcome {
        override fun toString(): String = "CarbsConfirmed(***)"
    }

    data class Failed(val reason: FailureReason, val text: String?) : SessionOutcome {
        override fun toString(): String = "Failed(reason=$reason)"
    }

    /** „AAPS nie odpowiada”. phase: SENDING_COMMAND, AWAITING_REPLY albo AWAITING_CODE_REQUEST. */
    data class TimedOut(val phase: SessionPhase) : SessionOutcome

    /** „Nieznany – sprawdź w AAPS”. Tylko po rozpoczęciu wysyłania kodu (SESSION-14). */
    data class Unknown(val reason: UnknownReason, val text: String?) : SessionOutcome {
        override fun toString(): String = "Unknown(reason=$reason)"
    }

    data object Cancelled : SessionOutcome
}

enum class FailureReason {
    SEND_FAILED, UNRECOGNIZED_REPLY, AMOUNT_MISMATCH, UNEXPECTED_REPLY, WRONG_CODE,
    CODE_REQUEST_EXPIRED, CODE_PREPARATION_TIMEOUT, INTERNAL_ERROR,
}

enum class UnknownReason {
    CONFIRMATION_TIMEOUT, CANCELLED_AFTER_CODE, CODE_SEND_FAILED,
    UNRECOGNIZED_REPLY, AMOUNT_MISMATCH, UNEXPECTED_REPLY, INTERNAL_ERROR,
}
