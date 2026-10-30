package pl.twojanazwa.aapsremote.commands

/** PROF-02: stałe zakresy zmiany profilu. */
object ProfileLimits {
    const val MIN_PERCENT = 50
    const val MAX_PERCENT = 200
    /** Zmiana profilu dotyczy zawsze profilu 1 (PROF-01). */
    const val PROFILE_NUMBER = 1
}
