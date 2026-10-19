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

            is ExpectedCommand.Carbs -> parseCarbs(n, firstLine, body, expected.grams)
        }
        return result ?: AapsReply.Unrecognized(body)
    }

    private fun parseCarbs(n: String, firstLine: String, body: String, expectedGrams: Int): AapsReply? {
        if (AapsPl3426Patterns.WRONG_CODE.matchEntire(n) != null) return AapsReply.WrongCode
        val requested = AapsPl3426Patterns.CODE_REQUEST.matchEntire(n)?.groupValues?.get(1)?.toInt()
        if (requested != null) {
            return if (requested == expectedGrams) {
                AapsReply.CodeRequest(requested, body)
            } else {
                AapsReply.CarbsAmountMismatch(expectedGrams, requested, CarbsStage.CODE_REQUEST, body)
            }
        }
        val done = AapsPl3426Patterns.CARBS_DONE_FIRST_LINE.matchEntire(firstLine)
            ?.groupValues?.get(1)?.toInt() ?: return null
        return if (done == expectedGrams) {
            AapsReply.CarbsDone(done, body)
        } else {
            AapsReply.CarbsAmountMismatch(expectedGrams, done, CarbsStage.CONFIRMATION, body)
        }
    }
}
