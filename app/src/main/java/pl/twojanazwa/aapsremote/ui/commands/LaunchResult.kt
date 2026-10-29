package pl.twojanazwa.aapsremote.ui.commands

enum class LaunchRejection {
    START_FAILED, COMMAND_ACTIVE, NUMBER_LOADING, NUMBER_MISSING, SMS_PERMISSIONS_MISSING,
    SECRET_ABSENT, SECRET_INVALIDATED, SECRET_LOCKED, SECRET_UNAVAILABLE,
    LIMITS_UNAVAILABLE, AMOUNT_ABOVE_LIMIT, EXTRA_CONFIRMATION_REQUIRED,
    BOLUS_INTERVAL, BOLUS_HISTORY_UNAVAILABLE,
}

sealed interface LaunchResult {
    /**
     * Sesja wystartowała; przebieg i wynik widok bierze z `CurrentRun` (publikowanego przez launcher),
     * a `activeSession` służy tylko blokadom i anulowaniu.
     */
    data object Started : LaunchResult

    /** [minutesLeft] tylko dla BOLUS_INTERVAL (BOL-12). */
    data class Rejected(val reason: LaunchRejection, val minutesLeft: Int? = null) : LaunchResult
}
