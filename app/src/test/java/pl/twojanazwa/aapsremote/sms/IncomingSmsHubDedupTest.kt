package pl.twojanazwa.aapsremote.sms

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.session.IncomingSms

@OptIn(ExperimentalCoroutinesApi::class)
class IncomingSmsHubDedupTest {
    private val aaps = HubFixture.AAPS
    private val other = HubFixture.OTHER
    private val t0 = 1_700_000_000_000L

    @Test
    fun `SMS-06 ten sam broadcast dwa razy daje jedna wiadomosc z pierwsza chwila odbioru`() = runTest {
        val f = HubFixture(clock = QueueClock(t0, t0 + 1000))
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        val part = RawSmsPart(aaps, AapsSamples.carbsCodeRequest, 1111111000000L)
        f.registrar.deliver(part)
        f.registrar.deliver(part)
        runCurrent()
        assertEquals(1, received.size)
        assertEquals(t0, received[0].receivedAtEpochMillis)
    }

    @Test
    fun `SMS-06 ta sama tresc ze znacznikami centrum SMS roznych o minute jest dostarczona dwa razy`() = runTest {
        val f = HubFixture()
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        f.registrar.deliver(RawSmsPart(aaps, AapsSamples.bg, 1111111000000L))
        f.registrar.deliver(RawSmsPart(aaps, AapsSamples.bg, 1111111060000L))
        runCurrent()
        assertEquals(2, received.size)
    }

    @Test
    fun `SMS-06 wiadomosc z sesji 1 nie jest dostarczona kolektorowi sesji 2`() = runTest {
        val f = HubFixture()
        val session1 = mutableListOf<IncomingSms>()
        val job = f.collect(backgroundScope, session1)
        val part = RawSmsPart(aaps, AapsSamples.bg, 1L)
        f.registrar.deliver(part)
        runCurrent()
        assertEquals(1, session1.size)
        job.cancel()
        runCurrent()

        val session2 = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, session2)
        f.registrar.deliver(part)
        runCurrent()
        assertTrue(session2.isEmpty())
    }

    @Test
    fun `SMS-06 wiadomosci spoza numeru AAPS nie zajmuja pamieci rejestru`() = runTest {
        val f = HubFixture()
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        val w = RawSmsPart(aaps, "W", 1L)
        f.registrar.deliver(w)
        repeat(150) { f.registrar.deliver(RawSmsPart(other, "obca $it", 1L)) }
        f.registrar.deliver(w)
        runCurrent()
        assertEquals(listOf("W"), received.map { it.body })
    }

    @Test
    fun `SMS-06 wiadomosc obsluzona bez kolektora jest zapamietana`() = runTest {
        val f = HubFixture()
        f.hub.onBroadcast(listOf(RawSmsPart(aaps, "bez kolektora", 5L)))

        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        f.registrar.deliver(RawSmsPart(aaps, "bez kolektora", 5L))
        runCurrent()
        assertTrue(received.isEmpty())
    }
}
