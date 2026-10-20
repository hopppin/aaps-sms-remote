package pl.twojanazwa.aapsremote.session

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsSamples

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPinClearingTest {

    @Test
    fun `SESSION-19 dostawca TOTP rzuca wyjatek - Failed INTERNAL_ERROR PIN wyczyszczony bez kodu`() = runTest {
        val sender = FakeSmsSender()
        val source = FakeIncomingSmsSource()
        val credentials = SessionTestFixtures.credentials()
        val totp = mockk<UsableTotpProvider>()
        coEvery { totp.awaitUsableCode(any()) } throws IllegalStateException("x")
        val session = CommandSession(
            command = (Command.Carbs.of(1) as CarbsValidation.Valid).command,
            credentials = credentials,
            sender = sender,
            incoming = source,
            clock = virtualClock(),
            totp = totp,
            timeSource = testScheduler.timeSource,
        )
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        source.emit(IncomingSms(AapsSamples.carbsCodeRequest, SessionTestFixtures.T0 + 60_000))
        runCurrent()
        val result = run.await()
        assertEquals(SessionOutcome.Failed(FailureReason.INTERNAL_ERROR, null), result.outcome)
        assertTrue(credentials.pin.isCleared)
        assertTrue(sender.sentCodeCopies.isEmpty())
        assertFalse(result.outcome.toString().contains("x"))
    }
}
