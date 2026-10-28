package pl.twojanazwa.aapsremote.ui.commands

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.ui.permissions.SmsPermissionsViewModel
import pl.twojanazwa.aapsremote.ui.settings.permissions.SmsPermissionsSection

/** Panel Polecenia i odświeżanie uprawnień przy powrocie z ustawień systemu. */
@Composable
fun CommandsScreen(
    commandsViewModel: CommandsViewModel,
    runViewModel: CommandRunViewModel,
    onOpenSettings: () -> Unit,
    smsPermissionsViewModel: SmsPermissionsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val commands by commandsViewModel.uiState.collectAsStateWithLifecycle()
    val permissions by smsPermissionsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        commandsViewModel.refreshPermissions()
        smsPermissionsViewModel.onResume()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { commandsViewModel.onStop() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        smsPermissionsViewModel.onPermissionsResult { permission ->
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
        commandsViewModel.refreshPermissions()
    }
    val message = commands.message?.let { stringResource(it.textRes()) }
    val actionLabel = if (commands.message?.opensSettings == true) {
        stringResource(R.string.commands_msg_action_settings)
    } else {
        null
    }
    LaunchedEffect(message, actionLabel) {
        if (message != null) {
            commandsViewModel.onMessageShown()
            val result = snackbarHostState.showSnackbar(message, actionLabel)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed && actionLabel != null) {
                onOpenSettings()
            }
        }
    }

    val openAppSettings = {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        } catch (_: ActivityNotFoundException) {
            // Okrojony system może nie udostępniać ekranu informacji o aplikacji.
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        CommandsPanel(
            state = commands,
            onBgClicked = commandsViewModel::onBgClicked,
            onPumpClicked = commandsViewModel::onPumpClicked,
            onCarbsInputChanged = commandsViewModel::onCarbsInputChanged,
            onQuickValueClicked = commandsViewModel::onQuickValueClicked,
            onCarbsNextClicked = commandsViewModel::onCarbsNextClicked,
            onOpenSettings = onOpenSettings,
            smsPermissions = {
                SmsPermissionsSection(
                    state = permissions,
                    onRequest = {
                        val toRequest = smsPermissionsViewModel.permissionsToRequest()
                        if (toRequest.isNotEmpty()) permissionLauncher.launch(toRequest.toTypedArray())
                        else smsPermissionsViewModel.onResume()
                    },
                    onOpenAppSettings = openAppSettings,
                )
            },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    actionOnNewLine = false,
                )
            },
        )
    }
}
