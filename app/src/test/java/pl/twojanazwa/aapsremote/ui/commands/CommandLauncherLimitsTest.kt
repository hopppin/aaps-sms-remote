package pl.twojanazwa.aapsremote.ui.commands

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.data.CommandLimitsState
import pl.twojanazwa.aapsremote.data.CommandLimitsStore
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.session.SessionTestFixtures

@OptIn(ExperimentalCoroutinesApi::class)
class CommandLauncherLimitsTest {

    private fun CommandTestEnv.withSecret() = also { secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret())) }

    private fun CommandTestEnv.assertNotStarted() {
        assertNull(coordinator.activeSession.value)
        assertNull(currentRun.state.value)
        assertTrue(sender.sentCommands.isEmpty())
    }

    @Test
    fun `LIM-09 CARBS 40 przy limicie obnizonym do 30 daje AMOUNT_ABOVE_LIMIT bez odczytu sekretu`() = runTest {
        val env = CommandTestEnv(this).withSecret()
        val carbs = CommandTestFixtures.carbs(40)
        env.limits.setCarbsMax(30)
        val pin = CommandTestFixtures.pin()

        val result = env.launcher().launch(carbs, pin, extraConfirmed = true)

        assertEquals(LaunchResult.Rejected(LaunchRejection.AMOUNT_ABOVE_LIMIT), result)
        assertTrue(pin.isCleared)
        assertEquals(1, env.secrets.loadResults.size) // load() niewywołane
        env.assertNotStarted()
    }

    @Test
    fun `LIM-09 LoadFailed daje LIMITS_UNAVAILABLE dla CARBS, a BG startuje`() = runTest {
        val env = CommandTestEnv(this).withSecret()
        env.limits.setState(CommandLimitsState.LoadFailed)
        val pin = CommandTestFixtures.pin()

        assertEquals(
            LaunchResult.Rejected(LaunchRejection.LIMITS_UNAVAILABLE),
            env.launcher().launch(CommandTestFixtures.carbs(20), pin),
        )
        assertTrue(pin.isCleared)
        assertEquals(1, env.secrets.loadResults.size) // load() niewywołane
        env.assertNotStarted()

        assertEquals(LaunchResult.Started, env.launcher().launch(Command.Bg, null))
        env.coordinator.activeSession.value?.cancel()
        runCurrent()
    }

    @Test
    fun `LIM-09 LoadFailed bez uprawnien SMS daje SMS_PERMISSIONS_MISSING, uprawnienia przed limitami`() = runTest {
        val env = CommandTestEnv(this).withSecret()
        env.limits.setState(CommandLimitsState.LoadFailed)
        env.permissions.send = false
        val pin = CommandTestFixtures.pin()

        assertEquals(
            LaunchResult.Rejected(LaunchRejection.SMS_PERMISSIONS_MISSING),
            env.launcher().launch(CommandTestFixtures.carbs(20), pin),
        )
        assertTrue(pin.isCleared)
        env.assertNotStarted()
    }

    @Test
    fun `LIM-09 Loading daje LIMITS_UNAVAILABLE dla CARBS`() = runTest {
        val env = CommandTestEnv(this).withSecret()
        env.limits.setState(CommandLimitsState.Loading)
        assertEquals(
            LaunchResult.Rejected(LaunchRejection.LIMITS_UNAVAILABLE),
            env.launcher().launch(CommandTestFixtures.carbs(20), CommandTestFixtures.pin()),
        )
    }

    @Test
    fun `LIM-09 CARBS 35 bez okna przy limicie podniesionym do 40 wymaga potwierdzenia, z potwierdzeniem startuje`() =
        runTest {
            val env = CommandTestEnv(this).withSecret()
            env.limits.setCarbsMax(40)
            val pin = CommandTestFixtures.pin()

            val rejected = env.launcher().launch(CommandTestFixtures.carbs(35), pin, extraConfirmed = false)

            assertEquals(LaunchResult.Rejected(LaunchRejection.EXTRA_CONFIRMATION_REQUIRED), rejected)
            assertTrue(pin.isCleared)
            assertEquals(1, env.secrets.loadResults.size) // load() niewywołane
            env.assertNotStarted()

            val started = env.launcher().launch(CommandTestFixtures.carbs(35), CommandTestFixtures.pin(), extraConfirmed = true)
            assertEquals(LaunchResult.Started, started)
            assertNotNull(env.coordinator.activeSession.value)
            env.coordinator.activeSession.value?.cancel()
            runCurrent()
        }

    @Test
    fun `LIM-09 stan, limit i prog pochodza z jednej migawki odczytanej raz`() = runTest {
        val env = CommandTestEnv(this).withSecret()
        val state = mockk<StateFlow<CommandLimitsState>>()
        // Pierwszy odczyt: limit 50 (40 g bez pytania); kolejny, gdyby nastąpił: LoadFailed.
        every { state.value } returnsMany listOf(
            CommandLimitsState.Loaded(FakeCommandLimitsStore.limits(50)),
            CommandLimitsState.LoadFailed,
        )
        val store = object : CommandLimitsStore {
            override val state = state
            override suspend fun save(limits: pl.twojanazwa.aapsremote.commands.CommandLimits) =
                pl.twojanazwa.aapsremote.data.SaveLimitsResult.Saved
        }
        val launcher = CommandLauncher(
            env.coordinator, env.numbers, store, env.permissions, env.secrets, env.recorder, env.history, env.clock,
            env.currentRun, env.appScope,
        )

        val result = launcher.launch(CommandTestFixtures.carbs(40), CommandTestFixtures.pin())

        assertEquals(LaunchResult.Started, result)
        verify(exactly = 1) { state.value }
        env.coordinator.activeSession.value?.cancel()
        runCurrent()
    }

    @Test
    fun `LIM-09 BG i PUMP nie sprawdzaja limitow`() = runTest {
        val env = CommandTestEnv(this)
        env.limits.setState(CommandLimitsState.LoadFailed)
        assertEquals(LaunchResult.Started, env.launcher().launch(Command.Pump, null))
        env.coordinator.activeSession.value?.cancel()
        runCurrent()
    }

    @Test
    fun `LIM-11 start w trakcie zapisu stosuje poprzednie limity`() = runTest {
        val env = CommandTestEnv(this).withSecret()
        val gate = CompletableDeferred<Unit>()
        env.limits.gate = gate
        val saving = launch { env.limits.save(FakeCommandLimitsStore.limits(10)) }
        runCurrent()

        // Zapis zawieszony: obowiązuje poprzedni stan Loaded(DEFAULT, 50 g).
        val result = env.launcher().launch(CommandTestFixtures.carbs(30), CommandTestFixtures.pin())

        assertEquals(LaunchResult.Started, result)
        env.coordinator.activeSession.value?.cancel()
        runCurrent()
        gate.complete(Unit)
        saving.join()
    }
}
