package pl.twojanazwa.aapsremote.ui.history

import androidx.annotation.StringRes
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.ui.commands.textRes
import pl.twojanazwa.aapsremote.ui.commands.titleRes

/** HISTUI-14: BG i PUMP używają tekstów Spec 009. CARBS wymaga argumentu %1$d (gramy). */
@StringRes
fun HistoryCommandLabel.textRes(): Int = when (this) {
    HistoryCommandLabel.BG -> R.string.commands_bg
    HistoryCommandLabel.PUMP -> R.string.commands_pump
    HistoryCommandLabel.CARBS -> R.string.history_command_carbs
    HistoryCommandLabel.CARBS_NO_AMOUNT -> R.string.history_command_carbs_no_amount
    HistoryCommandLabel.UNRECOGNIZED -> R.string.history_command_unrecognized
}

@StringRes
fun HistoryTimeLabel.textRes(): Int = when (this) {
    HistoryTimeLabel.STARTED -> R.string.history_time_started
    HistoryTimeLabel.COMMAND_SENT -> R.string.history_time_command_sent
    HistoryTimeLabel.CODE_SENT -> R.string.history_time_code_sent
    HistoryTimeLabel.FINISHED -> R.string.history_time_finished
}

@StringRes
fun HistoryMessage.textRes(): Int = when (this) {
    HistoryMessage.CLEARED -> R.string.history_msg_cleared
    HistoryMessage.CLEAR_FAILED -> R.string.history_msg_clear_failed
}

/** kind == null -> history_kind_unrecognized, inaczej ResultKind.titleRes() (Spec 009). */
@StringRes
fun HistoryResultView.titleRes(): Int = kind?.titleRes() ?: R.string.history_kind_unrecognized

/** message == null -> history_result_details_missing, inaczej ResultMessage.textRes() (Spec 009). */
@StringRes
fun HistoryResultView.messageRes(): Int = message?.textRes() ?: R.string.history_result_details_missing
