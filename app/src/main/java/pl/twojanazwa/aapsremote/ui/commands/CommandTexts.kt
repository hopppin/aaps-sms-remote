package pl.twojanazwa.aapsremote.ui.commands

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.BolusAmountError
import pl.twojanazwa.aapsremote.commands.CarbsAmountError
import pl.twojanazwa.aapsremote.commands.CarbsLimits
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.commands.ProfileLimits
import pl.twojanazwa.aapsremote.commands.ProfileSwitchError
import pl.twojanazwa.aapsremote.commands.smsText
import pl.twojanazwa.aapsremote.data.CommandLimitsState

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
    CommandMessage.LIMITS_UNAVAILABLE -> R.string.commands_banner_limits_load_failed
    CommandMessage.AMOUNT_ABOVE_LIMIT -> R.string.commands_msg_amount_above_limit
    CommandMessage.EXTRA_CONFIRMATION_REQUIRED -> R.string.commands_msg_extra_confirmation_required
    CommandMessage.BOLUS_INTERVAL -> R.string.commands_msg_bolus_interval
    CommandMessage.BOLUS_HISTORY_UNAVAILABLE -> R.string.commands_msg_bolus_history_unavailable
}

@StringRes
fun CarbsAmountError.textRes(): Int = when (this) {
    CarbsAmountError.EMPTY -> R.string.commands_carbs_error_empty
    CarbsAmountError.INVALID_CHARACTERS -> R.string.commands_carbs_error_invalid_characters
    CarbsAmountError.BELOW_MIN -> R.string.commands_carbs_error_below_min
    CarbsAmountError.ABOVE_MAX -> R.string.commands_carbs_error_above_max
}

/** CMDUI-25: ABOVE_MAX podaje największą dozwoloną ilość; pozostałe błędy – najmniejszą i największą. */
fun CarbsAmountError.textArgs(limits: CommandLimits): List<Int> = when (this) {
    CarbsAmountError.ABOVE_MAX -> listOf(limits.carbsMaxGrams)
    else -> listOf(CarbsLimits.MIN_GRAMS, limits.carbsMaxGrams)
}

/** Gotowy numer nie ma banera (null). */
@StringRes
fun NumberReadiness.bannerRes(): Int? = when (this) {
    NumberReadiness.LOADING -> R.string.commands_banner_number_loading
    NumberReadiness.CONFIGURED -> null
    NumberReadiness.NOT_CONFIGURED -> R.string.commands_banner_number_not_configured
    NumberReadiness.LOAD_FAILED -> R.string.commands_banner_number_load_failed
}

/** LIM-05: baner tylko przy `LoadFailed`; `Loading` i `Loaded` bez banera. */
@StringRes
fun CommandLimitsState.bannerRes(): Int? =
    if (this == CommandLimitsState.LoadFailed) R.string.commands_banner_limits_load_failed else null

/** UNKNOWN (CARBS dostępne) i PRESENT nie mają banera (null). */
@StringRes
fun SecretReadiness.bannerRes(): Int? = when (this) {
    SecretReadiness.UNKNOWN, SecretReadiness.PRESENT -> null
    SecretReadiness.ABSENT -> R.string.commands_banner_secret_absent
    SecretReadiness.INVALIDATED -> R.string.commands_banner_secret_invalidated
}

@StringRes
fun BolusAmountError.textRes(): Int = when (this) {
    BolusAmountError.EMPTY -> R.string.commands_bolus_error_empty
    BolusAmountError.INVALID_CHARACTERS -> R.string.commands_bolus_error_invalid_characters
    BolusAmountError.TOO_MANY_DECIMALS -> R.string.commands_bolus_error_too_many_decimals
    BolusAmountError.BELOW_MIN -> R.string.commands_bolus_error_below_min
    BolusAmountError.ABOVE_MAX -> R.string.commands_bolus_error_above_max
    BolusAmountError.NOT_STEP -> R.string.commands_bolus_error_not_step
}

/** BOL-13: X = limit bolusa z przecinkiem i dwoma miejscami (tylko EMPTY i ABOVE_MAX mają argument). */
fun BolusAmountError.textArgs(limits: CommandLimits): List<String> = when (this) {
    BolusAmountError.EMPTY, BolusAmountError.ABOVE_MAX -> listOf(formatCentiUnits(limits.bolusMaxCentiUnits))
    else -> emptyList()
}

/** BOL-14: Y = połowa limitu; dla nieparzystego limitu (setne) trzecia cyfra, bez zaokrąglania. */
fun formatHalfLimit(limitCentiUnits: Int): String {
    val thousandths = limitCentiUnits * 5
    val text = "${thousandths / 1000},${(thousandths % 1000).toString().padStart(3, '0')}"
    return if (thousandths % 10 == 0) text.dropLast(1) else text
}

@Composable
fun summaryTitle(command: Command): String = when (command) {
    is Command.Carbs -> stringResource(R.string.commands_summary_title, command.grams)
    is Command.Bolus -> stringResource(R.string.commands_bolus_summary_title, formatCentiUnits(command.centiUnits))
    is Command.ProfileSwitch ->
        stringResource(R.string.commands_profile_summary_title, command.number, command.percent)
    Command.Bg, Command.Pump, Command.ProfileStatus, Command.ProfileList -> ""
}

/** PROF-15: błędy procentu. */
@Composable
fun profileErrorText(error: ProfileSwitchError): String = when (error) {
    ProfileSwitchError.EMPTY ->
        stringResource(R.string.commands_profile_error_empty, ProfileLimits.MIN_PERCENT, ProfileLimits.MAX_PERCENT)
    ProfileSwitchError.INVALID_CHARACTERS -> stringResource(R.string.commands_profile_error_invalid_characters)
    ProfileSwitchError.BELOW_MIN -> stringResource(R.string.commands_profile_error_below_min, ProfileLimits.MIN_PERCENT)
    ProfileSwitchError.ABOVE_MAX -> stringResource(R.string.commands_profile_error_above_max, ProfileLimits.MAX_PERCENT)
}

@Composable
fun summaryText(command: Command): String = when (command) {
    is Command.Carbs -> stringResource(R.string.commands_summary_text, command.grams)
    is Command.Bolus -> stringResource(R.string.commands_bolus_summary_text, formatCentiUnits(command.centiUnits))
    is Command.ProfileSwitch ->
        stringResource(R.string.commands_profile_summary_text, command.smsText(), command.percent)
    Command.Bg, Command.Pump, Command.ProfileStatus, Command.ProfileList -> ""
}
