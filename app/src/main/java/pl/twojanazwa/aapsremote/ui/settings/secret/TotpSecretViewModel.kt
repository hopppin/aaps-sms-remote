package pl.twojanazwa.aapsremote.ui.settings.secret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.otp.OtpauthError
import pl.twojanazwa.aapsremote.otp.OtpauthParser
import pl.twojanazwa.aapsremote.otp.OtpauthResult
import pl.twojanazwa.aapsremote.security.SaveFailure
import pl.twojanazwa.aapsremote.security.SaveResult
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.security.TotpSecretStore
import pl.twojanazwa.aapsremote.session.SessionCoordinator
import pl.twojanazwa.aapsremote.ui.permissions.CameraPermission

/**
 * Sekcja „Sekret TOTP” (UI-07, UI-08, UI-11). Sekret i wejście (URI) są wyłącznie zmiennymi
 * lokalnymi korutyny w [accept]; w stanie jest tylko tekst pola wklejania (czyszczony przed zapisem).
 * Skaner, usuwanie, blokady i schowek dochodzą w kolejnych zadaniach.
 */
@HiltViewModel
class TotpSecretViewModel @Inject constructor(
    private val store: TotpSecretStore,
    private val coordinator: SessionCoordinator,
    @Suppress("unused") private val cameraPermission: CameraPermission,
    @Suppress("unused") private val clipboard: SecretClipboard,
) : ViewModel() {
    /** Część stanu własna ViewModelu; `display` i `commandActive` dokłada `combine`. */
    private val local = MutableStateFlow(TotpSecretUiState())

    val uiState: StateFlow<TotpSecretUiState> = combine(
        store.state,
        coordinator.activeSession,
        local,
    ) { secretState, session, own ->
        own.copy(display = secretState.toDisplay(), commandActive = session != null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, compose())

    init {
        viewModelScope.launch { store.refreshState() }
        viewModelScope.launch {
            var previouslyActive = coordinator.activeSession.value != null
            coordinator.activeSession.collect { session ->
                val active = session != null
                if (active && !previouslyActive) {
                    local.update {
                        it.copy(
                            confirmingReplace = null,
                            confirmingDelete = false,
                            mode = SecretInputMode.NONE,
                            pasteText = "",
                            cameraPermissionRequest = false,
                        )
                    }
                }
                previouslyActive = active
            }
        }
    }

    /** Aktualny stan liczony od razu ze źródeł (bez opóźnienia `stateIn`). */
    private fun compose(): TotpSecretUiState =
        local.value.copy(
            display = store.state.value.toDisplay(),
            commandActive = coordinator.activeSession.value != null,
        )

    fun onPasteClicked() {
        val state = compose()
        if (!state.canPaste) return
        when (state.display) {
            SecretDisplay.CHECKING, SecretDisplay.PRESENT ->
                local.update { it.copy(confirmingReplace = PendingSecretInput.PASTE) }
            SecretDisplay.ABSENT, SecretDisplay.INVALIDATED -> openPaste()
        }
    }

    fun onReplaceConfirmed() {
        val state = compose()
        if (state.confirmingReplace == PendingSecretInput.PASTE && state.canPaste) openPaste()
    }

    fun onReplaceDismissed() {
        local.update { it.copy(confirmingReplace = null) }
    }

    private fun openPaste() {
        local.update { it.copy(confirmingReplace = null, mode = SecretInputMode.PASTE, pasteText = "") }
    }

    fun onPasteTextChanged(text: String) {
        local.update { if (it.mode == SecretInputMode.PASTE) it.copy(pasteText = text) else it }
    }

    /** UI-11/UI-14: pole czyszczone i okno zamykane przed [accept]. */
    fun onPasteSubmitted() {
        val state = compose()
        if (state.mode != SecretInputMode.PASTE || !state.canSubmitPaste) return
        val raw = state.pasteText
        local.update { it.copy(pasteText = "", mode = SecretInputMode.NONE) }
        accept(raw)
    }

    fun onPasteDismissed() {
        local.update {
            if (it.mode == SecretInputMode.PASTE) it.copy(pasteText = "", mode = SecretInputMode.NONE) else it
        }
    }

    fun onMessageShown() {
        local.update { it.copy(message = null) }
    }

    /** Wspólna procedura wejścia z pola (i skanera): parse, save, komunikat. */
    private fun accept(raw: String) {
        if (coordinator.activeSession.value != null) {
            local.update { it.copy(message = SecretMessage.COMMAND_ACTIVE) }
            return
        }
        var acquired = false
        local.update {
            acquired = !it.busy
            if (acquired) it.copy(busy = true) else it
        }
        if (!acquired) return
        viewModelScope.launch {
            var message = SecretMessage.SAVE_FAILED
            try {
                message = when (val parsed = OtpauthParser.parse(raw)) {
                    is OtpauthResult.Failure -> parsed.error.toMessage()
                    is OtpauthResult.Success -> store.save(parsed.secret).toMessage()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                message = SecretMessage.SAVE_FAILED
            } finally {
                local.update { it.copy(busy = false) }
            }
            local.update { it.copy(message = message) }
        }
    }

    private fun SecretState.toDisplay(): SecretDisplay = when (this) {
        SecretState.UNKNOWN -> SecretDisplay.CHECKING
        SecretState.ABSENT -> SecretDisplay.ABSENT
        SecretState.PRESENT -> SecretDisplay.PRESENT
        SecretState.INVALIDATED -> SecretDisplay.INVALIDATED
    }

    private fun OtpauthError.toMessage(): SecretMessage = when (this) {
        OtpauthError.NOT_OTPAUTH_URI,
        OtpauthError.MALFORMED_URI,
        OtpauthError.UNSUPPORTED_TYPE,
        -> SecretMessage.NOT_TOTP_QR
        OtpauthError.UNSUPPORTED_ALGORITHM,
        OtpauthError.UNSUPPORTED_DIGITS,
        OtpauthError.UNSUPPORTED_PERIOD,
        -> SecretMessage.UNSUPPORTED_PARAMETERS
        OtpauthError.MISSING_SECRET,
        OtpauthError.DUPLICATE_PARAMETER,
        OtpauthError.EMPTY_SECRET,
        OtpauthError.INVALID_BASE32,
        -> SecretMessage.INVALID_SECRET
    }

    private fun SaveResult.toMessage(): SecretMessage = when (this) {
        SaveResult.Saved -> SecretMessage.SAVED
        SaveResult.Locked -> SecretMessage.LOCKED
        is SaveResult.Failed -> when (reason) {
            SaveFailure.KEY_GENERATION -> SecretMessage.KEY_GENERATION_FAILED
            SaveFailure.CRYPTO, SaveFailure.STORAGE -> SecretMessage.SAVE_FAILED
        }
    }
}
