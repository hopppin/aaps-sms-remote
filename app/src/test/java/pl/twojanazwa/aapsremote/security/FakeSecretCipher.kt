package pl.twojanazwa.aapsremote.security

/**
 * Wspólna atrapa szyfratora dla testów magazynu (spec 006). Kolejki zadanych wyników mają pierwszeństwo;
 * gdy kolejka jest pusta, działa tryb „przezroczysty" (XOR ze stałą - tylko testy magazynu).
 */
class FakeSecretCipher : SecretCipher {
    private val encryptQueue = ArrayDeque<EncryptResult>()
    private val decryptQueue = ArrayDeque<DecryptResult>()

    var encryptCount = 0
        private set
    var decryptCount = 0
        private set
    var deleteKeyCount = 0
        private set

    /** Wynik [deleteKey]. */
    var deleteKeyResult = true

    /** Referencje tablic przekazanych do [encrypt] (kolejno). */
    val encryptInputs = mutableListOf<ByteArray>()

    /** Kopie tablic z chwili wywołania [encrypt]. */
    val encryptInputSnapshots = mutableListOf<ByteArray>()

    /** Referencje tablic zwróconych z [decrypt] (kolejno). */
    val decryptOutputs = mutableListOf<ByteArray>()

    fun enqueueEncrypt(vararg results: EncryptResult) = results.forEach { encryptQueue.addLast(it) }

    fun enqueueDecrypt(vararg results: DecryptResult) = results.forEach { decryptQueue.addLast(it) }

    fun enqueueEncryptFailure(reason: CryptoFailure) = enqueueEncrypt(EncryptResult.Failure(reason))

    fun enqueueDecryptFailure(reason: CryptoFailure) = enqueueDecrypt(DecryptResult.Failure(reason))

    override fun encrypt(plaintext: ByteArray): EncryptResult {
        encryptCount++
        encryptInputs += plaintext
        encryptInputSnapshots += plaintext.copyOf()
        return encryptQueue.removeFirstOrNull() ?: transparentEncrypt(plaintext)
    }

    override fun decrypt(sealed: SealedSecret): DecryptResult {
        decryptCount++
        val result = decryptQueue.removeFirstOrNull() ?: transparentDecrypt(sealed)
        if (result is DecryptResult.Success) decryptOutputs += result.plaintext
        return result
    }

    override fun deleteKey(): Boolean {
        deleteKeyCount++
        return deleteKeyResult
    }

    private fun transparentEncrypt(plaintext: ByteArray): EncryptResult {
        val ct = ByteArray(plaintext.size + SealedSecretFormat.TAG_LENGTH)
        for (i in plaintext.indices) ct[i] = (plaintext[i].toInt() xor XOR_MASK).toByte()
        return EncryptResult.Success(
            SealedSecret(SealedSecretFormat.VERSION_1, ByteArray(SealedSecretFormat.IV_LENGTH), ct),
        )
    }

    private fun transparentDecrypt(sealed: SealedSecret): DecryptResult {
        val n = sealed.ciphertext.size - SealedSecretFormat.TAG_LENGTH
        val plain = ByteArray(n) { (sealed.ciphertext[it].toInt() xor XOR_MASK).toByte() }
        return DecryptResult.Success(plain)
    }

    private companion object {
        const val XOR_MASK = 0x5A
    }
}
