package pl.twojanazwa.aapsremote.ui.settings.number

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.twojanazwa.aapsremote.R

/** Sekcja „Numer telefonu AAPS” (UI-04 – UI-06, UI-15); logika w [AapsNumberViewModel]. */
@Composable
fun NumberSection(
    state: AapsNumberUiState,
    onEditClicked: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onEditCancelled: () -> Unit,
    onClearClicked: () -> Unit,
    onClearConfirmed: () -> Unit,
    onClearDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.number_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = state.display.label())
        if (state.editing) {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.number_field_label)) },
                singleLine = true,
                isError = state.inputError != null,
                supportingText = state.inputError?.let { { Text(stringResource(R.string.number_error_invalid)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveClicked, enabled = state.canSave) {
                    Text(stringResource(R.string.action_save))
                }
                TextButton(onClick = onEditCancelled) { Text(stringResource(R.string.action_cancel)) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.display is NumberDisplay.Configured) {
                    OutlinedButton(onClick = onEditClicked, enabled = state.canChange) {
                        Text(stringResource(R.string.number_action_change))
                    }
                    OutlinedButton(onClick = onClearClicked, enabled = state.canClear) {
                        Text(stringResource(R.string.action_delete))
                    }
                } else {
                    OutlinedButton(onClick = onEditClicked, enabled = state.canSet) {
                        Text(stringResource(R.string.number_action_set))
                    }
                }
            }
        }
    }
    if (state.confirmingClear) {
        AlertDialog(
            onDismissRequest = onClearDismissed,
            title = { Text(stringResource(R.string.number_clear_title)) },
            text = { Text(stringResource(R.string.number_clear_text)) },
            confirmButton = {
                TextButton(onClick = onClearConfirmed) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = onClearDismissed) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun NumberDisplay.label(): String = when (this) {
    NumberDisplay.Loading -> stringResource(R.string.number_loading)
    NumberDisplay.NotConfigured -> stringResource(R.string.number_not_configured)
    is NumberDisplay.Configured -> destination
    NumberDisplay.LoadFailed -> stringResource(R.string.number_load_failed)
}
