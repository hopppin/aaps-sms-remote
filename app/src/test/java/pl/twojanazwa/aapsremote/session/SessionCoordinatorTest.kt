package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsSamples
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinatorTest {

    private val sender = FakeSmsSender()
    private val source = FakeIncomingSmsSource()

    private fun carbs(grams: Int): Command = (Command.Carbs.of(grams) as CarbsValidation.Valid).command

    private fun TestScope.coordinator(): SessionCoordinator {
        val clock = virtualClock()
        return SessionCoordinator(sender, source, clock, UsableTotpProvider(clock), testScheduler.timeSource)
    }

    private fun started(result: StartResult): CommandSession = (result as StartResult.Started).session

    @Test
    fun `SESSION-22 brak aktywnej sesji - start BG zwraca wystartowano i aktywna sesja jest niepusta`() = runTest {
        val coordinator = coordinator()
        assertNull(coordinator.activeSession.value)
        val result = coordinator.start(backgroundScope, Command.Bg, null)
        val session = started(result)
        assertSame(session, coordinator.activeSession.value)
        runCurrent()
        assertEquals(listOf("BG"), sender.sentCommands)
    }

    @Test
    fun `SESSION-22 aktywne CARBS 1 w AWAITING_CODE_REQUEST - start CARBS 2 daje zajete, nie wysyla SMS i czysci PIN`() =
        runTest {
            val coordinator = coordinator()
            val first = started(coordinator.start(backgroundScope, carbs(1), SessionTestFixtures.credentials()))
            runCurrent()
            assertEquals(SessionPhase.AWAITING_CODE_REQUEST, first.state.value.phase)
            val sentBefore = sender.sentCommands.toList()

            val credentials = SessionTestFixtures.credentials()
            val result = coordinator.start(backgroundScope, carbs(2), credentials)
            runCurrent()

            assertSame(StartResult.Busy, result)
            assertEquals(sentBefore, sender.sentCommands)
            assertTrue(sender.sentCommands.none { it.endsWith("2") })
            assertTrue(credentials.pin.isCleared)
            assertSame(first, coordinator.activeSession.value)
        }

    @Test
    fun `SESSION-22 zajete miejsce - BG z danymi do kodu i CARBS bez danych daja zajete bez wyjatku i czyszcza PIN`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(backgroundScope, carbs(1), SessionTestFixtures.credentials())
            runCurrent()

            val bgWithCredentials = SessionTestFixtures.credentials()
            assertSame(StartResult.Busy, coordinator.start(backgroundScope, Command.Bg, bgWithCredentials))
            assertTrue(bgWithCredentials.pin.isCleared)

            assertSame(StartResult.Busy, coordinator.start(backgroundScope, carbs(2), null))
        }

    @Test
    fun `SESSION-22 brak aktywnej sesji - BG z danymi do kodu rzuca IllegalArgumentException, zwalnia miejsce i czysci PIN`() =
        runTest {
            val coordinator = coordinator()
            val credentials = SessionTestFixtures.credentials()
            assertThrows(IllegalArgumentException::class.java) {
                coordinator.start(backgroundScope, Command.Bg, credentials)
            }
            assertTrue(credentials.pin.isCleared)
            assertNull(coordinator.activeSession.value)
            assertTrue(coordinator.start(backgroundScope, Command.Bg, null) is StartResult.Started)
        }

    @Test
    fun `SESSION-22 sesja, ktorej run zwrocil wynik - aktywna sesja pusta i kolejny start BG sie udaje`() = runTest {
        val coordinator = coordinator()
        val session = started(coordinator.start(backgroundScope, Command.Bg, null))
        runCurrent()
        source.emit(IncomingSms(AapsSamples.bg, SessionTestFixtures.T0))
        runCurrent()

        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
        assertNull(coordinator.activeSession.value)
        assertTrue(coordinator.start(backgroundScope, Command.Bg, null) is StartResult.Started)
    }

    @Test
    fun `SESSION-22 sesja wstrzymana po FINISHED a przed zwolnieniem miejsca - start BG daje zajete`() = runTest {
        val coordinator = coordinator()
        val session = started(coordinator.start(backgroundScope, carbs(1), SessionTestFixtures.credentials()))
        val original = session.onFinished
        val inWindow = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        session.onFinished = {
            inWindow.countDown()
            proceed.await(10, TimeUnit.SECONDS)
            original()
        }
        // run() jeszcze nie ruszył, więc cancel() kończy sesję w wątku pomocniczym (finish → onFinished).
        val canceller = Thread { session.cancel() }
        canceller.start()
        assertTrue(inWindow.await(10, TimeUnit.SECONDS))

        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
        assertSame(session, coordinator.activeSession.value)
        assertSame(StartResult.Busy, coordinator.start(backgroundScope, Command.Bg, null))
        assertSame(session, coordinator.activeSession.value)

        proceed.countDown()
        canceller.join(10_000)
        assertNull(coordinator.activeSession.value)
        assertTrue(coordinator.start(backgroundScope, Command.Bg, null) is StartResult.Started)
    }

    @Test
    fun `SESSION-22 100 rownoczesnych startow BG na Dispatchers Default - dokladnie jeden wystartowano`() {
        val clock = EpochClock { System.currentTimeMillis() }
        val coordinator = SessionCoordinator(FakeSmsSender(), FakeIncomingSmsSource(), clock, UsableTotpProvider(clock))
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val results = runBlocking {
                val gate = CompletableDeferred<Unit>()
                val jobs = List(100) {
                    async(Dispatchers.Default) {
                        gate.await()
                        coordinator.start(scope, Command.Bg, null)
                    }
                }
                gate.complete(Unit)
                jobs.awaitAll()
            }
            assertEquals(1, results.count { it is StartResult.Started })
            assertEquals(99, results.count { it === StartResult.Busy })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `SESSION-22 aktywna sesja anulowana z zewnatrz przez zamkniecie zakresu - aktywna sesja pusta`() = runTest {
        val coordinator = coordinator()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val session = started(coordinator.start(scope, Command.Bg, null))
        runCurrent()
        assertSame(session, coordinator.activeSession.value)

        scope.cancel()
        runCurrent()

        assertEquals(SessionPhase.FINISHED, session.state.value.phase)
        assertNull(coordinator.activeSession.value)
        assertTrue(coordinator.start(backgroundScope, Command.Bg, null) is StartResult.Started)
    }

    @Test
    fun `SESSION-22 zakres anulowany po starcie CARBS 1 przed uruchomieniem sesji - Cancelled, PIN wyczyszczony, zero SMS`() =
        runTest {
            val coordinator = coordinator()
            val credentials = SessionTestFixtures.credentials()
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
            val session = started(coordinator.start(scope, carbs(1), credentials))

            scope.cancel()
            runCurrent()

            val state = session.state.value
            assertEquals(SessionPhase.FINISHED, state.phase)
            assertEquals(SessionOutcome.Cancelled, state.result?.outcome)
            assertTrue(credentials.pin.isCleared)
            assertEquals(0, sender.sendCount)
            assertNotNull(state.result)
            assertNull(coordinator.activeSession.value)
            assertFalse(source.subscriptionCount.value > 0)
        }
}
