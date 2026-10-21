package pl.twojanazwa.aapsremote.ui.permissions

/** Stan sekcji „Uprawnienia SMS” (UI-17, UI-18). */
data class SmsPermissionsUiState(
    val canSend: Boolean,
    val canReceive: Boolean,
    val denial: PermissionDenial = PermissionDenial.NONE,
) {
    val allGranted: Boolean get() = canSend && canReceive
    val canRequest: Boolean get() = !allGranted
}
