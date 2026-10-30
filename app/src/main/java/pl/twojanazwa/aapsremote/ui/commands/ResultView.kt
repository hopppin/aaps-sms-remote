package pl.twojanazwa.aapsremote.ui.commands

import pl.twojanazwa.aapsremote.commands.CommandId

enum class ResultKind { SUCCESS, FAILURE, NO_RESPONSE, UNKNOWN, CANCELLED }

enum class ResultMessage {
    BG_REPLY, PUMP_REPLY, CARBS_CONFIRMED, BOLUS_CONFIRMED,
    /** Bolus: osobne teksty niezgodnej ilości przed kodem i nierozpoznanej odpowiedzi po kodzie (BOL-16). */
    BOLUS_AMOUNT_MISMATCH, BOLUS_UNKNOWN_UNRECOGNIZED_REPLY,
    /** Profil (PROF-16): odpowiedzi na PROFILE STATUS / LIST i niezgodność lub nierozpoznana odpowiedź zmiany profilu. */
    PROFILE_STATUS_REPLY, PROFILE_LIST_REPLY, PROFILE_AMOUNT_MISMATCH, PROFILE_UNKNOWN_AMOUNT_MISMATCH,
    PROFILE_UNKNOWN_UNRECOGNIZED_REPLY,
    /** Completed dla CARBS (nie występuje, Spec 004); jak „brak szczegółów” w HISTUI-04 (Spec 010). */
    DETAILS_MISSING,
    SEND_FAILED, UNRECOGNIZED_REPLY, AMOUNT_MISMATCH, UNEXPECTED_REPLY, WRONG_CODE,
    CODE_REQUEST_EXPIRED, CODE_PREPARATION_TIMEOUT, INTERNAL_ERROR,
    NO_RESPONSE, SEND_TIMEOUT,
    UNKNOWN_CONFIRMATION_TIMEOUT, UNKNOWN_CANCELLED_AFTER_CODE, UNKNOWN_CODE_SEND_FAILED,
    UNKNOWN_UNRECOGNIZED_REPLY, UNKNOWN_AMOUNT_MISMATCH, UNKNOWN_UNEXPECTED_REPLY, UNKNOWN_INTERNAL_ERROR,
    CANCELLED,
}

data class ResultView(
    val commandId: CommandId,
    /** Ilość z polecenia CARBS (nagłówek „Polecenie CARBS X g”), null dla BG/PUMP. */
    val commandGrams: Int?,
    /** Ilość z polecenia BOLUS w setnych (nagłówek „Polecenie BOLUS X j.”), inaczej null. */
    val commandCentiUnits: Int? = null,
    /** Numer i procent z polecenia zmiany profilu (nagłówek PROF-16), inaczej null. */
    val profileNumber: Int? = null,
    val profilePercent: Int? = null,
    val kind: ResultKind,
    val message: ResultMessage,
    /** Argument komunikatu: gramy (CARBS_CONFIRMED) albo minuty (NO_RESPONSE, SEND_TIMEOUT, UNKNOWN_CONFIRMATION_TIMEOUT). */
    val messageArg: Int?,
    val replyText: String?,
    val codeNotSent: Boolean,
    val unknownWarning: Boolean,
    val unexpectedCount: Int,
) {
    /** CMDUI-26: bez gramów, argumentu i treści SMS. */
    override fun toString(): String =
        "ResultView(commandId=$commandId, kind=$kind, message=$message, ***)"
}
