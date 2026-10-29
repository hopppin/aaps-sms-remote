package pl.twojanazwa.aapsremote.ui.commands

import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionTimeouts
import pl.twojanazwa.aapsremote.session.UnknownReason

/** Wspólne odwzorowanie powodów na komunikaty (CMDUI-19, HISTUI-04). */

/** Wartość [ResultMessage] o tej samej nazwie. Wyczerpujący when. */
fun FailureReason.toResultMessage(): ResultMessage = when (this) {
    FailureReason.SEND_FAILED -> ResultMessage.SEND_FAILED
    FailureReason.UNRECOGNIZED_REPLY -> ResultMessage.UNRECOGNIZED_REPLY
    FailureReason.AMOUNT_MISMATCH -> ResultMessage.AMOUNT_MISMATCH
    FailureReason.UNEXPECTED_REPLY -> ResultMessage.UNEXPECTED_REPLY
    FailureReason.WRONG_CODE -> ResultMessage.WRONG_CODE
    FailureReason.CODE_REQUEST_EXPIRED -> ResultMessage.CODE_REQUEST_EXPIRED
    FailureReason.CODE_PREPARATION_TIMEOUT -> ResultMessage.CODE_PREPARATION_TIMEOUT
    FailureReason.INTERNAL_ERROR -> ResultMessage.INTERNAL_ERROR
}

/** UNKNOWN_<nazwa>. Wyczerpujący when. */
fun UnknownReason.toResultMessage(): ResultMessage = when (this) {
    UnknownReason.CONFIRMATION_TIMEOUT -> ResultMessage.UNKNOWN_CONFIRMATION_TIMEOUT
    UnknownReason.CANCELLED_AFTER_CODE -> ResultMessage.UNKNOWN_CANCELLED_AFTER_CODE
    UnknownReason.CODE_SEND_FAILED -> ResultMessage.UNKNOWN_CODE_SEND_FAILED
    UnknownReason.UNRECOGNIZED_REPLY -> ResultMessage.UNKNOWN_UNRECOGNIZED_REPLY
    UnknownReason.AMOUNT_MISMATCH -> ResultMessage.UNKNOWN_AMOUNT_MISMATCH
    UnknownReason.UNEXPECTED_REPLY -> ResultMessage.UNKNOWN_UNEXPECTED_REPLY
    UnknownReason.INTERNAL_ERROR -> ResultMessage.UNKNOWN_INTERNAL_ERROR
}

/** BOL-16: bolus ma osobne teksty niezgodnej ilości i nierozpoznanej odpowiedzi po kodzie. */
fun ResultMessage.forBolus(): ResultMessage = when (this) {
    ResultMessage.AMOUNT_MISMATCH -> ResultMessage.BOLUS_AMOUNT_MISMATCH
    ResultMessage.UNKNOWN_UNRECOGNIZED_REPLY -> ResultMessage.BOLUS_UNKNOWN_UNRECOGNIZED_REPLY
    else -> this
}

/** SENDING_COMMAND -> SEND_TIMEOUT; każda inna faza i null -> NO_RESPONSE. */
fun timedOutResultMessage(phase: SessionPhase?): ResultMessage = when (phase) {
    SessionPhase.SENDING_COMMAND -> ResultMessage.SEND_TIMEOUT
    null,
    SessionPhase.NOT_STARTED,
    SessionPhase.AWAITING_REPLY,
    SessionPhase.AWAITING_CODE_REQUEST,
    SessionPhase.PREPARING_CODE,
    SessionPhase.SENDING_CODE,
    SessionPhase.AWAITING_CONFIRMATION,
    SessionPhase.FINISHED,
    -> ResultMessage.NO_RESPONSE
}

/** NO_RESPONSE, SEND_TIMEOUT -> reply w min; UNKNOWN_CONFIRMATION_TIMEOUT -> confirmation w min; inne -> null. */
fun ResultMessage.minutesArg(timeouts: SessionTimeouts = SessionTimeouts()): Int? = when (this) {
    ResultMessage.NO_RESPONSE,
    ResultMessage.SEND_TIMEOUT,
    -> timeouts.reply.inWholeMinutes.toInt()
    ResultMessage.UNKNOWN_CONFIRMATION_TIMEOUT -> timeouts.confirmation.inWholeMinutes.toInt()
    ResultMessage.BG_REPLY,
    ResultMessage.PUMP_REPLY,
    ResultMessage.CARBS_CONFIRMED,
    ResultMessage.BOLUS_CONFIRMED,
    ResultMessage.BOLUS_AMOUNT_MISMATCH,
    ResultMessage.BOLUS_UNKNOWN_UNRECOGNIZED_REPLY,
    ResultMessage.DETAILS_MISSING,
    ResultMessage.SEND_FAILED,
    ResultMessage.UNRECOGNIZED_REPLY,
    ResultMessage.AMOUNT_MISMATCH,
    ResultMessage.UNEXPECTED_REPLY,
    ResultMessage.WRONG_CODE,
    ResultMessage.CODE_REQUEST_EXPIRED,
    ResultMessage.CODE_PREPARATION_TIMEOUT,
    ResultMessage.INTERNAL_ERROR,
    ResultMessage.UNKNOWN_CANCELLED_AFTER_CODE,
    ResultMessage.UNKNOWN_CODE_SEND_FAILED,
    ResultMessage.UNKNOWN_UNRECOGNIZED_REPLY,
    ResultMessage.UNKNOWN_AMOUNT_MISMATCH,
    ResultMessage.UNKNOWN_UNEXPECTED_REPLY,
    ResultMessage.UNKNOWN_INTERNAL_ERROR,
    ResultMessage.CANCELLED,
    -> null
}
