package pl.twojanazwa.aapsremote.ui.commands

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
    val run by runViewModel.uiState.collectAsStateWithLifecycle()
    val permissions by smsPermissionsViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        commandsViewModel.refreshPermissions()
        smsPermissionsViewModel.onResume()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { commandsViewModel.onStop() }
    // Zmiana zakładki: ON_STOP nie ma gwarancji dotarcia (CMDUI-11); przebiegu nie dotyka.
    DisposableEffect(Unit) { onDispose { commandsViewModel.onStop() } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        smsPermissionsViewModel.onPermissionsResult { permission ->
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
        commandsViewModel.refreshPermissions()
    }
    val message = commands.message?.let { stringResource(it.textRes(), *commands.messageArgs.toTypedArray()) }
    val actionLabel = if (commands.message?.opensSettings == true) {
        stringResource(R.string.commands_msg_action_settings)
    } else {
        null
    }
    // Komunikat kasujemy od razu; Snackbar biegnie w scope kompozycji, więc zmiana klucza go nie anuluje.
    LaunchedEffect(commands.message) {
        val text = message ?: return@LaunchedEffect
        val action = actionLabel
        commandsViewModel.onMessageShown()
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(text, action)
            if (result == SnackbarResult.ActionPerformed && action != null) onOpenSettings()
        }
    }
    // CMDUI-24: jednorazowe żądanie zamknięcia aplikacji po „Wyjdź”.
    LaunchedEffect(run.exitRequested) {
        if (run.exitRequested) {
            activity?.finish()
            runViewModel.onExitHandled()
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

    BackHandler(enabled = run.interceptsBack) { runViewModel.onBack() }
    BackHandler(enabled = !run.interceptsBack && commands.interceptsBack) { commandsViewModel.onBack() }
    if (run.confirmingStopWaiting) {
        StopWaitingDialog(onConfirm = runViewModel::onStopWaitingConfirmed, onDismiss = runViewModel::onStopWaitingDismissed)
    }
    if (run.confirmingExit) {
        ExitDuringCommandDialog(onConfirm = runViewModel::onExitConfirmed, onDismiss = runViewModel::onExitDismissed)
    }
    val confirmThreshold = commands.carbsExtraConfirmationAboveGrams
    commands.confirmingLargeGrams?.takeIf { confirmThreshold != null }?.let { grams ->
        LargeAmountDialog(
            grams = grams,
            thresholdGrams = checkNotNull(confirmThreshold),
            onConfirm = commandsViewModel::onLargeAmountConfirmed,
            onDismiss = commandsViewModel::onLargeAmountDismissed,
        )
    }

    val bolusLimits = commands.loadedLimits
    commands.confirmingLargeCentiUnits?.takeIf { bolusLimits != null }?.let { centiUnits ->
        BolusLargeDialog(
            centiUnits = centiUnits,
            halfLimitText = formatHalfLimit(checkNotNull(bolusLimits).bolusMaxCentiUnits),
            onConfirm = commandsViewModel::onBolusLargeConfirmed,
            onDismiss = commandsViewModel::onBolusLargeDismissed,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        val summary = commands.summary
        // Treść chroniona wybierana z tego samego pola, z którego liczona jest flaga (run.secureWindow).
        val runView = run.view
        if (runView is RunView.Active) {
            RunScreen(run = runView, onCancel = runViewModel::onCancelClicked)
        } else if (runView is RunView.Finished) {
            ResultCard(
                result = runView.result,
                secret = run.secret,
                onProfileChosen = runViewModel::onProfileChosen,
                onResultDismissed = runViewModel::onResultDismissed,
            )
        } else if (commands.profile != null && summary == null) {
            ProfileForm(
                form = checkNotNull(commands.profile),
                canProceed = commands.canStartProfile,
                onPercentChanged = commandsViewModel::onProfilePercentChanged,
                onNext = commandsViewModel::onProfileNextClicked,
                onCancel = commandsViewModel::onProfileFormCancelled,
            )
        } else if (summary != null) {
            CommandSummary(
                command = summary.command,
                pinText = commands.pinText,
                canSubmit = commands.canSubmitPin,
                launching = commands.launching,
                onPinChanged = commandsViewModel::onPinChanged,
                onSend = commandsViewModel::onSendClicked,
                onCancel = commandsViewModel::onSummaryDismissed,
            )
        } else {
            CommandsPanel(
                state = commands,
                onBgClicked = commandsViewModel::onBgClicked,
                onPumpClicked = commandsViewModel::onPumpClicked,
                onProfileStatusClicked = commandsViewModel::onProfileStatusClicked,
                onCarbsInputChanged = commandsViewModel::onCarbsInputChanged,
                onQuickValueClicked = commandsViewModel::onQuickValueClicked,
                onCarbsNextClicked = commandsViewModel::onCarbsNextClicked,
                onBolusInputChanged = commandsViewModel::onBolusInputChanged,
                onBolusNextClicked = commandsViewModel::onBolusNextClicked,
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
        }
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
