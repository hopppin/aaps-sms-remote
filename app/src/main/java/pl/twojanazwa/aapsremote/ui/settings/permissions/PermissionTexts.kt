package pl.twojanazwa.aapsremote.ui.settings.permissions

import androidx.annotation.StringRes
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.ui.permissions.PermissionDenial
import pl.twojanazwa.aapsremote.ui.permissions.SmsPermissionsUiState

/** Odwzorowanie stanu uprawnień SMS na identyfikatory tekstów (UI-17, UI-18, UI-20). */
object PermissionTexts {
    @StringRes
    val sectionTitle: Int = R.string.sms_section_title

    @StringRes
    val allGranted: Int = R.string.sms_all_granted

    @StringRes
    val missingLabel: Int = R.string.sms_missing_label

    @StringRes
    val requestAction: Int = R.string.sms_action_request

    @StringRes
    val openSettingsAction: Int = R.string.action_open_app_settings

    /** Nazwy brakujących uprawnień, w kolejności: wysyłanie, odbiór. */
    fun missing(state: SmsPermissionsUiState): List<Int> = buildList {
        if (!state.canSend) add(R.string.sms_permission_send)
        if (!state.canReceive) add(R.string.sms_permission_receive)
    }

    /** Tekst odmowy; null, gdy brak odmowy. */
    @StringRes
    fun denial(denial: PermissionDenial): Int? =
        when (denial) {
            PermissionDenial.NONE -> null
            PermissionDenial.DENIED, PermissionDenial.PERMANENTLY_DENIED -> R.string.sms_denied
        }

    fun showOpenSettings(denial: PermissionDenial): Boolean = denial == PermissionDenial.PERMANENTLY_DENIED
}
