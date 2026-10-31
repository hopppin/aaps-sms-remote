package pl.twojanazwa.aapsremote.ui.commands

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.CommandId

/** CMDUI-25: jedna funkcja wartość → zasób na typ. */
@StringRes
fun ResultKind.titleRes(): Int = when (this) {
    ResultKind.SUCCESS -> R.string.result_kind_success
    ResultKind.FAILURE -> R.string.result_kind_failure
    ResultKind.NO_RESPONSE -> R.string.result_kind_no_response
    ResultKind.UNKNOWN -> R.string.result_kind_unknown
    ResultKind.CANCELLED -> R.string.result_kind_cancelled
}

/** CMDUI-16: nagłówek przebiegu i wyniku. */
@StringRes
fun CommandId.titleRes(): Int = when (this) {
    CommandId.BG -> R.string.run_title_bg
    CommandId.PUMP -> R.string.run_title_pump
    CommandId.CARBS -> R.string.run_title_carbs
    CommandId.BOLUS -> R.string.run_title_bolus
    CommandId.PROFILE_STATUS -> R.string.run_title_profile_status
    CommandId.PROFILE_LIST -> R.string.run_title_profile_list
    CommandId.PROFILE_SWITCH -> R.string.run_title_profile_switch
}

@StringRes
fun ResultMessage.textRes(): Int = when (this) {
    ResultMessage.BG_REPLY -> R.string.result_bg_reply
    ResultMessage.PUMP_REPLY -> R.string.result_pump_reply
    ResultMessage.CARBS_CONFIRMED -> R.string.result_carbs_confirmed
    ResultMessage.BOLUS_CONFIRMED -> R.string.result_bolus_confirmed
    ResultMessage.BOLUS_AMOUNT_MISMATCH -> R.string.result_bolus_amount_mismatch
    ResultMessage.BOLUS_UNKNOWN_UNRECOGNIZED_REPLY -> R.string.result_bolus_unknown_unrecognized_reply
    ResultMessage.AAPS_BOLUS_INTERVAL -> R.string.result_failed_aaps_bolus_interval
    ResultMessage.AAPS_BOLUS_QUEUE -> R.string.result_failed_aaps_bolus_queue
    ResultMessage.UNKNOWN_BOLUS_FAILED -> R.string.result_unknown_bolus_failed
    ResultMessage.PROFILE_STATUS_REPLY -> R.string.result_profile_status_reply
    ResultMessage.PROFILE_LIST_REPLY -> R.string.result_profile_list_reply
    ResultMessage.PROFILE_AMOUNT_MISMATCH -> R.string.result_profile_amount_mismatch
    ResultMessage.PROFILE_UNKNOWN_AMOUNT_MISMATCH -> R.string.result_profile_unknown_amount_mismatch
    ResultMessage.PROFILE_UNKNOWN_UNRECOGNIZED_REPLY -> R.string.result_profile_unknown_unrecognized_reply
    ResultMessage.DETAILS_MISSING -> R.string.result_details_missing
    ResultMessage.SEND_FAILED -> R.string.result_send_failed
    ResultMessage.UNRECOGNIZED_REPLY -> R.string.result_unrecognized_reply
    ResultMessage.AMOUNT_MISMATCH -> R.string.result_amount_mismatch
    ResultMessage.UNEXPECTED_REPLY -> R.string.result_unexpected_reply
    ResultMessage.WRONG_CODE -> R.string.result_wrong_code
    ResultMessage.CODE_REQUEST_EXPIRED -> R.string.result_code_request_expired
    ResultMessage.CODE_PREPARATION_TIMEOUT -> R.string.result_code_preparation_timeout
    ResultMessage.INTERNAL_ERROR -> R.string.result_internal_error
    ResultMessage.NO_RESPONSE -> R.string.result_no_response
    ResultMessage.SEND_TIMEOUT -> R.string.result_send_timeout
    ResultMessage.UNKNOWN_CONFIRMATION_TIMEOUT -> R.string.result_unknown_confirmation_timeout
    ResultMessage.UNKNOWN_CANCELLED_AFTER_CODE -> R.string.result_unknown_cancelled_after_code
    ResultMessage.UNKNOWN_CODE_SEND_FAILED -> R.string.result_unknown_code_send_failed
    ResultMessage.UNKNOWN_UNRECOGNIZED_REPLY -> R.string.result_unknown_unrecognized_reply
    ResultMessage.UNKNOWN_AMOUNT_MISMATCH -> R.string.result_unknown_amount_mismatch
    ResultMessage.UNKNOWN_UNEXPECTED_REPLY -> R.string.result_unknown_unexpected_reply
    ResultMessage.UNKNOWN_INTERNAL_ERROR -> R.string.result_unknown_internal_error
    ResultMessage.CANCELLED -> R.string.result_cancelled
}

@StringRes
fun ProgressStepId.textRes(): Int = when (this) {
    ProgressStepId.SEND_COMMAND -> R.string.run_step_send_command
    ProgressStepId.AWAIT_REPLY -> R.string.run_step_await_reply
    ProgressStepId.AWAIT_CODE_REQUEST -> R.string.run_step_await_code_request
    ProgressStepId.PREPARE_CODE -> R.string.run_step_prepare_code
    ProgressStepId.SEND_CODE -> R.string.run_step_send_code
    ProgressStepId.AWAIT_CONFIRMATION -> R.string.run_step_await_confirmation
}

/** Setne jednostki jako tekst z przecinkiem i dwoma miejscami (np. 120 -> „1,20”). */
fun formatCentiUnits(centiUnits: Int): String = "${centiUnits / 100},${(centiUnits % 100).toString().padStart(2, '0')}"

/** BOL-16: ostrzeżenie dla każdego `Unknown`; bolus ma własny tekst. */
@StringRes
fun unknownWarningRes(bolus: Boolean): Int =
    if (bolus) R.string.result_unknown_warning_bolus else R.string.result_unknown_warning

/** Nagłówek przebiegu i wyniku z ilością (CARBS w gramach, BOLUS w setnych jednostki). */
@Composable
fun commandTitle(
    id: CommandId,
    grams: Int?,
    centiUnits: Int?,
    profileNumber: Int? = null,
    profilePercent: Int? = null,
): String = when {
    profileNumber != null && profilePercent != null -> stringResource(id.titleRes(), profileNumber, profilePercent)
    grams != null -> stringResource(id.titleRes(), grams)
    centiUnits != null -> stringResource(id.titleRes(), formatCentiUnits(centiUnits))
    else -> stringResource(id.titleRes())
}

/** Komunikat wyniku z argumentem: ilość bolusa jest formatowana, reszta (gramy, minuty) idzie wprost. */
@Composable
fun resultMessageText(message: ResultMessage, arg: Int?): String = when {
    arg == null -> stringResource(message.textRes())
    message == ResultMessage.BOLUS_CONFIRMED -> stringResource(message.textRes(), formatCentiUnits(arg))
    else -> stringResource(message.textRes(), arg)
}

/** CMDUI-17: opis stanu kroku dla czytnika ekranu (stateDescription). */
@StringRes
fun StepStatus.descriptionRes(): Int = when (this) {
    StepStatus.DONE -> R.string.run_step_status_done
    StepStatus.CURRENT -> R.string.run_step_status_current
    StepStatus.PENDING -> R.string.run_step_status_pending
}
