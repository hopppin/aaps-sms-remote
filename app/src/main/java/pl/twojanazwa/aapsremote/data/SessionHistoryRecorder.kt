package pl.twojanazwa.aapsremote.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionState
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DATA-13. Czeka (w zakresie aplikacji) na fazę FINISHED przekazanego stanu sesji i zapisuje
 * dokladnie jeden wpis. Nie wplywa na sesje, nie ponawia zapisu, nie rzuca (DATA-15).
 */
@Singleton
class SessionHistoryRecorder @Inject constructor(
    private val history: CommandHistory,
    @DataScope private val scope: CoroutineScope,
) {
    private val lock = Any()

    /** Klucze slabe, rownosc tozsamosciowa (MutableStateFlow nie nadpisuje equals). */
    private val tracked = WeakHashMap<StateFlow<SessionState>, Unit>()

    /** Sprawiedliwy (FIFO): zapisy w kolejnosci wywolan track. */
    private val recordMutex = Mutex()

    /** Nieblokujace, bezpieczne z dowolnego watku. Ten sam obiekt stanu: najwyzej jeden wpis. */
    fun track(state: StateFlow<SessionState>) {
        synchronized(lock) {
            if (tracked.put(state, Unit) != null) return
            // UNDISPATCHED: kolejka do Mutexu ustalana jeszcze w wywolaniu track, pod lock.
            try {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    recordMutex.withLock {
                        val result = state.first { it.phase == SessionPhase.FINISHED }.result
                            ?: return@launch
                        try {
                            history.record(result)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // DATA-13: brak ponawiania, brak wyjatku na zewnatrz; wynik Failed ignorowany.
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Zakres anulowany: brak zapisu.
            }
        }
    }
}
