package pl.twojanazwa.aapsremote.commands

/** PROF-02, PROF-03. toString() bez danych (przez Command.ProfileSwitch). */
sealed interface ProfileSwitchValidation {
    data class Valid(val command: Command.ProfileSwitch) : ProfileSwitchValidation
    data class Invalid(val error: ProfileSwitchError) : ProfileSwitchValidation
}

enum class ProfileSwitchError { INVALID_CHOICE, EMPTY, INVALID_CHARACTERS, BELOW_MIN, ABOVE_MAX }
