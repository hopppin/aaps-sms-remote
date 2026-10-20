package pl.twojanazwa.aapsremote.security

sealed interface EncryptResult {
    class Success(val sealed: SealedSecret) : EncryptResult {
        override fun toString(): String = "Success(***)"
    }

    data class Failure(val reason: CryptoFailure) : EncryptResult
}

sealed interface DecryptResult {
    /** Wywołujący zeruje [plaintext]. */
    class Success(val plaintext: ByteArray) : DecryptResult {
        override fun toString(): String = "Success(***)"
    }

    data class Failure(val reason: CryptoFailure) : DecryptResult
}

/** Szyfrator rekordu. Nie rzuca poza CancellationException (SECRET-17). */
interface SecretCipher {
    /** Klucz istniejący albo wygenerowany. Nie modyfikuje i nie zeruje [plaintext] (zeruje magazyn). */
    fun encrypt(plaintext: ByteArray): EncryptResult

    /** Nigdy nie generuje klucza (SECRET-01). */
    fun decrypt(sealed: SealedSecret): DecryptResult

    /** false, gdy usunięcie rzuciło. */
    fun deleteKey(): Boolean
}
