package pl.twojanazwa.aapsremote.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarbsDoneTest {

    private fun parse(body: String, grams: Int) = AapsReplyParser.parse(body, ExpectedCommand.Carbs(grams))

    private fun unrecognized(body: String) = AapsReply.Unrecognized(body)

    private fun withFirstLine(first: String) =
        AapsSamples.carbsDone.replaceFirst("Pomyslnie wprowadzono 1 g weglowodanow", first)

    @Test
    fun `PARSER-07 carbs-sukces w kontekscie CARBS 1 to wykonano CARBS z trescia wejscia`() {
        val body = AapsSamples.carbsDone
        assertEquals(AapsReply.CarbsDone(1, body), parse(body, 1))
    }

    @Test
    fun `PARSER-07 sama pierwsza linia przy CARBS 25 to wykonano CARBS 25`() {
        val body = "Pomyslnie wprowadzono 25 g weglowodanow"
        assertEquals(AapsReply.CarbsDone(25, body), parse(body, 25))
    }

    @Test
    fun `PARSER-07 pierwsza linia bez spacji lub z polskimi znakami jest nierozpoznana`() {
        for (first in listOf("Pomyslnie wprowadzono 1g weglowodanow", "Pomyślnie wprowadzono 1 g węglowodanów")) {
            val body = withFirstLine(first)
            assertEquals(unrecognized(body), parse(body, 1))
        }
    }

    @Test
    fun `PARSER-07 linia Powiadomienie przed potwierdzeniem daje nierozpoznana`() {
        val body = "Powiadomienie\n" + AapsSamples.carbsDone
        assertEquals(unrecognized(body), parse(body, 1))
    }

    @Test
    fun `PARSER-07 ilosc z zerem wiodacym lub piecioma cyframi jest nierozpoznana`() {
        for (g in listOf("01", "00", "12345")) {
            val body = withFirstLine("Pomyslnie wprowadzono $g g weglowodanow")
            assertEquals(unrecognized(body), parse(body, 1))
        }
    }

    @Test
    fun `PARSER-07 CarbsDone powstaje wylacznie przy rownej ilosci`() {
        for (expected in 1..150) {
            for (received in listOf(0, 1, 2, 12, 100, 150, 9999)) {
                val body = "Pomyslnie wprowadzono $received g weglowodanow"
                assertEquals(expected == received, parse(body, expected) is AapsReply.CarbsDone)
            }
        }
    }

    @Test
    fun `PARSER-02 carbs-sukces z koncami linii CR w kontekscie CARBS 1 to wykonano CARBS`() {
        for (eol in listOf("\r", "\r\n")) {
            val body = AapsSamples.carbsDone.replace("\n", eol)
            assertEquals(AapsReply.CarbsDone(1, body), parse(body, 1))
        }
    }
}
