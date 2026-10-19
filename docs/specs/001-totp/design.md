# Design 001: Generator TOTP

Dotyczy: [spec.md](spec.md)

## Przegląd

Cała funkcja leży w pakiecie `pl.twojanazwa.aapsremote.otp` i jest czystym Kotlinem/JVM (bez `android.*`), zgodnie z [AGENTS.md](../../../AGENTS.md#architektura). Z JDK używa tylko `javax.crypto.Mac` (`HmacSHA1`) i `javax.crypto.spec.SecretKeySpec`, dostępnych także na Androidzie. Czekanie realizuje `kotlinx.coroutines.delay`, więc testy działają w czasie wirtualnym `runTest`.

Składniki:

- `EpochClock` – wstrzykiwane źródło czasu (TOTP-04).
- `TotpSecret`, `TotpCode` – typy z redagowanym `toString()` (TOTP-09).
- `TotpGenerator` – czysta funkcja: sekret + chwila → kod i okno ważności (TOTP-01, TOTP-03).
- `UsableTotpProvider` – zegar + reguła 5000 ms + czekanie (TOTP-02).
- `Base32` – dekoder RFC 4648 (TOTP-05).
- `OtpauthParser` – tekst URI → `TotpSecret` albo `OtpauthError` (TOTP-06, TOTP-07, TOTP-08).

Konsumenci: konfiguracja (wynik skanu QR → `OtpauthParser`), `security` (zapis i odczyt bajtów sekretu, poza tą specyfikacją), `session` (`UsableTotpProvider.awaitUsableCode` przed doklejeniem PIN-u).

## Interfejsy

```kotlin
package pl.twojanazwa.aapsremote.otp

/** Milisekundy od epoki Unix. Produkcyjnie: System.currentTimeMillis(). */
fun interface EpochClock {
    fun nowEpochMillis(): Long
}

/** Sekret TOTP. Kopiuje bajty, nie ujawnia ich w toString(). */
class TotpSecret private constructor(private val key: ByteArray) {
    /** Dostęp do kopii bajtów dla `security` przy zapisie; wywołujący zeruje kopię po użyciu. */
    fun copyBytes(): ByteArray = key.copyOf()

    override fun toString(): String = "TotpSecret(***)"

    companion object {
        /** Kopiuje [bytes]; wywołujący może je potem wyzerować. Rzuca IllegalArgumentException dla pustej tablicy. */
        fun fromBytes(bytes: ByteArray): TotpSecret
    }
}

/** Kod 6-cyfrowy z oknem ważności [validFromEpochMillis, validUntilEpochMillis). */
class TotpCode(
    val value: String,
    val validFromEpochMillis: Long,
    val validUntilEpochMillis: Long,
) {
    fun remainingMillis(nowEpochMillis: Long): Long = validUntilEpochMillis - nowEpochMillis
    override fun toString(): String =
        "TotpCode(***, validUntilEpochMillis=$validUntilEpochMillis)"
}

data class TotpWindow(
    val validFromEpochMillis: Long,
    val validUntilEpochMillis: Long,
    val remainingMillis: Long,
)

object TotpGenerator {
    const val PERIOD_MILLIS: Long = 30_000
    const val DIGITS: Int = 6

    /** TOTP-03. Rzuca IllegalArgumentException dla epochMillis < 0 lub > Long.MAX_VALUE - PERIOD_MILLIS. */
    fun window(epochMillis: Long): TotpWindow

    /** TOTP-01. Rzuca IllegalArgumentException dla epochMillis < 0 lub > Long.MAX_VALUE - PERIOD_MILLIS. */
    fun generate(secret: TotpSecret, epochMillis: Long): TotpCode
}

class UsableTotpProvider(
    private val clock: EpochClock,
) {
    /** TOTP-02. Zwraca kod z co najmniej [MIN_REMAINING_MILLIS] ważności wg [clock]. Anulowalne. */
    suspend fun awaitUsableCode(secret: TotpSecret): TotpCode

    companion object {
        const val MIN_REMAINING_MILLIS: Long = 5_000
    }
}

object Base32 {
    fun decode(text: String): Base32Result
}

sealed interface Base32Result {
    /** [bytes] należy wyzerować po użyciu. Brak data class – toString() redagowane. */
    class Success(val bytes: ByteArray) : Base32Result {
        override fun toString(): String = "Base32Result.Success(***)"
    }
    data object Empty : Base32Result
    data object Invalid : Base32Result
}

object OtpauthParser {
    fun parse(uri: String): OtpauthResult
}

sealed interface OtpauthResult {
    class Success(val secret: TotpSecret) : OtpauthResult {
        override fun toString(): String = "OtpauthResult.Success(***)"
    }
    data class Failure(val error: OtpauthError) : OtpauthResult
}

/** Rodzaje błędów bez żadnych danych z wejścia (TOTP-09). */
enum class OtpauthError {
    NOT_OTPAUTH_URI,        // „to nie jest URI otpauth”
    MALFORMED_URI,          // „nieprawidłowy URI otpauth” (np. złe %XX)
    UNSUPPORTED_TYPE,       // „nieobsługiwany typ”
    MISSING_SECRET,         // „brak sekretu”
    DUPLICATE_PARAMETER,    // „zduplikowany parametr”
    EMPTY_SECRET,           // „pusty sekret”
    INVALID_BASE32,         // „nieprawidłowy Base32”
    UNSUPPORTED_ALGORITHM,  // „nieobsługiwany algorytm”
    UNSUPPORTED_DIGITS,     // „nieobsługiwana liczba cyfr”
    UNSUPPORTED_PERIOD,     // „nieobsługiwany okres”
}
```

Sygnatury są wiążące dla nazw i typów publicznych; ciała i prywatne pomocniki zależą od wykonawcy.

## Model danych i stany

Algorytm `TotpGenerator.generate` (TOTP-01):

1. `require(epochMillis in 0..(Long.MAX_VALUE - 30_000))` z komunikatem bez danych, np. `"epochMillis out of range"`.
2. `counter = epochMillis / 30_000` (dzielenie całkowite na `Long`, dla `t >= 0` równe `floor`).
3. `msg = 8 bajtów big-endian(counter)`.
4. `hmac = HmacSHA1(key, msg)` (20 bajtów); `offset = hmac[19] and 0x0F`.
5. `binary = ((hmac[offset] and 0x7F) shl 24) or ((hmac[offset+1] and 0xFF) shl 16) or ((hmac[offset+2] and 0xFF) shl 8) or (hmac[offset+3] and 0xFF)`.
6. `value = (binary % 1_000_000).toString().padStart(6, '0')`.
7. Okno: `validFrom = counter * 30_000`, `validUntil = validFrom + 30_000`.

Po użyciu tablica `msg` i `hmac` są zerowane (`fill(0)`).

`UsableTotpProvider.awaitUsableCode` – pętla, bez stanu między wywołaniami:

| Stan | Zdarzenie | Nowy stan | Akcja | Wymaganie |
| --- | --- | --- | --- | --- |
| Odczyt | `now = clock.nowEpochMillis()`, `remaining = window(now).remainingMillis >= 5000` | Zakończony | zwróć `generate(secret, now)` | TOTP-02 |
| Odczyt | `remaining < 5000` | Czekanie | `delay(remaining)` | TOTP-02 |
| Czekanie | upłynęło `remaining` ms | Odczyt | ponowny odczyt zegara | TOTP-02, TOTP-04 |
| Czekanie | anulowanie korutyny | Anulowany | `CancellationException` | TOTP-02 |

Kod zwracany jest zawsze z chwili odczytanej w tym samym przebiegu pętli, więc niezmiennik „zwrócony kod ma ≥ 5000 ms ważności wg zegara” wynika z warunku przejścia. Zatrzymany zegar daje nieskończoną pętlę z `delay`, przerywaną tylko anulowaniem – limit czasu całego polecenia należy do `session`.

`OtpauthParser.parse` – kolejność kontroli (pierwszy niespełniony warunek wyznacza błąd):

1. `trim()` białych znaków ASCII.
2. Prefiks `otpauth://` bez względu na wielkość liter → inaczej `NOT_OTPAUTH_URI`.
3. Typ = tekst do pierwszego `/` lub `?`; `totp` bez względu na wielkość liter → inaczej `UNSUPPORTED_TYPE`.
4. Zapytanie po pierwszym `?`; podział na `&`; element bez `=` → `MALFORMED_URI`; każdy element dzielony na pierwszym `=` (wartości Base32 z `=` dopełnienia pozostają nienaruszone). Dekodowanie `%XX` (UTF-8) nazw i wartości; błąd dekodowania → `MALFORMED_URI`. `+` nie jest zamieniany na spację.
5. Duplikat `secret`/`algorithm`/`digits`/`period` → `DUPLICATE_PARAMETER`.
6. Brak `secret` → `MISSING_SECRET`.
7. `algorithm` (jeśli jest) ≠ `SHA1` bez względu na wielkość liter → `UNSUPPORTED_ALGORITHM`.
8. `digits` (jeśli jest) ≠ dokładnie `"6"` → `UNSUPPORTED_DIGITS`.
9. `period` (jeśli jest) ≠ dokładnie `"30"` → `UNSUPPORTED_PERIOD`.
10. `Base32.decode(secret)`: `Empty` → `EMPTY_SECRET`, `Invalid` → `INVALID_BASE32`.
11. Brak sprawdzania minimalnej długości – każdy niepusty sekret jest przyjmowany (TOTP-08).
12. `TotpSecret.fromBytes(bytes)`, potem `bytes.fill(0)`; `Success`.

Etykieta, `issuer` i parametry o nieznanych nazwach są pomijane bez błędu (TOTP-06).

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| Własna implementacja RFC 6238 | biblioteka `kotlin-onetimepassword` | Algorytm to ok. 30 linii na `javax.crypto.Mac`; brak dodatkowej zależności w aplikacji z sekretem medycznym (mniejsza powierzchnia ataku i mniej aktualizacji do śledzenia); pełna kontrola nad tym, czy sekret trafia do `toString()`/wyjątków (TOTP-09); zgodność potwierdzają testy na wektorach RFC 6238. Biblioteka i tak wymagałaby własnego parsera URI z walidacją TOTP-07 i własnej reguły 5000 ms. |
| Własny parser `otpauth://` zamiast `java.net.URI` / `android.net.Uri` | `java.net.URI`, `android.net.Uri` | `URISyntaxException` zawiera w komunikacie całe wejście (z sekretem) – łamie TOTP-09; `android.net.Uri` łamie zasadę braku zależności od Androida. Format jest prosty i w pełni opisany w TOTP-06. |
| Własny dekoder Base32 | Apache Commons Codec | Commons Codec dekoduje „łagodnie” (pomija niepoprawne znaki), co łamie TOTP-05; to kolejna zależność. Dekoder ma ok. 40 linii. |
| `EpochClock` jako `fun interface` z `Long` | `java.time.Clock`, `kotlin.time.TimeSource` | Testy z `runTest` łatwo wiążą zegar z czasem wirtualnym: `EpochClock { base + testScheduler.currentTime }`. `java.time.Clock` wymagałby podklasy; `TimeSource` mierzy czas względny, a TOTP potrzebuje czasu epoki. |
| Wynik parsera jako `sealed`/`enum`, bez wyjątków | wyjątki z komunikatami | Błędy to oczekiwane przypadki (zły QR); enum nie może przenosić fragmentów wejścia (TOTP-09); UI mapuje enum na komunikat. |
| `TotpSecret`/`TotpCode` jako zwykłe klasy, nie `data class` | `data class`, `value class` | `data class` generuje `toString()` z polami; `value class` nad `ByteArray` nie pozwala nadpisać `toString()` w sposób gwarantowany przy boxingu i porównuje tablice po referencji. |
| Kod jako `String` | `CharArray` z zerowaniem | Kod żyje do 30 s, jest wysyłany SMS-em w jawnej postaci i i tak trafia do `String` przy budowie treści SMS; zerowanie nic nie daje. PIN (inna spec) jest traktowany ostrzej. |
| Próg 5000 ms jako parametr konstruktora z domyślną stałą | stała na sztywno | Testy mogą sprawdzić granicę wprost; produkcja używa stałej z planu. Wartość nie jest ustawieniem użytkownika. |
| `TotpSecret` bez metody `wipe()` | `Closeable` z zerowaniem | Cykl życia sekretu w pamięci określi spec `security`; tu tylko kopia przy tworzeniu i brak publicznej tablicy. Można dodać w zmianie spec. |
| Uwaga: `SecretKeySpec` kopiuje klucz wewnętrznie | – | Kopii w JCA nie da się wyzerować; akceptowane, bo sekret i tak jest w pamięci procesu przy liczeniu kodu. |

## Mapowanie wymagań na testy

Wszystkie testy jednostkowe JUnit 5 w `app/src/test/java/pl/twojanazwa/aapsremote/otp/`, nazwy zawierają identyfikator wymagania.

| Wymaganie | Test (klasa / rodzaj) |
| --- | --- |
| TOTP-01 | `TotpGeneratorTest` – jednostkowy, parametryzowany 6 wektorami RFC 6238 + przypadki 59000/59999 ms |
| TOTP-02 | `UsableTotpProviderTest` – jednostkowy, `runTest` + zegar na `testScheduler.currentTime`; granice 5000/4999 ms, zatrzymany zegar (z `withTimeoutOrNull` w czasie wirtualnym), anulowanie |
| TOTP-03 | `TotpGeneratorTest` – jednostkowy, `window()` dla chwil z spec |
| TOTP-04 | `TotpGeneratorTest` (chwila −1) i `UsableTotpProviderTest` (stały zegar) – jednostkowe |
| TOTP-05 | `Base32Test` – jednostkowy, wektory RFC 4648 i przypadki błędne |
| TOTP-06 | `OtpauthParserTest` – jednostkowy |
| TOTP-07 | `OtpauthParserTest` – jednostkowy, parametryzowany wartościami z spec |
| TOTP-08 | `OtpauthParserTest` – jednostkowy |
| TOTP-09 | `OtpRedactionTest` – jednostkowy: `toString()` sekretu, kodu, wyników parsera i dekodera; komunikat wyjątku dla chwili −1; zerowanie tablicy wejściowej nie zmienia kodu |

Brak wywołań API logowania w `otp` (TOTP-09) sprawdza recenzja kodu; test nie jest wymagany.

## Podział na zadania

Kolejność wynika z zależności. Każde zadanie to jeden PR, ok. 200–400 linii z testami.

1. **Generator TOTP** – `EpochClock`, `TotpSecret`, `TotpCode`, `TotpWindow`, `TotpGenerator` + `TotpGeneratorTest` i część `OtpRedactionTest` (sekret, kod, wyjątek). Wymagania: TOTP-01, TOTP-03, TOTP-04 (część generatora), TOTP-09 (część).
2. **Kod z zapasem ważności** – `UsableTotpProvider` + `UsableTotpProviderTest`. Wymagania: TOTP-02, TOTP-04 (część dostawcy).
3. **Base32 i parser otpauth** – `Base32`, `Base32Result`, `OtpauthParser`, `OtpauthResult`, `OtpauthError` + `Base32Test`, `OtpauthParserTest`, reszta `OtpRedactionTest`. Wymagania: TOTP-05, TOTP-06, TOTP-07, TOTP-08, TOTP-09 (część).
