package pl.twojanazwa.aapsremote.otp

/** Sekret TOTP. Kopiuje bajty, nie ujawnia ich w toString(). */
class TotpSecret private constructor(private val key: ByteArray) {
    /** Dostęp do kopii bajtów dla `security` przy zapisie; wywołujący zeruje kopię po użyciu. */
    fun copyBytes(): ByteArray = key.copyOf()

    override fun toString(): String = "TotpSecret(***)"

    companion object {
        /** Kopiuje [bytes]; wywołujący może je potem wyzerować. Rzuca IllegalArgumentException dla pustej tablicy. */
        fun fromBytes(bytes: ByteArray): TotpSecret {
            require(bytes.isNotEmpty()) { "secret must not be empty" }
            return TotpSecret(bytes.copyOf())
        }
    }
}
