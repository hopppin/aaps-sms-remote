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
class IncomingSmsHubTimestampTest {
    private val aaps = HubFixture.AAPS
    private val t0 = 1_700_000_000_000L

    @Test
    fun `SMS-05 chwila odbioru pochodzi z zegara a nie ze znacznika centrum SMS`() = runTest {
        val f = HubFixture(clock = QueueClock(t0))
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        f.registrar.deliver(RawSmsPart(aaps, AapsSamples.bg, 1111111000000L))
        runCurrent()
        assertEquals(listOf(t0), received.map { it.receivedAtEpochMillis })
    }

    @Test
    fun `SMS-05 dwa broadcasty maja chwile z dwoch odczytow zegara`() = runTest {
        val f = HubFixture(clock = QueueClock(t0, t0 + 5000))
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        f.registrar.deliver(RawSmsPart(aaps, "pierwsza", 1L))
        f.registrar.deliver(RawSmsPart(aaps, "druga", 2L))
        runCurrent()
        assertEquals(listOf(t0, t0 + 5000), received.map { it.receivedAtEpochMillis })
        assertEquals(2, f.clock.reads)
    }

    @Test
    fun `SMS-05 dwie wiadomosci z jednego broadcastu maja ta sama chwile i jeden odczyt zegara`() = runTest {
        val f = HubFixture(clock = QueueClock(t0, t0 + 5000))
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        f.registrar.deliver(RawSmsPart("+447700900123", "pierwsza", 1L), RawSmsPart("07700900123", "druga", 1L))
        runCurrent()
        assertEquals(2, received.size)
        assertEquals(listOf(t0, t0), received.map { it.receivedAtEpochMillis })
        assertEquals(1, f.clock.reads)
    }

    @Test
    fun `SMS-05 wyjatek zegara pomija broadcast bez propagacji a nastepny jest dostarczony`() = runTest {
        val f = HubFixture(clock = QueueClock(t0))
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        f.clock.failNext = true
        f.registrar.deliver(RawSmsPart(aaps, AapsSamples.bg, 1L))
        runCurrent()
        assertTrue(received.isEmpty())
        f.clock.failNext = false
        f.registrar.deliver(RawSmsPart(aaps, AapsSamples.bg, 2L))
        runCurrent()
        assertEquals(listOf(t0), received.map { it.receivedAtEpochMillis })
    }
}
