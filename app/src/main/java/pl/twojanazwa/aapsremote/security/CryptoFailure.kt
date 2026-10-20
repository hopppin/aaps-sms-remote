package pl.twojanazwa.aapsremote.security

/** Sklasyfikowany błąd kryptograficzny (SECRET-02, SECRET-07). */
enum class CryptoFailure { LOCKED, KEY_INVALIDATED, CORRUPTED, KEY_GENERATION_FAILED, UNAVAILABLE }
