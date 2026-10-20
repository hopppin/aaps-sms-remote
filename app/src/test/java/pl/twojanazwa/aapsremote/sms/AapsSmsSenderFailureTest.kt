package pl.twojanazwa.aapsremote.sms

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.session.OutgoingSms
import pl.twojanazwa.aapsremote.session.SendResult

class AapsSmsSenderFailureTest {
    private val valid = "+447700900123"

    private suspend fun sendBg(sender: AapsSmsSender) = sender.send(OutgoingSms.CommandText("BG"))

    @Test
    fun `SMS-12 brak SEND_SMS daje Failed bez numeru i bez warstwy systemowej`() = runTest {
        val system = FakeSmsSystemApi()
        var numberReads = 0
        val sender = AapsSmsSender({ numberReads++; valid }, FakeSmsPermissions(send = false), system)
        assertEquals(SendResult.Failed, sendBg(sender))
        assertEquals(0, numberReads)
        assertEquals(0, system.totalCalls)
    }

    @Test
    fun `SMS-12 nieprawidlowy numer daje Failed bez warstwy systemowej`() = runTest {
        for (number in listOf(null, "70090012", "+44 7700 9OO123")) {
            val system = FakeSmsSystemApi()
            val sender = AapsSmsSender({ number }, FakeSmsPermissions(), system)
            assertEquals(SendResult.Failed, sendBg(sender))
            assertEquals(0, system.totalCalls)
        }
    }

    @Test
    fun `SMS-12 wyjatek divideMessage daje Failed`() = runTest {
        val system = FakeSmsSystemApi().apply { divideException = SecurityException("x") }
        val sender = AapsSmsSender({ valid }, FakeSmsPermissions(), system)
        assertEquals(SendResult.Failed, sendBg(sender))
    }

    @Test
    fun `SMS-12 wyjatek registerSentResults daje Failed`() = runTest {
        val system = FakeSmsSystemApi().apply { registerException = SecurityException("x") }
        val sender = AapsSmsSender({ valid }, FakeSmsPermissions(), system)
        assertEquals(SendResult.Failed, sendBg(sender))
        assertEquals(0, system.sendPartsCalls)
    }

    @Test
    fun `SMS-12 wyjatek sendParts daje Failed i wyrejestrowanie`() = runTest {
        val system = FakeSmsSystemApi().apply { sendPartsException = SecurityException("x") }
        val sender = AapsSmsSender({ valid }, FakeSmsPermissions(), system)
        assertEquals(SendResult.Failed, sendBg(sender))
        assertEquals(1, system.unregisterCount)
    }

    @Test
    fun `SMS-12 wyjatek dostawcy numeru daje Failed bez warstwy systemowej`() = runTest {
        val system = FakeSmsSystemApi()
        val sender = AapsSmsSender({ throw IllegalStateException("x") }, FakeSmsPermissions(), system)
        assertEquals(SendResult.Failed, sendBg(sender))
        assertEquals(0, system.totalCalls)
    }

    @Test
    fun `SMS-12 wyjatek sprawdzenia uprawnienia daje Failed`() = runTest {
        val system = FakeSmsSystemApi()
        val permissions = object : SmsPermissions {
            override fun canSend(): Boolean = throw SecurityException("x")

            override fun canReceive(): Boolean = true
        }
        val sender = AapsSmsSender({ valid }, permissions, system)
        assertEquals(SendResult.Failed, sendBg(sender))
        assertEquals(0, system.totalCalls)
    }
}
