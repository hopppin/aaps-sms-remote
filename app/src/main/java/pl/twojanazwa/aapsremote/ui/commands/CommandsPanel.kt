package pl.twojanazwa.aapsremote.ui.commands

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.twojanazwa.aapsremote.R

/** Zawartość zakładki w trybie panelu, bez stanu lokalnego zapisywanego przez Compose. */
@Composable
fun CommandsPanel(
    state: CommandsUiState,
    onBgClicked: () -> Unit,
    onPumpClicked: () -> Unit,
    onCarbsInputChanged: (String) -> Unit,
    onQuickValueClicked: (Int) -> Unit,
    onCarbsNextClicked: () -> Unit,
    onBolusInputChanged: (String) -> Unit,
    onBolusNextClicked: () -> Unit,
    onOpenSettings: () -> Unit,
    smsPermissions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.number.bannerRes()?.let { Banner(it, onOpenSettings.takeIf { state.number != NumberReadiness.LOADING }) }
        if (!state.smsGranted) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) { smsPermissions() }
            }
        }
        state.secret.bannerRes()?.let { Banner(it, onOpenSettings) }
        state.limits.bannerRes()?.let { Banner(it, onOpenSettings) }

        if (state.launching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        FilledTonalButton(
            onClick = onBgClicked,
            enabled = state.canSendNoCode,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(stringResource(R.string.commands_bg), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.commands_bg_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
        FilledTonalButton(
            onClick = onPumpClicked,
            enabled = state.canSendNoCode,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(stringResource(R.string.commands_pump), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.commands_pump_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.commands_carbs_title), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = state.carbsInput,
                    onValueChange = onCarbsInputChanged,
                    enabled = state.canStartCarbs,
                    label = { Text(stringResource(R.string.commands_carbs_label)) },
                    isError = state.carbsError != null,
                    supportingText = state.carbsError?.let { error ->
                        state.loadedLimits?.let { limits ->
                            { Text(stringResource(error.textRes(), *error.textArgs(limits).toTypedArray())) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.quickValues.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.quickValues.forEach { grams ->
                            OutlinedButton(
                                onClick = { onQuickValueClicked(grams) },
                                enabled = state.canStartCarbs,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.commands_carbs_quick, grams)) }
                        }
                    }
                }
                Button(
                    onClick = onCarbsNextClicked,
                    enabled = state.canStartCarbs,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.commands_carbs_next)) }
            }
        }

        // BOL-13: bez szybkich wartości.
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.commands_bolus_title), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = state.bolusInput,
                    onValueChange = onBolusInputChanged,
                    enabled = state.canStartBolus,
                    label = { Text(stringResource(R.string.commands_bolus_label)) },
                    isError = state.bolusError != null,
                    supportingText = state.bolusError?.let { error ->
                        state.loadedLimits?.let { limits ->
                            { Text(stringResource(error.textRes(), *error.textArgs(limits).toTypedArray())) }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = onBolusNextClicked,
                    enabled = state.canStartBolus,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.commands_carbs_next)) }
            }
        }
    }
}

@Composable
private fun Banner(textRes: Int, onOpenSettings: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(textRes))
            onOpenSettings?.let { action ->
                TextButton(onClick = action, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.commands_open_settings))
                }
            }
        }
    }
}
