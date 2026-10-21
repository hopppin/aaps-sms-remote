package pl.twojanazwa.aapsremote.ui.permissions

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.twojanazwa.aapsremote.sms.SmsPermissions

/**
 * UI-17, UI-18. Nie zależy od sesji (UI-15): uprawnienia można prosić także w trakcie polecenia.
 * Nie zapamiętuje stanu uprawnień poza ostatnim odczytem.
 */
@HiltViewModel
class SmsPermissionsViewModel @Inject constructor(
    private val permissions: SmsPermissions,
) : ViewModel() {
    private val _uiState = MutableStateFlow(read(PermissionDenial.NONE))
    val uiState: StateFlow<SmsPermissionsUiState> = _uiState.asStateFlow()

    /** ON_RESUME ekranu: ponowny odczyt; wszystkie przyznane -> denial = NONE, inaczej denial bez zmian. */
    fun onResume() {
        val fresh = read(_uiState.value.denial)
        _uiState.value = if (fresh.allGranted) fresh.copy(denial = PermissionDenial.NONE) else fresh
    }

    /** Brakujące z [SmsPermissions.REQUIRED], w tej kolejności. */
    fun permissionsToRequest(): List<String> = missing(read(PermissionDenial.NONE))

    /** Po wyniku prośby: ponowny odczyt SmsPermissions (źródło prawdy), potem klasyfikacja odmowy. */
    fun onPermissionsResult(shouldShowRationale: (String) -> Boolean) {
        val fresh = read(PermissionDenial.NONE)
        val denial = PermissionDenialClassifier.classify(missing(fresh), shouldShowRationale)
        _uiState.value = fresh.copy(denial = denial)
    }

    private fun read(denial: PermissionDenial) =
        SmsPermissionsUiState(permissions.canSend(), permissions.canReceive(), denial)

    private fun missing(state: SmsPermissionsUiState): List<String> =
        SmsPermissions.REQUIRED.filter {
            when (it) {
                SmsPermissions.SEND_SMS -> !state.canSend
                SmsPermissions.RECEIVE_SMS -> !state.canReceive
                else -> false
            }
        }
}
