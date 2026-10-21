package pl.twojanazwa.aapsremote.data

/** DATA-12. */
sealed interface ClearHistoryResult {
    data object Cleared : ClearHistoryResult
    data object Failed : ClearHistoryResult
}
