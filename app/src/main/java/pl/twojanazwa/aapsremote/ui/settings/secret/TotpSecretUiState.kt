package pl.twojanazwa.aapsremote.ui.settings.secret

import pl.twojanazwa.aapsremote.ui.permissions.PermissionDenial

data class TotpSecretUiState(
    val display: SecretDisplay = SecretDisplay.CHECKING,
    val mode: SecretInputMode = SecretInputMode.NONE,
    val pasteText: String = "",
    val confirmingReplace: PendingSecretInput? = null,
    val confirmingDelete: Boolean = false,
    val cameraPermissionRequest: Boolean = false,
    val cameraDenial: PermissionDenial = PermissionDenial.NONE,
    val busy: Boolean = false,
    val commandActive: Boolean = false,
    val message: SecretMessage? = null,
) {
    private val idle: Boolean get() = !busy && !commandActive
    val canScan: Boolean get() = idle
    val canPaste: Boolean get() = idle
    val canDelete: Boolean get() = idle && display != SecretDisplay.ABSENT
    val canSubmitPaste: Boolean get() = idle && pasteText.isNotBlank()

    /** UI-23: FLAG_SECURE, gdy skaner albo okno wklejania otwarte. */
    val secureWindow: Boolean get() = mode != SecretInputMode.NONE

    /** UI-21: bez [pasteText]. */
    override fun toString(): String =
        "TotpSecretUiState(display=$display, mode=$mode, confirmingReplace=$confirmingReplace, " +
            "confirmingDelete=$confirmingDelete, cameraPermissionRequest=$cameraPermissionRequest, " +
            "cameraDenial=$cameraDenial, busy=$busy, commandActive=$commandActive, message=$message)"
}
