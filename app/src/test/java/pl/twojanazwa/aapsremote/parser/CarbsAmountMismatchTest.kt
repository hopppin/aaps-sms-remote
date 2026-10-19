package pl.twojanazwa.aapsremote.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CarbsAmountMismatchTest {

    private fun request(grams: Int) =
        "Aby wprowadzic ${grams}g o 14:50 odeslij w SMS kod z aplikacji Authenticator dla: Jan, z doklejonym PIN-em"

    private fun mismatch(expected: Int, received: Int, body: String) =
        AapsReply.CarbsAmountMismatch(expected, received, CarbsStage.CODE_REQUEST, body)

    @Test
    fun `PARSER-06 plik prosby 1 g przy CARBS 10 to niezgodna ilosc`() {
        val body = AapsSamples.carbsCodeRequest
        assertEquals(mismatch(10, 1, body), AapsReplyParser.parse(body, ExpectedCommand.Carbs(10)))
    }

    @Test
    fun `PARSER-06 100g przy CARBS 150 to niezgodna ilosc`() {
        val body = request(100)
        assertEquals(mismatch(150, 100, body), AapsReplyParser.parse(body, ExpectedCommand.Carbs(150)))
    }

    @Test
    fun `PARSER-06 0g przy CARBS 1 to niezgodna ilosc`() {
        val body = request(0)
        assertEquals(mismatch(1, 0, body), AapsReplyParser.parse(body, ExpectedCommand.Carbs(1)))
    }

    @Test
    fun `PARSER-06 12g przy CARBS 1 i CARBS 2 to niezgodna ilosc`() {
        val body = request(12)
        for (g in listOf(1, 2)) {
            assertEquals(mismatch(g, 12, body), AapsReplyParser.parse(body, ExpectedCommand.Carbs(g)))
        }
    }

    @Test
    fun `PARSER-06 CodeRequest powstaje wylacznie przy rownej ilosci`() {
        for (expected in 1..150) {
            for (received in listOf(0, 1, 2, 12, 100, 150, 9999)) {
                val reply = AapsReplyParser.parse(request(received), ExpectedCommand.Carbs(expected))
                assertEquals(expected == received, reply is AapsReply.CodeRequest)
            }
        }
    }

    @Test
    fun `PARSER-10 toString niezgodnej ilosci 150 i 100 nie zawiera ilosci`() {
        val text = AapsReplyParser.parse(request(100), ExpectedCommand.Carbs(150)).toString()
        assertTrue(text.contains("CarbsAmountMismatch"))
        assertFalse(text.contains("150"))
        assertFalse(text.contains("100"))
    }
}
