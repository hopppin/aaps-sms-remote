package pl.twojanazwa.aapsremote.ui.settings.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.twojanazwa.aapsremote.ui.permissions.SmsPermissionsUiState

/** Sekcja „Uprawnienia SMS” (UI-17, UI-18). Bez blokady w trakcie polecenia (UI-15). */
@Composable
fun SmsPermissionsSection(
    state: SmsPermissionsUiState,
    onRequest: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(PermissionTexts.sectionTitle),
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.allGranted) {
            Text(
                text = stringResource(PermissionTexts.allGranted),
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            val names = PermissionTexts.missing(state).map { stringResource(it) }.joinToString(", ")
            Text(
                text = stringResource(PermissionTexts.missingLabel) + " " + names,
                modifier = Modifier.padding(top = 8.dp),
            )
            PermissionTexts.denial(state.denial)?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.canRequest) {
                Button(onClick = onRequest, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(PermissionTexts.requestAction))
                }
            }
            if (PermissionTexts.showOpenSettings(state.denial)) {
                OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(PermissionTexts.openSettingsAction))
                }
            }
        }
    }
}
