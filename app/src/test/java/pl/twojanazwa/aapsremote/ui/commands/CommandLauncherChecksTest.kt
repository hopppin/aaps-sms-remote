package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.data.AapsNumberState
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.session.SessionTestFixtures

@OptIn(ExperimentalCoroutinesApi::class)
class CommandLauncherChecksTest {

    private fun rejected(reason: LaunchRejection) = LaunchResult.Rejected(reason)

    @Test
    fun `CMDUI-12 wynik load daje odpowiedni powod odrzucenia i wyczyszczony PIN bez startu`() = runTest {
        val env = CommandTestEnv(this)
        env.secrets.loadResults.addAll(
            listOf(LoadResult.Absent, LoadResult.Invalidated, LoadResult.Locked, LoadResult.Unavailable),
        )
        val launcher = env.launcher()
        val expected = listOf(
            LaunchRejection.SECRET_ABSENT,
            LaunchRejection.SECRET_INVALIDATED,
            LaunchRejection.SECRET_LOCKED,
            LaunchRejection.SECRET_UNAVAILABLE,
        )
        for (reason in expected) {
            val pin = CommandTestFixtures.pin()
            assertEquals(rejected(reason), launcher.launch(CommandTestFixtures.carbs(20), pin))
            assertTrue(pin.isCleared)
        }
        runCurrent()
        assertEquals(4, env.secrets.loadCount)
        assertEquals(0, env.sender.sendCount)
        assertNull(env.coordinator.activeSession.value)
    }

    @Test
    fun `CMDUI-12 numer w ladowaniu lub bez konfiguracji odrzuca polecenie bez odczytu sekretu`() = runTest {
        val env = CommandTestEnv(this)
        val launcher = env.launcher()
        val cases = listOf(
            AapsNumberState.Loading to LaunchRejection.NUMBER_LOADING,
            AapsNumberState.NotConfigured to LaunchRejection.NUMBER_MISSING,
            AapsNumberState.LoadFailed to LaunchRejection.NUMBER_MISSING,
        )
        for ((state, reason) in cases) {
            env.setNumber(state)
            val pin = CommandTestFixtures.pin()
            assertEquals(rejected(reason), launcher.launch(CommandTestFixtures.carbs(20), pin))
            assertTrue(pin.isCleared)
        }
        assertEquals(0, env.secrets.loadCount)
        assertEquals(0, env.sender.sendCount)
    }

    @Test
    fun `CMDUI-12 brak uprawnienia do odbioru lub wysylania daje SMS_PERMISSIONS_MISSING`() = runTest {
        val env = CommandTestEnv(this)
        val launcher = env.launcher()
        env.permissions.receive = false
        assertEquals(rejected(LaunchRejection.SMS_PERMISSIONS_MISSING), launcher.launch(Command.Bg, null))
        env.permissions.receive = true
        env.permissions.send = false
        assertEquals(rejected(LaunchRejection.SMS_PERMISSIONS_MISSING), launcher.launch(Command.Pump, null))
        val pin = CommandTestFixtures.pin()
        assertEquals(
            rejected(LaunchRejection.SMS_PERMISSIONS_MISSING),
            launcher.launch(CommandTestFixtures.carbs(20), pin),
        )
        assertTrue(pin.isCleared)
        assertEquals(0, env.secrets.loadCount)
        assertEquals(0, env.sender.sendCount)
        assertNull(env.coordinator.activeSession.value)
    }

    @Test
    fun `CMDUI-12 BG i PUMP nie wywoluja load`() = runTest {
        val env = CommandTestEnv(this)
        val launcher = env.launcher()
        assertEquals(LaunchResult.Started, launcher.launch(Command.Bg, null))
        env.coordinator.activeSession.value?.cancel()
        runCurrent()
        assertEquals(LaunchResult.Started, launcher.launch(Command.Pump, null))
        env.coordinator.activeSession.value?.cancel()
        runCurrent()
        assertEquals(0, env.secrets.loadCount)
    }

    @Test
    fun `CMDUI-12 anulowanie w trakcie load czyści PIN i nie startuje takze po zwolnieniu load`() = runTest {
        val env = CommandTestEnv(this)
        val gate = CompletableDeferred<Unit>()
        env.secrets.loadGate = gate
        env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
        val pin = CommandTestFixtures.pin()
        val launcher = env.launcher()
        val job = launch { launcher.launch(CommandTestFixtures.carbs(20), pin) }
        runCurrent()
        assertEquals(1, env.secrets.loadCount)
        assertFalse(pin.isCleared)

        job.cancel()
        runCurrent()
        assertTrue(job.isCancelled)
        assertTrue(pin.isCleared)

        gate.complete(Unit)
        runCurrent()
        assertTrue(pin.isCleared)
        assertEquals(0, env.sender.sendCount)
        assertNull(env.coordinator.activeSession.value)
    }

    @Test
    fun `CMDUI-12 load wracajacy po anulowaniu nie startuje sesji a PIN jest wyczyszczony`() = runTest {
        val env = CommandTestEnv(this)
        val gate = CompletableDeferred<Unit>()
        env.secrets.loadGate = gate
        env.secrets.loadIgnoresCancellation = true
        env.secrets.loadResults.add(LoadResult.Loaded(SessionTestFixtures.secret()))
        val pin = CommandTestFixtures.pin()
        val launcher = env.launcher()
        val job = launch { launcher.launch(CommandTestFixtures.carbs(20), pin) }
        runCurrent()
        assertEquals(1, env.secrets.loadCount)

        job.cancel()
        gate.complete(Unit)
        runCurrent()

        assertTrue(job.isCancelled)
        assertTrue(pin.isCleared)
        assertEquals(0, env.sender.sendCount)
        assertNull(env.coordinator.activeSession.value)
        assertNull(env.currentRun.state.value)
    }

    @Test
    fun `CMDUI-12 zajeta sesja odrzuca polecenie przed odczytem sekretu`() = runTest {
        val env = CommandTestEnv(this)
        val launcher = env.launcher()
        assertEquals(LaunchResult.Started, launcher.launch(Command.Bg, null))
        val pin = CommandTestFixtures.pin()
        val second = async { launcher.launch(CommandTestFixtures.carbs(20), pin) }
        runCurrent()
        assertEquals(rejected(LaunchRejection.COMMAND_ACTIVE), second.await())
        assertTrue(pin.isCleared)
        assertEquals(0, env.secrets.loadCount)
        env.coordinator.activeSession.value?.cancel()
        runCurrent()
    }
}
