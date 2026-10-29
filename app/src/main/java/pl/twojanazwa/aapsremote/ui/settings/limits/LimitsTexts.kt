package pl.twojanazwa.aapsremote.ui.settings.limits

import androidx.annotation.StringRes
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.LimitField
import pl.twojanazwa.aapsremote.commands.LimitRanges
import pl.twojanazwa.aapsremote.commands.LimitValueError

@StringRes
fun LimitsMessage.textRes(): Int = when (this) {
    LimitsMessage.SAVED -> R.string.limits_msg_saved
    LimitsMessage.SAVE_FAILED -> R.string.limits_msg_save_failed
    LimitsMessage.COMMAND_ACTIVE -> R.string.msg_command_active
}

/** Tekst błędu pod polem: zasób i argumenty (granice z LIM-02). */
data class LimitErrorText(@StringRes val res: Int, val args: List<String> = emptyList())

/** Setne jednostki -> „2,50” (LIM-10). */
fun formatCentiUnits(centi: Int): String = "${centi / 100},${(centi % 100).toString().padStart(2, '0')}"

fun limitErrorText(field: LimitField, error: LimitValueError): LimitErrorText {
    val bolus = field == LimitField.BOLUS_MAX
    val (min, max) = when (field) {
        LimitField.CARBS_MAX -> LimitRanges.CARBS_MAX_MIN.toString() to LimitRanges.CARBS_MAX_MAX.toString()
        LimitField.BOLUS_MAX ->
            formatCentiUnits(LimitRanges.BOLUS_MAX_MIN) to formatCentiUnits(LimitRanges.BOLUS_MAX_MAX)
        LimitField.BOLUS_INTERVAL -> LimitRanges.INTERVAL_MIN.toString() to LimitRanges.INTERVAL_MAX.toString()
    }
    return when (error) {
        LimitValueError.EMPTY -> LimitErrorText(R.string.limits_error_empty, listOf(min, max))
        LimitValueError.INVALID_CHARACTERS ->
            LimitErrorText(if (bolus) R.string.limits_error_invalid_decimal else R.string.limits_error_invalid_digits)
        LimitValueError.BELOW_MIN -> LimitErrorText(R.string.limits_error_below_min, listOf(min))
        LimitValueError.ABOVE_MAX -> LimitErrorText(R.string.limits_error_above_max, listOf(max))
        LimitValueError.NOT_STEP -> LimitErrorText(R.string.limits_error_not_step)
        LimitValueError.TOO_MANY_DECIMALS -> LimitErrorText(R.string.limits_error_decimals)
    }
}
