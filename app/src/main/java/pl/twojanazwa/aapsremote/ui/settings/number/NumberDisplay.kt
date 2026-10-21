package pl.twojanazwa.aapsremote.ui.settings.number

/** Sposób wyświetlania numeru AAPS w sekcji ustawień (UI-04). */
sealed interface NumberDisplay {
    data object Loading : NumberDisplay

    data object NotConfigured : NumberDisplay

    class Configured(val destination: String) : NumberDisplay {
        override fun equals(other: Any?): Boolean = other is Configured && other.destination == destination

        override fun hashCode(): Int = destination.hashCode()

        override fun toString(): String = "Configured(***)"
    }

    data object LoadFailed : NumberDisplay
}
