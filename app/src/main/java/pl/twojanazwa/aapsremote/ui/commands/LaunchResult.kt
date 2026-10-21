package pl.twojanazwa.aapsremote.ui.commands

enum class LaunchRejection {
    START_FAILED, COMMAND_ACTIVE, NUMBER_LOADING, NUMBER_MISSING, SMS_PERMISSIONS_MISSING,
    SECRET_ABSENT, SECRET_INVALIDATED, SECRET_LOCKED, SECRET_UNAVAILABLE,
}

sealed interface LaunchResult {
    /**
     * Sesja wystartowała; przebieg i wynik widok bierze z `CurrentRun` (publikowanego przez launcher),
     * a `activeSession` służy tylko blokadom i anulowaniu.
     */
    data object Started : LaunchResult

    data class Rejected(val reason: LaunchRejection) : LaunchResult
}
