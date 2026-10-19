package pl.twojanazwa.aapsremote.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnrecognizedReplyTest {

    @Test
    fun `PARSER-09 SMS od czlowieka w kontekscie BG i PUMP jest nierozpoznany z trescia`() {
        val body = "Czesc, kupisz chleb?"
        for (expected in listOf(ExpectedCommand.Bg, ExpectedCommand.Pump)) {
            assertEquals(AapsReply.Unrecognized(body), AapsReplyParser.parse(body, expected))
        }
    }
}
