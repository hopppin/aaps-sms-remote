package pl.twojanazwa.aapsremote.otp

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Własny parser URI `otpauth://totp/...` (TOTP-06, TOTP-07, TOTP-08).
 * Nie używa `java.net.URI` ani `android.net.Uri`, nie loguje i nie przepuszcza wyjątków z wejściem (TOTP-09).
 */
object OtpauthParser {
    private const val PREFIX = "otpauth://"
    private val uniqueParameters = setOf("secret", "algorithm", "digits", "period")

    fun parse(uri: String): OtpauthResult {
        val text = uri.trim { it.code <= 0x20 }
        if (!text.startsWith(PREFIX, ignoreCase = true)) return fail(OtpauthError.NOT_OTPAUTH_URI)
        val rest = text.substring(PREFIX.length)

        val typeEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
        if (!rest.substring(0, typeEnd).equals("totp", ignoreCase = true)) {
            return fail(OtpauthError.UNSUPPORTED_TYPE)
        }

        val queryStart = rest.indexOf('?')
        val query = if (queryStart < 0) "" else rest.substring(queryStart + 1)
        val params = HashMap<String, String>()
        if (query.isNotEmpty()) {
            for (element in query.split('&')) {
                val eq = element.indexOf('=')
                if (eq < 0) return fail(OtpauthError.MALFORMED_URI)
                val name = percentDecode(element.substring(0, eq)) ?: return fail(OtpauthError.MALFORMED_URI)
                val value = percentDecode(element.substring(eq + 1)) ?: return fail(OtpauthError.MALFORMED_URI)
                if (name in uniqueParameters) {
                    if (params.containsKey(name)) return fail(OtpauthError.DUPLICATE_PARAMETER)
                    params[name] = value
                }
            }
        }

        val secret = params["secret"] ?: return fail(OtpauthError.MISSING_SECRET)
        params["algorithm"]?.let {
            if (!it.equals("SHA1", ignoreCase = true)) return fail(OtpauthError.UNSUPPORTED_ALGORITHM)
        }
        params["digits"]?.let { if (it != "6") return fail(OtpauthError.UNSUPPORTED_DIGITS) }
        params["period"]?.let { if (it != "30") return fail(OtpauthError.UNSUPPORTED_PERIOD) }

        return when (val decoded = Base32.decode(secret)) {
            Base32Result.Empty -> fail(OtpauthError.EMPTY_SECRET)
            Base32Result.Invalid -> fail(OtpauthError.INVALID_BASE32)
            is Base32Result.Success -> {
                try {
                    OtpauthResult.Success(TotpSecret.fromBytes(decoded.bytes))
                } finally {
                    decoded.bytes.fill(0)
                }
            }
        }
    }

    private fun fail(error: OtpauthError): OtpauthResult = OtpauthResult.Failure(error)

    /** Dekoduje `%XX` jako UTF-8; `+` zostaje bez zmian. Zwraca null przy błędzie (bez wyjątków z wejściem). */
    private fun percentDecode(s: String): String? {
        if (s.indexOf('%') < 0) return s
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                if (i + 2 >= s.length) return null
                val hi = Character.digit(s[i + 1], 16)
                val lo = Character.digit(s[i + 2], 16)
                if (hi < 0 || lo < 0) return null
                out.write(hi * 16 + lo)
                i += 3
            } else {
                val end = if (Character.isHighSurrogate(c) && i + 1 < s.length) i + 2 else i + 1
                val bytes = s.substring(i, end).toByteArray(Charsets.UTF_8)
                out.write(bytes, 0, bytes.size)
                i = end
            }
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(out.toByteArray()))
                .toString()
        } catch (e: CharacterCodingException) {
            null
        }
    }
}

sealed interface OtpauthResult {
    class Success(val secret: TotpSecret) : OtpauthResult {
        override fun toString(): String = "OtpauthResult.Success(***)"
    }

    data class Failure(val error: OtpauthError) : OtpauthResult
}

/** Rodzaje błędów bez żadnych danych z wejścia (TOTP-09). */
enum class OtpauthError {
    NOT_OTPAUTH_URI,
    MALFORMED_URI,
    UNSUPPORTED_TYPE,
    MISSING_SECRET,
    DUPLICATE_PARAMETER,
    EMPTY_SECRET,
    INVALID_BASE32,
    UNSUPPORTED_ALGORITHM,
    UNSUPPORTED_DIGITS,
    UNSUPPORTED_PERIOD,
}
