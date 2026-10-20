package pl.twojanazwa.aapsremote.security.platform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import pl.twojanazwa.aapsremote.security.KeyGenerationPolicy
import pl.twojanazwa.aapsremote.security.SecretKeySource
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject

/**
 * Klucz AES-256-GCM w AndroidKeyStore pod stałym aliasem (SECRET-01), generowany przez
 * [KeyGenerationPolicy]: StrongBox, a przy "StrongBox niedostępny" jedna próba TEE (SECRET-02).
 * Bez stanu i bez logowania (SECRET-15).
 */
class AndroidKeystoreKeySource @Inject constructor() : SecretKeySource {

    override fun getKey(): SecretKey? = keyStore().getKey(ALIAS, null) as? SecretKey

    override fun generateKey(): SecretKey = KeyGenerationPolicy.generate(
        attempt = { strongBox ->
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
                .apply { init(spec(strongBox)) }
                .generateKey()
        },
        isStrongBoxUnavailable = { it is StrongBoxUnavailableException },
    )

    override fun deleteKey() {
        keyStore().deleteEntry(ALIAS)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun spec(strongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(KEY_SIZE_BITS)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .setUnlockedDeviceRequired(true)
        if (strongBox) builder.setIsStrongBoxBacked(true)
        return builder.build()
    }

    override fun toString(): String = "AndroidKeystoreKeySource(***)"

    companion object {
        const val PROVIDER: String = "AndroidKeyStore"
        const val ALIAS: String = "pl.twojanazwa.aapsremote.totp-secret-key"
        private const val KEY_SIZE_BITS = 256
    }
}
