package pl.twojanazwa.aapsremote.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PumpReplyTest {

    private fun parsePump(body: String) = AapsReplyParser.parse(body, ExpectedCommand.Pump)

    private fun assertUnrecognized(body: String) =
        assertEquals(AapsReply.Unrecognized(body), parsePump(body))

    @Test
    fun `PARSER-04 pump txt daje stan pompy z trescia wejsciowa`() {
        val body = AapsSamples.pump
        assertEquals(AapsReply.PumpStatus(body), parsePump(body))
    }

    @Test
    fun `PARSER-04 sama pierwsza linia`() {
        val body = "Ost. Pol.: 12 m. temu"
        assertEquals(AapsReply.PumpStatus(body), parsePump(body))
    }

    @Test
    fun `PARSER-04 inne linie 2 i 3 nie maja znaczenia`() {
        val first = AapsSamples.pump.substringBefore('\n')
        val body = "$first\nBat.: 15%"
        assertEquals(AapsReply.PumpStatus(body), parsePump(body))
    }

    @Test
    fun `PARSER-04 brak kropki po Pol jest nierozpoznany`() {
        assertUnrecognized(AapsSamples.pump.replaceFirst("Ost. Pol.: 0", "Ost. Pol: 0"))
    }

    @Test
    fun `PARSER-04 brak liczby minut jest nierozpoznany`() {
        assertUnrecognized(AapsSamples.pump.replaceFirst("Ost. Pol.: 0 m.", "Ost. Pol.: m."))
    }

    @Test
    fun `PARSER-04 linia Ost Pol jako druga linia jest nierozpoznana`() {
        assertUnrecognized(AapsSamples.carbsDone)
    }

    @Test
    fun `PARSER-02 pump txt z CRLF daje stan pompy`() {
        val body = AapsSamples.pump.replace("\n", "\r\n")
        assertEquals(AapsReply.PumpStatus(body), parsePump(body))
    }
}
