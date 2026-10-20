# Design 004: Sesja polecenia

Dotyczy: [spec.md](spec.md)

## Przegląd

Funkcja leży w pakiecie `pl.twojanazwa.aapsremote.session` i jest czystym Kotlinem/JVM z `kotlinx.coroutines` (bez `android.*`), zgodnie z [AGENTS.md](../../../AGENTS.md#architektura). Jedyny typ poza `session` to `AapsPin` w `pl.twojanazwa.aapsremote.security` (SESSION-02).

Zależności (kierunek): `session` → `commands` (`Command`, `smsText()`, `toExpectedCommand()`, `definition.requiresCode`), `session` → `parser` (`AapsReplyParser`, `AapsReply`), `session` → `otp` (`UsableTotpProvider`, `EpochClock`, `TotpSecret`), `session` → `security` (`AapsPin`). Żaden z tych pakietów nie importuje `session`. `sms` (przyszła spec) implementuje porty `SmsSender` i `IncomingSmsSource`; `ui` obserwuje `SessionCoordinator.activeSession` i `CommandSession.state`; `data` zapisuje `SessionResult`.

Składniki:

- `AapsPin` – PIN w pamięci z czyszczeniem (SESSION-02).
- `SessionTimeouts`, `SessionLimits` – limity czasu (SESSION-01, SESSION-10).
- Porty: `SmsSender`, `OutgoingSms`, `SendResult`, `IncomingSmsSource`, `IncomingSms` (SESSION-03 – SESSION-05, SESSION-09).
- `CodeCredentials` – PIN i sekret dla polecenia z kodem (SESSION-02).
- `SessionPhase`, `SessionState`, `SessionResult`, `SessionOutcome`, `FailureReason`, `UnknownReason` – stan i wynik (SESSION-21).
- `CommandSession` – maszyna stanów jednego polecenia (SESSION-03 – SESSION-21, SESSION-23).
- `SessionCoordinator`, `StartResult` – jedno aktywne polecenie (SESSION-22).

## Interfejsy

```kotlin
package pl.twojanazwa.aapsremote.security

/** PIN AAPS tylko w pamięci (SESSION-02). Metody synchronizowane – clear() może przyjść z innego wątku. */
class AapsPin private constructor(private val chars: CharArray) {
    val length: Int
    val isCleared: Boolean

    /** Kopia znaków; wywołujący zeruje ją po użyciu. IllegalStateException("PIN cleared") po clear(). */
    fun copyChars(): CharArray

    /** Zeruje znaki; idempotentne. */
    fun clear()

    override fun toString(): String = "AapsPin(***)"

    companion object {
        /** Kopiuje [source] i zeruje go. IllegalArgumentException("PIN must not be empty") dla pustej tablicy. */
        fun fromChars(source: CharArray): AapsPin
    }
}
```

```kotlin
package pl.twojanazwa.aapsremote.session

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/** SESSION-01. require(reply.isPositive() && confirmation.isPositive()) ze stałym komunikatem. */
data class SessionTimeouts(
    val reply: Duration = 3.minutes,
    val confirmation: Duration = 6.minutes,
)

object SessionLimits {
    /** A1: czas na kod w AAPS (Constants.SMS_CONFIRM_TIMEOUT) – informacyjnie, do opisu marginesu. */
    const val AAPS_CODE_VALIDITY_MILLIS: Long = 300_000

    /** SESSION-10: maks. wiek prośby o kod w chwili rozpoczęcia wysyłania kodu. */
    const val CODE_REQUEST_MAX_AGE_MILLIS: Long = 240_000

    /** SESSION-10: maks. czas fazy PREPARING_CODE (czas korutyn). */
    const val CODE_PREPARATION_TIMEOUT_MILLIS: Long = 10_000
}

// ---- Porty (implementuje `sms`) ----

/**
 * Wiadomość od numeru AAPS, już po filtrze numeru i sklejeniu części.
 * receivedAtEpochMillis: odczyt tego samego EpochClock co sesja w chwili odbioru (nie znacznik centrum SMS) – K1.
 * Każda wiadomość dostarczana najwyżej raz (deduplikacja) – K2; kontrakt portów w spec.md.
 */
data class IncomingSms(val body: String, val receivedAtEpochMillis: Long) {
    override fun toString(): String = "IncomingSms(***, receivedAtEpochMillis=$receivedAtEpochMillis)"
}

/**
 * Gorący strumień (bez powtórek starych wiadomości). Wiadomość odebrana po rozpoczęciu
 * zbierania jest dostarczana każdemu aktywnemu kolektorowi.
 */
fun interface IncomingSmsSource {
    fun messages(): Flow<IncomingSms>
}

sealed interface OutgoingSms {
    /** command.smsText(). toString() bez treści (ilość CARBS). */
    class CommandText(val text: String) : OutgoingSms {
        override fun toString(): String = "OutgoingSms.CommandText(***)"
    }

    /**
     * "<6 cyfr><PIN>". Tablica należy do sesji i jest zerowana po powrocie z send();
     * port nie przechowuje jej ani kopii (w tym String dla SmsManager) po powrocie.
     */
    class CodeReply(val chars: CharArray) : OutgoingSms {
        override fun toString(): String = "OutgoingSms.CodeReply(***)"
    }
}

sealed interface SendResult {
    data object Sent : SendResult
    /** Szczegóły błędu (np. brak zasięgu) – do dodania zmianą spec przy specyfikacji `sms`. */
    data object Failed : SendResult
}

/** Zawiesza się do wyniku wysłania (SENT PendingIntent). Anulowalne. */
fun interface SmsSender {
    suspend fun send(message: OutgoingSms): SendResult
}

// ---- Dane wejściowe ----

/** SESSION-02. Wymagane dokładnie dla command.definition.requiresCode == true. */
class CodeCredentials(val pin: AapsPin, val secret: TotpSecret) {
    override fun toString(): String = "CodeCredentials(***)"
}

// ---- Stan i wynik ----

enum class SessionPhase {
    NOT_STARTED, SENDING_COMMAND, AWAITING_REPLY, AWAITING_CODE_REQUEST,
    PREPARING_CODE, SENDING_CODE, AWAITING_CONFIRMATION, FINISHED,
}

/** result != null wtedy i tylko wtedy, gdy phase == FINISHED. */
data class SessionState(
    val phase: SessionPhase,
    val unexpectedMessages: List<IncomingSms>,
    val result: SessionResult?,
) {
    override fun toString(): String =
        "SessionState(phase=$phase, unexpected=${unexpectedMessages.size}, result=$result)"
}

data class SessionResult(
    val command: Command,
    /** null tylko, gdy zegar nie zwrócił w sesji żadnej poprawnej wartości (SESSION-19). */
    val startedAtEpochMillis: Long?,
    val commandSentAtEpochMillis: Long?,
    val codeSentAtEpochMillis: Long?,
    /** Odczyt przy zakończeniu albo ostatni poprawny odczyt, gdy zegar rzucił; null jak wyżej (SESSION-19). */
    val finishedAtEpochMillis: Long?,
    val outcome: SessionOutcome,
    val unexpectedMessages: List<IncomingSms>,
) {
    override fun toString(): String =
        "SessionResult(command=$command, outcome=$outcome, unexpected=${unexpectedMessages.size})"
}

sealed interface SessionOutcome {
    /** BG/PUMP. reply to AapsReply.BgReport albo AapsReply.PumpStatus. */
    data class Completed(val reply: AapsReply, val text: String) : SessionOutcome {
        override fun toString(): String = "Completed(***)"
    }

    /** „AAPS potwierdził wprowadzenie X g” – nie dowód wykonania polecenia z aplikacji (A5). */
    data class CarbsConfirmed(val grams: Int, val text: String) : SessionOutcome {
        override fun toString(): String = "CarbsConfirmed(***)"
    }

    data class Failed(val reason: FailureReason, val text: String?) : SessionOutcome {
        override fun toString(): String = "Failed(reason=$reason)"
    }

    /** „AAPS nie odpowiada”. phase: SENDING_COMMAND, AWAITING_REPLY albo AWAITING_CODE_REQUEST. */
    data class TimedOut(val phase: SessionPhase) : SessionOutcome

    /** „Nieznany – sprawdź w AAPS”. Tylko po rozpoczęciu wysyłania kodu (SESSION-14). */
    data class Unknown(val reason: UnknownReason, val text: String?) : SessionOutcome {
        override fun toString(): String = "Unknown(reason=$reason)"
    }

    data object Cancelled : SessionOutcome
}

enum class FailureReason {
    SEND_FAILED, UNRECOGNIZED_REPLY, AMOUNT_MISMATCH, UNEXPECTED_REPLY, WRONG_CODE,
    CODE_REQUEST_EXPIRED, CODE_PREPARATION_TIMEOUT, INTERNAL_ERROR,
}

enum class UnknownReason {
    CONFIRMATION_TIMEOUT, CANCELLED_AFTER_CODE, CODE_SEND_FAILED,
    UNRECOGNIZED_REPLY, AMOUNT_MISMATCH, UNEXPECTED_REPLY, INTERNAL_ERROR,
}

// ---- Sesja ----

/**
 * Jedno polecenie. Konstruktor: IllegalArgumentException, gdy (credentials != null) != requiresCode;
 * przed rzuceniem czyści credentials?.pin. clock i totp muszą używać tego samego zegara.
 */
class CommandSession(
    val command: Command,
    credentials: CodeCredentials?,
    sender: SmsSender,
    incoming: IncomingSmsSource,
    clock: EpochClock,
    totp: UsableTotpProvider,
    timeouts: SessionTimeouts = SessionTimeouts(),
    /** Czas korutyn dla terminów limitów; w testach testScheduler.timeSource. */
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    val state: StateFlow<SessionState>

    /**
     * Raz. Po cancel() w NOT_STARTED zwraca od razu Cancelled (bez subskrypcji, limitu i wysyłki).
     * Kolejne wywołanie: IllegalStateException("session already started"). Nie rzuca dla błędów sesji (SESSION-19).
     */
    suspend fun run(): SessionResult

    /** Nieblokujące, bezpieczne z dowolnego wątku; w NOT_STARTED kończy sesję od razu; bez efektu w FINISHED (SESSION-17). */
    fun cancel()

    override fun toString(): String // "CommandSession(phase=…)"
}

// ---- Koordynator ----

/** Jeden na aplikację (singleton w Hilt przy integracji z ui). */
class SessionCoordinator(
    sender: SmsSender,
    incoming: IncomingSmsSource,
    clock: EpochClock,
    totp: UsableTotpProvider,
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    /** null, gdy brak aktywnej sesji (SESSION-22). */
    val activeSession: StateFlow<CommandSession?>

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
    ): StartResult
}

sealed interface StartResult {
    class Started(val session: CommandSession) : StartResult
    data object Busy : StartResult
}
```

Sygnatury są wiążące dla nazw i typów publicznych; ciała i prywatne pomocniki zależą od wykonawcy. `TimedOut` i `Cancelled` nie niosą danych, więc domyślny `toString()` jest dopuszczalny.

## Model danych i stany

### Pętla zdarzeń

`run()` działa w `coroutineScope` jako pętla po kanale zdarzeń `Channel<Event>(UNLIMITED)` utworzonym w konstruktorze. Wszystkie decyzje (w tym „rozpocznij wysyłanie kodu” i „anuluj”) zapadają w tej jednej pętli, więc są wzajemnie wykluczające się bez blokad. Zdarzenia:

| Zdarzenie | Źródło |
| --- | --- |
| `Message(sms)` | kolektor `incoming.messages()` uruchomiony z `CoroutineStart.UNDISPATCHED` przed wywołaniem portu wysyłania (subskrypcja aktywna, zanim wyjdzie SMS – SESSION-03); `catch {}` połyka błędy strumienia (SESSION-05) |
| `CommandSendResult(result)` | korutyna potomna wywołująca `sender.send(CommandText)`; wyjątek → `Failed` (SESSION-04) |
| `CodeReady(code)` / `CodeError` | korutyna potomna `totp.awaitUsableCode(secret)` |
| `CodeSendResult(result)` | korutyna potomna `sender.send(CodeReply)`; `finally { chars.fill('\u0000') }` (SESSION-09) |
| `ReplyDeadline` | `delay(timeouts.reply)` od wejścia w `SENDING_COMMAND` |
| `PreparationDeadline` | `delay(10 000 ms)` od wejścia w `PREPARING_CODE` |
| `ConfirmationDeadline` | `delay(timeouts.confirmation)` od rozpoczęcia wysyłania kodu |
| `Cancel` | `cancel()` przez `trySend` |
| `InternalError` | wyjątek (nie `CancellationException`) w pętli lub w korutynie potomnej poza obsłużonymi przypadkami (SESSION-19) |

**Pierwszeństwo limitu.** Przy wejściu w fazę z limitem pętla zapisuje termin jako znacznik `deadline = timeSource.markNow() + limit` (`ReplyDeadline`: przy wejściu w `SENDING_COMMAND`; `PreparationDeadline`: w `PREPARING_CODE`; `ConfirmationDeadline`: przy rozpoczęciu wysyłania kodu). Przed obsługą każdego zdarzenia innego niż zdarzenie limitu (`Message`, `CommandSendResult`, `CodeReady`, `CodeSendResult`, `Cancel`) pętla sprawdza `timeSource.markNow() >= deadline` bieżącej fazy; jeśli tak, kończy sesję wynikiem limitu i zdarzenia nie obsługuje. Dzięki temu wynik nie zależy od kolejności, w jakiej korutyny potomne wstawiły zdarzenia do kanału w tej samej chwili. Korutyny `delay(...)` dla limitów służą tylko do wybudzenia pętli, gdy nie przychodzą inne zdarzenia. W testach `timeSource = testScheduler.timeSource`, więc termin i `delay` liczą ten sam czas wirtualny.

Liczniki czasu i wysyłanie działają w korutynach potomnych, żeby pętla mogła w tym czasie przetwarzać `Cancel` i wiadomości. Po przejściu do `FINISHED` pętla kończy się, a `coroutineScope` anuluje potomków (kolektor, liczniki, zawieszone `send`) – SESSION-20.

Wiadomości w fazach `SENDING_COMMAND` i `SENDING_CODE` są odkładane do listy i przetwarzane po zdarzeniu wyniku wysyłania, w kolejności odbioru (SESSION-05, SESSION-12, SESSION-13). Przy `Failed` dla kodu lista jest porzucana (SESSION-16).

Wiadomość przetwarzana: `if (sms.receivedAtEpochMillis < commandSentAt) ignore` → `AapsReplyParser.parse(sms.body, command.toExpectedCommand())` → tabela poniżej. Wyniki z `Failed`/`Unknown`/`Completed`/`CarbsConfirmed` biorą `text = sms.body` (także dla `WrongCode`, który w parserze nie ma pola `text`).

### Zakończenie

Każdy odczyt zegara przechodzi przez pomocnika, który zapamiętuje ostatnią poprawną wartość (`lastClockReading: Long?`). Jedna funkcja `finish(outcome)` (niezawieszająca): (1) `credentials?.pin?.clear()`, (2) jeden odczyt zegara jako `finishedAt` w `try`; wyjątek → `finishedAt = lastClockReading` (może być `null`), bez ponownego odczytu i bez zmiany `outcome`, (3) zbudowanie `SessionResult`, (4) wywołanie wewnętrznego `onFinished` (koordynator zwalnia `activeSession`), (5) publikacja `SessionState(FINISHED, …, result)`. Kolejność gwarantuje, że obserwator `FINISHED` widzi wyczyszczony PIN i pustą `activeSession` (SESSION-19, SESSION-22).

Anulowanie z zewnątrz: `catch (e: CancellationException)` wokół pętli → `finish(Cancelled)` albo `finish(Unknown(CANCELLED_AFTER_CODE))` wg fazy → `throw e` (SESSION-17). Inny wyjątek (także z odczytu zegara w pętli, np. `startedAt`, `commandSentAt`, wiek prośby) → `finish(Failed(INTERNAL_ERROR))` albo `finish(Unknown(INTERNAL_ERROR))` wg fazy, `run()` zwraca wynik (SESSION-19). Ponieważ `finish()` sam nie rzuca przy błędzie zegara, ta ścieżka zawsze kończy się `FINISHED`.

### Start i anulowanie przed startem

`AtomicReference<Lifecycle>` z wartościami `NOT_STARTED`, `RUNNING`, `CANCELLED_BEFORE_START`, `RESULT_TAKEN`:

- `cancel()`: `compareAndSet(NOT_STARTED, CANCELLED_BEFORE_START)` → od razu `finish(Cancelled)` w wątku wywołującego (pętla jeszcze nie istnieje, więc nie ma wyścigu). W przeciwnym razie `events.trySend(Cancel)`.
- `run()`: `compareAndSet(NOT_STARTED, RUNNING)` → dopiero wtedy odczyt `startedAt`, start kolektora, termin limitu i wysyłka. Gdy CAS się nie uda: `compareAndSet(CANCELLED_BEFORE_START, RESULT_TAKEN)` → zwrot wyniku `Cancelled` bez subskrypcji, limitu i wysyłki; inaczej `IllegalStateException`.

Jeden atomowy przełącznik rozstrzyga wyścig `cancel()` / `run()`: albo anulowanie przed startem (zero SMS-ów), albo zdarzenie `Cancel` w kanale obsłużone w `SENDING_COMMAND` (SESSION-17).

### Tabela przejść

Fazy „przed kodem” = `NOT_STARTED` … `PREPARING_CODE`; „po kodzie” = `SENDING_CODE`, `AWAITING_CONFIRMATION`. „Treść” = `sms.body`.

| Stan | Zdarzenie | Nowy stan | Akcja | Wymaganie |
| --- | --- | --- | --- | --- |
| `NOT_STARTED` | `run()` (CAS `NOT_STARTED` → `RUNNING`) | `SENDING_COMMAND` | `startedAt = clock`; start kolektora; termin i start `ReplyDeadline`; `send(CommandText(smsText()))` | SESSION-03, SESSION-07 |
| `NOT_STARTED` | `cancel()` (CAS `NOT_STARTED` → `CANCELLED_BEFORE_START`) | `FINISHED` | `finish(Cancelled)` od razu; pierwszy `run()` zwraca ten wynik bez subskrypcji, limitu i wysyłania | SESSION-17 |
| fazy z limitem | dowolne zdarzenie przy `markNow() >= deadline` | `FINISHED` | wynik limitu bieżącej fazy; zdarzenie nieobsłużone | SESSION-07, SESSION-10, SESSION-15 |
| `SENDING_COMMAND` | `Message` | – | odłóż | SESSION-05 |
| `SENDING_COMMAND` | `CommandSendResult(Sent)` | `AWAITING_REPLY` / `AWAITING_CODE_REQUEST` | `commandSentAt = clock`; przetwórz odłożone | SESSION-03, SESSION-05 |
| `SENDING_COMMAND` | `CommandSendResult(Failed)` | `FINISHED` | `Failed(SEND_FAILED)` | SESSION-04 |
| `SENDING_COMMAND`, `AWAITING_REPLY`, `AWAITING_CODE_REQUEST` | `ReplyDeadline` | `FINISHED` | `TimedOut(bieżąca faza)` | SESSION-07 |
| `AWAITING_REPLY` | `BgReport` / `PumpStatus` | `FINISHED` | `Completed(reply, treść)` | SESSION-06 |
| `AWAITING_REPLY` | inny wynik | `FINISHED` | `Failed(UNRECOGNIZED_REPLY, treść)` | SESSION-06 |
| `AWAITING_CODE_REQUEST` | `CodeRequest` | `PREPARING_CODE` | `codeRequestReceivedAt = sms.receivedAt`; anuluj `ReplyDeadline`; start `PreparationDeadline`; start `awaitUsableCode` | SESSION-08 |
| `AWAITING_CODE_REQUEST`, `PREPARING_CODE` | `CarbsAmountMismatch` | `FINISHED` | `Failed(AMOUNT_MISMATCH, treść)` | SESSION-11 |
| `AWAITING_CODE_REQUEST`, `PREPARING_CODE` | `CarbsDone` | bez zmian | dopisz do nieoczekiwanych | SESSION-11, SESSION-18 |
| `AWAITING_CODE_REQUEST`, `PREPARING_CODE` | `WrongCode` | `FINISHED` | `Failed(UNEXPECTED_REPLY, treść)` | SESSION-11 |
| `AWAITING_CODE_REQUEST`, `PREPARING_CODE` | `Unrecognized` / niemożliwy | `FINISHED` | `Failed(UNRECOGNIZED_REPLY, treść)` | SESSION-11 |
| `PREPARING_CODE` | `CodeRequest` | `FINISHED` | `Failed(UNEXPECTED_REPLY, treść)` | SESSION-11 |
| `PREPARING_CODE` | `CodeReady(code)`, wiek ∉ [0, 240 000] | `FINISHED` | `Failed(CODE_REQUEST_EXPIRED, treść prośby)` | SESSION-10 |
| `PREPARING_CODE` | `CodeReady(code)`, wiek ∈ [0, 240 000] | `SENDING_CODE` | złóż `chars = code.value + pin.copyChars()`; wyzeruj kopię PIN-u; `pin.clear()`; start `ConfirmationDeadline`; `send(CodeReply(chars))` | SESSION-09, SESSION-10, SESSION-15 |
| `PREPARING_CODE` | `PreparationDeadline` | `FINISHED` | `Failed(CODE_PREPARATION_TIMEOUT)` | SESSION-10 |
| przed kodem (po `run()`) | `Cancel` | `FINISHED` | `Cancelled` | SESSION-17 |
| przed kodem | `InternalError` | `FINISHED` | `Failed(INTERNAL_ERROR)` | SESSION-19 |
| `SENDING_CODE` | `Message` | – | odłóż | SESSION-13 |
| `SENDING_CODE` | `CodeSendResult(Sent)` | `AWAITING_CONFIRMATION` | `codeSentAt = clock`; przetwórz odłożone | SESSION-09, SESSION-12 |
| `SENDING_CODE` | `CodeSendResult(Failed)` | `FINISHED` | `Unknown(CODE_SEND_FAILED)`; porzuć odłożone | SESSION-16 |
| `AWAITING_CONFIRMATION` | `CarbsDone`, `receivedAt ≥ codeSentAt` | `FINISHED` | `CarbsConfirmed(grams, treść)` | SESSION-12 |
| `AWAITING_CONFIRMATION` | `CarbsDone`, `receivedAt < codeSentAt` | bez zmian | dopisz do nieoczekiwanych | SESSION-12, SESSION-18 |
| `AWAITING_CONFIRMATION` | `WrongCode` | `FINISHED` | `Failed(WRONG_CODE, treść)` | SESSION-13 |
| `AWAITING_CONFIRMATION` | `Unrecognized` / niemożliwy | `FINISHED` | `Unknown(UNRECOGNIZED_REPLY, treść)` | SESSION-13 |
| `AWAITING_CONFIRMATION` | `CarbsAmountMismatch` | `FINISHED` | `Unknown(AMOUNT_MISMATCH, treść)` | SESSION-13 |
| `AWAITING_CONFIRMATION` | `CodeRequest` | `FINISHED` | `Unknown(UNEXPECTED_REPLY, treść)` | SESSION-13 |
| po kodzie | `ConfirmationDeadline` | `FINISHED` | `Unknown(CONFIRMATION_TIMEOUT)` | SESSION-15 |
| po kodzie | `Cancel` | `FINISHED` | `Unknown(CANCELLED_AFTER_CODE)` | SESSION-17 |
| po kodzie | `InternalError` | `FINISHED` | `Unknown(INTERNAL_ERROR)` | SESSION-19 |
| `FINISHED` | dowolne | `FINISHED` | nic | SESSION-17, SESSION-20 |

Składanie kodu: `CharArray(6 + pin.length)`; `code.value` (6 cyfr ASCII ze Spec 001) kopiowane znak po znaku na pozycje 0–5, znaki PIN-u na 6…; bez `String` z PIN-em po stronie sesji.

### Koordynator

Stan: `AtomicBoolean reserved` (miejsce zajęte od rezerwacji do zakończenia sesji) i `MutableStateFlow<CommandSession?>(null)` dla `ui`. `start`:

1. `reserved.compareAndSet(false, true)`; porażka → `credentials?.pin?.clear()`, `Busy` – bez tworzenia sesji i bez walidacji danych (brak wyjątku dla niepoprawnego zestawu polecenie/dane).
2. Utworzenie `CommandSession` w `try`; wyjątek → `credentials?.pin?.clear()` (konstruktor też czyści), `reserved.set(false)`, wyjątek dalej.
3. `active.value = session`; `job = scope.launch { session.run() }`.
4. Zwolnienie miejsca w jednej funkcji `release(session)`: jeśli `active.compareAndSet(session, null)` – `reserved.set(false)`. Wywołują ją `onFinished` sesji (przed publikacją `FINISHED`) i `job.invokeOnCompletion { credentials?.pin?.clear(); release(session) }` – ta druga zwalnia miejsce także, gdy zakres był już anulowany i `run()` nie wystartował.

Rezerwacja przed utworzeniem sesji sprawia, że przy zajętym miejscu wynik zawsze brzmi „zajęte”, niezależnie od poprawności danych wejściowych (SESSION-22).

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| **Chwila wysłania = wynik portu „wysłano”** (filtr `receivedAt < commandSentAt`) | chwila wywołania portu | Ostrzejszy filtr: wiadomość odebrana, zanim nasz SMS opuścił telefon, nie może być odpowiedzią na niego (np. spóźniona prośba o kod z anulowanej wcześniej sesji). Odpowiedź AAPS wymaga doręczenia polecenia, przetworzenia i doręczenia odpowiedzi, więc zawsze przychodzi po potwierdzeniu wysłania; ryzyko utraty prawdziwej odpowiedzi kończy się bezpiecznie (`TimedOut`). Ta sama reguła dla kodu i potwierdzenia (`codeSentAt`). |
| `receivedAtEpochMillis` stemplowane przez `sms` tym samym zegarem | znacznik centrum SMS (`SmsMessage.getTimestampMillis`) | Znacznik operatora pochodzi z innego zegara i bywa przesunięty; porównanie z `commandSentAt` wymaga jednego zegara. Wymóg dla spec `sms`. |
| **Margines wieku prośby: 240 000 ms (4 min)** | 5 min; 4 min 50 s; brak kontroli | AAPS liczy 5 min od utworzenia prośby (przed jej wysłaniem), a sesja zna tylko chwilę odbioru. 60 s marginesu pokrywa: opóźnienie doręczenia prośby (nieznane, większe przy słabym zasięgu), czekanie na kod TOTP (≤ 5000 ms, Spec 001) i doręczenie kodu do AAPS. Gdy sesja przetwarza prośbę od razu po odbiorze, wiek ≤ 5000 ms; kontrola chroni przed prośbą dostarczoną z dużym opóźnieniem. Wiek ujemny (zegar cofnięty) też blokuje – wątpliwość = stop. |
| Wiek liczony bezpośrednio przed rozpoczęciem wysyłania kodu, po otrzymaniu kodu TOTP | przy odbiorze prośby | Uwzględnia czekanie na kod TOTP; sprawdzenie przy odbiorze jest wtedy zbędne. |
| **Limit fazy `PREPARING_CODE`: 10 000 ms** | brak limitu; limit odpowiedzi | `awaitUsableCode` przy poprawnym zegarze czeka < 5000 ms, ale przy zatrzymanym zegarze czekałby bez końca (Spec 001, TOTP-02). 2 × 5000 ms to zapas na jedną zmianę okna. Limit odpowiedzi nie obejmuje tej fazy, żeby prośba z ostatniej sekundy limitu nie była porzucana w trakcie przygotowania kodu. |
| **Limit odpowiedzi liczony od rozpoczęcia wysyłania polecenia** | od wyniku „wysłano” | Obejmuje też zawieszony port wysyłania (`TimedOut(SENDING_COMMAND)`) jednym licznikiem; użytkownik czeka najwyżej 3 min od dotknięcia przycisku. |
| Limit potwierdzenia od rozpoczęcia wysyłania kodu | od „wysłano” dla kodu | Analogicznie; obejmuje zawieszony port przy kodzie. |
| **Przy równej chwili wygrywa limit** (sprawdzenie terminu przed obsługą każdego zdarzenia) | kolejność w kanale; wygrywa zdarzenie | Kolejność wstawiania do kanału przez różne korutyny w tej samej chwili nie jest określona, więc bez reguły wynik na granicy byłby niedeterministyczny. Limit wygrywa, bo to bezpieczniejszy kierunek: kod gotowy po 10 000 ms nie wychodzi, odpowiedź na granicy nie zmienia `TimedOut` w sukces. Koszt: zdarzenie dokładnie na granicy przepada (różnica 1 ms). |
| Termin jako znacznik `TimeSource.WithComparableMarks` wstrzykiwany do sesji | odczyt `EpochClock`; same `delay` | Czas monotoniczny, odporny na zmianę zegara telefonu; w testach `testScheduler.timeSource` daje ten sam czas wirtualny co `delay`. |
| **Błąd zegara przy zakończeniu: ostatni poprawny odczyt, `null` gdy brak** | ponowny odczyt; stała zastępcza (np. −1); zmiana wyniku na `INTERNAL_ERROR` | Ponowny odczyt wadliwego zegara mógłby znów rzucić i sesja nie doszłaby do `FINISHED`. `null` jest jednoznaczny (−1 to poprawna chwila epoki). Wynik już ustalony (np. `CarbsConfirmed`) jest prawdziwy niezależnie od zegara, więc się nie zmienia. |
| Anulowanie przed `run()` rozstrzyga jeden atomowy przełącznik cyklu życia | zdarzenie `Cancel` w kanale | Zdarzenie w kanale byłoby odczytane dopiero po starcie pętli, czyli po wysłaniu SMS-a; przełącznik gwarantuje zero SMS-ów, gdy anulowanie było pierwsze. |
| **Koordynator rezerwuje miejsce przed utworzeniem sesji** | utworzenie sesji, potem CAS | Przy zajętym miejscu wynik „zajęte” nie zależy od danych wejściowych (bez wyjątku z walidacji), a PIN jest czyszczony w jednym miejscu. |
| Ponownie dostarczona prośba o kod – ograniczenie opisane, a deduplikacja w `sms` (K2) | identyfikator prośby; porównanie godziny z prośby z zegarem | AAPS nie nadaje identyfikatora, a porównanie godziny wymagałoby tolerancji dla dwóch zegarów (decyzja Spec 002). Skutek jest ograniczony do potwierdzenia bieżącego, identycznego polecenia (A6); źródło problemu (powtórne dostarczenie) leży w `sms`. |
| **Błąd wysyłania kodu → `Unknown(CODE_SEND_FAILED)`**, nie `Failed` | `Failed` | Wynik portu „błąd” nie wyklucza, że SMS z kodem dotarł (np. część wieloczęściowa, błąd po wysłaniu). Wynik „błąd” zachęcałby do ponownego `CARBS`, co grozi podwójnym wprowadzeniem; „sprawdź w AAPS” jest bezpieczniejsze. |
| `CarbsAmountMismatch` przed kodem zawsze kończy sesję (także etap „potwierdzenie”) | traktować potwierdzenie z inną ilością jak wiadomość nieoczekiwaną | Decyzja użytkownika 5 obejmuje każdą niezgodną ilość. Wynik jest bezpieczny (kod nie wychodzi); koszt: obce potwierdzenie z inną ilością przerywa naszą sesję przed kodem. |
| Wiadomość wątpliwa w `PREPARING_CODE` przerywa przygotowanie kodu | dokończyć wysłanie kodu | AGENTS.md: w razie wątpliwości stop. Okno trwa ≤ 5000 ms, więc koszt jest mały, a np. druga prośba o kod w tym czasie oznacza, że stan AAPS jest inny, niż zakłada sesja (A6). |
| Odpowiedź z kodem jako `CharArray` w porcie (`OutgoingSms.CodeReply`) | `String` | Sesja nie tworzy `String` z PIN-em i zeruje tablicę po wysłaniu; kopia `String` powstaje tylko w `sms` tuż przed `SmsManager`. Kod TOTP sam w sobie jest `String` (decyzja Spec 001). |
| `AapsPin` w pakiecie `security` | w `session`; `CharArray` bez opakowania | AGENTS.md przypisuje „obsługę PIN-u AAPS w pamięci” do `security`; typ powstaje teraz, bo sesja go potrzebuje, a przyszła spec `security` może go rozszerzyć. Opakowanie daje redagowany `toString()`, idempotentne czyszczenie i jawny stan „wyczyszczony”. |
| PIN czyszczony zaraz po złożeniu kodu, a nie dopiero na końcu | tylko na końcu | Skraca czas życia PIN-u w pamięci z kilku minut (czekanie na potwierdzenie) do sekund. Zgodne z AGENTS.md („do końca polecenia” to górna granica). |
| Jedno aktywne polecenie egzekwuje `SessionCoordinator` w `session` | blokada w ViewModelu (`ui`) | Reguła bezpieczeństwa w czystej logice da się przetestować jednostkowo, także równoległość; `ui` tylko obserwuje `activeSession` i blokuje przyciski. Koordynator jest singletonem, więc obejmuje wszystkie ekrany. |
| Pętla zdarzeń na kanale, decyzje w jednej korutynie | `Mutex`, `select` na wielu źródłach | Prosta, deterministyczna kolejność w `runTest`; anulowanie i „rozpocznij wysyłanie kodu” nie mogą się przeplatać. |
| Limity w czasie korutyn (`delay`), wiek prośby wg `EpochClock` | wszystko wg zegara epoki | `delay` jest monotoniczne i wirtualne w testach; wiek prośby trzeba porównać z `receivedAtEpochMillis`, które jest chwilą epoki. W testach zegar = `T0 + testScheduler.currentTime`, więc oba są spójne. |
| Nieoczekiwany wyjątek kończy sesję wynikiem (`INTERNAL_ERROR`), `run()` nie rzuca | propagacja wyjątku | Sesja zawsze ma wynik do pokazania i zapisania; PIN zawsze czyszczony. |
| `SendResult.Failed` bez szczegółów | enum przyczyn (`NO_SERVICE`, `RADIO_OFF`…) | Przyczyny zależą od `SmsManager` i należą do spec `sms`; dodanie pola to zmiana tej specyfikacji. |
| Wynik niesie `text` z `IncomingSms.body`, nie z `AapsReply.text` | z wyniku parsera | `AapsReply.WrongCode` nie ma treści; jedno źródło treści dla wszystkich wyników. |

## Mapowanie wymagań na testy

Testy jednostkowe JUnit 5 w `app/src/test/java/pl/twojanazwa/aapsremote/session/` (i `…/security/` dla `AapsPin`), `runTest` + Turbine, nazwy zawierają identyfikator wymagania. Wspólne pomocniki testowe:

- `FakeSmsSender` – zapisuje kopie wysłanych treści (dla `CodeReply` także referencję tablicy), konfigurowalny wynik, opóźnienie (`delay`), wyjątek albo zawieszenie bez końca.
- `FakeIncomingSmsSource` – `MutableSharedFlow<IncomingSms>(replay = 0)`, udostępnia `subscriptionCount`; opcjonalnie strumień kończący się wyjątkiem.
- Zegar `EpochClock { T0 + testScheduler.currentTime }`, `timeSource = testScheduler.timeSource` i `UsableTotpProvider` na tym zegarze; zegar zatrzymany dla SESSION-10; zegar rzucający od wybranej chwili dla SESSION-19.
- Treści z `app/src/test/resources/parser/aaps-3.4.2.6-pl/` (istniejący pomocnik `AapsSamples`).
- Przy sprawdzaniu kolejności faz Turbine port wysyłania czeka ≥ 1 ms czasu wirtualnego, żeby `StateFlow` nie zlał faz przejściowych.

| Wymaganie | Test (klasa / rodzaj) |
| --- | --- |
| SESSION-01 | `SessionTimeoutsTest` – jednostkowy |
| SESSION-02 | `AapsPinTest` (pakiet `security`) i `CommandSessionCreationTest` – jednostkowe |
| SESSION-03 | `CommandSessionSendTest` – jednostkowy, `runTest` |
| SESSION-04 | `CommandSessionSendTest` – jednostkowy |
| SESSION-05 | `CommandSessionIncomingTest` – jednostkowy |
| SESSION-06 | `CommandSessionReplyTest` – jednostkowy, `bg.txt`, `pump.txt`, `carbs-sukces.txt`, tekst obcy |
| SESSION-07 | `CommandSessionReplyTimeoutTest` – jednostkowy, granice 179 999 / 180 000 ms; równość: `bg.txt`, prośba o kod i wynik portu dokładnie w chwili 180 000 |
| SESSION-08 | `CarbsSessionCodeTest` – jednostkowy |
| SESSION-09 | `CarbsSessionCodeTest` – jednostkowy, wektor `1111111111` → `0504714321`, `050471987654`, zerowanie tablicy |
| SESSION-10 | `CarbsSessionCodeValidityTest` – jednostkowy, wiek 240 000 / 240 001 / −1 ms, zatrzymany zegar; równość: dostawca TOTP z MockK zwracający kod po 9 999 / 10 000 ms |
| SESSION-11 | `CarbsSessionBeforeCodeTest` – jednostkowy, parametryzowany tabelą z spec dla obu faz |
| SESSION-12 | `CarbsSessionConfirmationTest` – jednostkowy |
| SESSION-13 | `CarbsSessionAfterCodeTest` – jednostkowy, parametryzowany tabelą z spec |
| SESSION-14 | `SessionUnknownInvariantTest` – jednostkowy: przegląd scenariuszy (lista z pozostałych testów) i asercja „`Unknown` ⇔ port dostał kod” |
| SESSION-15 | `CarbsSessionConfirmationTimeoutTest` – jednostkowy, granice 419 999 / 420 000 ms; równość: `carbs-sukces.txt` i wynik portu kodu dokładnie w chwili 420 000 |
| SESSION-16 | `CarbsSessionCodeSendFailureTest` – jednostkowy |
| SESSION-17 | `SessionCancelTest` – jednostkowy, każda faza, anulowanie z zewnątrz, anulowanie przed `run()` (zero SMS-ów, `subscriptionCount` = 0); `SessionCancelRaceTest` – 1000 × równoczesne `cancel()`/`run()` na `Dispatchers.Default` |
| SESSION-18 | `SessionUnexpectedMessagesTest` – jednostkowy |
| SESSION-19 | `SessionPinClearingTest` – jednostkowy, parametryzowany wynikami; obserwator Turbine sprawdza `pin.isCleared` przy `FINISHED`; dostawca TOTP z MockK rzucający wyjątek; `SessionClockFailureTest` – zegar rzucający od wybranej chwili (przed kodem, od startu, przy zakończeniu po `CarbsConfirmed`) |
| SESSION-20 | `SessionNoRetryTest` – jednostkowy: liczniki SMS-ów, `subscriptionCount == 0` po `FINISHED` |
| SESSION-21 | `SessionStateFlowTest` – jednostkowy, Turbine: kolejność faz, pola `SessionResult`, brak `4321`/`050471` w polach tekstowych |
| SESSION-22 | `SessionCoordinatorTest` – jednostkowy: „zajęte” bez wyjątku dla niepoprawnych danych, zwolnienie rezerwacji po `IllegalArgumentException`; równoległość: 100 × `start` na `Dispatchers.Default` (bez `runTest` dla tej części) |
| SESSION-23 | `SessionRedactionTest` – jednostkowy: `toString()` `AapsPin`, `CodeCredentials`, `OutgoingSms`, `IncomingSms`, `SessionState`, `SessionResult`, wyników; komunikaty wyjątków |

Brak wywołań API logowania i brak importów `android.*` w `session` (SESSION-23, Przegląd) sprawdza recenzja kodu; test nie jest wymagany.

## Podział na zadania

Kolejność wynika z zależności. Każde zadanie to jeden PR, ok. 200–400 linii z testami.

1. **Typy, porty i PIN** – `AapsPin` (`security`), `SessionTimeouts`, `SessionLimits`, `IncomingSms`, `IncomingSmsSource`, `OutgoingSms`, `SendResult`, `SmsSender`, `CodeCredentials`, `SessionPhase`, `SessionState`, `SessionResult`, `SessionOutcome`, `FailureReason`, `UnknownReason` + `AapsPinTest`, `SessionTimeoutsTest`, część `SessionRedactionTest`; pomocniki testowe `FakeSmsSender`, `FakeIncomingSmsSource`, zegar i `timeSource` na czasie wirtualnym. Wymagania: SESSION-01, SESSION-02 (część `AapsPin`), SESSION-23 (część). Bez zależności.
2. **Rdzeń sesji BG i PUMP** – `CommandSession`: walidacja konstruktora, przełącznik cyklu życia (jedno uruchomienie), pętla zdarzeń, wysłanie polecenia, filtr chwili wysłania, odpowiedzi `BG`/`PUMP`, `finish()` z czyszczeniem PIN-u i ostatnim poprawnym odczytem zegara, `StateFlow` + `CommandSessionCreationTest`, `CommandSessionSendTest`, `CommandSessionIncomingTest`, `CommandSessionReplyTest`, część `SessionClockFailureTest`, `SessionStateFlowTest` (`BG`), `SessionNoRetryTest` (`BG`). Wymagania: SESSION-02 (część sesji), SESSION-03, SESSION-04, SESSION-05, SESSION-06, SESSION-19 (część), SESSION-20 (część), SESSION-21 (część). Zależy od 1.
3. **Limit odpowiedzi i anulowanie przed kodem** – termin limitu na `timeSource` z pierwszeństwem limitu, `TimedOut`, anulowanie w fazach przed kodem (także przed `run()` i równoczesne z `run()`), anulowanie z zewnątrz, `INTERNAL_ERROR` przed kodem + `CommandSessionReplyTimeoutTest`, część `SessionCancelTest`, `SessionCancelRaceTest`, część `SessionClockFailureTest`. Wymagania: SESSION-07, SESSION-17 (część), SESSION-19 (część). Zależy od 2.
4. **CARBS do wysłania kodu** – przyjęcie prośby, `PREPARING_CODE`, `awaitUsableCode`, kontrola wieku, limit 10 000 ms z pierwszeństwem limitu, składanie kodu i czyszczenie PIN-u, wiadomości przed kodem, wiadomości nieoczekiwane, anulowanie w `PREPARING_CODE` + `CarbsSessionCodeTest`, `CarbsSessionCodeValidityTest`, `CarbsSessionBeforeCodeTest`, `SessionUnexpectedMessagesTest`, część `SessionPinClearingTest` i `SessionCancelTest`. Wymagania: SESSION-08, SESSION-09, SESSION-10, SESSION-11, SESSION-18, SESSION-17 (część), SESSION-19 (część). Zależy od 3. Po tym zadaniu sesja dochodzi do `AWAITING_CONFIRMATION` i kończy się tam tylko anulowaniem (testy anulują ją na końcu); obsługę tej fazy dodaje zadanie 5.
5. **CARBS po wysłaniu kodu** – potwierdzenie, wiadomości po kodzie, limit potwierdzenia z pierwszeństwem limitu, błąd wysyłania kodu, anulowanie po kodzie, `INTERNAL_ERROR` po kodzie + `CarbsSessionConfirmationTest`, `CarbsSessionAfterCodeTest`, `CarbsSessionConfirmationTimeoutTest`, `CarbsSessionCodeSendFailureTest`, `SessionUnknownInvariantTest`, reszta `SessionCancelTest`, `SessionPinClearingTest`, `SessionClockFailureTest`, `SessionStateFlowTest`, `SessionNoRetryTest`, `SessionRedactionTest`. Wymagania: SESSION-12, SESSION-13, SESSION-14, SESSION-15, SESSION-16, SESSION-17 (reszta), SESSION-19 (reszta), SESSION-20 (reszta), SESSION-21 (reszta), SESSION-23 (reszta). Zależy od 4.
6. **Koordynator jednego polecenia** – `SessionCoordinator` z rezerwacją miejsca przed utworzeniem sesji, `StartResult`, `onFinished` w sesji + `SessionCoordinatorTest`. Wymagania: SESSION-22. Zależy od 2 (może iść równolegle z 3–5).
