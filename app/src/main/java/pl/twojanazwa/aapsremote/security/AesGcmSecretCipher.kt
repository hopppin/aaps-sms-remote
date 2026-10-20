package pl.twojanazwa.aapsremote.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

// Poza klasą, żeby nie dodawać do niej pól statycznych (SECRET-14).
private const val TRANSFORMATION = "AES/GCM/NoPadding"

/**
 * SECRET-03, SECRET-07. AES/GCM/NoPadding, IV z szyfru, tag 128 bitów, AAD = bajt wersji.
 * Pola: tylko zależności (SECRET-14). Nie rzuca poza `CancellationException` (SECRET-17).
 */
class AesGcmSecretCipher @Inject constructor(
    private val keySource: SecretKeySource,
    private val environment: KeystoreEnvironment,
) : SecretCipher {

    override fun encrypt(plaintext: ByteArray): EncryptResult {
        val existing: SecretKey? = try {
            keySource.getKey()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EncryptResult.Failure(CryptoFailureClassifier.classify(e, environment))
        }
        val key = existing ?: try {
            keySource.generateKey()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EncryptResult.Failure(CryptoFailure.KEY_GENERATION_FAILED)
        }
        val iv: ByteArray?
        val ct: ByteArray
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(SealedSecretFormat.aad(SealedSecretFormat.VERSION_1))
            ct = cipher.doFinal(plaintext)
            iv = cipher.iv
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return EncryptResult.Failure(CryptoFailureClassifier.classify(e, environment))
        }
        if (iv == null || iv.size != SealedSecretFormat.IV_LENGTH ||
            ct.size != plaintext.size + SealedSecretFormat.TAG_LENGTH ||
            ct.size < SealedSecretFormat.MIN_CIPHERTEXT_LENGTH
        ) {
            return EncryptResult.Failure(CryptoFailure.UNAVAILABLE)
        }
        return EncryptResult.Success(SealedSecret(SealedSecretFormat.VERSION_1, iv, ct))
    }

    override fun decrypt(sealed: SealedSecret): DecryptResult {
        val key: SecretKey = try {
            keySource.getKey()
                ?: return DecryptResult.Failure(CryptoFailureClassifier.classifyMissingKey(environment))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return DecryptResult.Failure(CryptoFailureClassifier.classify(e, environment))
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(SealedSecretFormat.TAG_LENGTH * 8, sealed.iv),
            )
            cipher.updateAAD(SealedSecretFormat.aad(sealed.version))
            DecryptResult.Success(cipher.doFinal(sealed.ciphertext))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DecryptResult.Failure(CryptoFailureClassifier.classify(e, environment))
        }
    }

    override fun deleteKey(): Boolean = try {
        keySource.deleteKey()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    override fun toString(): String = "AesGcmSecretCipher(***)"
}
