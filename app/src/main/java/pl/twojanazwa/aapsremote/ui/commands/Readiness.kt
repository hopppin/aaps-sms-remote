package pl.twojanazwa.aapsremote.ui.commands

import pl.twojanazwa.aapsremote.data.AapsNumberState
import pl.twojanazwa.aapsremote.security.SecretState

enum class NumberReadiness { LOADING, CONFIGURED, NOT_CONFIGURED, LOAD_FAILED }

/** Z SecretState: UNKNOWN -> UNKNOWN (CARBS dostępne). */
enum class SecretReadiness { UNKNOWN, PRESENT, ABSENT, INVALIDATED }

fun AapsNumberState.toReadiness(): NumberReadiness = when (this) {
    AapsNumberState.Loading -> NumberReadiness.LOADING
    is AapsNumberState.Configured -> NumberReadiness.CONFIGURED
    AapsNumberState.NotConfigured -> NumberReadiness.NOT_CONFIGURED
    AapsNumberState.LoadFailed -> NumberReadiness.LOAD_FAILED
}

fun SecretState.toReadiness(): SecretReadiness = when (this) {
    SecretState.UNKNOWN -> SecretReadiness.UNKNOWN
    SecretState.PRESENT -> SecretReadiness.PRESENT
    SecretState.ABSENT -> SecretReadiness.ABSENT
    SecretState.INVALIDATED -> SecretReadiness.INVALIDATED
}
