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
import androidx.compose.ui.unit.dp
import pl.twojanazwa.aapsremote.R

/** PROF-15: formularz procentu dla wybranego profilu; wartość pola wyłącznie z [ProfileFormState]. */
@Composable
fun ProfileForm(
    form: ProfileFormState,
    canProceed: Boolean,
    onPercentChanged: (String) -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.commands_profile_form_title, form.choice.number, form.choice.name),
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = form.percentText,
            onValueChange = onPercentChanged,
            label = { Text(stringResource(R.string.commands_profile_percent_label)) },
            isError = form.error != null,
            supportingText = form.error?.let { error -> { Text(profileErrorText(error)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = onNext, enabled = canProceed) { Text(stringResource(R.string.commands_profile_next)) }
        }
    }
}
