package pl.twojanazwa.aapsremote.data

/** DATA-04. */
sealed interface ClearNumberResult {
    data object Cleared : ClearNumberResult

    data object Failed : ClearNumberResult
}
