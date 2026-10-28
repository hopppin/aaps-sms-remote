package pl.twojanazwa.aapsremote.ui.commands

import androidx.annotation.StringRes
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.CarbsAmountError
import pl.twojanazwa.aapsremote.commands.CarbsLimits

/** CMDUI-25: jedna funkcja wartość → zasób na typ; teksty wspólne ze Spec 008 są używane ponownie. */
@StringRes
fun CommandMessage.textRes(): Int = when (this) {
    CommandMessage.COMMAND_ACTIVE -> R.string.msg_command_active
    CommandMessage.NUMBER_LOADING -> R.string.commands_msg_number_loading
    CommandMessage.NUMBER_MISSING -> R.string.commands_msg_number_missing
    CommandMessage.SMS_PERMISSIONS_MISSING -> R.string.sms_denied
    CommandMessage.SECRET_ABSENT -> R.string.commands_msg_secret_absent
    CommandMessage.SECRET_INVALIDATED -> R.string.commands_msg_secret_invalidated
    CommandMessage.SECRET_LOCKED -> R.string.secret_msg_locked
    CommandMessage.SECRET_UNAVAILABLE -> R.string.commands_msg_secret_unavailable
    CommandMessage.START_FAILED -> R.string.commands_msg_start_failed
}

@StringRes
fun CarbsAmountError.textRes(): Int = when (this) {
    CarbsAmountError.EMPTY -> R.string.commands_carbs_error_empty
    CarbsAmountError.INVALID_CHARACTERS -> R.string.commands_carbs_error_invalid_characters
    CarbsAmountError.BELOW_MIN -> R.string.commands_carbs_error_below_min
    CarbsAmountError.ABOVE_MAX -> R.string.commands_carbs_error_above_max
}

/** CMDUI-25: ABOVE_MAX podaje największą dozwoloną ilość; pozostałe błędy – najmniejszą i największą. */
fun CarbsAmountError.textArgs(): List<Int> = when (this) {
    CarbsAmountError.ABOVE_MAX -> listOf(CarbsLimits.MAX_GRAMS)
    else -> listOf(CarbsLimits.MIN_GRAMS, CarbsLimits.MAX_GRAMS)
}

/** Gotowy numer nie ma banera (null). */
@StringRes
fun NumberReadiness.bannerRes(): Int? = when (this) {
    NumberReadiness.LOADING -> R.string.commands_banner_number_loading
    NumberReadiness.CONFIGURED -> null
    NumberReadiness.NOT_CONFIGURED -> R.string.commands_banner_number_not_configured
    NumberReadiness.LOAD_FAILED -> R.string.commands_banner_number_load_failed
}

/** UNKNOWN (CARBS dostępne) i PRESENT nie mają banera (null). */
@StringRes
fun SecretReadiness.bannerRes(): Int? = when (this) {
    SecretReadiness.UNKNOWN, SecretReadiness.PRESENT -> null
    SecretReadiness.ABSENT -> R.string.commands_banner_secret_absent
    SecretReadiness.INVALIDATED -> R.string.commands_banner_secret_invalidated
}
