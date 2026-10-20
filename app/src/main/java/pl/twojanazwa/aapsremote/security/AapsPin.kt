package pl.twojanazwa.aapsremote.security

/** PIN AAPS tylko w pamięci (SESSION-02). Metody synchronizowane – clear() może przyjść z innego wątku. */
class AapsPin private constructor(private val chars: CharArray) {
    private var cleared = false

    val length: Int
        @Synchronized get() = chars.size

    val isCleared: Boolean
        @Synchronized get() = cleared

    /** Kopia znaków; wywołujący zeruje ją po użyciu. IllegalStateException("PIN cleared") po clear(). */
    @Synchronized
    fun copyChars(): CharArray {
        check(!cleared) { "PIN cleared" }
        return chars.copyOf()
    }

    /** Zeruje znaki; idempotentne. */
    @Synchronized
    fun clear() {
        chars.fill('\u0000')
        cleared = true
    }

    override fun toString(): String = "AapsPin(***)"

    companion object {
        /** Kopiuje [source] i zeruje go. IllegalArgumentException("PIN must not be empty") dla pustej tablicy. */
        fun fromChars(source: CharArray): AapsPin {
            require(source.isNotEmpty()) { "PIN must not be empty" }
            val copy = source.copyOf()
            source.fill('\u0000')
            return AapsPin(copy)
        }
    }
}
