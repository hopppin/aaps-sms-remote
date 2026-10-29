package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.commands.LimitsValidation
import pl.twojanazwa.aapsremote.data.CommandLimitsState
import pl.twojanazwa.aapsremote.data.HistoryCommand
import pl.twojanazwa.aapsremote.data.HistoryEntry
import pl.twojanazwa.aapsremote.data.HistoryOutcome
import pl.twojanazwa.aapsremote.data.HistorySnapshot
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionTestFixtures
import pl.twojanazwa.aapsremote.session.UnknownReason
import pl.twojanazwa.aapsremote.ui.commands.CommandTestFixtures.T0

@OptIn(ExperimentalCoroutinesApi::class)
class CommandLauncherBolusTest {
    private fun env(scope: TestScope, vararg entries: HistoryEntry) =
        CommandTestEnv(scope).also {
            it.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
            it.history.entries = flowOf(HistorySnapshot.Entries(entries.toList()))
        }

    private fun CommandTestEnv.setLimits(bolusMax: Int = 500, interval: Int = 15) {
        limits.setState(
            CommandLimitsState.Loaded((CommandLimits.of(50, bolusMax, interval) as LimitsValidation.Valid).limits),
        )
    }

    private suspend fun CommandTestEnv.assertRejected(reason: LaunchRejection, minutes: Int? = null, amount: Int = 10) {
        val pin = CommandTestFixtures.pin()
        assertEquals(LaunchResult.Rejected(reason, minutes), launcher().launch(bolus(amount), pin))
        assertTrue(pin.isCleared)
        assertEquals(1, secrets.loadResults.size) // load() niewywołane
        assertNull(coordinator.activeSession.value)
        assertNull(currentRun.state.value)
        assertTrue(sender.sentCommands.isEmpty())
    }

    private suspend fun CommandTestEnv.assertStarted(amount: Int = 10) {
        assertEquals(LaunchResult.Started, launcher().launch(bolus(amount), CommandTestFixtures.pin()))
        coordinator.activeSession.value?.cancel()
    }

    @Test
    fun `BOL-12 bolus sprzed 10 min daje BOLUS_INTERVAL z 5 min, PIN wyczyszczony, bez startu`() = runTest {
        env(this, bolusEntry(HistoryOutcome.BolusConfirmed, codeSentAt = T0 - 600_000))
            .assertRejected(LaunchRejection.BOLUS_INTERVAL, 5)
    }

    @Test
    fun `BOL-12 bolus sprzed dokladnie 15 min startuje`() = runTest {
        env(this, bolusEntry(HistoryOutcome.BolusConfirmed, codeSentAt = T0 - 900_000)).assertStarted()
        runCurrent()
    }

    @Test
    fun `BOL-12 Unknown bez kodu sprzed minuty daje BOLUS_INTERVAL z 14 min`() = runTest {
        val entry = bolusEntry(HistoryOutcome.Unknown(UnknownReason.CODE_SEND_FAILED), finishedAt = T0 - 60_000)
        env(this, entry).assertRejected(LaunchRejection.BOLUS_INTERVAL, 14)
    }

    @Test
    fun `BOL-12 Failed AMOUNT_MISMATCH bez kodu nie blokuje`() = runTest {
        val entry = bolusEntry(HistoryOutcome.Failed(FailureReason.AMOUNT_MISMATCH), finishedAt = T0 - 60_000)
        env(this, entry).assertStarted()
        runCurrent()
    }

    @Test
    fun `BOL-12 Failed WRONG_CODE z wyslanym kodem blokuje`() = runTest {
        val entry = bolusEntry(HistoryOutcome.Failed(FailureReason.WRONG_CODE), codeSentAt = T0 - 60_000)
        env(this, entry).assertRejected(LaunchRejection.BOLUS_INTERVAL, 14)
    }

    @Test
    fun `BOL-12 zegar cofniety blokuje na pelny odstep`() = runTest {
        env(this, bolusEntry(HistoryOutcome.BolusConfirmed, codeSentAt = T0 + 60_000))
            .assertRejected(LaunchRejection.BOLUS_INTERVAL, 15)
    }

    @Test
    fun `BOL-12 CARBS w historii nie blokuje bolusa`() = runTest {
        val carbs = bolusEntry(HistoryOutcome.CarbsConfirmed, command = HistoryCommand.CARBS, codeSentAt = T0 - 60_000)
        env(this, carbs).assertStarted()
        runCurrent()
    }

    @Test
    fun `BOL-12 historia niedostepna daje BOLUS_HISTORY_UNAVAILABLE`() = runTest {
        val env = env(this)
        env.history.entries = flowOf(HistorySnapshot.Unavailable)
        env.assertRejected(LaunchRejection.BOLUS_HISTORY_UNAVAILABLE)
    }

    @Test
    fun `BOL-12 odstep 0 startuje bez czytania historii`() = runTest {
        val env = env(this)
        env.setLimits(interval = 0)
        env.history.entries = flowOf(HistorySnapshot.Unavailable)
        env.assertStarted()
        runCurrent()
    }

    @Test
    fun `LIM-09 BOLUS powyzej limitu daje AMOUNT_ABOVE_LIMIT przed odstepem`() = runTest {
        val env = env(this, bolusEntry(HistoryOutcome.BolusConfirmed, codeSentAt = T0 - 60_000))
        env.setLimits(bolusMax = 100)
        env.assertRejected(LaunchRejection.AMOUNT_ABOVE_LIMIT, amount = 150)
    }

    @Test
    fun `LIM-09 BOLUS ponad polowe limitu bez potwierdzenia daje EXTRA_CONFIRMATION_REQUIRED`() = runTest {
        val env = env(this)
        env.setLimits(bolusMax = 100)
        env.assertRejected(LaunchRejection.EXTRA_CONFIRMATION_REQUIRED, amount = 60)
        val pin = CommandTestFixtures.pin()
        assertEquals(LaunchResult.Started, env.launcher().launch(bolus(60), pin, extraConfirmed = true))
        env.coordinator.activeSession.value?.cancel()
        runCurrent()
    }

    @Test
    fun `LIM-09 LoadFailed daje LIMITS_UNAVAILABLE dla BOLUS`() = runTest {
        val env = env(this)
        env.limits.setState(CommandLimitsState.LoadFailed)
        env.assertRejected(LaunchRejection.LIMITS_UNAVAILABLE)
    }
}
