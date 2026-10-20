package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider

class SessionCancelRaceTest {

    @Test
    fun `SESSION-17 1000 powtorzen rownoczesnego cancel i run - zawsze Cancelled bez kodu z PIN-em wyczyszczonym`() =
        runBlocking(Dispatchers.Default) {
            val command = (Command.Carbs.of(1) as CarbsValidation.Valid).command
            val clock = EpochClock { SessionTestFixtures.T0 }
            repeat(1000) {
                val credentials = SessionTestFixtures.credentials()
                val sender = FakeSmsSender().apply { suspendForever = true }
                val session = CommandSession(
                    command = command,
                    credentials = credentials,
                    sender = sender,
                    incoming = FakeIncomingSmsSource(),
                    clock = clock,
                    totp = UsableTotpProvider(clock),
                )
                val run = async(Dispatchers.Default) { session.run() }
                val cancel = async(Dispatchers.Default) { session.cancel() }
                val result = run.await()
                cancel.await()
                assertEquals(SessionOutcome.Cancelled, result.outcome)
                assertTrue(credentials.pin.isCleared)
                assertEquals(SessionPhase.FINISHED, session.state.value.phase)
                assertEquals(SessionOutcome.Cancelled, session.state.value.result?.outcome)
                assertTrue(sender.sendCount in 0..1)
                assertTrue(sender.sentCommands.all { it == "CARBS 1" })
                assertEquals(0, sender.sentCodeCopies.size)
            }
        }
}
