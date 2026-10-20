package pl.twojanazwa.aapsremote.sms

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.session.IncomingSms

@OptIn(ExperimentalCoroutinesApi::class)
class IncomingSmsHubFilterTest {
    private val aaps = HubFixture.AAPS
    private val other = HubFixture.OTHER

    @Test
    fun `SMS-04 dwie czesci od numeru AAPS daja jedna wiadomosc ze sklejona trescia`() = runTest {
        val f = HubFixture()
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        f.registrar.deliver(RawSmsPart("07700900123", "Czesc pierwsza, ", 1L), RawSmsPart("07700900123", "czesc druga", 1L))
        runCurrent()
        assertEquals(listOf("Czesc pierwsza, czesc druga"), received.map { it.body })
    }

    @Test
    fun `SMS-04 wiadomosc od innego numeru nie trafia do kolektora`() = runTest {
        val f = HubFixture()
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        f.registrar.deliver(RawSmsPart(other, AapsSamples.bg, 1L))
        runCurrent()
        assertTrue(received.isEmpty())
    }

    @Test
    fun `SMS-04 z dwoch nadawcow w jednym broadcascie dostarczona jest tylko wiadomosc AAPS`() = runTest {
        val f = HubFixture()
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        f.registrar.deliver(RawSmsPart(other, "X", 1L), RawSmsPart(aaps, AapsSamples.bg, 1L))
        runCurrent()
        assertEquals(listOf(AapsSamples.bg), received.map { it.body })
    }

    @Test
    fun `SMS-04 numer z ustawien null lub zbyt krotki oznacza brak dostarczenia`() = runTest {
        val f = HubFixture()
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        f.numberProvider.number = null
        f.registrar.deliver(RawSmsPart("70090012", AapsSamples.bg, 1L))
        f.numberProvider.number = "70090012"
        f.registrar.deliver(RawSmsPart("70090012", AapsSamples.bg, 2L))
        runCurrent()
        assertTrue(received.isEmpty())
    }

    @Test
    fun `SMS-04 po nieudanej rejestracji osierocony kanal nie blokuje wyrejestrowania`() = runTest {
        val f = HubFixture()
        f.registrar.registerException = SecurityException("odmowa")
        val failedReceived = mutableListOf<IncomingSms>()
        val failed = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching { f.hub.messages().collect { failedReceived.add(it) } }
        }
        runCurrent()
        assertTrue(failed.isActive) // SMS-08: wyjątek rejestracji jest połykany, kolektor czeka

        f.registrar.registerException = null
        val received = mutableListOf<IncomingSms>()
        val second = f.collect(backgroundScope, received)
        f.registrar.deliver(RawSmsPart(aaps, AapsSamples.bg, 1L))
        runCurrent()
        assertEquals(listOf(AapsSamples.bg), received.map { it.body })
        assertEquals(listOf(AapsSamples.bg), failedReceived.map { it.body })

        second.cancel()
        failed.cancel()
        runCurrent()
        assertEquals(1, f.registrar.unregisterCount)
    }

    @Test
    fun `SMS-04 numer odczytywany raz na broadcast zmiana ustawien dziala od nastepnego`() = runTest {
        val f = HubFixture(numberProvider = MutableNumberProvider("+44 7700 900123"))
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        f.registrar.deliver(RawSmsPart(aaps, "pierwsza", 1L))
        f.numberProvider.number = "+44 7700 900999"
        f.registrar.deliver(RawSmsPart(aaps, "druga", 2L))
        runCurrent()
        assertEquals(listOf("pierwsza"), received.map { it.body })
    }
}
