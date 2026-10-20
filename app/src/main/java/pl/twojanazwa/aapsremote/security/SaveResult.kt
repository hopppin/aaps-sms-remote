package pl.twojanazwa.aapsremote.security

enum class SaveFailure { KEY_GENERATION, CRYPTO, STORAGE }

sealed interface SaveResult {
    data object Saved : SaveResult

    data object Locked : SaveResult

    data class Failed(val reason: SaveFailure) : SaveResult {
        override fun toString(): String = "Failed($reason)"
    }
}
