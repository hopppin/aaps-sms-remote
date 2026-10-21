package pl.twojanazwa.aapsremote.data

/** DATA-11. */
sealed interface HistorySnapshot {
    data class Entries(val entries: List<HistoryEntry>) : HistorySnapshot {
        override fun toString(): String = "Entries(size=${entries.size})"
    }

    data object Unavailable : HistorySnapshot
}
