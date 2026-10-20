# Design 005: SMS (wysyłanie i odbiór)

Dotyczy: [spec.md](spec.md)

## Przegląd

Pakiet `pl.twojanazwa.aapsremote.sms` implementuje porty `SmsSender` i `IncomingSmsSource` z `session/SmsPorts.kt` (Spec 004) bez zmiany ich sygnatur. Podział, zgodnie z [AGENTS.md](../../../AGENTS.md#architektura):

- **`pl.twojanazwa.aapsremote.sms` – logika czysta** (Kotlin/JVM + `kotlinx.coroutines`, bez importów `android.*`; dozwolone adnotacje `javax.inject`): numer AAPS, składanie części, deduplikacja, koncentrator odbioru, port wysyłania, śledzenie wyników części. Testy JUnit 5 z atrapami warstwy Androida.
- **`pl.twojanazwa.aapsremote.sms.platform` – cienka warstwa Androida**: receiver `SMS_RECEIVED`, `SmsManager` z `PendingIntent` wyniku, `checkSelfPermission`, moduł Hilt. Bez decyzji – tylko tłumaczenie wywołań systemu na interfejsy logiki czystej. Sprawdzana ręcznie na telefonie (W1–W7), bez Robolectric i `androidTest` (decyzja P3).

Zależności (kierunek): `sms` → `session` (porty, `IncomingSms`, `OutgoingSms`, `SendResult`), `sms` → `otp` (`EpochClock`). `session` nie importuje `sms`. `data` (przyszła spec) dostarcza `AapsNumberProvider`; `ui` używa `SmsPermissions` i prosi o uprawnienia.

Przepływ odbioru: system → `AndroidSmsBroadcastRegistrar` (receiver, `getMessagesFromIntent` → `RawSmsPart`) → `IncomingSmsHub.onBroadcast` (zegar → numer → `SmsAssembler` → filtr `AapsNumber.matchesSender` → `DeliveredSmsRegistry`) → kanał każdego kolektora → `IncomingSms`.

Przepływ wysyłania: sesja → `AapsSmsSender.send` (uprawnienie → numer → tekst lokalny) → `SmsSystemApi` (`divideMessage`, rejestracja wyników, `sendParts`) → `MultipartSendTracker` → `SendResult`.

## Interfejsy

```kotlin
package pl.twojanazwa.aapsremote.sms

// ---- Zależności zewnętrzne ----

/**
 * Numer AAPS z ustawień; null = brak konfiguracji. Implementacja: spec `data`.
 * Wołane z onReceive (wątek główny) i z send(): nie może wykonywać blokującego I/O
 * (np. zwraca wartość z pamięci podręcznej StateFlow ustawień).
 */
fun interface AapsNumberProvider {
    fun currentNumber(): String?
}

/** SMS-14. Stan w chwili wywołania, bez zapamiętywania. Implementacja: platform.AndroidSmsPermissions. */
interface SmsPermissions {
    fun canSend(): Boolean
    fun canReceive(): Boolean

    companion object {
        const val SEND_SMS: String = "android.permission.SEND_SMS"
        const val RECEIVE_SMS: String = "android.permission.RECEIVE_SMS"

        /** Dla ui (prośba o uprawnienia). Dokładnie te dwa, w tej kolejności. */
        val REQUIRED: List<String> = listOf(SEND_SMS, RECEIVE_SMS)
    }
}

// ---- Numer AAPS (SMS-01, SMS-02) ----

class AapsNumber private constructor(
    /** Adres docelowy: numer bez znaków białych, '-', '(', ')'; opcjonalny '+' i cyfry ASCII. */
    val destination: String,
    /** Ostatnie 9 cyfr. */
    private val suffix: String,
) {
    /** SMS-02: null / litera / < 9 cyfr → false; inaczej ostatnie 9 cyfr == suffix. */
    fun matchesSender(sender: String?): Boolean

    override fun toString(): String = "AapsNumber(***)"

    companion object {
        const val SUFFIX_DIGITS: Int = 9

        /** null = nieprawidłowa konfiguracja (SMS-01). Nie rzuca. */
        fun parse(raw: String?): AapsNumber?
    }
}

// ---- Składanie i deduplikacja (SMS-03, SMS-06) ----

/** Jedna część z intentu SMS_RECEIVED. toString() bez nadawcy i treści. */
class RawSmsPart(
    val sender: String?,
    val body: String?,
    val serviceCenterTimestampMillis: Long,
) {
    override fun toString(): String = "RawSmsPart(***)"
}

/** Wiadomość złożona z części jednego nadawcy z jednego broadcastu. toString() bez nadawcy i treści. */
class AssembledSms(
    val sender: String?,
    val body: String,
    val serviceCenterTimestampMillis: Long,
) {
    override fun toString(): String = "AssembledSms(***)"
}

object SmsAssembler {
    /** SMS-03: grupy wg równości sender (null osobno), kolejność pierwszego wystąpienia; body null = "". */
    fun assemble(parts: List<RawSmsPart>): List<AssembledSms>
}

/**
 * SMS-06. Pamięć ostatnich [capacity] kluczy (FIFO), metody synchronizowane.
 * Klucz = SHA-256 z (sender, serviceCenterTimestampMillis, body) – treść nie jest przechowywana.
 */
class DeliveredSmsRegistry(private val capacity: Int = DEFAULT_CAPACITY) {
    /** true i zapamiętanie przy pierwszym wystąpieniu; false dla duplikatu (bez zmiany kolejności). */
    fun markFirstDelivery(sms: AssembledSms): Boolean

    override fun toString(): String // "DeliveredSmsRegistry(size=N)" – bez kluczy

    companion object {
        const val DEFAULT_CAPACITY: Int = 100
    }
}

// ---- Odbiór (SMS-04 – SMS-08) ----

/** Cienka warstwa nad Context.registerReceiver dla SMS_RECEIVED. Implementacja: platform. */
interface SmsBroadcastRegistrar {
    /** Rejestruje receiver; [onBroadcast] wołane z onReceive (wątek główny). Może rzucić (np. SecurityException). */
    fun register(onBroadcast: (List<RawSmsPart>) -> Unit)

    /** Wyrejestrowuje; może rzucić IllegalArgumentException, gdy nie zarejestrowano. */
    fun unregister()
}

/** Implementacja IncomingSmsSource. Jedna na aplikację (@Singleton). */
@Singleton
class IncomingSmsHub @Inject constructor(
    private val registrar: SmsBroadcastRegistrar,
    private val permissions: SmsPermissions,
    private val numberProvider: AapsNumberProvider,
    private val clock: EpochClock,
    private val registry: DeliveredSmsRegistry,
) : IncomingSmsSource {
    /** SMS-07: gorący; dołączenie synchroniczne przed pierwszym zawieszeniem; nie kończy się sam, nie rzuca. */
    override fun messages(): Flow<IncomingSms>

    /** Obsługa jednego broadcastu (przekazywana do registrar.register). Nie rzuca. */
    internal fun onBroadcast(parts: List<RawSmsPart>)

    override fun toString(): String = "IncomingSmsHub(***)"
}

// ---- Wysyłanie (SMS-09 – SMS-13) ----

/** Cienka warstwa nad SmsManager domyślnej karty i receiverem wyniku. Implementacja: platform. */
interface SmsSystemApi {
    /** SmsManager.divideMessage. Rzuca IllegalStateException, gdy brak SmsManager. */
    fun divideMessage(text: String): List<String>

    /**
     * Rejestruje odbiór wyników wysłania dla jednego wywołania send() (unikalna akcja).
     * [onPartResult] (indeks części, wynik == RESULT_OK) wołane z wątku głównego.
     */
    fun registerSentResults(onPartResult: (partIndex: Int, ok: Boolean) -> Unit): SentResultsRegistration

    /** 1 część → sendTextMessage, > 1 → sendMultipartTextMessage; PendingIntent wyniku dla każdej części, bez deliveryIntent. */
    fun sendParts(destination: String, parts: List<String>, registration: SentResultsRegistration)
}

/** Uchwyt rejestracji wyników jednego wysłania. Nie przechowuje treści. */
interface SentResultsRegistration {
    /** Wyrejestrowuje receiver i anuluje PendingIntent; idempotentne, nie rzuca. */
    fun unregister()
}

/** SMS-10. Synchronizowane. Wynik zwracany dokładnie raz – w wywołaniu, które go ustala; poza tym null. */
class MultipartSendTracker(private val partCount: Int) { // require(partCount >= 1)
    /** ok=false → Failed od razu; wszystkie indeksy 0 until partCount z ok=true → Sent; powtórzenia i indeksy spoza zakresu ignorowane. */
    fun onPartResult(partIndex: Int, ok: Boolean): SendResult?
}

/** Implementacja SmsSender. Pola wyłącznie niezmienne zależności (SMS-13). */
@Singleton
class AapsSmsSender @Inject constructor(
    private val numberProvider: AapsNumberProvider,
    private val permissions: SmsPermissions,
    private val system: SmsSystemApi,
) : SmsSender {
    /** Nie rzuca poza CancellationException (SMS-12). */
    override suspend fun send(message: OutgoingSms): SendResult

    override fun toString(): String = "AapsSmsSender(***)"
}
```

```kotlin
package pl.twojanazwa.aapsremote.sms.platform

class AndroidSmsPermissions @Inject constructor(@ApplicationContext private val context: Context) : SmsPermissions
class AndroidSmsBroadcastRegistrar @Inject constructor(@ApplicationContext private val context: Context) : SmsBroadcastRegistrar
class AndroidSmsSystemApi @Inject constructor(@ApplicationContext private val context: Context) : SmsSystemApi

@Module
@InstallIn(SingletonComponent::class)
abstract class SmsModule {
    @Binds abstract fun sender(impl: AapsSmsSender): SmsSender
    @Binds abstract fun incoming(impl: IncomingSmsHub): IncomingSmsSource
    @Binds abstract fun permissions(impl: AndroidSmsPermissions): SmsPermissions
    @Binds abstract fun registrar(impl: AndroidSmsBroadcastRegistrar): SmsBroadcastRegistrar
    @Binds abstract fun system(impl: AndroidSmsSystemApi): SmsSystemApi

    companion object {
        @Provides @Singleton fun registry(): DeliveredSmsRegistry = DeliveredSmsRegistry()
    }
}
```

```kotlin
package pl.twojanazwa.aapsremote.otp

/** K1: jeden zegar dla sesji, UsableTotpProvider i sms. Dodawany, jeśli wiązanie EpochClock jeszcze nie istnieje. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides @Singleton fun clock(): EpochClock = EpochClock { System.currentTimeMillis() }
}
```

Sygnatury są wiążące dla nazw i typów publicznych; ciała i prywatne pomocniki zależą od wykonawcy. `AapsNumberProvider` nie ma jeszcze wiązania – dostarczy je spec `data`. Dagger domyślnie sprawdza tylko wiązania osiągalne z punktów wejścia (`fullBindingGraphValidation` wyłączone), a do czasu `ui` nic nie wstrzykuje `SmsSender` ani `IncomingSmsSource`, więc `assembleDebug` przechodzi; jeśli nie przejdzie, zadanie 6 zatrzymuje się i zgłasza to jako zmianę spec (nie dodaje tymczasowego dostawcy numeru).

## Model danych i stany

### Numer AAPS (SMS-01, SMS-02)

`parse(raw)`:

1. `raw == null` → `null`.
2. `candidate = raw.filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' }`.
3. `candidate` musi pasować do `^\+?[0-9]+$` (tylko ASCII) i mieć ≥ 9 cyfr; inaczej `null`.
4. `AapsNumber(destination = candidate, suffix = candidate.takeLast(9))` (ostatnie 9 znaków to cyfry).

`matchesSender(sender)`: `sender == null` → `false`; `sender.any { it.isLetter() }` → `false`; `digits = sender.filter { it in '0'..'9' }`; `digits.length >= 9 && digits.takeLast(9) == suffix`.

### Składanie (SMS-03)

`LinkedHashMap<String?, MutableList<RawSmsPart>>` w kolejności pierwszego wystąpienia; dla każdej grupy `AssembledSms(sender, parts.joinToString("") { it.body ?: "" }, parts.first().serviceCenterTimestampMillis)`.

Założenie (W4): system (`InboundSmsHandler`) czeka na wszystkie części wiadomości wieloczęściowej i wysyła je w jednym broadcaście, w kolejności numerów części; `getMessagesFromIntent` zwraca je w tej kolejności. Części z osobnych broadcastów nie są łączone – brak buforowania między broadcastami upraszcza logikę, a niepełna wiadomość i tak dałaby u parsera `Unrecognized` (bezpieczny stop).

### Deduplikacja (SMS-06)

Klucz: SHA-256 (`java.security.MessageDigest`) z bajtów UTF-8 zapisu jednoznacznego `"${sender?.length ?: -1}:${sender.orEmpty()}|$serviceCenterTimestampMillis|$body"` (długość nadawcy chroni przed niejednoznacznością sklejenia). Przechowywany jako tekst szesnastkowy w `LinkedHashSet<String>`; przy `size > capacity` usuwany jest pierwszy element. Duplikat nie zmienia kolejności (FIFO, nie LRU). `require(capacity >= 1)`.

Rejestr jest singletonem wspólnym dla wszystkich kolektorów i sesji. Zapisywane są wiadomości po filtrze numeru AAPS, niezależnie od liczby kolektorów w chwili obsługi.

### Koncentrator odbioru (SMS-04 – SMS-08)

Stan pod jedną blokadą `lock`: `subscribers: MutableList<Channel<IncomingSms>>`, `registered: Boolean`.

```kotlin
override fun messages(): Flow<IncomingSms> = flow {
    val channel = Channel<IncomingSms>(Channel.UNLIMITED)
    join(channel)                       // synchronicznie, przed pierwszym zawieszeniem
    try {
        for (sms in channel) emit(sms)  // nie kończy się sam: kanał zamykany tylko w leave()
    } finally {
        leave(channel)
    }
}
```

| Stan | Zdarzenie | Nowy stan | Akcja | Wymaganie |
| --- | --- | --- | --- | --- |
| `registered = false` | `join`, `canReceive() == true` | `registered = true` (gdy `register` nie rzucił) | dodaj kanał; `registrar.register(::onBroadcast)`; wyjątek połknięty | SMS-07, SMS-08 |
| `registered = false` | `join`, `canReceive() == false` albo wyjątek z `canReceive()` | bez zmian | dodaj kanał; bez rejestracji | SMS-08 |
| `registered = true` | `join` | bez zmian | dodaj kanał (bez drugiej rejestracji) | SMS-07 |
| dowolny | `leave`, zostają inni | bez zmian | usuń i zamknij kanał | SMS-07 |
| `registered = true` | `leave` ostatniego | `registered = false` | usuń kanał; `registrar.unregister()`; wyjątek połknięty | SMS-07, SMS-08 |
| `registered = false` | `leave` ostatniego | bez zmian | usuń kanał | SMS-08 |

`onBroadcast(parts)` (wątek główny, nie rzuca):

1. `t = clock.nowEpochMillis()` – pierwsza instrukcja; wyjątek → koniec obsługi (SMS-05, K1).
2. `number = AapsNumber.parse(numberProvider.currentNumber())`; `null` → koniec (SMS-04).
3. `fresh = SmsAssembler.assemble(parts).filter { number.matchesSender(it.sender) }.filter { registry.markFirstDelivery(it) }` (SMS-03, SMS-04, SMS-06).
4. `targets = synchronized(lock) { subscribers.toList() }`; dla każdej `m` z `fresh` po kolei: `IncomingSms(m.body, t)` → `trySend` do każdego kanału (nieograniczony – nie blokuje, SMS-07).
5. Kroki 1–4 w `try { } catch (e: Exception) { }` – wyjątek (także zegara w kroku 1) pomija broadcast; `onBroadcast` nie propaguje wyjątków.

`join`/`leave` i `register`/`unregister` pod blokadą: wywołania systemu nie wołają zwrotnie `onBroadcast` synchronicznie, więc blokada nie grozi zakleszczeniem; `onBroadcast` bierze blokadę tylko na kopię listy.

### Port wysyłania (SMS-09 – SMS-13)

```kotlin
override suspend fun send(message: OutgoingSms): SendResult = try {
    if (!permissions.canSend()) SendResult.Failed
    else {
        val number = AapsNumber.parse(numberProvider.currentNumber())
        if (number == null) SendResult.Failed
        else {
            val text = when (message) {                 // zmienna lokalna – SMS-13
                is OutgoingSms.CommandText -> message.text
                is OutgoingSms.CodeReply -> String(message.chars)
            }
            sendAndAwait(number.destination, text)
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    SendResult.Failed
}
```

`sendAndAwait(destination, text)`:

1. `parts = system.divideMessage(text)`; pusta lista → `Failed`.
2. `suspendCancellableCoroutine { cont -> … }`:
   - `tracker = MultipartSendTracker(parts.size)`;
   - `registration = system.registerSentResults { i, ok -> tracker.onPartResult(i, ok)?.let { registration.unregister(); cont.resume(it) } }` (referencja przez `AtomicReference`, bo wynik może przyjść dopiero po `sendParts`, ale kompilator wymaga inicjalizacji);
   - `cont.invokeOnCancellation { registration.unregister() }` (SMS-11);
   - `system.sendParts(destination, parts, registration)`; wyjątek → `registration.unregister()`, `cont.resume(Failed)` (SMS-12).
3. Brak wyniku → brak wznowienia; czekanie kończy anulowanie przez sesję (SMS-10, SESSION-07, SESSION-15). `resume` na anulowanej kontynuacji jest ignorowane przez `kotlinx.coroutines`, a `tracker` zwraca wynik najwyżej raz, więc nie ma podwójnego wznowienia.

`MultipartSendTracker`: `BooleanArray(partCount)` dla przyjętych części, licznik, flaga „ustalono”; `onPartResult` pod `synchronized`.

| Stan | Zdarzenie | Nowy stan | Wynik | Wymaganie |
| --- | --- | --- | --- | --- |
| czeka | `ok = true`, nowy indeks, brakuje innych | czeka | `null` | SMS-10 |
| czeka | `ok = true`, ostatni brakujący indeks | ustalono | `Sent` | SMS-10 |
| czeka | `ok = false`, indeks w zakresie | ustalono | `Failed` | SMS-10 |
| czeka | powtórzony indeks albo spoza `0 until partCount` | czeka | `null` | SMS-10 |
| ustalono | dowolne | ustalono | `null` | SMS-10, SMS-11 |

### Warstwa Androida (`sms.platform`)

- **`AndroidSmsPermissions`:** `ContextCompat.checkSelfPermission(context, SEND_SMS / RECEIVE_SMS) == PERMISSION_GRANTED` przy każdym wywołaniu.
- **`AndroidSmsBroadcastRegistrar.register`:** receiver, który dla akcji `Telephony.Sms.Intents.SMS_RECEIVED_ACTION` wywołuje `Telephony.Sms.Intents.getMessagesFromIntent(intent)` (w `try`; `null`-e pomijane) i mapuje każdą część na `RawSmsPart(originatingAddress, messageBody, timestampMillis)`. Rejestracja: `ContextCompat.registerReceiver(context, receiver, IntentFilter(SMS_RECEIVED_ACTION), Manifest.permission.BROADCAST_SMS, null, ContextCompat.RECEIVER_EXPORTED)` na kontekście aplikacji. `unregister`: `context.unregisterReceiver(receiver)`.
- **`AndroidSmsSystemApi`:**
  - `SmsManager` domyślnej karty pobierany przy każdym wywołaniu: API ≥ 31 – `context.getSystemService(SmsManager::class.java)`, API 28–30 – `SmsManager.getDefault()` (przestarzałe od API 31). `null` → `IllegalStateException` ze stałym komunikatem.
  - `registerSentResults`: unikalna akcja `"${context.packageName}.sms.SENT.${UUID.randomUUID()}"`; receiver czyta `getIntExtra(EXTRA_PART_INDEX, -1)` i `resultCode == Activity.RESULT_OK`; rejestracja `ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)`.
  - `sendParts`: dla każdej części `PendingIntent.getBroadcast(context, i, Intent(action).setPackage(context.packageName).putExtra(EXTRA_PART_INDEX, i), FLAG_IMMUTABLE or FLAG_ONE_SHOT)` (zapamiętane w rejestracji do anulowania); 1 część → `sendTextMessage(destination, null, part, sentIntent, null)`, więcej → `sendMultipartTextMessage(destination, null, ArrayList(parts), ArrayList(sentIntents), null)`.
  - Rejestracja (`SentResultsRegistration`) trzyma receiver, akcję (UUID) i `PendingIntent`-y – nigdy treść; `unregister` jest idempotentne (`AtomicBoolean`), połyka `IllegalArgumentException` i anuluje `PendingIntent`-y.
- **Manifest:** dwa `uses-permission` (`SEND_SMS`, `RECEIVE_SMS`), bez `<receiver>`/`<service>`. `ContextCompat.registerReceiver` z `RECEIVER_NOT_EXPORTED` na API < 33 dodaje przez scalanie manifestu biblioteki AndroidX uprawnienie podpisu `<pakiet>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; to jest w manifeście scalonym, nie źródłowym – test SMS-15 czyta plik źródłowy.

Żadna klasa `sms` ani `sms.platform` nie wywołuje API logowania (SMS-16).

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| **Domyślna karta SMS systemu, bez wyboru** | `SubscriptionManager` + ustawienie karty (plan) | Decyzja użytkownika dla MVP; mniej ustawień i brak uprawnienia `READ_PHONE_STATE` potrzebnego do listy kart. Odstępstwo od planu zapisane w spec. |
| **Porównanie nadawcy po ostatnich 9 cyfrach**, litery → brak dopasowania | dokładna równość; normalizacja E.164 (libphonenumber) | Decyzja użytkownika. Nadawca przychodzi w różnych formatach (`+44…`, `0…`, `0044…`) zależnie od sieci; 9 cyfr pokrywa numer krajowy bez prefiksów. Litery = nadawca alfanumeryczny, który nigdy nie jest AAPS. Bez biblioteki i jej danych. |
| Adres docelowy = numer bez znaków białych, `-`, `(`, `)`; inne znaki = błąd konfiguracji | wysyłanie surowego tekstu; tylko cyfry | Numer wysyłany tak, jak wpisał użytkownik (z `+`), bez znaków formatujących; znak nieoczekiwany oznacza literówkę – lepiej nie wysyłać niż wysłać pod zły numer. |
| Numer odczytywany przy każdym `send` i broadcaście | migawka przy starcie sesji | Najprostsze i deterministyczne w testach; zmiana numeru w trakcie sesji kończy się bezpiecznie (odpowiedzi ze starego numeru odrzucone → limit czasu). |
| `AapsNumberProvider` niezawieszający | `suspend` / `Flow` | Wołany z `onReceive` na wątku głównym, gdzie nie można czekać; `data` trzyma wartość w pamięci. |
| **Receiver rejestrowany dynamicznie na czas zbierania** | receiver w manifeście; usługa pierwszoplanowa | Decyzja użytkownika. Brak odbioru poza sesją = brak przetwarzania obcych SMS-ów i brak broadcastów „sprzed sesji” (K3). Na Androidzie 14+ proces w stanie „cached” może nie dostawać broadcastów – założenie MVP: aplikacja na pierwszym planie z włączonym ekranem w trakcie polecenia (spec, „Założenia i zależności”). |
| Receiver `SMS_RECEIVED` eksportowany z wymaganym uprawnieniem nadawcy `BROADCAST_SMS` | `RECEIVER_NOT_EXPORTED`; bez uprawnienia nadawcy | `SMS_RECEIVED` wysyła proces telefonii (inny UID), więc receiver nieeksportowany mógłby go nie dostać; wymóg `BROADCAST_SMS` (uprawnienie systemowe) blokuje fałszywe broadcasty od innych aplikacji. Do potwierdzenia W3. |
| **Jedna rejestracja receivera, kanał `UNLIMITED` dla każdego kolektora** | `callbackFlow` z rejestracją na kolektora; `shareIn(WhileSubscribed)`; `MutableSharedFlow` z buforem | Receiver na kolektora dawałby kilka `onReceive` dla jednego SMS-a, a wspólna deduplikacja odcięłaby drugiego kolektora. `shareIn` rejestruje upstream asynchronicznie w osobnej korutynie, więc subskrypcja nie byłaby aktywna w chwili wywołania `send` (SESSION-03). `MutableSharedFlow` z ograniczonym buforem gubi albo blokuje przy wolnym kolektorze, a `onReceive` nie może czekać. Kanał na kolektora daje dołączenie synchroniczne, brak utraty i brak blokowania; pamięć ogranicza liczba SMS-ów od AAPS w czasie sesji. |
| **Chwila odbioru = pierwszy odczyt zegara w `onReceive`, raz na broadcast** | `SmsMessage.timestampMillis`; odczyt na wiadomość | K1 i decyzja Spec 004: jeden zegar dla porównań. Jeden odczyt przed całą obsługą daje tę samą chwilę wszystkim wiadomościom broadcastu i nie zależy od czasu obsługi. |
| **Deduplikacja: skrót SHA-256 z nadawcy, znacznika centrum SMS i treści, 100 wpisów FIFO** | bez deduplikacji; klucz z treści; czas życia wpisu | Ponowny broadcast tej samej wiadomości ma identyczne trzy pola; dwie prawdziwe odpowiedzi AAPS o tej samej treści mają różne znaczniki (dokładność 1 s). Skrót zamiast treści – w pamięci nie zostaje tekst SMS-a. 100 wpisów (kilka kB) z dużym zapasem pokrywa wiele sesji (sesja ≤ kilka SMS-ów); FIFO bez czasu – zachowanie niezależne od zegara. |
| Deduplikacja w obrębie procesu, bez zapisu na dysk | trwały rejestr | Po restarcie procesu receiver nie istnieje do nowej sesji, a `SMS_RECEIVED` nie jest broadcastem trwałym, więc stare wiadomości nie wracają (K2). Trwały rejestr wymagałby zapisu treści/skrótów i nie zamknąłby przypadku powtórzenia przez telefonię w trakcie nowej sesji (ograniczenie z SESSION-08). |
| Grupowanie części po dokładnym tekście nadawcy | po końcówce numeru; wszystko w jedną wiadomość | Jeden broadcast to w praktyce jedna wiadomość; grupowanie chroni przed sklejeniem treści różnych nadawców. Filtr numeru działa po złożeniu. |
| Uprawnienie `RECEIVE_SMS` sprawdzane przy dołączaniu kolektora | rejestracja zawsze (system i tak nie dostarczy) | Deterministyczne w testach (liczba rejestracji = 0); przy przyznaniu uprawnienia w trakcie sesji kolejna sesja zarejestruje receiver. |
| `SEND_SMS` sprawdzane przy każdym `send`, przed numerem | tylko wyjątek `SecurityException` z `SmsManager` | Brak próby wysyłania bez uprawnienia; `SecurityException` i tak jest łapany (wyścig z cofnięciem). |
| **`Sent` tylko po `RESULT_OK` każdej części; pierwszy błąd → `Failed` od razu** | czekać na wszystkie części | K5. Po pierwszym błędzie wynik jest przesądzony; sesja (SESSION-16) traktuje błąd przy kodzie jako „nieznany”, bo inne części mogły wyjść. |
| Brak własnego limitu czasu w `send` | limit w porcie | Limity należą do sesji (SESSION-07, SESSION-15), która anuluje `send`; dwa limity byłyby niespójne. |
| `PendingIntent` z `FLAG_IMMUTABLE`, receiver wyniku `RECEIVER_NOT_EXPORTED`, unikalna akcja na wywołanie | `FLAG_MUTABLE`; jedna akcja z licznikiem | Kod wyniku przekazywany przy `PendingIntent.send` nie wymaga zmienności intentu (dodatkowe pola od systemu, np. kod błędu, nie są potrzebne); broadcast z `PendingIntent` ma tożsamość aplikacji, więc nieeksportowany receiver go dostaje (W2). Unikalna akcja (UUID) rozdziela równoczesne wysłania i nie wymaga pola licznika (SMS-13). |
| **Podział na logikę czystą i `sms.platform` z interfejsami `SmsBroadcastRegistrar`, `SmsSystemApi`** | wszystko w klasach Androida | Decyzje (filtr, sklejanie, deduplikacja, wynik części, anulowanie) testowane jednostkowo bez telefonu; warstwa Androida bez gałęzi logicznych. |
| Tekst odpowiedzi z kodem tworzony lokalnie w `send` | `CharSequence` z tablicy bez kopii | `SmsManager` wymaga `String`; lokalna zmienna to najkrótszy możliwy czas życia kopii w aplikacji. `String` nie da się wyzerować (ograniczenie JVM, spec). |
| `SendResult.Failed` bez przyczyny | rozszerzenie portu | Instrukcja: bez zmiany Spec 004 w tej specyfikacji; propozycja w „Przyszłe zmiany” spec. |
| **Brak testów automatycznych `sms.platform`** (decyzja użytkownika, P3) | Robolectric; `androidTest` | Projekt nie ma tych narzędzi, a klasy platformy są cienkie, bez gałęzi logicznych; logika czysta ma testy JVM. Warstwa platformy sprawdzana ręcznie na telefonie według W1–W7, gdy powstanie `ui`. |

## Mapowanie wymagań na testy

Testy jednostkowe JUnit 5 w `app/src/test/java/pl/twojanazwa/aapsremote/sms/`, `runTest` + Turbine. Nazwa każdej metody testowej zaczyna się od identyfikatora wymagania, np. `` `SMS-01 numer z mniej niż 9 cyframi to błędna konfiguracja` ``; klasy w tabeli niżej grupują te metody. Wspólne pomocniki testowe:

- `FakeSmsBroadcastRegistrar` – liczniki `register`/`unregister`, przechwycona funkcja `onBroadcast`, opcjonalny wyjątek przy rejestracji/wyrejestrowaniu, `isRegistered`.
- `FakeSmsPermissions` – przełączane `canSend`/`canReceive`.
- `FakeSmsSystemApi` – zapisuje zlecenia (adres, części), konfigurowalny podział, przechwytuje `onPartResult` do ręcznego zgłaszania wyników, licznik `unregister`, opcjonalne wyjątki.
- Zegar `EpochClock` z kolejką wartości / wyjątkiem i licznikiem odczytów; dostawca numeru ze zmienną wartością.
- Treści z `app/src/test/resources/parser/aaps-3.4.2.6-pl/` (istniejący pomocnik `AapsSamples`).

| Wymaganie | Test (klasa / rodzaj) |
| --- | --- |
| SMS-01 | `AapsNumberParseTest` – jednostkowy, parametryzowany |
| SMS-02 | `AapsNumberSenderMatchTest` – jednostkowy, parametryzowany |
| SMS-03 | `SmsAssemblerTest` – jednostkowy |
| SMS-04 | `IncomingSmsHubFilterTest` – jednostkowy, `FakeSmsBroadcastRegistrar` |
| SMS-05 | `IncomingSmsHubTimestampTest` – jednostkowy, zegar z licznikiem odczytów |
| SMS-06 | `DeliveredSmsRegistryTest` (pojemność, FIFO) i `IncomingSmsHubDedupTest` (dwie sesje, wiadomości spoza AAPS) – jednostkowe |
| SMS-07 | `IncomingSmsHubSubscriptionTest` – jednostkowy: `CoroutineStart.UNDISPATCHED`, dwóch kolektorów, liczniki rejestracji, 200 wiadomości dla wolnego kolektora, 600 000 ms bez zakończenia |
| SMS-08 | `IncomingSmsHubPermissionTest` – jednostkowy |
| SMS-09 | `AapsSmsSenderTest` – jednostkowy (adres, tekst, tablica bez zmian, zmiana numeru); urządzenie: W1, W5 |
| SMS-10 | `MultipartSendTrackerTest` i `AapsSmsSenderResultTest` – jednostkowe (`FakeSmsSystemApi` zgłasza wyniki ręcznie); urządzenie: W2 |
| SMS-11 | `AapsSmsSenderCancelTest` – jednostkowy: anulowanie, `unregister`, spóźniony wynik |
| SMS-12 | `AapsSmsSenderFailureTest` – jednostkowy |
| SMS-13 | `SmsSenderRetentionTest` – jednostkowy, refleksja na `AapsSmsSender` i `AndroidSmsSystemApi` (ładowanie klasy bez wywoływania metod Androida), `toString()` |
| SMS-14 | `SmsPermissionsTest` – jednostkowy (lista `REQUIRED`); urządzenie: stan przyznany/odrzucony |
| SMS-15 | `SmsManifestTest` – jednostkowy JVM, parsuje `src/main/AndroidManifest.xml` (katalog roboczy testów = moduł `app`) |
| SMS-16 | `SmsRedactionTest` – jednostkowy: `toString()` `AapsNumber`, `RawSmsPart`, `AssembledSms`, `DeliveredSmsRegistry`, `IncomingSmsHub`, `AapsSmsSender` |

Brak wywołań API logowania w `sms` i brak importów `android.*` w pakiecie `sms` (poza `sms.platform`) sprawdza recenzja kodu.

Weryfikacja na urządzeniu (W1–W7 ze spec) jest ręczna (decyzja P3) i odbywa się, gdy powstanie `ui`; wynik (telefon, wersja Androida, wynik każdego punktu) opisuje pierwszy PR `ui`, który uruchamia wysyłanie i odbiór. Zadania 5 i 6 kończą się bez niej: testy JVM, `testDebugUnitTest lintDebug assembleDebug`.

## Podział na zadania

Kolejność wynika z zależności. Każde zadanie to jeden PR, ok. 200–400 linii z testami. Zadania 1, 2 i 4 mają rozłączne pliki i mogą iść równolegle (4 potrzebuje tylko `AapsNumber` z 1 – gdy 1 nie jest scalone, 4 czeka albo startuje po nim); 5 może iść równolegle z 4.

1. **Numer AAPS i uprawnienia (interfejs)** – `AapsNumber`, `AapsNumberProvider`, `SmsPermissions` (interfejs i stałe) + `AapsNumberParseTest`, `AapsNumberSenderMatchTest`, `SmsPermissionsTest`, część `SmsRedactionTest`, `FakeSmsPermissions`. Wymagania: SMS-01, SMS-02, SMS-14 (część), SMS-16 (część). Bez zależności.
2. **Składanie części i deduplikacja** – `RawSmsPart`, `AssembledSms`, `SmsAssembler`, `DeliveredSmsRegistry` + `SmsAssemblerTest`, `DeliveredSmsRegistryTest`, część `SmsRedactionTest`. Wymagania: SMS-03, SMS-06 (część), SMS-16 (część). Bez zależności; równolegle z 1.
3. **Koncentrator odbioru** – `SmsBroadcastRegistrar`, `IncomingSmsHub` + `FakeSmsBroadcastRegistrar`, `IncomingSmsHubFilterTest`, `IncomingSmsHubTimestampTest`, `IncomingSmsHubDedupTest`, `IncomingSmsHubSubscriptionTest`, `IncomingSmsHubPermissionTest`, część `SmsRedactionTest`. Wymagania: SMS-04, SMS-05, SMS-06 (reszta), SMS-07, SMS-08, SMS-16 (część). Zależy od 1 i 2.
4. **Port wysyłania (logika)** – `SmsSystemApi`, `SentResultsRegistration`, `MultipartSendTracker`, `AapsSmsSender` + `FakeSmsSystemApi`, `AapsSmsSenderTest`, `MultipartSendTrackerTest`, `AapsSmsSenderResultTest`, `AapsSmsSenderCancelTest`, `AapsSmsSenderFailureTest`, `SmsSenderRetentionTest` (część: `AapsSmsSender`), część `SmsRedactionTest`. Wymagania: SMS-09 (część), SMS-10, SMS-11, SMS-12, SMS-13 (część), SMS-16 (część). Zależy od 1; równolegle z 2 i 3.
5. **Platforma: odbiór, uprawnienia, manifest** – `sms.platform.AndroidSmsBroadcastRegistrar`, `AndroidSmsPermissions`, wpisy `uses-permission` w `AndroidManifest.xml` + `SmsManifestTest`; W3, W4, W6 i stan uprawnień (SMS-14) sprawdzane ręcznie, gdy powstanie `ui` (decyzja P3). Wymagania: SMS-14 (reszta), SMS-15, SMS-07 i SMS-03 (część urządzeniowa). Zależy od 3.
6. **Platforma: wysyłanie i Hilt** – `sms.platform.AndroidSmsSystemApi` z rejestracją wyników, `SmsModule`, `otp.ClockModule` (jeśli brak wiązania `EpochClock`) + `SmsSenderRetentionTest` (reszta: `AndroidSmsSystemApi`); `assembleDebug`; W1, W2, W5, W7 sprawdzane ręcznie, gdy powstanie `ui` (decyzja P3). Wymagania: SMS-09 (reszta), SMS-10 (część urządzeniowa), SMS-13 (reszta). Zależy od 3, 4, 5.

Rozstrzygnięcia P1–P3 nie dodają wymagań `SMS-xx`: P1 to ryzyko zaakceptowane (spec, Bezpieczeństwo; W7), P2 to założenie dla spec `ui` (pierwszy plan, ekran włączony), P3 – ręczna weryfikacja W1–W7.
