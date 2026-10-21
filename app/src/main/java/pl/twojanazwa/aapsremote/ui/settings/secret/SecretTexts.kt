package pl.twojanazwa.aapsremote.ui.settings.secret

import androidx.annotation.StringRes
import pl.twojanazwa.aapsremote.R

@StringRes
fun SecretMessage.textRes(): Int = when (this) {
    SecretMessage.SAVED -> R.string.secret_msg_saved
    SecretMessage.LOCKED -> R.string.secret_msg_locked
    SecretMessage.KEY_GENERATION_FAILED -> R.string.secret_msg_key_generation_failed
    SecretMessage.SAVE_FAILED -> R.string.secret_msg_save_failed
    SecretMessage.NOT_TOTP_QR -> R.string.secret_msg_not_totp_qr
    SecretMessage.UNSUPPORTED_PARAMETERS -> R.string.secret_msg_unsupported_parameters
    SecretMessage.INVALID_SECRET -> R.string.secret_msg_invalid_secret
    SecretMessage.DELETED -> R.string.secret_msg_deleted
    SecretMessage.DELETE_FAILED -> R.string.secret_msg_delete_failed
    SecretMessage.CAMERA_UNAVAILABLE -> R.string.secret_msg_camera_unavailable
    SecretMessage.COMMAND_ACTIVE -> R.string.msg_command_active
}

@StringRes
fun SecretDisplay.textRes(): Int = when (this) {
    SecretDisplay.CHECKING -> R.string.secret_checking
    SecretDisplay.ABSENT -> R.string.secret_absent
    SecretDisplay.PRESENT -> R.string.secret_present
    SecretDisplay.INVALIDATED -> R.string.secret_invalidated
}
