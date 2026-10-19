package pl.twojanazwa.aapsremote.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CarbsCodeRequestTest {

    private fun request(grams: String = "1", time: String = "14:50", name: String = "Jan") =
        "Aby wprowadzic ${grams}g o $time odeslij w SMS kod z aplikacji Authenticator dla: $name, z doklejonym PIN-em"

    private fun parse(body: String, grams: Int = 1) = AapsReplyParser.parse(body, ExpectedCommand.Carbs(grams))

    private fun assertUnrecognized(body: String, grams: Int = 1) =
        assertEquals(AapsReply.Unrecognized(body), parse(body, grams))

    @Test
    fun `PARSER-05 plik prosby o kod daje CodeRequest z trescia wejsciowa`() {
        val body = AapsSamples.carbsCodeRequest
        assertEquals(AapsReply.CodeRequest(1, body), parse(body))
    }

    @ParameterizedTest
    @ValueSource(strings = ["Anna Nowak", "X"])
    fun `PARSER-05 inna nazwa uzytkownika jest akceptowana`(name: String) {
        val body = request(name = name)
        assertEquals(AapsReply.CodeRequest(1, body), parse(body))
    }

    @Test
    fun `PARSER-05 pusta nazwa jest nierozpoznana`() {
        assertUnrecognized(request(name = ""))
    }

    @ParameterizedTest
    @ValueSource(strings = ["9:05", "09:05", "0:00", "23:59"])
    fun `PARSER-05 godzina 24h jest akceptowana`(time: String) {
        val body = request(grams = "20", time = time)
        assertEquals(AapsReply.CodeRequest(20, body), parse(body, 20))
    }

    @ParameterizedTest
    @ValueSource(strings = ["24:00", "14:5", "14:60", "2:50 PM"])
    fun `PARSER-05 niepoprawna godzina jest nierozpoznana`(time: String) {
        assertUnrecognized(request(time = time))
    }

    @ParameterizedTest
    @ValueSource(strings = ["01", "1 ", "1,5", "10000"])
    fun `PARSER-05 niepoprawna ilosc jest nierozpoznana`(grams: String) {
        assertUnrecognized(request(grams = grams))
    }

    @ParameterizedTest
    @ValueSource(strings = [" Dziekujemy", "\nDruga linia"])
    fun `PARSER-05 dopisek na koncu jest nierozpoznany`(suffix: String) {
        assertUnrecognized(request() + suffix)
    }

    @Test
    fun `PARSER-05 Aby wprowadzic ze znakiem c z kreska jest nierozpoznane`() {
        assertUnrecognized(request().replace("wprowadzic", "wprowadzić"))
    }

    @Test
    fun `PARSER-02 podwojna spacja w tresci prosby jest nierozpoznana`() {
        assertUnrecognized(AapsSamples.carbsCodeRequest.replace("odeslij w", "odeslij  w"))
    }

    @Test
    fun `PARSER-09 wzorzec osadzony w innym tekscie jest nierozpoznany`() {
        assertUnrecognized("Uzytkownik wykonal polecenie. " + request())
    }

    @Test
    fun `PARSER-09 dodatkowa linia przed prosba jest nierozpoznana`() {
        assertUnrecognized("Bolus 2,00U podany\n" + AapsSamples.carbsCodeRequest)
    }

    @Test
    fun `PARSER-09 SMS od czlowieka i sam kod sa nierozpoznane`() {
        assertEquals(AapsReply.Unrecognized("Czesc, kupisz chleb?"), parse("Czesc, kupisz chleb?"))
        assertUnrecognized("123456")
    }
}
