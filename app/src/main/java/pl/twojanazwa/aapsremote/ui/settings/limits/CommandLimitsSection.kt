package pl.twojanazwa.aapsremote.ui.settings.limits

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.LimitField

/** Sekcja „Limity poleceń” (LIM-10, LIM-11); logika w [CommandLimitsViewModel]. */
@Composable
fun CommandLimitsSection(
    state: CommandLimitsUiState,
    onCarbsChanged: (String) -> Unit,
    onBolusChanged: (String) -> Unit,
    onIntervalChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editable = state.load != LimitsLoad.LOADING && !state.busy
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.limits_section_title), style = MaterialTheme.typography.titleMedium)
        if (state.commandActive) {
            Text(
                text = stringResource(R.string.limits_command_active),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.load == LimitsLoad.LOAD_FAILED) Text(stringResource(R.string.limits_load_failed))
        Text(text = stringResource(R.string.limits_note), style = MaterialTheme.typography.bodyMedium)
        LimitInput(state, LimitField.CARBS_MAX, state.carbsMax, R.string.limits_carbs_label, KeyboardType.Number, editable, onCarbsChanged)
        LimitInput(state, LimitField.BOLUS_MAX, state.bolusMax, R.string.limits_bolus_label, KeyboardType.Decimal, editable, onBolusChanged)
        LimitInput(state, LimitField.BOLUS_INTERVAL, state.interval, R.string.limits_interval_label, KeyboardType.Number, editable, onIntervalChanged)
        Button(onClick = onSaveClicked, enabled = state.canSave) { Text(stringResource(R.string.limits_action_save)) }
    }
}

@Composable
private fun LimitInput(
    state: CommandLimitsUiState,
    field: LimitField,
    value: String,
    @StringRes label: Int,
    keyboard: KeyboardType,
    enabled: Boolean,
    onChanged: (String) -> Unit,
) {
    val error = state.errors[field]?.let { limitErrorText(field, it) }
    OutlinedTextField(
        value = value,
        onValueChange = onChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it.res, *it.args.toTypedArray())) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}
