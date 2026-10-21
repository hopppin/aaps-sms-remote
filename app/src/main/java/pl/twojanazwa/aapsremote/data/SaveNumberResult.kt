package pl.twojanazwa.aapsremote.data

/** DATA-03. */
sealed interface SaveNumberResult {
    data object Saved : SaveNumberResult

    data object Invalid : SaveNumberResult

    data object Failed : SaveNumberResult
}
