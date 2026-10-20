package pl.twojanazwa.aapsremote.sms

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.session.IncomingSms

@OptIn(ExperimentalCoroutinesApi::class)
class IncomingSmsHubSubscriptionTest {
    private val aaps = HubFixture.AAPS

    @Test
    fun `SMS-07 wiadomosc obsluzona przed rozpoczeciem zbierania nie trafia do nowego kolektora`() = runTest {
        val f = HubFixture()
        val received = mutableListOf<IncomingSms>()
        f.registrar.deliver(RawSmsPart(aaps, AapsSamples.bg, 1L))
        f.collect(backgroundScope, received)
        runCurrent()
        assertTrue(received.isEmpty())
    }

    @Test
    fun `SMS-07 po powrocie z launch UNDISPATCHED receiver jest zarejestrowany raz i dostarcza kolejny broadcast`() = runTest {
        val f = HubFixture()
        val received = mutableListOf<IncomingSms>()
        val job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            f.hub.messages().collect { received.add(it) }
        }
        assertTrue(job.isActive)
        assertEquals(1, f.registrar.registerCount)
        f.registrar.deliver(RawSmsPart(aaps, AapsSamples.bg, 1L))
        runCurrent()
        assertEquals(listOf(AapsSamples.bg), received.map { it.body })
    }

    @Test
    fun `SMS-07 dwaj kolektorzy dostaja te same wiadomosci w tej samej kolejnosci przy jednej rejestracji`() = runTest {
        val f = HubFixture()
        val first = mutableListOf<IncomingSms>()
        val second = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, first)
        f.collect(backgroundScope, second)
        f.registrar.deliver(RawSmsPart(aaps, AapsSamples.bg, 1L))
        f.registrar.deliver(RawSmsPart(aaps, AapsSamples.pump, 2L))
        runCurrent()
        val expected = listOf(AapsSamples.bg, AapsSamples.pump)
        assertEquals(expected, first.map { it.body })
        assertEquals(expected, second.map { it.body })
        assertEquals(1, f.registrar.registerCount)
    }

    @Test
    fun `SMS-07 receiver zostaje zarejestrowany do anulowania ostatniego kolektora`() = runTest {
        val f = HubFixture()
        val first = f.collect(backgroundScope, mutableListOf())
        val second = f.collect(backgroundScope, mutableListOf())
        first.cancelAndJoin()
        assertTrue(f.registrar.isRegistered)
        assertEquals(0, f.registrar.unregisterCount)
        second.cancelAndJoin()
        assertEquals(1, f.registrar.unregisterCount)
        assertFalse(f.registrar.isRegistered)
    }

    @Test
    fun `SMS-07 nowy kolektor po wyrejestrowaniu rejestruje receiver ponownie`() = runTest {
        val f = HubFixture()
        f.collect(backgroundScope, mutableListOf()).cancelAndJoin()
        f.collect(backgroundScope, mutableListOf())
        assertEquals(2, f.registrar.registerCount)
        assertTrue(f.registrar.isRegistered)
    }

    @Test
    fun `SMS-07 wolny kolektor dostaje 200 wiadomosci w kolejnosci a obsluga broadcastu nie blokuje`() = runTest {
        val f = HubFixture()
        val received = mutableListOf<IncomingSms>()
        val gate = CompletableDeferred<Unit>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            f.hub.messages().collect {
                gate.await()
                received.add(it)
            }
        }
        val bodies = (1..200).map { "Wiadomosc numer $it" }
        bodies.forEachIndexed { i, body -> f.registrar.deliver(RawSmsPart(aaps, body, i.toLong())) }
        runCurrent()
        assertTrue(received.isEmpty())
        gate.complete(Unit)
        runCurrent()
        assertEquals(bodies, received.map { it.body })
    }

    @Test
    fun `SMS-07 strumien bez wiadomosci nie konczy sie i nie rzuca przez 600000 ms`() = runTest {
        val f = HubFixture()
        val job = f.collect(backgroundScope, mutableListOf())
        advanceTimeBy(600_000L)
        runCurrent()
        assertTrue(job.isActive)
        assertFalse(job.isCancelled)
    }
}
