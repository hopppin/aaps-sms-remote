package pl.twojanazwa.aapsremote.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionResult
import pl.twojanazwa.aapsremote.session.SessionState
import java.lang.reflect.Modifier
import java.util.WeakHashMap

class SessionHistoryRecorderTest {

    private fun awaiting() = MutableStateFlow(SessionState(SessionPhase.AWAITING_REPLY, emptyList(), null))

    private fun finished(result: SessionResult) = SessionState(SessionPhase.FINISHED, emptyList(), result)

    private class Env(testScope: TestScope) {
        val appScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScope.testScheduler))
        val history = FakeCommandHistory()
        val recorder = SessionHistoryRecorder(history, appScope)
    }

    @Test
    fun `DATA-13 stan przekazany w trakcie sesji zapisuje wynik po FINISHED`() = runTest {
        val env = Env(this)
        val state = awaiting()
        env.recorder.track(state)
        advanceUntilIdle()
        assertTrue(env.history.recorded.isEmpty())

        val result = HistoryFixtures.bgCompleted()
        state.value = finished(result)
        advanceUntilIdle()

        assertEquals(listOf(result), env.history.recorded)
    }

    @Test
    fun `DATA-13 stan juz w FINISHED z Cancelled daje jeden zapis`() = runTest {
        val env = Env(this)
        val result = HistoryFixtures.cancelledNoTimes()
        env.recorder.track(MutableStateFlow(finished(result)))
        advanceUntilIdle()

        assertEquals(listOf(result), env.history.recorded)
    }

    @Test
    fun `DATA-13 ten sam stan przekazany wielokrotnie daje jeden zapis`() = runTest {
        val env = Env(this)
        val state = awaiting()
        repeat(3) { env.recorder.track(state) }
        state.value = finished(HistoryFixtures.bgCompleted())
        advanceUntilIdle()
        env.recorder.track(state)
        advanceUntilIdle()

        assertEquals(1, env.history.recorded.size)
    }

    @Test
    fun `DATA-13 dwie sesje zapisane w kolejnosci zakonczenia`() = runTest {
        val env = Env(this)
        val first = awaiting()
        val second = awaiting()
        env.recorder.track(first)
        env.recorder.track(second)
        val a = HistoryFixtures.bgCompleted()
        val b = HistoryFixtures.carbsConfirmed()

        first.value = finished(a)
        advanceUntilIdle()
        second.value = finished(b)
        advanceUntilIdle()

        assertEquals(listOf(a, b), env.history.recorded)
    }

    @Test
    fun `DATA-13 zapis kolejnej sesji nie wyprzedza wczesniej przekazanej`() = runTest {
        val env = Env(this)
        val first = awaiting()
        val second = awaiting()
        env.recorder.track(first)
        env.recorder.track(second)
        val a = HistoryFixtures.bgCompleted()
        val b = HistoryFixtures.wrongCode()

        second.value = finished(b)
        advanceUntilIdle()
        assertTrue(env.history.recorded.isEmpty())
        first.value = finished(a)
        advanceUntilIdle()

        assertEquals(listOf(a, b), env.history.recorded)
    }

    @Test
    fun `DATA-13 DATA-15 niepowodzenie zapisu nie jest ponawiane i nie rzuca`() = runTest {
        val env = Env(this)

        env.history.returnFailed = true
        val s1 = MutableStateFlow(finished(HistoryFixtures.bgCompleted()))
        env.recorder.track(s1)
        advanceUntilIdle()
        assertEquals(1, env.history.recorded.size)

        env.history.returnFailed = false
        env.history.throwOnRecord = RuntimeException("x")
        val s2 = MutableStateFlow(finished(HistoryFixtures.wrongCode()))
        env.recorder.track(s2)
        advanceUntilIdle()
        assertEquals(2, env.history.recorded.size)

        env.history.throwOnRecord = null
        val next = HistoryFixtures.carbsConfirmed()
        env.recorder.track(MutableStateFlow(finished(next)))
        advanceUntilIdle()
        assertEquals(3, env.history.recorded.size)
        assertEquals(next, env.history.recorded.last())
        assertTrue(env.appScope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
    }

    @Test
    fun `DATA-13 DATA-15 CancellationException z record nie jest polykany`() = runTest {
        val env = Env(this)
        env.history.throwOnRecord = kotlinx.coroutines.CancellationException("c")
        val cancelled = MutableStateFlow(finished(HistoryFixtures.bgCompleted()))
        env.recorder.track(cancelled)
        advanceUntilIdle()

        val job = env.appScope.coroutineContext[kotlinx.coroutines.Job]!!
        assertEquals(1, env.history.recorded.size)
        assertTrue(job.isActive)
        // zadanie zapisu zakonczone anulowaniem: Mutex zwolniony, kolejna sesja zapisana
        env.history.throwOnRecord = null
        val next = HistoryFixtures.carbsConfirmed()
        env.recorder.track(MutableStateFlow(finished(next)))
        advanceUntilIdle()

        assertEquals(listOf(next), env.history.recorded.drop(1))
        assertTrue(job.isActive)
    }

    @Test
    fun `DATA-13 anulowany zakres przed FINISHED nie zapisuje`() = runTest {
        val env = Env(this)
        val state = awaiting()
        env.recorder.track(state)
        advanceUntilIdle()

        env.appScope.cancel()
        state.value = finished(HistoryFixtures.bgCompleted())
        advanceUntilIdle()

        assertTrue(env.history.recorded.isEmpty())
    }

    @Test
    fun `DATA-13 track na anulowanym zakresie nie rzuca i nie zapisuje`() = runTest {
        val env = Env(this)
        env.appScope.cancel()
        env.recorder.track(MutableStateFlow(finished(HistoryFixtures.bgCompleted())))
        advanceUntilIdle()

        assertTrue(env.history.recorded.isEmpty())
    }

    @Test
    fun `DATA-13 pola rejestratora sa final i tylko WeakHashMap trzyma stany`() {
        val fields = SessionHistoryRecorder::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
        assertEquals(
            setOf("lock", "tracked", "recordMutex", "history", "scope"),
            fields.map { it.name }.toSet(),
        )
        val forbidden = listOf(
            kotlinx.coroutines.flow.Flow::class.java, SessionState::class.java, SessionResult::class.java,
            pl.twojanazwa.aapsremote.session.CommandSession::class.java,
            java.util.Collection::class.java, java.lang.Iterable::class.java,
        )
        for (field in fields) {
            assertTrue(Modifier.isFinal(field.modifiers), "pole ${field.name} nie jest final")
            assertFalse(field.type.isArray, "pole tablicowe ${field.name}")
            for (type in forbidden) {
                assertFalse(type.isAssignableFrom(field.type), "zabroniony typ pola ${field.name}")
            }
        }
        val map = fields.single { it.name == "tracked" }
        assertEquals(WeakHashMap::class.java, map.type)
    }
}
