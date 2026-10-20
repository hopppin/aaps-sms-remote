package pl.twojanazwa.aapsremote.security

sealed interface DeleteResult {
    data object Deleted : DeleteResult

    data object Failed : DeleteResult
}
