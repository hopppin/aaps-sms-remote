package pl.twojanazwa.aapsremote.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BgReplyTest {

    private fun parseBg(body: String) = AapsReplyParser.parse(body, ExpectedCommand.Bg)

    private fun assertUnrecognized(body: String) =
        assertEquals(AapsReply.Unrecognized(body), parseBg(body))

    @Test
    fun `PARSER-03 bg txt daje odpowiedz BG z glikemia 210 i trescia wejsciowa`() {
        val body = AapsSamples.bg
        assertEquals(AapsReply.BgReport(210, body), parseBg(body))
    }

    @Test
    fun `PARSER-03 liczby ujemne i delta z kropka`() {
        val body = "BG: 95, Delta: -4.5 MGDL, IOB: -0,12U (Bolus: 0,00U Baza: -0,12U), COB: 0g"
        assertEquals(AapsReply.BgReport(95, body), parseBg(body))
    }

    @Test
    fun `PARSER-03 zamienione separatory dziesietne`() {
        val body = "BG: 210, Delta: 10,0 MGDL, IOB: 0.99U (Bolus: 1.33U Baza: -0.34U), COB: 2g"
        assertEquals(AapsReply.BgReport(210, body), parseBg(body))
    }

    @Test
    fun `PARSER-03 MMOL zamiast MGDL jest nierozpoznane`() {
        assertUnrecognized(AapsSamples.bg.replace("MGDL", "MMOL"))
    }

    @Test
    fun `PARSER-03 glikemia ulamkowa jest nierozpoznana`() {
        assertUnrecognized(AapsSamples.bg.replace("BG: 210", "BG: 6,5"))
    }

    @Test
    fun `PARSER-03 glikemia 4 cyfry jest nierozpoznana`() {
        assertUnrecognized(AapsSamples.bg.replace("BG: 210", "BG: 1000"))
    }

    @Test
    fun `PARSER-03 dopisana druga linia jest nierozpoznana`() {
        assertUnrecognized(AapsSamples.bg + "\nUwaga")
    }

    @Test
    fun `PARSER-03 tekst przed wzorcem jest nierozpoznany`() {
        assertUnrecognized(
            "Odpowiedz: BG: 210, Delta: 10.0 MGDL, IOB: 0,99U (Bolus: 1,33U Baza: -0,34U), COB: 2g",
        )
    }
}
