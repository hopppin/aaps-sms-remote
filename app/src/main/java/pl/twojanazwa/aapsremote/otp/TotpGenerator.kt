package pl.twojanazwa.aapsremote.otp

import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Generator TOTP wg RFC 6238: HMAC-SHA1, krok 30 s, 6 cyfr. Nie odczytuje zegara systemowego. */
object TotpGenerator {
    const val PERIOD_MILLIS: Long = 30_000
    const val DIGITS: Int = 6

    private const val MODULUS = 1_000_000
    private const val MAX_EPOCH_MILLIS = Long.MAX_VALUE - PERIOD_MILLIS

    /** TOTP-03. Rzuca IllegalArgumentException dla epochMillis < 0 lub > Long.MAX_VALUE - PERIOD_MILLIS. */
    fun window(epochMillis: Long): TotpWindow {
        requireInRange(epochMillis)
        val from = epochMillis / PERIOD_MILLIS * PERIOD_MILLIS
        val until = from + PERIOD_MILLIS
        return TotpWindow(from, until, until - epochMillis)
    }

    /** TOTP-01. Rzuca IllegalArgumentException dla epochMillis < 0 lub > Long.MAX_VALUE - PERIOD_MILLIS. */
    fun generate(secret: TotpSecret, epochMillis: Long): TotpCode {
        val window = window(epochMillis)
        val counter = epochMillis / PERIOD_MILLIS
        val msg = ByteArray(8) { i -> (counter ushr (56 - 8 * i)).toByte() }
        val keyBytes = secret.copyBytes()
        val hmac: ByteArray
        try {
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
            hmac = mac.doFinal(msg)
        } catch (_: GeneralSecurityException) {
            // Celowo bez przyczyny: wyjątki JCA mogłyby zawierać dane wejściowe.
            throw IllegalStateException("HMAC computation failed")
        } finally {
            keyBytes.fill(0)
            msg.fill(0)
        }
        try {
            val offset = hmac[hmac.size - 1].toInt() and 0x0F
            val binary = ((hmac[offset].toInt() and 0x7F) shl 24) or
                ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
                ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
                (hmac[offset + 3].toInt() and 0xFF)
            val value = (binary % MODULUS).toString().padStart(DIGITS, '0')
            return TotpCode(value, window.validFromEpochMillis, window.validUntilEpochMillis)
        } finally {
            hmac.fill(0)
        }
    }

    private fun requireInRange(epochMillis: Long) {
        require(epochMillis in 0..MAX_EPOCH_MILLIS) { "epochMillis out of range" }
    }
}
