package pl.twojanazwa.aapsremote.data

/** DATA-10. */
sealed interface RecordResult {
    data object Recorded : RecordResult
    data object Failed : RecordResult
}
