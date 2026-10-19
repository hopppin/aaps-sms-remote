package pl.twojanazwa.aapsremote.parser

/** Normalizacja treści SMS przed dopasowaniem wzorców (PARSER-02). Nic poza białymi znakami. */
internal object ReplyNormalizer {
    const val MAX_LENGTH: Int = 2000

    /** Zwraca null, gdy treść przekracza [MAX_LENGTH] znaków (liczone przed normalizacją). */
    fun normalize(body: String): String? {
        if (body.length > MAX_LENGTH) return null
        return body
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .joinToString("\n") { it.trimEnd(' ', '\t') }
            .trim(' ', '\t', '\n')
    }
}
