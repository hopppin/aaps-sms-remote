package pl.twojanazwa.aapsremote.otp

/** Ścisły dekoder Base32 wg RFC 4648 rozdz. 6 (TOTP-05). Nie rzuca wyjątków i nie ujawnia wejścia (TOTP-09). */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val invalidRemainders = setOf(1, 3, 6)

    fun decode(text: String): Base32Result {
        val body = text.trimEnd('=')
        if (body.isEmpty()) return Base32Result.Empty
        if (body.length % 8 in invalidRemainders) return Base32Result.Invalid

        val out = ByteArray(body.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var index = 0
        for (c in body) {
            val value = if (c.code < 0x80) ALPHABET.indexOf(c.uppercaseChar()) else -1
            if (value < 0) {
                out.fill(0)
                return Base32Result.Invalid
            }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out[index++] = (buffer shr bits).toByte()
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return Base32Result.Success(out)
    }
}

sealed interface Base32Result {
    /** [bytes] należy wyzerować po użyciu. Brak data class – toString() redagowane. */
    class Success(val bytes: ByteArray) : Base32Result {
        override fun toString(): String = "Base32Result.Success(***)"
    }

    data object Empty : Base32Result

    data object Invalid : Base32Result
}
