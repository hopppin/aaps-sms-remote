package pl.twojanazwa.aapsremote.sms

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.session.OutgoingSms
import pl.twojanazwa.aapsremote.session.SendResult

@OptIn(ExperimentalCoroutinesApi::class)
class AapsSmsSenderResultTest {
    private fun TestScope.start(
        parts: Int,
        block: (FakeSmsSystemApi, Deferred<SendResult>) -> Unit,
    ) {
        val system = FakeSmsSystemApi { text -> List(parts) { "$text$it" } }
        val sender = AapsSmsSender({ "+447700900123" }, FakeSmsPermissions(), system)
        val result = async(UnconfinedTestDispatcher(testScheduler), CoroutineStart.UNDISPATCHED) {
            sender.send(OutgoingSms.CommandText("BG"))
        }
        block(system, result)
        result.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Deferred<SendResult>.completedOrNull(): SendResult? = if (isCompleted) getCompleted() else null

    @Test
    fun `SMS-10 jedna czesc RESULT_OK daje Sent`() = runTest {
        start(1) { system, result ->
            system.reportPart(0, true)
            assertEquals(SendResult.Sent, result.completedOrNull())
            assertEquals(1, system.unregisterCount)
        }
    }

    @Test
    fun `SMS-10 trzy czesci - Sent dopiero po ok wszystkich`() = runTest {
        start(3) { system, result ->
            system.reportPart(0, true)
            system.reportPart(2, true)
            assertNull(result.completedOrNull())
            system.reportPart(1, true)
            assertEquals(SendResult.Sent, result.completedOrNull())
        }
    }

    @Test
    fun `SMS-10 blad czesci 1 daje Failed bez czekania na pozostale`() = runTest {
        start(3) { system, result ->
            system.reportPart(1, false)
            assertEquals(SendResult.Failed, result.completedOrNull())
            assertEquals(1, system.unregisterCount)
        }
    }

    @Test
    fun `SMS-10 dwa razy ok dla czesci 0 nadal czeka`() = runTest {
        start(2) { system, result ->
            system.reportPart(0, true)
            system.reportPart(0, true)
            assertNull(result.completedOrNull())
        }
    }

    @Test
    fun `SMS-10 kazdy kod bledu daje Failed`() = runTest {
        // Mapowanie kodow (GENERIC_FAILURE, RADIO_OFF, NULL_PDU, 12345) na ok=false jest w platform (zad. 6).
        repeat(4) {
            start(1) { system, result ->
                system.reportPart(0, false)
                assertEquals(SendResult.Failed, result.completedOrNull())
            }
        }
    }

    @Test
    fun `SMS-10 brak wyniku po 600000 ms nadal zawiesza send`() = runTest {
        start(1) { _, result ->
            testScheduler.advanceTimeBy(600_000)
            assertNull(result.completedOrNull())
            assertTrue(result.isActive)
        }
    }
}
