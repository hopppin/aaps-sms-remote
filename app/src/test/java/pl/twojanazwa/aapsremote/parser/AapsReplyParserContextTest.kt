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
    fun `PARSER-01 pusta tresc w kontekscie BG i PUMP bez wyjatku`() {
        for (expected in listOf(ExpectedCommand.Bg, ExpectedCommand.Pump)) {
            assertEquals(AapsReply.Unrecognized(""), AapsReplyParser.parse("", expected))
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
