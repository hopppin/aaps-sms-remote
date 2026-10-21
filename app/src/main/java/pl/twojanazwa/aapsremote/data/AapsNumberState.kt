package pl.twojanazwa.aapsremote.data

import pl.twojanazwa.aapsremote.sms.AapsNumber

/** DATA-02. */
sealed interface AapsNumberState {
    data object Loading : AapsNumberState

    data object NotConfigured : AapsNumberState

    class Configured(val number: AapsNumber) : AapsNumberState {
        override fun toString(): String = "Configured(***)"
    }

    data object LoadFailed : AapsNumberState
}
