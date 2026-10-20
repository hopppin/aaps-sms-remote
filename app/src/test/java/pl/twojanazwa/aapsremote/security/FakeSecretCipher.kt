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

    /** Gdy ustawiony, [deleteKey] go rzuca (po zliczeniu). */
    var deleteKeyError: Exception? = null

    /** Gdy ustawione, [encrypt] / [decrypt] rzucają (po zliczeniu). */
    var encryptError: Exception? = null
    var decryptError: Exception? = null

    /** Akcja wywoływana w [deleteKey] (np. odczyt licznika magazynu - kolejność wywołań). */
    var onDeleteKey: (() -> Unit)? = null

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

    /** Opcjonalny licznik równoczesnych wywołań i rejestr nazw wątków (SECRET-13). */
    var probe: ConcurrencyProbe? = null

    private fun <T> tracked(block: () -> T): T = probe?.track(block) ?: block()

    override fun encrypt(plaintext: ByteArray): EncryptResult = tracked { encryptImpl(plaintext) }

    override fun decrypt(sealed: SealedSecret): DecryptResult = tracked { decryptImpl(sealed) }

    override fun deleteKey(): Boolean = tracked { deleteKeyImpl() }

    private fun encryptImpl(plaintext: ByteArray): EncryptResult {
        encryptCount++
        encryptInputs += plaintext
        encryptInputSnapshots += plaintext.copyOf()
        encryptError?.let { throw it }
        return encryptQueue.removeFirstOrNull() ?: transparentEncrypt(plaintext)
    }

    private fun decryptImpl(sealed: SealedSecret): DecryptResult {
        decryptCount++
        decryptError?.let { throw it }
        val result = decryptQueue.removeFirstOrNull() ?: transparentDecrypt(sealed)
        if (result is DecryptResult.Success) decryptOutputs += result.plaintext
        return result
    }

    private fun deleteKeyImpl(): Boolean {
        deleteKeyCount++
        onDeleteKey?.invoke()
        deleteKeyError?.let { throw it }
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
