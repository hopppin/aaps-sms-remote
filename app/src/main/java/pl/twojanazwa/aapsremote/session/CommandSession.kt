package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.definition
import pl.twojanazwa.aapsremote.commands.smsText
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/**
 * Jedno polecenie. Konstruktor: IllegalArgumentException, gdy (credentials != null) != requiresCode;
 * przed rzuceniem czyści credentials?.pin. clock i totp muszą używać tego samego zegara.
 *
 * Wszystkie decyzje zapadają w jednej pętli zdarzeń w [run]; stan jest zmieniany tylko stamtąd
 * (oraz z [finish]), więc nie potrzeba blokad.
 */
class CommandSession(
    val command: Command,
    private val credentials: CodeCredentials?,
    private val sender: SmsSender,
    private val incoming: IncomingSmsSource,
    private val clock: EpochClock,
    @Suppress("unused") private val totp: UsableTotpProvider,
    @Suppress("unused") private val timeouts: SessionTimeouts = SessionTimeouts(),
    /** Czas korutyn dla terminów limitów; w testach testScheduler.timeSource. */
    @Suppress("unused") private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private enum class Lifecycle { NOT_STARTED, RUNNING }

    private sealed interface Event {
        class Message(val sms: IncomingSms) : Event
        class CommandSendResult(val result: SendResult) : Event
    }

    private val _state = MutableStateFlow(SessionState(SessionPhase.NOT_STARTED, emptyList(), null))
    val state: StateFlow<SessionState> = _state

    private val lifecycle = AtomicReference(Lifecycle.NOT_STARTED)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val finishedResult = CompletableDeferred<SessionResult>()

    private var lastClockReading: Long? = null
    private var startedAt: Long? = null
    private var commandSentAt: Long? = null

    /** Wywoływany po opublikowaniu FINISHED, przed uzupełnieniem wyniku (używa koordynator). */
    internal var onFinished: () -> Unit = {}

    init {
        if ((credentials != null) != command.definition.requiresCode) {
            credentials?.pin?.clear()
            throw IllegalArgumentException("credentials required exactly for commands that require a code")
        }
    }

    private fun readClock(): Long = clock.nowEpochMillis().also { lastClockReading = it }

    /**
     * Raz. Kolejne wywołanie: IllegalStateException("session already started").
     * Nie rzuca dla błędów sesji (SESSION-19).
     */
    suspend fun run(): SessionResult {
        check(lifecycle.compareAndSet(Lifecycle.NOT_STARTED, Lifecycle.RUNNING)) { "session already started" }
        return try {
            coroutineScope {
                startedAt = readClock()
                _state.value = _state.value.copy(phase = SessionPhase.SENDING_COMMAND)
                // Subskrypcja aktywna, zanim wyjdzie SMS (SESSION-03). Wiadomości obsłuży kolejne zadanie.
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        incoming.messages().collect { events.trySend(Event.Message(it)) }
                    } catch (e: CancellationException) {
                        // Anulowana sesja przepuszcza wyjątek; cudzy CancellationException nie kończy sesji.
                        ensureActive()
                    } catch (e: Exception) {
                        // Błędy strumienia są połykane (SESSION-05).
                    }
                }
                launch {
                    val result = try {
                        sender.send(OutgoingSms.CommandText(command.smsText()))
                    } catch (e: CancellationException) {
                        // Anulowana sesja przepuszcza wyjątek; CancellationException z samego portu = błąd wysyłania.
                        ensureActive()
                        SendResult.Failed
                    } catch (e: Exception) {
                        SendResult.Failed
                    }
                    events.trySend(Event.CommandSendResult(result))
                }
                val result = eventLoop()
                coroutineContext.job.cancelChildren()
                result
            }
        } catch (e: CancellationException) {
            // Anulowanie z zewnątrz: obsługa w zadaniu „limit odpowiedzi i anulowanie".
            throw e
        } catch (e: Exception) {
            finish(SessionOutcome.Failed(FailureReason.INTERNAL_ERROR, null))
        }
    }

    private suspend fun eventLoop(): SessionResult {
        for (event in events) {
            when (event) {
                is Event.Message -> Unit // zadanie „przetwarzanie wiadomości"
                is Event.CommandSendResult -> {
                    if (_state.value.phase != SessionPhase.SENDING_COMMAND) continue
                    when (event.result) {
                        SendResult.Sent -> {
                            commandSentAt = readClock()
                            _state.value = _state.value.copy(
                                phase = if (command.definition.requiresCode) {
                                    SessionPhase.AWAITING_CODE_REQUEST
                                } else {
                                    SessionPhase.AWAITING_REPLY
                                },
                            )
                        }
                        SendResult.Failed ->
                            return finish(SessionOutcome.Failed(FailureReason.SEND_FAILED, null))
                    }
                }
            }
        }
        error("event channel closed")
    }

    /** Nieblokujące, bezpieczne z dowolnego wątku. Pełna obsługa – zadanie „anulowanie". */
    fun cancel() {
    }

    /** Kolejność wg design.md: PIN → jeden odczyt zegara → FINISHED → onFinished → wynik. Nie rzuca. */
    private fun finish(outcome: SessionOutcome): SessionResult {
        credentials?.pin?.clear()
        val finishedAt = try {
            clock.nowEpochMillis()
        } catch (e: Exception) {
            lastClockReading
        }
        val result = SessionResult(
            command = command,
            startedAtEpochMillis = startedAt,
            commandSentAtEpochMillis = commandSentAt,
            codeSentAtEpochMillis = null,
            finishedAtEpochMillis = finishedAt,
            outcome = outcome,
            unexpectedMessages = _state.value.unexpectedMessages,
        )
        _state.value = SessionState(SessionPhase.FINISHED, result.unexpectedMessages, result)
        onFinished()
        finishedResult.complete(result)
        return result
    }

    override fun toString(): String = "CommandSession(phase=${_state.value.phase})"
}
