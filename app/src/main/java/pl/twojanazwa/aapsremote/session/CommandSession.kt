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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.definition
import pl.twojanazwa.aapsremote.commands.smsText
import pl.twojanazwa.aapsremote.commands.toExpectedCommand
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.TotpCode
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsPl3426Patterns
import pl.twojanazwa.aapsremote.parser.AapsReply
import pl.twojanazwa.aapsremote.parser.ReplyNormalizer
import pl.twojanazwa.aapsremote.parser.AapsReplyParser
import pl.twojanazwa.aapsremote.parser.ExpectedCommand
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeMark
import kotlin.time.Duration.Companion.milliseconds
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
    private val totp: UsableTotpProvider,
    private val timeouts: SessionTimeouts = SessionTimeouts(),
    /** Czas korutyn dla terminów limitów; w testach testScheduler.timeSource. */
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    /** Tylko do testów BOL-19 (podstawiony parser); domyślnie prawdziwy parser. */
    private val parse: (String, ExpectedCommand) -> AapsReply = AapsReplyParser::parse,
) {
    private enum class Lifecycle { NOT_STARTED, RUNNING, CANCELLED_BEFORE_START, RESULT_TAKEN }

    private sealed interface Event {
        class Message(val sms: IncomingSms) : Event
        class CommandSendResult(val result: SendResult) : Event

        /** Tylko wybudzenie pętli; o limicie decyduje znacznik [deadline]. */
        data object ReplyDeadline : Event
        data object PreparationDeadline : Event
        data object ConfirmationDeadline : Event
        class CodeReady(val code: TotpCode) : Event
        data object CodeError : Event
        class CodeSendResult(val result: SendResult) : Event
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
    private var codeSentAt: Long? = null
    private var codeRequestReceivedAt: Long? = null
    private var codeRequestText: String? = null

    /** true od rozpoczęcia wysyłania kodu: od tej chwili wynik niepewny to Unknown (SESSION-14). */
    private var codeSendStarted = false

    /** Odpowiedź z kodem; zerowana po powrocie z portu i przy zakończeniu sesji. */
    private var codeChars: CharArray? = null
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
            // Anulowanie z zewnątrz (SESSION-17): przed kodem Cancelled, po rozpoczęciu wysyłania kodu Unknown.
            if (_state.value.phase != SessionPhase.FINISHED) finish(cancelOutcome())
            throw e
        } catch (e: Exception) {
            finish(
                if (codeSendStarted) {
                    SessionOutcome.Unknown(UnknownReason.INTERNAL_ERROR, null)
                } else {
                    SessionOutcome.Failed(FailureReason.INTERNAL_ERROR, null)
                },
            )
        }
    }

    private suspend fun CoroutineScope.eventLoop(): SessionResult {
        for (event in events) {
            // Pierwszeństwo limitu: przy równej chwili limit wygrywa z każdym zdarzeniem (nie jest ono obsługiwane).
            if (deadline?.hasPassedNow() == true) return endWith(timeoutOutcome())
            when (event) {
                // Wybudzenia; termin sprawdzony wyżej. Stare liczniki (poprzednie fazy) też trafiają tutaj.
                Event.ReplyDeadline, Event.PreparationDeadline, Event.ConfirmationDeadline -> Unit
                Event.Cancel -> return endWith(cancelOutcome())
                Event.CodeError ->
                    if (_state.value.phase == SessionPhase.PREPARING_CODE) {
                        return endWith(SessionOutcome.Failed(FailureReason.INTERNAL_ERROR, null))
                    }
                is Event.CodeReady ->
                    if (_state.value.phase == SessionPhase.PREPARING_CODE) {
                        startCodeSending(event.code)?.let { return endWith(it) }
                    }
                is Event.CodeSendResult ->
                    if (_state.value.phase == SessionPhase.SENDING_CODE) {
                        when (event.result) {
                            SendResult.Sent -> {
                                codeSentAt = readClock()
                                _state.value = _state.value.copy(phase = SessionPhase.AWAITING_CONFIRMATION)
                                // Wiadomości odłożone w trakcie wysyłania kodu – w kolejności odbioru (SESSION-12).
                                val pending = pendingMessages.toList()
                                pendingMessages.clear()
                                for (sms in pending) processMessage(sms)?.let { return endWith(it) }
                            }
                            // Odłożone wiadomości są porzucane, kod nie jest wysyłany ponownie (SESSION-16).
                            SendResult.Failed -> {
                                pendingMessages.clear()
                                return endWith(SessionOutcome.Unknown(UnknownReason.CODE_SEND_FAILED, null))
                            }
                        }
                    }
                is Event.Message -> {
                    val phase = _state.value.phase
                    if (phase == SessionPhase.SENDING_COMMAND || phase == SessionPhase.SENDING_CODE) {
                        pendingMessages += event.sms
                    } else {
                        processMessage(event.sms)?.let { return endWith(it) }
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
                            for (sms in pending) processMessage(sms)?.let { return endWith(it) }
                        }
                        SendResult.Failed ->
                            return endWith(SessionOutcome.Failed(FailureReason.SEND_FAILED, null))
                    }
                }
            }
        }
        error("event channel closed")
    }

    /**
     * Wiadomość po wysłaniu polecenia. Zwraca wynik, gdy sesja się kończy, inaczej null.
     * Wiadomości sprzed chwili wysłania są ignorowane (SESSION-05). W AWAITING_CODE_REQUEST i PREPARING_CODE
     * (SESSION-11): pierwsza zgodna prośba o kod uruchamia przygotowanie, CarbsDone trafia do unexpectedMessages
     * (SESSION-18), każdy inny wynik kończy sesję. Po kodzie: AWAITING_CONFIRMATION (SESSION-12, SESSION-13); w SENDING_CODE wiadomości są odkładane.
     */
    private fun CoroutineScope.processMessage(sms: IncomingSms): SessionOutcome? {
        val sentAt = commandSentAt ?: return null
        if (sms.receivedAtEpochMillis < sentAt) return null
        when (_state.value.phase) {
            SessionPhase.AWAITING_REPLY -> {
                val reply = parseMatching(sms)
                return when (reply) {
                    // Typ raportu zgodny z poleceniem gwarantuje amountMatches (PROF-11).
                    is AapsReply.BgReport, is AapsReply.PumpStatus,
                    is AapsReply.ProfileStatusReport, is AapsReply.ProfileListReport,
                    -> SessionOutcome.Completed(reply, sms.body)
                    else -> SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, sms.body)
                }
            }
            SessionPhase.AWAITING_CODE_REQUEST, SessionPhase.PREPARING_CODE -> {
                val reply = parseMatching(sms)
                val preparing = _state.value.phase == SessionPhase.PREPARING_CODE
                return when (reply) {
                    is AapsReply.MatchingCodeRequest ->
                        if (preparing) {
                            SessionOutcome.Failed(FailureReason.UNEXPECTED_REPLY, sms.body)
                        } else {
                            startPreparingCode(sms)
                            null
                        }
                    is AapsReply.CommandMismatch -> SessionOutcome.Failed(FailureReason.AMOUNT_MISMATCH, sms.body)
                    // Potwierdzenie sprzed prośby o kod nie wpływa na fazę ani limity (SESSION-11, SESSION-18).
                    is AapsReply.MatchingDone -> addUnexpected(sms)
                    AapsReply.WrongCode -> SessionOutcome.Failed(FailureReason.UNEXPECTED_REPLY, sms.body)
                    // Unrecognized (także raporty BG/PUMP niezgodne z poleceniem, patrz amountMatches).
                    else -> SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, sms.body)
                }
            }
            // Po kodzie (SESSION-12, SESSION-13). SENDING_CODE nie trafia tu: wiadomości są wtedy odkładane.
            SessionPhase.AWAITING_CONFIRMATION -> {
                val reply = parseMatching(sms)
                return when (reply) {
                    is AapsReply.MatchingDone ->
                        if (sms.receivedAtEpochMillis >= checkNotNull(codeSentAt)) {
                            confirmed(reply, sms.body)
                        } else {
                            addUnexpected(sms)
                        }
                    AapsReply.WrongCode -> SessionOutcome.Failed(FailureReason.WRONG_CODE, sms.body)
                    is AapsReply.CommandMismatch ->
                        SessionOutcome.Unknown(UnknownReason.AMOUNT_MISMATCH, sms.body)
                    is AapsReply.MatchingCodeRequest -> SessionOutcome.Unknown(UnknownReason.UNEXPECTED_REPLY, sms.body)
                    else -> SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, sms.body)
                }
            }
            else -> return null
        }
    }

    /** BOL-19: prośba o kod / potwierdzenie innego rodzaju niż polecenie sesji = Unrecognized. */
    private fun parseMatching(sms: IncomingSms): AapsReply {
        val reply = parse(sms.body, command.toExpectedCommand())
        val matches = when (reply) {
            is AapsReply.MatchingCodeRequest -> ReplyMatch.isCodeRequestFor(command.id, reply)
            is AapsReply.MatchingDone -> ReplyMatch.isDoneFor(command.id, reply)
            else -> true
        } && amountMatches(reply)
        return if (matches) reply else AapsReply.Unrecognized(sms.body)
    }

    /** Druga linia obrony (BOL-10, BOL-11): ilość w prośbie/potwierdzeniu musi być równa wysłanej, niezależnie od parsera. */
    private fun amountMatches(reply: AapsReply): Boolean {
        val sent = command
        return when (reply) {
            is AapsReply.BolusCodeRequest -> sent is Command.Bolus && reply.centiUnits == sent.centiUnits
            is AapsReply.BolusDone -> sent is Command.Bolus && reply.centiUnits == sent.centiUnits
            is AapsReply.CodeRequest -> sent is Command.Carbs && reply.grams == sent.grams
            is AapsReply.CarbsDone -> sent is Command.Carbs && reply.grams == sent.grams
            // PROF-11, PROF-12: raport tylko dla własnego polecenia; prośba o kod tylko z dokładnie tą nazwą i procentem.
            is AapsReply.ProfileStatusReport -> sent is Command.ProfileStatus
            is AapsReply.ProfileListReport -> sent is Command.ProfileList
            is AapsReply.ProfileCodeRequest -> sent is Command.ProfileSwitch && profileRequestMatches(reply.text, sent)
            is AapsReply.ProfileSwitchMismatch -> sent is Command.ProfileSwitch
            is AapsReply.BgReport -> sent is Command.Bg
            is AapsReply.PumpStatus -> sent is Command.Pump
            is AapsReply.BolusAmountMismatch, is AapsReply.CarbsAmountMismatch,
            AapsReply.WrongCode, is AapsReply.Unrecognized,
            -> true
        }
    }

    /** PROF-12: niezależne od parsera porównanie nazwy i procentu (wielkość liter znacząca) z treścią prośby. */
    private fun profileRequestMatches(text: String, sent: Command.ProfileSwitch): Boolean {
        val n = ReplyNormalizer.normalize(text) ?: return false
        val g = AapsPl3426Patterns.PROFILE_CODE_REQUEST.matchEntire(n)?.groupValues ?: return false
        return g[1] == sent.profileName && g[2].toIntOrNull() == sent.percent
    }

    /** Tylko dla odpowiedzi zgodnej z poleceniem (po [parseMatching]). */
    private fun confirmed(reply: AapsReply.MatchingDone, text: String): SessionOutcome = when (reply) {
        is AapsReply.CarbsDone -> SessionOutcome.CarbsConfirmed(reply.grams, text)
        is AapsReply.BolusDone -> SessionOutcome.BolusConfirmed(reply.centiUnits, text)
    }

    /** Wiadomość nieoczekiwana (SESSION-18): trafia do wyniku, nie zmienia fazy ani limitów. Zawsze null. */
    private fun addUnexpected(sms: IncomingSms): SessionOutcome? {
        _state.value = _state.value.copy(unexpectedMessages = _state.value.unexpectedMessages + sms)
        return null
    }

    /** Prośba o kod: wyłącza limit odpowiedzi, zaczyna limit przygotowania i czeka na użyteczny kod TOTP. */
    private fun CoroutineScope.startPreparingCode(sms: IncomingSms) {
        val secret = checkNotNull(credentials).secret
        codeRequestReceivedAt = sms.receivedAtEpochMillis
        codeRequestText = sms.body
        _state.value = _state.value.copy(phase = SessionPhase.PREPARING_CODE)
        val preparationDeadline = timeSource.markNow() + SessionLimits.CODE_PREPARATION_TIMEOUT_MILLIS.milliseconds
        deadline = preparationDeadline
        launchDeadlineTimer(preparationDeadline, Event.PreparationDeadline)
        launch {
            val event = try {
                Event.CodeReady(totp.awaitUsableCode(secret))
            } catch (e: CancellationException) {
                ensureActive() // cudzy CancellationException = błąd dostawcy
                Event.CodeError
            } catch (e: Exception) {
                Event.CodeError
            }
            events.trySend(event)
        }
    }

    /**
     * Kod gotowy: kontrola wieku prośby, złożenie odpowiedzi, czyszczenie PIN-u, start wysyłania kodu.
     * Zwraca wynik, gdy kod nie może być wysłany.
     */
    private fun CoroutineScope.startCodeSending(code: TotpCode): SessionOutcome? {
        val age = readClock() - checkNotNull(codeRequestReceivedAt)
        if (age < 0 || age > SessionLimits.CODE_REQUEST_MAX_AGE_MILLIS) {
            return SessionOutcome.Failed(FailureReason.CODE_REQUEST_EXPIRED, codeRequestText)
        }
        val pin = checkNotNull(credentials).pin
        val chars = CharArray(CODE_DIGITS + pin.length)
        codeChars = chars
        val pinChars = pin.copyChars()
        try {
            for (i in 0 until CODE_DIGITS) chars[i] = code.value[i]
            pinChars.copyInto(chars, CODE_DIGITS)
        } finally {
            pinChars.fill('\u0000')
            pin.clear()
        }
        codeSendStarted = true
        _state.value = _state.value.copy(phase = SessionPhase.SENDING_CODE)
        val confirmationDeadline = timeSource.markNow() + timeouts.confirmation
        deadline = confirmationDeadline
        launchDeadlineTimer(confirmationDeadline, Event.ConfirmationDeadline)
        launch {
            val result = try {
                sender.send(OutgoingSms.CodeReply(chars))
            } catch (e: CancellationException) {
                ensureActive()
                SendResult.Failed
            } catch (e: Exception) {
                SendResult.Failed
            } finally {
                chars.fill('\u0000')
            }
            events.trySend(Event.CodeSendResult(result))
        }
        return null
    }

    /** Anulowanie: przed rozpoczęciem wysyłania kodu Cancelled, potem Unknown (SESSION-17). */
    private fun cancelOutcome(): SessionOutcome =
        if (codeSendStarted) SessionOutcome.Unknown(UnknownReason.CANCELLED_AFTER_CODE, null) else SessionOutcome.Cancelled

    /**
     * Każde zakończenie z pętli: najpierw anulowanie korutyn potomnych (zawieszone send nie dokończy się
     * po FINISHED na wielowątkowym dispatcherze), potem finish().
     */
    private suspend fun endWith(outcome: SessionOutcome): SessionResult {
        currentCoroutineContext().job.cancelChildren()
        return finish(outcome)
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

    /** Wynik limitu bieżącej fazy. */
    private fun timeoutOutcome(): SessionOutcome = when (_state.value.phase) {
        SessionPhase.PREPARING_CODE -> SessionOutcome.Failed(FailureReason.CODE_PREPARATION_TIMEOUT, null)
        SessionPhase.SENDING_CODE, SessionPhase.AWAITING_CONFIRMATION ->
            SessionOutcome.Unknown(UnknownReason.CONFIRMATION_TIMEOUT, null)
        else -> SessionOutcome.TimedOut(_state.value.phase)
    }

    /** Nieblokujące, bezpieczne z dowolnego wątku. Bez efektu po FINISHED. */
    fun cancel() {
        if (lifecycle.compareAndSet(Lifecycle.NOT_STARTED, Lifecycle.CANCELLED_BEFORE_START)) {
            finish(SessionOutcome.Cancelled)
        } else if (lifecycle.get() == Lifecycle.RUNNING && _state.value.phase != SessionPhase.FINISHED) {
            events.trySend(Event.Cancel)
        }
    }

    /** Kolejność wg design.md: PIN → jeden odczyt zegara → FINISHED → onFinished → wynik. Nie rzuca. */
    private fun finish(outcome: SessionOutcome): SessionResult {
        credentials?.pin?.clear()
        codeChars?.fill('\u0000')
        val finishedAt = try {
            clock.nowEpochMillis()
        } catch (e: Exception) {
            lastClockReading
        }
        val result = SessionResult(
            command = command,
            startedAtEpochMillis = startedAt,
            commandSentAtEpochMillis = commandSentAt,
            codeSentAtEpochMillis = codeSentAt,
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

    private companion object {
        const val CODE_DIGITS = 6
    }
}
