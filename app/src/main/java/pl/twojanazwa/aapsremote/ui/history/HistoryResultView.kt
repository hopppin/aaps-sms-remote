package pl.twojanazwa.aapsremote.ui.history

import pl.twojanazwa.aapsremote.ui.commands.ResultKind
import pl.twojanazwa.aapsremote.ui.commands.ResultMessage

data class HistoryResultView(
    /** null = „Nierozpoznany wynik” (HistoryOutcome.Unrecognized). */
    val kind: ResultKind?,
    /** null = „Brak szczegółów wyniku …”. */
    val message: ResultMessage?,
    /** Gramy (CARBS_CONFIRMED) albo minuty (ResultMessage.minutesArg). */
    val messageArg: Int?,
    val codeNotSent: Boolean,
    val unknownWarning: Boolean,
) {
    /** HISTUI-15: bez messageArg (gramy). */
    override fun toString(): String =
        "HistoryResultView(kind=$kind, message=$message, codeNotSent=$codeNotSent, unknownWarning=$unknownWarning)"
}
