package pl.twojanazwa.aapsremote.ui.settings.secret

/** Wyświetlany stan sekretu; `SecretState.UNKNOWN` daje [CHECKING] (UI-07). */
enum class SecretDisplay { CHECKING, ABSENT, PRESENT, INVALIDATED }

enum class SecretInputMode { NONE, SCANNER, PASTE }

/** Czynność, której dotyczy pytanie o zastąpienie istniejącego sekretu (UI-08). */
enum class PendingSecretInput { SCAN, PASTE }
