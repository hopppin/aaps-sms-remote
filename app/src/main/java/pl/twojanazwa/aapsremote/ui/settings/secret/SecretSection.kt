package pl.twojanazwa.aapsremote.ui.settings.secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.twojanazwa.aapsremote.R

/**
 * Sekcja „Sekret TOTP” (UI-07, UI-08, UI-13); logika w [TotpSecretViewModel].
 * [scanAvailable] jest fałszem do czasu dodania skanera (zad. 10): bez widoku skanera przycisk byłby martwy.
 */
@Composable
fun SecretSection(
    state: TotpSecretUiState,
    onScanClicked: () -> Unit,
    onPasteClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onReplaceConfirmed: () -> Unit,
    onReplaceDismissed: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    scanAvailable: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.secret_section_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(text = stringResource(state.display.textRes()))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onScanClicked, enabled = scanAvailable && state.canScan) {
                Text(stringResource(R.string.secret_action_scan))
            }
            OutlinedButton(onClick = onPasteClicked, enabled = state.canPaste) {
                Text(stringResource(R.string.secret_action_paste))
            }
        }
        if (state.display != SecretDisplay.ABSENT) {
            OutlinedButton(onClick = onDeleteClicked, enabled = state.canDelete) {
                Text(stringResource(R.string.secret_action_delete))
            }
        }
    }
    if (state.confirmingReplace != null) {
        AlertDialog(
            onDismissRequest = onReplaceDismissed,
            title = { Text(stringResource(R.string.secret_replace_title)) },
            text = { Text(stringResource(R.string.secret_replace_text)) },
            confirmButton = {
                TextButton(onClick = onReplaceConfirmed) { Text(stringResource(R.string.secret_replace_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onReplaceDismissed) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = onDeleteDismissed,
            title = { Text(stringResource(R.string.secret_delete_title)) },
            text = { Text(stringResource(R.string.secret_delete_text)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirmed) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismissed) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
