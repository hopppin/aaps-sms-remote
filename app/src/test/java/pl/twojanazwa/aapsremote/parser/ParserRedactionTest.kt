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
    fun `PARSER-10 toString potwierdzenia wykonania CARBS nie zawiera tresci`() {
        val body = AapsSamples.carbsDone
        val reply = AapsReplyParser.parse(body, ExpectedCommand.Carbs(1))
        assertTrue(reply is AapsReply.CarbsDone)
        assertRedacted(reply.toString(), body)
    }

    @Test
    fun `BOL-09 toString kontekstu Bolus nie zawiera ilosci`() {
        assertFalse(ExpectedCommand.Bolus(137).toString().contains("137"))
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

    @Test
    fun `BOL-08 toString niezgodnej ilosci bolusa nie zawiera ilosci`() {
        for (stage in CarbsStage.values()) {
            val text = AapsReply.BolusAmountMismatch(737, 419, stage, "Bolus 4,19 U podany prawidlowo").toString()
            for (word in listOf("737", "419", "7,37", "4,19", "podany")) {
                assertFalse(text.contains(word), "zawiera zabroniony fragment")
            }
        }
    }

    @Test
    fun `BOL-20 toString odmowy bolusa nie zawiera tresci`() {
        for (body in listOf(AapsSamples.bolusRefusedInterval, AapsSamples.bolusRefusedQueue)) {
            val reply = AapsReplyParser.parse(body, ExpectedCommand.Bolus(10))
            assertTrue(reply is AapsReply.BolusRefused)
            assertRedacted(reply.toString(), body)
        }
    }

    @Test
    fun `BOL-21 toString bledu bolusa nie zawiera tresci`() {
        val body = AapsSamples.bolusFailed
        val reply = AapsReplyParser.parse(body, ExpectedCommand.Bolus(10))
        assertTrue(reply is AapsReply.BolusFailed)
        assertFalse(reply.toString().contains("94%"))
        assertRedacted(reply.toString(), body)
    }

    @Test
    fun `PROF-19 PROF-20 toString potwierdzenia i powiadomienia profilu nie zawiera tresci`() {
        val done = AapsReplyParser.parse(AapsSamples.profileSwitchDone, ExpectedCommand.ProfileSwitch(100))
        assertTrue(done is AapsReply.ProfileSwitchDone)
        assertFalse(done.toString().contains("Zmiana profilu wykonana"))
        val base = AapsReplyParser.parse(AapsSamples.profileBaseUpdated, ExpectedCommand.ProfileSwitch(100))
        assertTrue(base is AapsReply.ProfileBaseUpdated)
        assertRedacted(base.toString(), AapsSamples.profileBaseUpdated)
    }
}
