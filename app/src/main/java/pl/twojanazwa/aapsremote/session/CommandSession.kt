package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.definition
import pl.twojanazwa.aapsremote.commands.smsText
import pl.twojanazwa.aapsremote.commands.toExpectedCommand
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsReply
import pl.twojanazwa.aapsremote.parser.AapsReplyParser
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeMark
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
    private val timeouts: SessionTimeouts = SessionTimeouts(),
    /** Czas korutyn dla terminów limitów; w testach testScheduler.timeSource. */
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private enum class Lifecycle { NOT_STARTED, RUNNING, CANCELLED_BEFORE_START, RESULT_TAKEN }

    private sealed interface Event {
        class Message(val sms: IncomingSms) : Event
        class CommandSendResult(val result: SendResult) : Event

        /** Tylko wybudzenie pętli; o limicie decyduje znacznik [deadline]. */
        data object ReplyDeadline : Event
        data object Cancel : Event
    }

    private val _state = MutableStateFlow(SessionState(SessionPhase.NOT_STARTED, emptyList(), null))
    val state: StateFlow<SessionState> = _state

    private val lifecycle = AtomicReference(Lifecycle.NOT_STARTED)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val finishedResult = CompletableDeferred<SessionResult>()

    private var lastClockReading: Long? = null
    private var startedAt: Long? = null
    private var commandSentAt: Long? = null
    private val pendingMessages = mutableListOf<IncomingSms>()

    /** Termin limitu bieżącej fazy (czas korutyn). Zadania CARBS ustawiają go przy zmianie fazy. */
    private var deadline: TimeMark? = null

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
        if (!lifecycle.compareAndSet(Lifecycle.NOT_STARTED, Lifecycle.RUNNING)) {
            // Anulowana przed startem: wynik dopiero po pełnym zakończeniu (PIN wyczyszczony, FINISHED opublikowany).
            check(lifecycle.compareAndSet(Lifecycle.CANCELLED_BEFORE_START, Lifecycle.RESULT_TAKEN)) {
                "session already started"
            }
            return finishedResult.await()
        }
        return try {
            coroutineScope {
                startedAt = readClock()
                _state.value = _state.value.copy(phase = SessionPhase.SENDING_COMMAND)
                val replyDeadline = timeSource.markNow() + timeouts.reply
                deadline = replyDeadline
                launchDeadlineTimer(replyDeadline, Event.ReplyDeadline)
                // Subskrypcja aktywna, zanim wyjdzie SMS (SESSION-03). Wiadomości trafiają do pętli zdarzeń.
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
            // Anulowanie z zewnątrz (SESSION-17). Fazy po kodzie (Unknown(CANCELLED_AFTER_CODE)) dopisze zadanie CARBS.
            if (_state.value.phase != SessionPhase.FINISHED) finish(SessionOutcome.Cancelled)
            throw e
        } catch (e: Exception) {
            finish(SessionOutcome.Failed(FailureReason.INTERNAL_ERROR, null))
        }
    }

    private suspend fun eventLoop(): SessionResult {
        for (event in events) {
            // Pierwszeństwo limitu: przy równej chwili limit wygrywa z każdym zdarzeniem (nie jest ono obsługiwane).
            if (deadline?.hasPassedNow() == true) return finish(timeoutOutcome())
            when (event) {
                Event.ReplyDeadline -> Unit // wybudzenie; termin sprawdzony wyżej
                Event.Cancel -> return finish(SessionOutcome.Cancelled) // tylko fazy przed kodem
                is Event.Message -> {
                    if (_state.value.phase == SessionPhase.SENDING_COMMAND) {
                        pendingMessages += event.sms
                    } else {
                        processMessage(event.sms)?.let { return it }
                    }
                }
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
                            // Wiadomości odłożone w trakcie wysyłania – w kolejności odbioru.
                            val pending = pendingMessages.toList()
                            pendingMessages.clear()
                            for (sms in pending) processMessage(sms)?.let { return it }
                        }
                        SendResult.Failed ->
                            return finish(SessionOutcome.Failed(FailureReason.SEND_FAILED, null))
                    }
                }
            }
        }
        error("event channel closed")
    }

    /**
     * Wiadomość po wysłaniu polecenia. Zwraca wynik, gdy sesja się kończy, inaczej null.
     * Wiadomości sprzed chwili wysłania są ignorowane (SESSION-05). W fazie AWAITING_CODE_REQUEST
     * i pozostałych stan się nie zmienia (obsługa w zadaniach CARBS).
     */
    private fun processMessage(sms: IncomingSms): SessionResult? {
        val sentAt = commandSentAt ?: return null
        if (sms.receivedAtEpochMillis < sentAt) return null
        if (_state.value.phase != SessionPhase.AWAITING_REPLY) return null
        val reply = AapsReplyParser.parse(sms.body, command.toExpectedCommand())
        return finish(
            when (reply) {
                is AapsReply.BgReport, is AapsReply.PumpStatus -> SessionOutcome.Completed(reply, sms.body)
                else -> SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, sms.body)
            },
        )
    }

    /**
     * Wspólny licznik terminów (ReplyDeadline, PreparationDeadline, ConfirmationDeadline): czeka na ten sam
     * znacznik, którego pętla używa w hasPassedNow(), więc nie wybudzi pętli przed terminem, nawet gdy
     * dispatcher wznowi delay odrobinę wcześniej niż źródło czasu. Zdarzenie służy tylko do wybudzenia.
     */
    private fun CoroutineScope.launchDeadlineTimer(mark: TimeMark, event: Event) {
        launch {
            while (!mark.hasPassedNow()) {
                delay(maxOf(1L, (-mark.elapsedNow()).inWholeMilliseconds))
            }
            events.trySend(event)
        }
    }

    /** Wynik limitu bieżącej fazy. Fazy z kodu (PREPARING_CODE, po kodzie) dopisują zadania CARBS. */
    private fun timeoutOutcome(): SessionOutcome = SessionOutcome.TimedOut(_state.value.phase)

    /** Nieblokujące, bezpieczne z dowolnego wątku. Bez efektu po FINISHED. */
    fun cancel() {
        if (lifecycle.compareAndSet(Lifecycle.NOT_STARTED, Lifecycle.CANCELLED_BEFORE_START)) {
            finish(SessionOutcome.Cancelled)
        } else if (lifecycle.get() == Lifecycle.RUNNING) {
            events.trySend(Event.Cancel)
        }
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
