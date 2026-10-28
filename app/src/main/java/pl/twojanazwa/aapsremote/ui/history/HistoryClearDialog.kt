package pl.twojanazwa.aapsremote.ui.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.twojanazwa.aapsremote.R

/** HISTUI-10: potwierdzenie czyszczenia; „Anuluj” i Wstecz zamykają okno bez czyszczenia. */
@Composable
fun HistoryClearDialog(count: Int, onClearConfirmed: () -> Unit, onClearDismissed: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClearDismissed,
        title = { Text(stringResource(R.string.history_clear_title)) },
        text = { Text(stringResource(R.string.history_clear_text, count)) },
        confirmButton = {
            TextButton(onClick = onClearConfirmed) { Text(stringResource(R.string.history_clear_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onClearDismissed) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
