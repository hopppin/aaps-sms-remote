package pl.twojanazwa.aapsremote.sms

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.session.OutgoingSms

@OptIn(ExperimentalCoroutinesApi::class)
class AapsSmsSenderTest {
    private var number: String? = "+44 7700 900123"
    private val system = FakeSmsSystemApi()
    private val sender = AapsSmsSender({ number }, FakeSmsPermissions(), system)

    @Test
    fun `SMS-09 CommandText trafia do warstwy systemowej bez zmian na adres AAPS`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler), CoroutineStart.UNDISPATCHED) {
            sender.send(OutgoingSms.CommandText("CARBS 20"))
        }
        assertEquals(1, system.requests.size)
        assertEquals("+447700900123", system.requests[0].destination)
        assertEquals(listOf("CARBS 20"), system.requests[0].parts)
        job.cancel()
    }

    @Test
    fun `SMS-09 CodeReply wysyla tekst ze znakow i nie zmienia tablicy`() = runTest {
        val chars = "0504714321".toCharArray()
        val job = launch(UnconfinedTestDispatcher(testScheduler), CoroutineStart.UNDISPATCHED) {
            sender.send(OutgoingSms.CodeReply(chars))
        }
        assertEquals(listOf("0504714321"), system.requests.single().parts)
        assertEquals("0504714321", String(chars))
        job.cancel()
    }

    @Test
    fun `SMS-09 numer jest odczytywany przy kazdym wywolaniu send`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val first = launch(dispatcher, CoroutineStart.UNDISPATCHED) { sender.send(OutgoingSms.CommandText("BG")) }
        number = "07700 900999"
        val second = launch(dispatcher, CoroutineStart.UNDISPATCHED) { sender.send(OutgoingSms.CommandText("BG")) }
        assertEquals(listOf("+447700900123", "07700900999"), system.requests.map { it.destination })
        first.cancel()
        second.cancel()
    }

    @Test
    fun `SMS-09 wszystkie czesci z divideMessage trafiaja do sendParts`() = runTest {
        val multi = FakeSmsSystemApi { listOf("a", "b", "c") }
        val s = AapsSmsSender({ number }, FakeSmsPermissions(), multi)
        val job = launch(UnconfinedTestDispatcher(testScheduler), CoroutineStart.UNDISPATCHED) {
            s.send(OutgoingSms.CommandText("BG"))
        }
        assertEquals(listOf("a", "b", "c"), multi.requests.single().parts)
        job.cancel()
    }
}
