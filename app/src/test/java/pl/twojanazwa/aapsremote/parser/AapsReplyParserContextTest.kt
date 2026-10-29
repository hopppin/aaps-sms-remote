package pl.twojanazwa.aapsremote.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AapsReplyParserContextTest {

    @Test
    fun `PARSER-01 bg txt w kontekscie PUMP jest nierozpoznane`() {
        val body = AapsSamples.bg
        assertEquals(AapsReply.Unrecognized(body), AapsReplyParser.parse(body, ExpectedCommand.Pump))
    }

    @Test
    fun `PARSER-01 pump txt w kontekscie BG jest nierozpoznane`() {
        val body = AapsSamples.pump
        assertEquals(AapsReply.Unrecognized(body), AapsReplyParser.parse(body, ExpectedCommand.Bg))
    }

    @Test
    fun `PARSER-01 pliki CARBS w kontekscie BG i PUMP sa nierozpoznane`() {
        val bodies = listOf(AapsSamples.carbsCodeRequest, AapsSamples.carbsDone, AapsSamples.wrongCode)
        for (body in bodies) {
            for (expected in listOf(ExpectedCommand.Bg, ExpectedCommand.Pump)) {
                assertEquals(AapsReply.Unrecognized(body), AapsReplyParser.parse(body, expected))
            }
        }
    }

    @Test
    fun `BOL-06 prosba o kod bolusa z poprawna iloscia jest nierozpoznana, bo parser bolusa to zad 2`() {
        val body = AapsSamples.bolusCodeRequest
        assertEquals(AapsReply.Unrecognized(body), AapsReplyParser.parse(body, ExpectedCommand.Bolus(10)))
    }

    @Test
    fun `PARSER-01 pusta tresc w kontekscie BG i PUMP bez wyjatku`() {
        for (expected in listOf(ExpectedCommand.Bg, ExpectedCommand.Pump)) {
            assertEquals(AapsReply.Unrecognized(""), AapsReplyParser.parse("", expected))
        }
    }

    @Test
    fun `PARSER-01 bg txt i pump txt w kontekscie CARBS 1 sa nierozpoznane`() {
        for (body in listOf(AapsSamples.bg, AapsSamples.pump)) {
            assertEquals(AapsReply.Unrecognized(body), AapsReplyParser.parse(body, ExpectedCommand.Carbs(1)))
        }
    }

    @Test
    fun `PARSER-01 pusta tresc w kontekscie CARBS 1 jest nierozpoznana bez wyjatku`() {
        assertEquals(AapsReply.Unrecognized(""), AapsReplyParser.parse("", ExpectedCommand.Carbs(1)))
    }

    @Test
    fun `PARSER-01 macierz kontekstow - kazdy plik w kazdym kontekscie daje tylko oczekiwany wynik`() {
        val bg = AapsSamples.bg
        val pump = AapsSamples.pump
        val request = AapsSamples.carbsCodeRequest
        val done = AapsSamples.carbsDone
        val wrong = AapsSamples.wrongCode
        val contexts = listOf(ExpectedCommand.Bg, ExpectedCommand.Pump, ExpectedCommand.Carbs(1))
        // Jedyne dozwolone wyniki inne niz Unrecognized: plik x wlasciwy kontekst.
        val allowed = mapOf<Pair<String, ExpectedCommand>, AapsReply>(
            (bg to ExpectedCommand.Bg) to AapsReply.BgReport(210, bg),
            (pump to ExpectedCommand.Pump) to AapsReply.PumpStatus(pump),
            (request to ExpectedCommand.Carbs(1)) to AapsReply.CodeRequest(1, request),
            (done to ExpectedCommand.Carbs(1)) to AapsReply.CarbsDone(1, done),
            (wrong to ExpectedCommand.Carbs(1)) to AapsReply.WrongCode,
        )
        for (body in listOf(bg, pump, request, done, wrong)) {
            for (context in contexts) {
                val expected = allowed[body to context] ?: AapsReply.Unrecognized(body)
                assertEquals(expected, AapsReplyParser.parse(body, context))
            }
        }
    }

    @Test
    fun `PARSER-01 kontekst CARBS z iloscia 0 lub -5 rzuca IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) { ExpectedCommand.Carbs(0) }
        assertThrows(IllegalArgumentException::class.java) { ExpectedCommand.Carbs(-5) }
    }

    @Test
    fun `PARSER-01 parser jest deterministyczny`() {
        val body = AapsSamples.bg
        val first = AapsReplyParser.parse(body, ExpectedCommand.Bg)
        val second = AapsReplyParser.parse(body, ExpectedCommand.Bg)
        assertEquals(first, second)
    }

    @Test
    fun `PARSER-02 bg txt dopelnione spacjami do 2000 znakow jest odpowiedzia BG`() {
        val body = AapsSamples.bg.padEnd(2000, ' ')
        assertEquals(2000, body.length)
        assertEquals(AapsReply.BgReport(210, body), AapsReplyParser.parse(body, ExpectedCommand.Bg))
    }

    @Test
    fun `PARSER-02 bg txt dopelnione spacjami do 2001 znakow jest nierozpoznane`() {
        val body = AapsSamples.bg.padEnd(2001, ' ')
        assertEquals(AapsReply.Unrecognized(body), AapsReplyParser.parse(body, ExpectedCommand.Bg))
    }
}
