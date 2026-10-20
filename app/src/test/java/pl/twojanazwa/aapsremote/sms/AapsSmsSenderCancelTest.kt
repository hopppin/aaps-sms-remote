package pl.twojanazwa.aapsremote.sms

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.session.OutgoingSms

@OptIn(ExperimentalCoroutinesApi::class)
class AapsSmsSenderCancelTest {
    @Test
    fun `SMS-11 anulowanie send daje CancellationException i wyrejestrowanie`() = runTest {
        val system = FakeSmsSystemApi()
        val sender = AapsSmsSender({ "+447700900123" }, FakeSmsPermissions(), system)
        val result = async(UnconfinedTestDispatcher(testScheduler), CoroutineStart.UNDISPATCHED) {
            sender.send(OutgoingSms.CommandText("BG"))
        }
        assertEquals(0, system.unregisterCount)
        result.cancel()
        assertThrows(CancellationException::class.java) { result.getCompleted() }
        assertTrue(result.isCancelled)
        assertEquals(1, system.unregisterCount)
    }

    @Test
    fun `SMS-11 wynik OK po anulowaniu jest ignorowany`() = runTest {
        val system = FakeSmsSystemApi()
        val sender = AapsSmsSender({ "+447700900123" }, FakeSmsPermissions(), system)
        val result = async(UnconfinedTestDispatcher(testScheduler), CoroutineStart.UNDISPATCHED) {
            sender.send(OutgoingSms.CommandText("BG"))
        }
        result.cancel()
        system.reportPart(0, true)
        assertTrue(result.isCancelled)
        assertEquals(1, system.unregisterCount)
    }
}
