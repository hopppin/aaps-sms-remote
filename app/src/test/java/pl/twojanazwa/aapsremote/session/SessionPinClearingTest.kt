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
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsSamples

@OptIn(ExperimentalCoroutinesApi::class)
class SessionPinClearingTest {

    @TestFactory
    fun `SESSION-19 PIN jest wyczyszczony w chwili fazy FINISHED dla kazdego wyniku CARBS`(): List<DynamicTest> =
        sessionScenarios.map { scenario ->
            DynamicTest.dynamicTest("SESSION-19 ${scenario.name}") {
                runTest {
                    val run = playChecked(scenario)
                    if (run.credentials != null) {
                        assertEquals(true, run.pinClearedWhenFinished, "PIN w chwili FINISHED")
                        assertTrue(run.credentials.pin.isCleared)
                    }
                }
            }
        }

    @Test
    fun `SESSION-19 dostawca TOTP rzuca wyjatek - Failed INTERNAL_ERROR PIN wyczyszczony bez kodu`() = runTest {
        val sender = FakeSmsSender()
        val source = FakeIncomingSmsSource()
        val credentials = SessionTestFixtures.credentials()
        val totp = mockk<UsableTotpProvider>()
        coEvery { totp.awaitUsableCode(any()) } throws IllegalStateException("TOTP_PROVIDER_FAILURE_MARKER")
        val session = CommandSession(
            command = (Command.Carbs.of(1, CommandLimits.DEFAULT) as CarbsValidation.Valid).command,
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
        assertFalse(result.outcome.toString().contains("TOTP_PROVIDER_FAILURE_MARKER"))
    }
}
