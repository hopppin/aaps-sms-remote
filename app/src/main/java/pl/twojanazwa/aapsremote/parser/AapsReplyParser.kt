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

            is ExpectedCommand.Bolus -> parseBolus(n, firstLine, body, expected.centiUnits)

            ExpectedCommand.ProfileStatus -> parseProfileStatus(n, body)

            ExpectedCommand.ProfileList -> parseProfileList(n, body)

            is ExpectedCommand.ProfileSwitch -> parseProfileSwitch(n, body, expected)
        }
        return result ?: AapsReply.Unrecognized(body)
    }

    private fun parseProfileStatus(n: String, body: String): AapsReply? {
        val name = AapsPl3426Patterns.PROFILE_STATUS.matchEntire(n)?.groupValues?.get(1) ?: return null
        return if (name.startsWith(' ')) null else AapsReply.ProfileStatusReport(body)
    }

    private fun parseProfileList(n: String, body: String): AapsReply? {
        val items = n.split('\n').mapIndexed { i, line ->
            val m = AapsPl3426Patterns.PROFILE_LIST_LINE.matchEntire(line) ?: return null
            if (m.groupValues[1].toInt() != i + 1) return null
            AapsReply.ProfileListItem(i + 1, m.groupValues[2])
        }
        if (items.map { it.name }.toSet().size != items.size) return null
        return AapsReply.ProfileListReport(items, body)
    }

    private fun parseProfileSwitch(n: String, body: String, expected: ExpectedCommand.ProfileSwitch): AapsReply? {
        if (AapsPl3426Patterns.WRONG_CODE.matchEntire(n) != null) return AapsReply.WrongCode
        val g = AapsPl3426Patterns.PROFILE_CODE_REQUEST.matchEntire(n)?.groupValues ?: return null
        // Porównanie dokładne (wielkość liter); inaczej stop, nigdy kod (PROF-09).
        return if (g[1] == expected.profileName && g[2].toInt() == expected.percent) {
            AapsReply.ProfileCodeRequest(body)
        } else {
            AapsReply.ProfileSwitchMismatch(body)
        }
    }

    private fun centiUnits(m: MatchResult): Int = m.groupValues[1].toInt() * 100 + m.groupValues[2].toInt()

    private fun parseBolus(n: String, firstLine: String, body: String, expected: Int): AapsReply? {
        if (AapsPl3426Patterns.WRONG_CODE.matchEntire(n) != null) return AapsReply.WrongCode
        AapsPl3426Patterns.BOLUS_CODE_REQUEST.matchEntire(n)?.let {
            val received = centiUnits(it)
            return if (received == expected) {
                AapsReply.BolusCodeRequest(received, body)
            } else {
                AapsReply.BolusAmountMismatch(expected, received, CarbsStage.CODE_REQUEST, body)
            }
        }
        val done = AapsPl3426Patterns.BOLUS_DONE_FIRST_LINE.matchEntire(firstLine)?.let(::centiUnits) ?: return null
        return if (done == expected) {
            AapsReply.BolusDone(done, body)
        } else {
            AapsReply.BolusAmountMismatch(expected, done, CarbsStage.CONFIRMATION, body)
        }
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
