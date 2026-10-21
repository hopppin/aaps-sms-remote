package pl.twojanazwa.aapsremote.ui.settings.secret

/** Komunikaty sekcji sekretu TOTP; ViewModel zwraca wartości, nie teksty (UI-20). */
enum class SecretMessage {
    SAVED, LOCKED, KEY_GENERATION_FAILED, SAVE_FAILED,
    NOT_TOTP_QR, UNSUPPORTED_PARAMETERS, INVALID_SECRET,
    DELETED, DELETE_FAILED, CAMERA_UNAVAILABLE, COMMAND_ACTIVE,
}
