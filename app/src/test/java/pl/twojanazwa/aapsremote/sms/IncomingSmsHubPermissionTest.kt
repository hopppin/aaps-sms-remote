package pl.twojanazwa.aapsremote.sms

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
class IncomingSmsHubPermissionTest {
    private val aaps = HubFixture.AAPS

    @Test
    fun `SMS-08 brak RECEIVE_SMS nie rejestruje receivera a strumien trwa przez 600000 ms`() = runTest {
        val f = HubFixture(permissions = FakeSmsPermissions(receive = false))
        val received = mutableListOf<IncomingSms>()
        val job = f.collect(backgroundScope, received)
        advanceTimeBy(600_000L)
        runCurrent()
        assertEquals(0, f.registrar.registerCount)
        assertTrue(received.isEmpty())
        assertTrue(job.isActive)
        assertFalse(job.isCancelled)
    }

    @Test
    fun `SMS-08 wyjatek z canReceive jest traktowany jak brak uprawnienia`() = runTest {
        val permissions = object : SmsPermissions {
            override fun canSend(): Boolean = true
            override fun canReceive(): Boolean = throw SecurityException("brak")
        }
        val registrar = FakeSmsBroadcastRegistrar()
        val hub = IncomingSmsHub(
            registrar, permissions, MutableNumberProvider(aaps), QueueClock(1_000L), DeliveredSmsRegistry(),
        )
        val received = mutableListOf<IncomingSms>()
        val job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            hub.messages().collect { received.add(it) }
        }
        runCurrent()
        assertEquals(0, registrar.registerCount)
        assertTrue(received.isEmpty())
        assertTrue(job.isActive)
    }

    @Test
    fun `SMS-08 wyjatek SecurityException przy rejestracji nie dociera do kolektora`() = runTest {
        val f = HubFixture()
        f.registrar.registerException = SecurityException("brak")
        val received = mutableListOf<IncomingSms>()
        val job = f.collect(backgroundScope, received)
        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(received.isEmpty())
        assertTrue(job.isActive)
        assertFalse(job.isCancelled)
    }

    @Test
    fun `SMS-08 uprawnienie przyznane po pierwszym kolektorze pozwala zarejestrowac receiver nowemu kolektorowi`() = runTest {
        val f = HubFixture(permissions = FakeSmsPermissions(receive = false))
        val first = f.collect(backgroundScope, mutableListOf())
        assertEquals(0, f.registrar.registerCount)
        first.cancelAndJoin()
        assertEquals(0, f.registrar.unregisterCount)
        f.permissions.receive = true
        val received = mutableListOf<IncomingSms>()
        f.collect(backgroundScope, received)
        assertEquals(1, f.registrar.registerCount)
        f.registrar.deliver(RawSmsPart(aaps, AapsSamples.bg, 1L))
        runCurrent()
        assertEquals(listOf(AapsSamples.bg), received.map { it.body })
    }

    @Test
    fun `SMS-08 wyjatek przy wyrejestrowaniu konczy sie zwyklym anulowaniem`() = runTest {
        val f = HubFixture()
        f.registrar.unregisterException = IllegalArgumentException("nie zarejestrowany")
        val job = f.collect(backgroundScope, mutableListOf())
        job.cancelAndJoin()
        assertEquals(1, f.registrar.unregisterCount)
        assertTrue(job.isCancelled)
    }
}
