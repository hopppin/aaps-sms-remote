package pl.twojanazwa.aapsremote.ui.commands

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.CarbsLimits

/** CMDUI-09: dodatkowe pytanie dla ilości powyżej limitu, przed PIN-em. */
@Composable
fun LargeAmountDialog(grams: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.commands_large_title, grams)) },
        text = { Text(stringResource(R.string.commands_large_text, CarbsLimits.EXTRA_CONFIRMATION_ABOVE_GRAMS)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.commands_large_confirm, grams)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.commands_large_dismiss)) }
        },
    )
}
