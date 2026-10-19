package pl.twojanazwa.aapsremote.parser

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ParserRedactionTest {

    private val forbidden = listOf("210", "89%", "Jan", "14:50", "Pomyslnie", "Zly")

    private fun assertRedacted(text: String, body: String) {
        for (word in forbidden) assertFalse(text.contains(word), "zawiera zabroniony fragment")
        for (line in body.lines().filter { it.isNotBlank() }) {
            assertFalse(text.contains(line), "zawiera linie tresci")
        }
    }

    @Test
    fun `PARSER-10 toString odpowiedzi BG nie zawiera tresci`() {
        val body = AapsSamples.bg
        val reply = AapsReplyParser.parse(body, ExpectedCommand.Bg)
        assertTrue(reply is AapsReply.BgReport)
        assertRedacted(reply.toString(), body)
    }

    @Test
    fun `PARSER-10 toString stanu pompy nie zawiera tresci`() {
        val body = AapsSamples.pump
        val reply = AapsReplyParser.parse(body, ExpectedCommand.Pump)
        assertTrue(reply is AapsReply.PumpStatus)
        assertRedacted(reply.toString(), body)
    }

    @Test
    fun `PARSER-10 toString nierozpoznanej nie zawiera tresci`() {
        val reply = AapsReplyParser.parse("Czesc, kupisz chleb?", ExpectedCommand.Bg)
        assertTrue(reply is AapsReply.Unrecognized)
        assertFalse(reply.toString().contains("chleb"))
    }

    @Test
    fun `PARSER-10 toString prosby o kod i zlego kodu nie zawiera tresci`() {
        for (body in listOf(AapsSamples.carbsCodeRequest, AapsSamples.wrongCode)) {
            val reply = AapsReplyParser.parse(body, ExpectedCommand.Carbs(1))
            assertTrue(reply is AapsReply.CodeRequest || reply is AapsReply.WrongCode)
            assertRedacted(reply.toString(), body)
        }
    }

    @Test
    fun `PARSER-10 toString kontekstu CARBS nie zawiera ilosci`() {
        assertFalse(ExpectedCommand.Carbs(37).toString().contains("37"))
    }

    @Test
    fun `PARSER-10 komunikat wyjatku require nie zawiera danych`() {
        val e = runCatching { ExpectedCommand.Carbs(-5) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
        assertFalse(e?.message.orEmpty().contains("5"))
    }
}
