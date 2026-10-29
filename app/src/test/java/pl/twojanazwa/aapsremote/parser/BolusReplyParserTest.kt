package pl.twojanazwa.aapsremote.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BolusReplyParserTest {

    private fun parse(body: String, expected: ExpectedCommand) = AapsReplyParser.parse(body, expected)

    private fun request(amount: String) =
        "Aby dostarczyc bolus ${amount}U wprowadz kod z aplikacji Authenticator dla: Jan, z doklejonym PIN-em"

    private fun unrecognized(body: String) = AapsReply.Unrecognized(body)

    // BOL-07

    @Test
    fun `BOL-07 plik prosby 0,10U w Bolus 10 to prosba o kod`() {
        val body = AapsSamples.bolusCodeRequest
        assertEquals(AapsReply.BolusCodeRequest(10, body), parse(body, ExpectedCommand.Bolus(10)))
    }

    @Test
    fun `BOL-07 prosba z 2,50U w Bolus 250 to prosba o kod`() {
        val body = request("2,50")
        assertEquals(AapsReply.BolusCodeRequest(250, body), parse(body, ExpectedCommand.Bolus(250)))
    }

    @Test
    fun `BOL-07 warianty formatu ilosci i tresci sa nierozpoznane`() {
        val variants = listOf(
            request("0.10"),
            request("0,1"),
            request("0,10 "),
            request("00,10"),
            request("1000,00"),
            request("0,10") + "\nDruga linia",
            request("0,10").replace("dostarczyc", "dostarczyć"),
            request("0,10").replace("Aby dostarczyc", "Aby dostarczyć"),
        )
        for (body in variants) assertEquals(unrecognized(body), parse(body, ExpectedCommand.Bolus(10)))
    }

    @Test
    fun `BOL-07 prosba o kod bolusa w kontekstach BG PUMP i Carbs jest nierozpoznana`() {
        val body = AapsSamples.bolusCodeRequest
        for (c in listOf(ExpectedCommand.Bg, ExpectedCommand.Pump, ExpectedCommand.Carbs(1))) {
            assertEquals(unrecognized(body), parse(body, c))
        }
    }

    @Test
    fun `BOL-07 prosba o kod CARBS w kontekscie Bolus jest nierozpoznana`() {
        val body = AapsSamples.carbsCodeRequest
        assertEquals(unrecognized(body), parse(body, ExpectedCommand.Bolus(10)))
    }

    // BOL-08

    @Test
    fun `BOL-08 obcieta prosba 5,00U w Bolus 600 to niezgodna ilosc`() {
        val body = AapsSamples.bolusCodeRequestTruncated
        assertEquals(
            AapsReply.BolusAmountMismatch(600, 500, CarbsStage.CODE_REQUEST, body),
            parse(body, ExpectedCommand.Bolus(600)),
        )
    }

    @Test
    fun `BOL-08 prosba 0,10U w Bolus 100 to niezgodna ilosc`() {
        val body = AapsSamples.bolusCodeRequest
        assertEquals(
            AapsReply.BolusAmountMismatch(100, 10, CarbsStage.CODE_REQUEST, body),
            parse(body, ExpectedCommand.Bolus(100)),
        )
    }

    @Test
    fun `BOL-08 sukces 1,20 U w Bolus 125 to niezgodna ilosc na etapie potwierdzenia`() {
        val body = AapsSamples.bolusDone
        assertEquals(
            AapsReply.BolusAmountMismatch(125, 120, CarbsStage.CONFIRMATION, body),
            parse(body, ExpectedCommand.Bolus(125)),
        )
    }

    @Test
    fun `BOL-08 BolusCodeRequest powstaje wylacznie przy rownej ilosci`() {
        for (expected in listOf(1, 10, 100, 250, 600)) {
            for (received in listOf("0,01", "0,10", "1,00", "2,50", "5,00", "6,00", "999,99")) {
                val reply = parse(request(received), ExpectedCommand.Bolus(expected))
                val value = received.replace(",", "").toInt()
                assertEquals(expected == value, reply is AapsReply.BolusCodeRequest)
            }
        }
    }

    // BOL-09

    @Test
    fun `BOL-09 plik sukcesu 1,20 U w Bolus 120 to wykonano bolus`() {
        val body = AapsSamples.bolusDone
        assertEquals(AapsReply.BolusDone(120, body), parse(body, ExpectedCommand.Bolus(120)))
    }

    @Test
    fun `BOL-09 sama pierwsza linia 0,10 U w Bolus 10 to wykonano bolus`() {
        val body = "Bolus 0,10 U podany prawidlowo"
        assertEquals(AapsReply.BolusDone(10, body), parse(body, ExpectedCommand.Bolus(10)))
    }

    @Test
    fun `BOL-09 pierwsza linia bez spacji lub poprzedzona inna linia jest nierozpoznana`() {
        for (body in listOf("Bolus 1,20U podany prawidlowo", "Powiadomienie\nBolus 1,20 U podany prawidlowo")) {
            assertEquals(unrecognized(body), parse(body, ExpectedCommand.Bolus(120)))
        }
    }

    @Test
    fun `BOL-09 zly kod w Bolus to WrongCode`() {
        assertEquals(AapsReply.WrongCode, parse(AapsSamples.wrongCode, ExpectedCommand.Bolus(10)))
    }

    @Test
    fun `BOL-09 sukces bolusa w kontekstach Carbs BG i PUMP jest nierozpoznany`() {
        val body = AapsSamples.bolusDone
        for (c in listOf(ExpectedCommand.Carbs(1), ExpectedCommand.Bg, ExpectedCommand.Pump)) {
            assertEquals(unrecognized(body), parse(body, c))
        }
    }

    @Test
    fun `BOL-09 nierozpoznane odmowy AAPS w kontekscie Bolus sa nierozpoznane`() {
        for (body in listOf("Bolus nie zostal podany", "Polecenie zablokowane", "", "Aby dostarczyc bolus")) {
            assertEquals(unrecognized(body), parse(body, ExpectedCommand.Bolus(10)))
        }
    }

    @Test
    fun `BOL-09 macierz kontekstow - plik bolusa daje tylko oczekiwany wynik`() {
        val files = listOf(
            AapsSamples.bolusCodeRequest, AapsSamples.bolusCodeRequestTruncated, AapsSamples.bolusDone,
        )
        val contexts = listOf(
            ExpectedCommand.Bg, ExpectedCommand.Pump, ExpectedCommand.Carbs(1), ExpectedCommand.Bolus(10),
            ExpectedCommand.Bolus(120), ExpectedCommand.Bolus(500),
        )
        val allowed = mapOf<Pair<String, ExpectedCommand>, AapsReply>(
            (AapsSamples.bolusCodeRequest to ExpectedCommand.Bolus(10)) to
                AapsReply.BolusCodeRequest(10, AapsSamples.bolusCodeRequest),
            (AapsSamples.bolusCodeRequest to ExpectedCommand.Bolus(120)) to
                AapsReply.BolusAmountMismatch(120, 10, CarbsStage.CODE_REQUEST, AapsSamples.bolusCodeRequest),
            (AapsSamples.bolusCodeRequest to ExpectedCommand.Bolus(500)) to
                AapsReply.BolusAmountMismatch(500, 10, CarbsStage.CODE_REQUEST, AapsSamples.bolusCodeRequest),
            (AapsSamples.bolusCodeRequestTruncated to ExpectedCommand.Bolus(10)) to
                AapsReply.BolusAmountMismatch(10, 500, CarbsStage.CODE_REQUEST, AapsSamples.bolusCodeRequestTruncated),
            (AapsSamples.bolusCodeRequestTruncated to ExpectedCommand.Bolus(120)) to
                AapsReply.BolusAmountMismatch(120, 500, CarbsStage.CODE_REQUEST, AapsSamples.bolusCodeRequestTruncated),
            (AapsSamples.bolusCodeRequestTruncated to ExpectedCommand.Bolus(500)) to
                AapsReply.BolusCodeRequest(500, AapsSamples.bolusCodeRequestTruncated),
            (AapsSamples.bolusDone to ExpectedCommand.Bolus(10)) to
                AapsReply.BolusAmountMismatch(10, 120, CarbsStage.CONFIRMATION, AapsSamples.bolusDone),
            (AapsSamples.bolusDone to ExpectedCommand.Bolus(120)) to
                AapsReply.BolusDone(120, AapsSamples.bolusDone),
            (AapsSamples.bolusDone to ExpectedCommand.Bolus(500)) to
                AapsReply.BolusAmountMismatch(500, 120, CarbsStage.CONFIRMATION, AapsSamples.bolusDone),
        )
        for (body in files) {
            for (c in contexts) {
                assertEquals(allowed[body to c] ?: unrecognized(body), parse(body, c))
            }
        }
    }

    @Test
    fun `BOL-09 toString wyniku pliku z dane nie zawiera ilosci ani tresci`() {
        val forbidden = listOf("0,10", "5,00", "1,20", "Jan", "94%")
        val cases = listOf(
            AapsSamples.bolusCodeRequest to ExpectedCommand.Bolus(10),
            AapsSamples.bolusCodeRequest to ExpectedCommand.Bolus(100),
            AapsSamples.bolusCodeRequestTruncated to ExpectedCommand.Bolus(600),
            AapsSamples.bolusDone to ExpectedCommand.Bolus(120),
            AapsSamples.bolusDone to ExpectedCommand.Bolus(125),
        )
        for ((body, c) in cases) {
            val reply = parse(body, c)
            assertFalse(reply is AapsReply.Unrecognized)
            val text = reply.toString()
            for (word in forbidden) assertFalse(text.contains(word), "zawiera zabroniony fragment")
            for (line in body.lines().filter { it.isNotBlank() }) assertFalse(text.contains(line))
            assertTrue(text.contains("Bolus"))
        }
    }

    @Test
    fun `BOL-07 BOL-09 macierz kontekst x plik - pozostale pliki w kontekscie Bolus`() {
        val others = listOf(
            AapsSamples.bg, AapsSamples.pump, AapsSamples.carbsCodeRequest, AapsSamples.carbsDone,
            AapsSamples.wrongCode,
        )
        for (body in others) {
            for (amount in listOf(10, 120, 500)) {
                val expectedReply = if (body == AapsSamples.wrongCode) AapsReply.WrongCode else unrecognized(body)
                assertEquals(expectedReply, parse(body, ExpectedCommand.Bolus(amount)))
            }
        }
    }

    @Test
    fun `BOL-09 ataki na potwierdzenie bolusa sa nierozpoznane`() {
        val ok = "Bolus 1,20 U podany prawidlowo"
        val variants = listOf(
            "$ok dopisek",
            "$ok 5",
            "$ok.",
            "Uwaga $ok",
            "5 $ok",
            "Bolus 1,200 U podany prawidlowo",
            "Bolus 01,20 U podany prawidlowo",
            "Bolus 1.20 U podany prawidlowo",
            "Bolus 1,2 U podany prawidlowo",
        )
        for (body in variants) assertEquals(unrecognized(body), parse(body, ExpectedCommand.Bolus(120)))
    }
}
