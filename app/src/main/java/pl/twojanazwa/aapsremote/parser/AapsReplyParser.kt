package pl.twojanazwa.aapsremote.parser

object AapsReplyParser {
    const val MAX_LENGTH: Int = ReplyNormalizer.MAX_LENGTH

    /** Bezstanowy, nigdy nie rzuca wyjątku (PARSER-01). */
    fun parse(body: String, expected: ExpectedCommand): AapsReply {
        val n = ReplyNormalizer.normalize(body) ?: return AapsReply.Unrecognized(body)
        val firstLine = n.substringBefore('\n')
        val result: AapsReply? = when (expected) {
            ExpectedCommand.Bg ->
                AapsPl3426Patterns.BG_LINE.matchEntire(n)
                    ?.let { AapsReply.BgReport(it.groupValues[1].toInt(), body) }

            ExpectedCommand.Pump ->
                if (AapsPl3426Patterns.PUMP_FIRST_LINE.matchEntire(firstLine) != null) {
                    AapsReply.PumpStatus(body)
                } else {
                    null
                }

            // Logika CARBS: kolejne zadania (#14, #15).
            is ExpectedCommand.Carbs -> null
        }
        return result ?: AapsReply.Unrecognized(body)
    }
}
