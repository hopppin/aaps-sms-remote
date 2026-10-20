package pl.twojanazwa.aapsremote.security

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** Wspólna atrapa źródła klucza dla testów spec 006: klucz programowy JCE. */
class FakeSecretKeySource(hasKey: Boolean = true) : SecretKeySource {

    companion object {
        /** Klucz K = 0x00..0x1F. */
        fun keyK(): SecretKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

        /** Drugi klucz = 0x20..0x3F. */
        fun otherKey(): SecretKey = SecretKeySpec(ByteArray(32) { (0x20 + it).toByte() }, "AES")
    }

    /** Aktualny klucz; null = brak aliasu. */
    var currentKey: SecretKey? = if (hasKey) keyK() else null

    var getKeyCount = 0
        private set
    var generateKeyCount = 0
        private set
    var deleteKeyCount = 0
        private set

    /** Tryb „utrata klucza”: po `generateKey` kolejne `getKey` zwracają null. */
    var loseKeyAfterGenerate = false

    var getKeyError: Throwable? = null
    var generateKeyError: Throwable? = null
    var deleteKeyError: Throwable? = null

    /** Podmienia klucz na drugi (0x20..0x3F). */
    fun swapKey() {
        currentKey = otherKey()
    }

    override fun getKey(): SecretKey? {
        getKeyCount++
        getKeyError?.let { throw it }
        return currentKey
    }

    override fun generateKey(): SecretKey {
        generateKeyCount++
        generateKeyError?.let { throw it }
        val generated = keyK()
        currentKey = if (loseKeyAfterGenerate) null else generated
        return generated
    }

    override fun deleteKey() {
        deleteKeyCount++
        deleteKeyError?.let { throw it }
        currentKey = null
    }
}
