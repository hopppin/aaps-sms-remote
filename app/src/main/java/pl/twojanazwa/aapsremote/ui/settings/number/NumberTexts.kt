package pl.twojanazwa.aapsremote.ui.settings.number

import androidx.annotation.StringRes
import pl.twojanazwa.aapsremote.R

@StringRes
fun NumberMessage.textRes(): Int = when (this) {
    NumberMessage.SAVED -> R.string.number_msg_saved
    NumberMessage.SAVE_FAILED -> R.string.number_msg_save_failed
    NumberMessage.CLEARED -> R.string.number_msg_cleared
    NumberMessage.CLEAR_FAILED -> R.string.number_msg_clear_failed
    NumberMessage.COMMAND_ACTIVE -> R.string.msg_command_active
}
