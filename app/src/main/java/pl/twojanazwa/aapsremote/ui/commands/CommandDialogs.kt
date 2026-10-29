package pl.twojanazwa.aapsremote.ui.commands

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.CommandLimits

/** CMDUI-09: dodatkowe pytanie dla ilości powyżej limitu, przed PIN-em. */
@Composable
fun LargeAmountDialog(grams: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // CMDUI-09: osobne okno systemowe nie dziedziczy FLAG_SECURE z Activity.
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.commands_large_title, grams)) },
        text = { Text(stringResource(R.string.commands_large_text, CommandLimits.DEFAULT.carbsExtraConfirmationAboveGrams)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.commands_large_confirm, grams)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.commands_large_dismiss)) }
        },
    )
}

/** CMDUI-18: ostrzeżenie przed zakończeniem czekania po przekazaniu kodu. */
@Composable
fun StopWaitingDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.run_stop_title)) },
        text = { Text(stringResource(R.string.run_stop_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.run_stop_waiting)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.run_stop_dismiss)) }
        },
    )
}

/** CMDUI-24: wyjście z aplikacji nie anuluje polecenia. */
@Composable
fun ExitDuringCommandDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.run_exit_title)) },
        text = { Text(stringResource(R.string.run_exit_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.run_exit_leave)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.run_exit_stay)) }
        },
    )
}
