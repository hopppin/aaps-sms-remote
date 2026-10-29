package pl.twojanazwa.aapsremote.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.ui.permissions.CameraPermission
import pl.twojanazwa.aapsremote.ui.permissions.SmsPermissionsViewModel
import pl.twojanazwa.aapsremote.ui.settings.limits.CommandLimitsSection
import pl.twojanazwa.aapsremote.ui.settings.limits.CommandLimitsViewModel
import pl.twojanazwa.aapsremote.ui.settings.limits.textRes
import pl.twojanazwa.aapsremote.ui.settings.number.AapsNumberViewModel
import pl.twojanazwa.aapsremote.ui.settings.number.NumberSection
import pl.twojanazwa.aapsremote.ui.settings.number.textRes
import pl.twojanazwa.aapsremote.ui.settings.permissions.SmsPermissionsSection
import pl.twojanazwa.aapsremote.ui.settings.secret.PasteUriDialog
import pl.twojanazwa.aapsremote.ui.settings.secret.QrScanner
import pl.twojanazwa.aapsremote.ui.settings.secret.SecretInputMode
import pl.twojanazwa.aapsremote.ui.settings.secret.SecureWindowEffect
import pl.twojanazwa.aapsremote.ui.settings.secret.SecretSection
import pl.twojanazwa.aapsremote.ui.settings.secret.TotpSecretViewModel
import pl.twojanazwa.aapsremote.ui.settings.secret.textRes

/** Ekran Ustawienia; sekcje dodają kolejne zadania spec 008. */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    numberViewModel: AapsNumberViewModel = hiltViewModel(),
    smsViewModel: SmsPermissionsViewModel = hiltViewModel(),
    secretViewModel: TotpSecretViewModel = hiltViewModel(),
    limitsViewModel: CommandLimitsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val numberState by numberViewModel.uiState.collectAsStateWithLifecycle()
    val smsState by smsViewModel.uiState.collectAsStateWithLifecycle()
    val secretState by secretViewModel.uiState.collectAsStateWithLifecycle()
    val limitsState by limitsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        smsViewModel.onResume()
        secretViewModel.onResume()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { secretViewModel.onStop() }
    // Przy zmianie zakładki ON_STOP może nie dotrzeć przed usunięciem treści z kompozycji (UI-14); onStop jest idempotentne.
    DisposableEffect(Unit) { onDispose { secretViewModel.onStop() } }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        smsViewModel.onPermissionsResult { permission ->
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        secretViewModel.onCameraPermissionResult(
            activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, CameraPermission.CAMERA),
        )
    }
    LaunchedEffect(secretState.cameraPermissionRequest) {
        if (secretState.cameraPermissionRequest) {
            cameraLauncher.launch(CameraPermission.CAMERA)
            secretViewModel.onCameraPermissionRequestLaunched()
        }
    }
    SecureWindowEffect(secretState.secureWindow)
    BackHandler(enabled = secretState.mode == SecretInputMode.SCANNER) { secretViewModel.onScannerClosed() }

    val openAppSettings = {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        } catch (e: ActivityNotFoundException) {
            // Okrojony system bez ekranu informacji o aplikacji: bez awarii i bez logowania.
        }
    }

    // Komunikat jest kasowany przed pokazaniem, a pasek pokazuje osobna korutyna: powtórzony identyczny
    // komunikat pokaże się ponownie, a po zmianie zakładki stary komunikat nie wraca.
    val snackbarScope = rememberCoroutineScope()
    val numberMessage = numberState.message?.let { stringResource(it.textRes()) }
    LaunchedEffect(numberMessage) {
        if (numberMessage != null) {
            numberViewModel.onMessageShown()
            snackbarScope.launch { snackbarHostState.showSnackbar(numberMessage) }
        }
    }

    val limitsMessage = limitsState.message?.let { stringResource(it.textRes()) }
    LaunchedEffect(limitsMessage) {
        if (limitsMessage != null) {
            limitsViewModel.onMessageShown()
            snackbarScope.launch { snackbarHostState.showSnackbar(limitsMessage) }
        }
    }

    val secretMessage = secretState.message?.let { stringResource(it.textRes()) }
    LaunchedEffect(secretMessage) {
        if (secretMessage != null) {
            secretViewModel.onMessageShown()
            snackbarScope.launch { snackbarHostState.showSnackbar(secretMessage) }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (secretState.mode == SecretInputMode.SCANNER) {
            QrScanner(
                onQrValue = secretViewModel::onQrScanned,
                onCameraError = secretViewModel::onCameraFailed,
                onCancel = secretViewModel::onScannerClosed,
            )
        } else Column(
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
            if (numberState.commandActive || secretState.commandActive) {
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
            SecretSection(
                state = secretState,
                onScanClicked = secretViewModel::onScanClicked,
                onPasteClicked = secretViewModel::onPasteClicked,
                onDeleteClicked = secretViewModel::onDeleteClicked,
                onReplaceConfirmed = secretViewModel::onReplaceConfirmed,
                onReplaceDismissed = secretViewModel::onReplaceDismissed,
                onDeleteConfirmed = secretViewModel::onDeleteConfirmed,
                onDeleteDismissed = secretViewModel::onDeleteDismissed,
                onOpenAppSettings = openAppSettings,
            )
            SmsPermissionsSection(
                state = smsState,
                onRequest = {
                    val toRequest = smsViewModel.permissionsToRequest()
                    if (toRequest.isNotEmpty()) {
                        smsLauncher.launch(toRequest.toTypedArray())
                    } else {
                        // Uprawnienia nadano z zewnątrz, a ON_RESUME jeszcze nie przyszło: odśwież stan.
                        smsViewModel.onResume()
                    }
                },
                onOpenAppSettings = openAppSettings,
            )
            CommandLimitsSection(
                state = limitsState,
                onCarbsChanged = limitsViewModel::onCarbsChanged,
                onBolusChanged = limitsViewModel::onBolusChanged,
                onIntervalChanged = limitsViewModel::onIntervalChanged,
                onSaveClicked = limitsViewModel::onSaveClicked,
            )
        }
        if (secretState.mode == SecretInputMode.PASTE) {
            PasteUriDialog(
                text = secretState.pasteText,
                canSubmit = secretState.canSubmitPaste,
                onTextChanged = secretViewModel::onPasteTextChanged,
                onSubmit = secretViewModel::onPasteSubmitted,
                onDismiss = secretViewModel::onPasteDismissed,
            )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
