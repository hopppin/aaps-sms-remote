package pl.twojanazwa.aapsremote.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class WrongCodeTest {

    private fun parse(body: String, grams: Int = 1) = AapsReplyParser.parse(body, ExpectedCommand.Carbs(grams))

    private fun assertUnrecognized(body: String) = assertEquals(AapsReply.Unrecognized(body), parse(body))

    @ParameterizedTest
    @ValueSource(ints = [1, 50])
    fun `PARSER-08 plik zlego kodu daje WrongCode`(grams: Int) {
        assertEquals(AapsReply.WrongCode, parse(AapsSamples.wrongCode, grams))
    }

    @ParameterizedTest
    @ValueSource(strings = ["Zly kod. Polecenie anulowano", "Zly kod."])
    fun `PARSER-08 niepelna tresc zlego kodu jest nierozpoznana`(body: String) {
        assertUnrecognized(body)
    }

    @Test
    fun `PARSER-08 druga linia po zlym kodzie jest nierozpoznana`() {
        assertUnrecognized(AapsSamples.wrongCode + "\nOst. Pol.: 1 m. temu")
    }

    @Test
    fun `PARSER-02 biale znaki na brzegach zlego kodu sa ignorowane`() {
        assertEquals(AapsReply.WrongCode, parse(" " + AapsSamples.wrongCode + "  \n"))
    }

    @Test
    fun `PARSER-02 wielkie litery w zlym kodzie sa nierozpoznane`() {
        assertUnrecognized("ZLY KOD. POLECENIE ANULOWANO.")
    }

    @Test
    fun `PARSER-02 twarda spacja w zlym kodzie jest nierozpoznana`() {
        assertUnrecognized(AapsSamples.wrongCode.replace("Zly kod", "Zly kod"))
    }
}
