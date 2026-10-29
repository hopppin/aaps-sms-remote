package pl.twojanazwa.aapsremote.ui.commands

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.Command

/** CMDUI-10: podsumowanie z maskowanym PIN-em; wartość pola wyłącznie z [pinText], bez stanu lokalnego. */
@Composable
fun CommandSummary(
    command: Command,
    pinText: String,
    canSubmit: Boolean,
    launching: Boolean,
    onPinChanged: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(summaryTitle(command), style = MaterialTheme.typography.headlineSmall)
        Text(summaryText(command), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = pinText,
            onValueChange = onPinChanged,
            label = { Text(stringResource(R.string.commands_pin_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onCancel, enabled = !launching) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = onSend, enabled = canSubmit) { Text(stringResource(R.string.commands_send)) }
        }
    }
}
