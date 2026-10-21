package pl.twojanazwa.aapsremote.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.ui.permissions.SmsPermissionsViewModel
import pl.twojanazwa.aapsremote.ui.settings.number.AapsNumberViewModel
import pl.twojanazwa.aapsremote.ui.settings.number.NumberSection
import pl.twojanazwa.aapsremote.ui.settings.number.textRes
import pl.twojanazwa.aapsremote.ui.settings.permissions.SmsPermissionsSection

/** Ekran Ustawienia; sekcje dodają kolejne zadania spec 008. */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    numberViewModel: AapsNumberViewModel = hiltViewModel(),
    smsViewModel: SmsPermissionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val numberState by numberViewModel.uiState.collectAsStateWithLifecycle()
    val smsState by smsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { smsViewModel.onResume() }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        smsViewModel.onPermissionsResult { permission ->
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    val numberMessage = numberState.message?.let { stringResource(it.textRes()) }
    LaunchedEffect(numberMessage) {
        if (numberMessage != null) {
            snackbarHostState.showSnackbar(numberMessage)
            numberViewModel.onMessageShown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (numberState.commandActive) {
                Text(
                    text = stringResource(R.string.settings_command_active),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            NumberSection(
                state = numberState,
                onEditClicked = numberViewModel::onEditClicked,
                onInputChanged = numberViewModel::onInputChanged,
                onSaveClicked = numberViewModel::onSaveClicked,
                onEditCancelled = numberViewModel::onEditCancelled,
                onClearClicked = numberViewModel::onClearClicked,
                onClearConfirmed = numberViewModel::onClearConfirmed,
                onClearDismissed = numberViewModel::onClearDismissed,
            )
            SmsPermissionsSection(
                state = smsState,
                onRequest = {
                    val toRequest = smsViewModel.permissionsToRequest()
                    if (toRequest.isNotEmpty()) smsLauncher.launch(toRequest.toTypedArray())
                },
                onOpenAppSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
            )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
