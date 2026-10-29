package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.ProfileChoice
import pl.twojanazwa.aapsremote.commands.ProfileSwitchValidation
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsReply

@OptIn(ExperimentalCoroutinesApi::class)
class CommandSessionProfileTest {
    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()

    private val BODY = "dowolna tresc"

    private val switch: Command =
        (Command.ProfileSwitch.of(ProfileChoice(1, "LocalProfile1"), 100) as ProfileSwitchValidation.Valid).command

    private suspend fun TestScope.codeNeverSent(command: Command, reply: (String) -> AapsReply) {
        val clock = virtualClock()
        val session = CommandSession(
            command, SessionTestFixtures.credentials(), sender, source, clock, UsableTotpProvider(clock),
            timeSource = testScheduler.timeSource, parse = { body, _ -> reply(body) },
        )
        val run = async { session.run() }
        runCurrent()
        deliverAt(source, 60_000, BODY)
        val outcome = run.await().outcome
        assertEquals(SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, BODY), outcome)
        assertEquals(1, sender.sentCommands.size)
        assertEquals(emptyList<String>(), sender.sentCodeCopies)
    }

    @Test
    fun `PROF-05 prosba o kod w sesji PROFILE_SWITCH nie powoduje wyslania kodu`() = runTest {
        codeNeverSent(switch) { AapsReply.CodeRequest(100, it) }
    }

    @Test
    fun `PROF-05 prosba o kod bolusa w sesji PROFILE_SWITCH nie powoduje wyslania kodu`() = runTest {
        codeNeverSent(switch) { AapsReply.BolusCodeRequest(100, it) }
    }
}
