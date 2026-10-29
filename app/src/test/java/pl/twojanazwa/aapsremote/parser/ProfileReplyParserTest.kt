package pl.twojanazwa.aapsremote.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileReplyParserTest {

    private fun parse(body: String, expected: ExpectedCommand) = AapsReplyParser.parse(body, expected)

    private fun unrecognized(body: String) = AapsReply.Unrecognized(body)

    private fun request(name: String, percent: Int) =
        "Aby przelaczyc profil na $name $percent% odpowiedz kodem z aplikacji Authenticator dla: Jan, z doklejonym PIN-em"

    private fun switch(name: String, percent: Int) = ExpectedCommand.ProfileSwitch(name, percent)

    private val allContexts = listOf(
        ExpectedCommand.Bg, ExpectedCommand.Pump, ExpectedCommand.Carbs(1), ExpectedCommand.Bolus(10),
        ExpectedCommand.ProfileStatus, ExpectedCommand.ProfileList, switch("LocalProfile1", 100),
    )

    // PROF-06

    @Test
    fun `PROF-06 plik stanu w kontekscie stan profilu to stan profilu`() {
        val body = AapsSamples.profileStatus
        assertEquals(AapsReply.ProfileStatusReport(body), parse(body, ExpectedCommand.ProfileStatus))
    }

    @Test
    fun `PROF-06 syntetyczny stan Moj profil 90 to stan profilu`() {
        val body = "Moj profil (90%)"
        assertEquals(AapsReply.ProfileStatusReport(body), parse(body, ExpectedCommand.ProfileStatus))
    }

    @Test
    fun `PROF-06 niepoprawne stany to nierozpoznana`() {
        for (body in listOf("LocalProfile1", "LocalProfile1 (115 %)", "LocalProfile1 (115%)\nDruga", " (115%)")) {
            assertEquals(unrecognized(body), parse(body, ExpectedCommand.ProfileStatus))
        }
    }

    @Test
    fun `PROF-06 plik stanu w innych kontekstach to nierozpoznana`() {
        val body = AapsSamples.profileStatus
        for (ctx in listOf(ExpectedCommand.Bg, ExpectedCommand.Pump, ExpectedCommand.ProfileList, ExpectedCommand.Carbs(1))) {
            assertEquals(unrecognized(body), parse(body, ctx))
        }
    }

    // PROF-07

    @Test
    fun `PROF-07 plik listy to lista dwoch profili`() {
        val body = AapsSamples.profileList
        val items = listOf(AapsReply.ProfileListItem(1, "LocalProfile1"), AapsReply.ProfileListItem(2, "LocalProfile2"))
        assertEquals(AapsReply.ProfileListReport(items, body), parse(body, ExpectedCommand.ProfileList))
    }

    @Test
    fun `PROF-07 lista z końcami linii CRLF`() {
        val body = AapsSamples.profileList.replace("\n", "\r\n")
        val reply = parse(body, ExpectedCommand.ProfileList) as AapsReply.ProfileListReport
        assertEquals(2, reply.items.size)
        assertEquals(body, reply.text)
    }

    @Test
    fun `PROF-07 syntetyczna lista z jednym profilem`() {
        val body = "1. Dzien roboczy"
        assertEquals(
            AapsReply.ProfileListReport(listOf(AapsReply.ProfileListItem(1, "Dzien roboczy")), body),
            parse(body, ExpectedCommand.ProfileList),
        )
    }

    @Test
    fun `PROF-07 niepoprawne listy to nierozpoznana`() {
        val tooLong = "1. " + "A".repeat(1998)
        assertEquals(2001, tooLong.length)
        for (body in listOf("2. A", "1. A\n3. B", "1. A\n1. B", "1.A", "1. A\n2. A", "Brak profili", tooLong)) {
            assertEquals(unrecognized(body), parse(body, ExpectedCommand.ProfileList))
        }
    }

    @Test
    fun `PROF-07 lista w jednej linii to jeden profil z calym tekstem w nazwie`() {
        val body = "1. A 2. B"
        val reply = parse(body, ExpectedCommand.ProfileList) as AapsReply.ProfileListReport
        assertEquals(listOf(AapsReply.ProfileListItem(1, "A 2. B")), reply.items)
    }

    @Test
    fun `PROF-07 prosba dla nazwy A 2 B przy innej nazwie to niezgodny profil`() {
        val body = request("A 2. B", 100)
        // Greedy nazwa: "A 2. B" == oczekiwana -> kod; inny podzial -> stop.
        assertEquals(AapsReply.ProfileCodeRequest(body), parse(body, switch("A 2. B", 100)))
        assertEquals(AapsReply.ProfileSwitchMismatch(body), parse(body, switch("A", 100)))
    }

    // PROF-08

    @Test
    fun `PROF-08 plik prosby w zmianie LocalProfile1 100 to prosba o kod`() {
        val body = AapsSamples.profileCodeRequest
        assertEquals(AapsReply.ProfileCodeRequest(body), parse(body, switch("LocalProfile1", 100)))
    }

    @Test
    fun `PROF-08 prosba dla Dzien roboczy 115 to prosba o kod`() {
        val body = request("Dzien roboczy", 115)
        assertEquals(AapsReply.ProfileCodeRequest(body), parse(body, switch("Dzien roboczy", 115)))
    }

    @Test
    fun `PROF-08 prosba z druga linia albo z przelaczyc to nierozpoznana`() {
        val base = AapsSamples.profileCodeRequest
        for (body in listOf(base + "\nDruga", base.replace("przelaczyc", "przełączyć"))) {
            assertEquals(unrecognized(body), parse(body, switch("LocalProfile1", 100)))
        }
    }

    @Test
    fun `PROF-08 plik prosby w innych kontekstach to nierozpoznana`() {
        val body = AapsSamples.profileCodeRequest
        val ctx = listOf(
            ExpectedCommand.Carbs(1), ExpectedCommand.Bolus(10), ExpectedCommand.Bg, ExpectedCommand.ProfileList,
        )
        for (c in ctx) assertEquals(unrecognized(body), parse(body, c))
    }

    // PROF-09

    @Test
    fun `PROF-09 niezgodna nazwa lub procent to niezgodny profil nigdy prosba o kod`() {
        val body = AapsSamples.profileCodeRequest
        for (c in listOf(
            switch("LocalProfile2", 100), switch("LocalProfile1", 115),
            switch("LocalProfile", 100), switch("localprofile1", 100),
        )) {
            assertEquals(AapsReply.ProfileSwitchMismatch(body), parse(body, c))
        }
    }

    // PROF-10

    @Test
    fun `PROF-10 zly kod w zmianie profilu to zly kod`() {
        assertEquals(AapsReply.WrongCode, parse(AapsSamples.wrongCode, switch("LocalProfile1", 100)))
    }

    @Test
    fun `PROF-10 bledny format w kontekstach profilu to nierozpoznana`() {
        val body = AapsSamples.profileBadFormat
        for (c in listOf(switch("LocalProfile1", 100), ExpectedCommand.ProfileStatus, ExpectedCommand.ProfileList)) {
            assertEquals(unrecognized(body), parse(body, c))
        }
    }

    // PROF-05 (cz.)

    @Test
    fun `PROF-05 toString wynikow parsera profilu nie zawiera tresci`() {
        val cases = listOf(
            AapsSamples.profileStatus to ExpectedCommand.ProfileStatus,
            AapsSamples.profileList to ExpectedCommand.ProfileList,
            AapsSamples.profileCodeRequest to switch("LocalProfile1", 100),
            AapsSamples.profileCodeRequest to switch("LocalProfile2", 100),
            AapsSamples.profileBadFormat to switch("LocalProfile1", 100),
        )
        for ((body, ctx) in cases) {
            val s = parse(body, ctx).toString()
            assertTrue(s.startsWith("AapsReply."))
            for (w in listOf("LocalProfile", "115%", "Jan")) assertFalse(s.contains(w))
            for (line in body.lines()) assertFalse(s.contains(line))
        }
        val list = parse(AapsSamples.profileList, ExpectedCommand.ProfileList) as AapsReply.ProfileListReport
        assertFalse(list.items.toString().contains("LocalProfile"))
    }

    @Test
    fun `PROF-06 macierz plik x kontekst rozpoznaje tylko wlasciwy kontekst`() {
        val files = mapOf(
            AapsSamples.profileStatus to ExpectedCommand.ProfileStatus,
            AapsSamples.profileList to ExpectedCommand.ProfileList,
            AapsSamples.profileCodeRequest to switch("LocalProfile1", 100),
        )
        for ((body, owner) in files) {
            for (ctx in allContexts) {
                val r = parse(body, ctx)
                if (ctx == owner) assertFalse(r is AapsReply.Unrecognized) else assertTrue(r is AapsReply.Unrecognized)
            }
        }
    }
}
