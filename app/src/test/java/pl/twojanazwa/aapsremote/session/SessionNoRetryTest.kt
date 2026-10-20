package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionNoRetryTest {

    @TestFactory
    fun `SESSION-20 w kazdym scenariuszu najwyzej jeden SMS z poleceniem i jeden z kodem`(): List<DynamicTest> =
        sessionScenarios.map { scenario ->
            DynamicTest.dynamicTest("SESSION-20 ${scenario.name}") {
                runTest {
                    val run = playChecked(scenario)
                    assertTrue(run.sender.sentCommands.size <= 1, "polecenia: ${run.sender.sentCommands.size}")
                    assertTrue(run.sender.sentCodeCopies.size <= 1, "kody: ${run.sender.sentCodeCopies.size}")
                    assertTrue(run.sender.codeArrays.size <= 1)
                }
            }
        }

    @Test
    fun `SESSION-20 po TimedOut AWAITING_CODE_REQUEST prosba o kod nic nie wysyla`() = runTest {
        val sender = FakeSmsSender()
        val source = FakeIncomingSmsSource()
        val session = testSession(carbsCommand(1), sender, source, SessionTestFixtures.credentials())
        val run = async { session.run() }
        runCurrent()
        advanceTimeBy(180_000)
        runCurrent()
        val result = run.await()
        assertEquals(SessionOutcome.TimedOut(SessionPhase.AWAITING_CODE_REQUEST), result.outcome)
        source.emit(IncomingSms(AapsSamples.carbsCodeRequest, SessionTestFixtures.T0 + 180_000))
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf("CARBS 1"), sender.sentCommands)
        assertTrue(sender.sentCodeCopies.isEmpty())
        assertEquals(result, session.state.value.result)
    }

    @Test
    fun `SESSION-20 po FINISHED port odbioru nie ma subskrybenta sesji`() = runTest {
        val clock = virtualClock()
        val source = FakeIncomingSmsSource()
        val sender = FakeSmsSender().apply { commandResult = SendResult.Failed }
        val session = CommandSession(
            command = Command.Bg,
            credentials = null,
            sender = sender,
            incoming = source,
            clock = clock,
            totp = UsableTotpProvider(clock),
            timeSource = testTimeSource,
        )
        session.run()
        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
        assertEquals(0, source.subscriptionCount.value)
    }
}
