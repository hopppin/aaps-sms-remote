package pl.twojanazwa.aapsremote.security

import java.util.Base64

/** SECRET-03. Tablice nie są kopiowane (szyfrogram nie jest tajny), toString() redagowane (SECRET-15). */
class SealedSecret(val version: Byte, val iv: ByteArray, val ciphertext: ByteArray) {
    init {
        require(version == SealedSecretFormat.VERSION_1) { "invalid version" }
        require(iv.size == SealedSecretFormat.IV_LENGTH) { "invalid iv length" }
        require(ciphertext.size >= SealedSecretFormat.MIN_CIPHERTEXT_LENGTH) { "invalid ciphertext length" }
    }

    override fun toString(): String = "SealedSecret(***)"
}

object SealedSecretFormat {
    const val VERSION_1: Byte = 0x01
    const val IV_LENGTH: Int = 12
    const val TAG_LENGTH: Int = 16
    const val MIN_CIPHERTEXT_LENGTH: Int = TAG_LENGTH + 1
    const val MIN_RECORD_LENGTH: Int = 1 + IV_LENGTH + MIN_CIPHERTEXT_LENGTH

    /** Base64 standardowy (RFC 4648, z '=', bez końców linii) z bajtów: wersja, IV, szyfrogram z tagiem. */
    fun encode(sealed: SealedSecret): String {
        val bytes = ByteArray(1 + sealed.iv.size + sealed.ciphertext.size)
        bytes[0] = sealed.version
        sealed.iv.copyInto(bytes, 1)
        sealed.ciphertext.copyInto(bytes, 1 + sealed.iv.size)
        return Base64.getEncoder().encodeToString(bytes)
    }

    /** null = rekord nieprawidłowy (SECRET-03). Nie rzuca. */
    fun decode(record: String): SealedSecret? {
        val bytes = try {
            Base64.getDecoder().decode(record)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (bytes.size < MIN_RECORD_LENGTH) return null
        if (bytes[0] != VERSION_1) return null
        return SealedSecret(
            VERSION_1,
            bytes.copyOfRange(1, 1 + IV_LENGTH),
            bytes.copyOfRange(1 + IV_LENGTH, bytes.size),
        )
    }

    /** AAD dla wersji. */
    fun aad(version: Byte): ByteArray = byteArrayOf(version)
}
