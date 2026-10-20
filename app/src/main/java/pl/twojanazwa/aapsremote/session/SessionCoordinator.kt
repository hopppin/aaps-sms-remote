package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeSource

/**
 * Jedno aktywne polecenie naraz (SESSION-22). Jeden na aplikację.
 *
 * Miejsce jest rezerwowane atomowo przed utworzeniem sesji, więc przy zajętym miejscu wynik zawsze
 * brzmi [StartResult.Busy], niezależnie od poprawności danych wejściowych.
 */
class SessionCoordinator(
    private val sender: SmsSender,
    private val incoming: IncomingSmsSource,
    private val clock: EpochClock,
    private val totp: UsableTotpProvider,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    /** Miejsce zajęte od rezerwacji do zakończenia sesji. */
    private val reserved = AtomicBoolean(false)
    private val active = MutableStateFlow<CommandSession?>(null)

    /** null, gdy brak aktywnej sesji (SESSION-22). */
    val activeSession: StateFlow<CommandSession?> = active

    /**
     * Najpierw atomowa rezerwacja miejsca; zajęte → Busy bez walidacji danych (czyści credentials?.pin).
     * Po rezerwacji tworzy sesję i uruchamia run() w [scope]. IllegalArgumentException z konstruktora
     * zwalnia rezerwację, czyści PIN i przechodzi do wywołującego.
     */
    fun start(
        scope: CoroutineScope,
        command: Command,
        credentials: CodeCredentials?,
        timeouts: SessionTimeouts = SessionTimeouts(),
    ): StartResult {
        if (!reserved.compareAndSet(false, true)) {
            credentials?.pin?.clear()
            return StartResult.Busy
        }
        val session = try {
            CommandSession(command, credentials, sender, incoming, clock, totp, timeouts, timeSource)
        } catch (e: Throwable) {
            credentials?.pin?.clear()
            reserved.set(false)
            throw e
        }
        session.onFinished = { release(session) }
        active.value = session
        val job = scope.launch { session.run() }
        job.invokeOnCompletion {
            session.cancel()
            release(session)
        }
        return StartResult.Started(session)
    }

    /** Idempotentne: miejsce zwalnia tylko ta sesja, która je zajmuje. */
    private fun release(session: CommandSession) {
        if (active.compareAndSet(session, null)) {
            reserved.set(false)
        }
    }
}

sealed interface StartResult {
    class Started(val session: CommandSession) : StartResult
    data object Busy : StartResult
}
