# Spec 001: Generator TOTP

- **Status:** szkic
- **Prefiks wymagań:** `TOTP`
- **Pakiety:** `otp`
- **Powiązane:** [docs/plan.md](../../plan.md#wybór-technologii) (TOTP), [docs/plan.md](../../plan.md#przepływ-działania) (krok 6 i reguła 5 s), [docs/plan.md](../../plan.md#bezpieczeństwo); przyszłe specyfikacje `security` (przechowywanie sekretu), `session` (użycie kodu), konfiguracja (skan QR)

## Cel

Aplikacja ma sama liczyć kod TOTP zgodny z AAPS, żeby użytkownik nie przepisywał kodu z Authenticatora. Ta specyfikacja opisuje czystą logikę w Kotlinie: odczyt sekretu z tekstu URI `otpauth://`, obliczenie kodu wg RFC 6238 i wybór kodu, który ma jeszcze co najmniej 5 s ważności.

## Zakres

- Obliczanie kodu TOTP wg RFC 6238: HMAC-SHA1, krok 30 s, T0 = 0 (epoka Unix), 6 cyfr.
- Wyliczanie okna ważności kodu (początek, koniec, pozostały czas) dla zadanej chwili.
- Wybór kodu do wysłania: gdy bieżącemu kodowi zostało mniej niż 5000 ms, czekanie na kod z następnego kroku.
- Wstrzykiwane źródło czasu (epoch ms), żeby testy były deterministyczne.
- Dekodowanie sekretu z Base32 (RFC 4648).
- Parsowanie tekstu URI `otpauth://totp/...` (wynik skanu QR) do sekretu, z walidacją `algorithm`, `digits`, `period`.
- Typy sekretu i kodu, które nie ujawniają wartości w `toString()`, wyjątkach ani błędach.

**Poza zakresem:**

- Przechowywanie i szyfrowanie sekretu (Keystore, DataStore) – specyfikacja `security`.
- Skan kodu QR kamerą (CameraX, ML Kit) i ekran konfiguracji – specyfikacja konfiguracji. Tu wchodzi tylko tekst URI, który skaner zwraca.
- Ręczne wpisywanie sekretu przez użytkownika.
- Doklejanie PIN-u AAPS do kodu i wysyłanie SMS – specyfikacja `session`.
- Korekta zegara telefonu i komunikat o złym zegarze (np. po odrzuceniu kodu przez AAPS) – inna specyfikacja. Aplikacja nie zmienia czasu ani nie liczy przesunięcia zegara.
- Weryfikacja kodów (akceptowanie sąsiednich kroków) – aplikacja tylko generuje kody, weryfikuje AAPS.
- HOTP (RFC 4226 z licznikiem), algorytmy SHA-256/SHA-512, długość kodu inna niż 6, krok inny niż 30 s.
- Odczyt etykiety (`label`) i parametru `issuer` z URI – nie są walidowane ani zwracane.

## Wymagania

### TOTP-01: Kod TOTP wg RFC 6238

Dla sekretu (ciąg bajtów) i chwili `t` w milisekundach od epoki Unix generator zwraca kod 6-cyfrowy obliczony wg RFC 6238 z parametrami: HMAC-SHA1, krok X = 30 s, T0 = 0, licznik `T = floor(t / 30000)` zapisany jako 8 bajtów big-endian, dynamiczne obcinanie wg RFC 4226 rozdz. 5.3, wynik `mod 10^6`. Kod jest tekstem o długości dokładnie 6 znaków `0`–`9`, uzupełnionym zerami z lewej.

RFC 6238 w Dodatku B podaje kody 8-cyfrowe (`mod 10^8`). Ponieważ 10^6 dzieli 10^8, kod 6-cyfrowy to zawsze ostatnie 6 cyfr kodu 8-cyfrowego z RFC (np. `94287082` → `287082`).

**Kryteria akceptacji:**

- **Zakładając** sekret ASCII `12345678901234567890` (20 bajtów), **gdy** generator liczy kod dla każdej chwili z tabeli w sekcji „Dane testowe”, **wtedy** zwraca dokładnie podany kod 6-cyfrowy.
- **Zakładając** ten sam sekret, **gdy** chwila to 1111111109000 ms, **wtedy** kod to `081804` (zero wiodące zachowane, długość 6).
- **Zakładając** ten sam sekret, **gdy** chwila to 1234567890000 ms, **wtedy** kod to `005924`.
- **Zakładając** ten sam sekret, **gdy** chwile 59000 ms i 59999 ms (ten sam krok T = 1), **wtedy** kod jest ten sam: `287082`.
- **Zakładając** ten sam sekret, **gdy** chwila to 20000000000000 ms (rok 2603), **wtedy** kod to `353130` (licznik nie przepełnia się).

### TOTP-02: Kod bliski wygaśnięcia czeka na następny

Dostawca kodu do wysłania odczytuje czas z wstrzykniętego zegara. Jeśli bieżącemu kodowi zostało mniej niż 5000 ms ważności (TOTP-03), czeka do początku następnego kroku, ponownie odczytuje zegar i zwraca kod dla nowej chwili. Zwrócony kod ma co najmniej 5000 ms ważności względem chwili odczytanej z zegara w tym samym przebiegu, z której został policzony. Próg jest stały i nie da się go zmienić przez API. Gdy zostało dokładnie 5000 ms lub więcej, kod jest zwracany bez czekania.

**Kryteria akceptacji** (sekret jak w TOTP-01, zegar i opóźnienia w czasie wirtualnym testu):

- **Zakładając** zegar wskazujący 1111111111000 ms (pozostało 29000 ms), **gdy** zażądano kodu, **wtedy** zwrócony jest `050471` po 0 ms czekania.
- **Zakładając** zegar wskazujący 1111111105000 ms (pozostało dokładnie 5000 ms), **gdy** zażądano kodu, **wtedy** zwrócony jest `081804` po 0 ms czekania.
- **Zakładając** zegar wskazujący 1111111105001 ms (pozostało 4999 ms), **gdy** zażądano kodu, **wtedy** dostawca czeka 4999 ms i zwraca `050471` z końcem ważności 1111111140000 ms.
- **Zakładając** zegar wskazujący 1111111109000 ms (pozostało 1000 ms), **gdy** zażądano kodu, **wtedy** dostawca czeka 1000 ms i zwraca `050471`.
- **Zakładając** zegar, który po czekaniu nadal wskazuje chwilę z pozostałą ważnością poniżej 5000 ms (np. zegar zatrzymany na 1111111109000 ms), **gdy** zażądano kodu, **wtedy** dostawca nie zwraca kodu `081804`, tylko czeka dalej, aż anuluje go wywołujący.
- **Zakładając** trwające czekanie, **gdy** korutyna wywołującego zostanie anulowana, **wtedy** dostawca kończy się `CancellationException` bez zwracania kodu.

### TOTP-03: Okno ważności kodu

Dla chwili `t` (ms) generator podaje: początek ważności `floor(t / 30000) * 30000`, koniec ważności (wyłączny) `początek + 30000` i pozostały czas `koniec − t` w milisekundach, w zakresie 1–30000.

**Kryteria akceptacji:**

- **Zakładając** chwilę 1111111109000 ms, **gdy** liczone jest okno, **wtedy** początek = 1111111080000, koniec = 1111111110000, pozostało = 1000.
- **Zakładając** chwilę 1111111110000 ms (dokładnie początek kroku), **gdy** liczone jest okno, **wtedy** pozostało = 30000.
- **Zakładając** chwilę 1111111109999 ms, **gdy** liczone jest okno, **wtedy** pozostało = 1.
- **Zakładając** chwilę 0 ms, **gdy** liczone jest okno, **wtedy** początek = 0, koniec = 30000, pozostało = 30000.

### TOTP-04: Wstrzykiwane źródło czasu

Logika `otp` nie odczytuje zegara systemowego bezpośrednio. Czas pochodzi z wstrzykniętego źródła zwracającego milisekundy od epoki Unix. Chwila ujemna jest błędem. Chwila większa niż `Long.MAX_VALUE - 30_000` ms też jest błędem, bo koniec jej okna (TOTP-03) nie mieści się w `Long`.

**Kryteria akceptacji:**

- **Zakładając** źródło czasu zwracające stałą 1111111111000 ms (inną niż czas systemowy maszyny testowej), **gdy** dostawca z TOTP-02 liczy kod, **wtedy** zwraca `050471` – wynik zależy wyłącznie od wstrzykniętego źródła.
- **Zakładając** chwilę −1 ms, **gdy** generator liczy kod lub okno, **wtedy** rzuca `IllegalArgumentException`, którego komunikat nie zawiera sekretu ani żadnego kodu.
- **Zakładając** chwilę `Long.MAX_VALUE` ms, **gdy** generator liczy kod lub okno, **wtedy** rzuca `IllegalArgumentException`, którego komunikat nie zawiera sekretu ani żadnego kodu.

### TOTP-05: Dekodowanie sekretu Base32

Sekret z URI jest dekodowany z Base32 wg RFC 4648 rozdz. 6 (alfabet `A`–`Z`, `2`–`7`).

- Wielkość liter nie ma znaczenia (`gezd…` = `GEZD…`).
- Końcowe znaki `=` są opcjonalne i są pomijane. Znak `=` w innym miejscu jest błędem.
- Każdy inny znak (w tym spacja, `0`, `1`, `8`, `9`, `-`) jest błędem.
- Długość bez `=` dająca resztę 1, 3 lub 6 z dzielenia przez 8 jest błędem (niepełny bajt wg RFC 4648). Bity dopełnienia ostatniego znaku są ignorowane.
- Pusty tekst (także po usunięciu `=`) jest błędem.

**Kryteria akceptacji:**

- **Zakładając** tekst `GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ`, **gdy** jest dekodowany, **wtedy** wynik to 20 bajtów ASCII `12345678901234567890`.
- **Zakładając** ten sam tekst małymi literami, **gdy** jest dekodowany, **wtedy** wynik jest identyczny.
- **Zakładając** tekst `MZXW6===` oraz `MZXW6`, **gdy** są dekodowane, **wtedy** oba dają bajty ASCII `foo` (wektor z RFC 4648 rozdz. 10).
- **Zakładając** tekst `GEZD GNBV`, `GEZ1`, `GE=ZD` albo `G`, **gdy** jest dekodowany, **wtedy** wynikiem jest błąd „nieprawidłowy Base32”.
- **Zakładając** tekst pusty albo `====`, **gdy** jest dekodowany, **wtedy** wynikiem jest błąd „pusty sekret”.

### TOTP-06: Parsowanie URI otpauth

Parser przyjmuje tekst (wynik skanu QR) i zwraca sekret albo jawny błąd. Zasady:

- Białe znaki ASCII na początku i końcu tekstu są usuwane przed parsowaniem.
- Schemat `otpauth` i typ `totp` (część po `otpauth://` do pierwszego `/`) są porównywane bez względu na wielkość liter.
- Inny schemat albo brak `://` → błąd „to nie jest URI otpauth”.
- Typ `hotp` lub dowolny inny niż `totp` → błąd „nieobsługiwany typ”.
- Parametry zapytania (po pierwszym `?`, rozdzielone `&`, postać `nazwa=wartość`) są dekodowane z kodowania procentowego (`%XX`). Nazwy parametrów porównywane są dokładnie (małe litery).
- Element zapytania bez znaku `=` (np. `?secret`) → błąd „nieprawidłowy URI otpauth”.
- Parametr `secret` jest wymagany; jego wartość dekodowana jest wg TOTP-05.
- Parametr `secret`, `algorithm`, `digits` lub `period` podany więcej niż raz → błąd „zduplikowany parametr”.
- Nieprawidłowe kodowanie procentowe → błąd „nieprawidłowy URI otpauth”.
- Parametry o nieznanych nazwach (np. `image`, `counter` przy `totp`, pola niestandardowe) są ignorowane. Jawny błąd dotyczy tylko nieobsługiwanych wartości `algorithm`, `digits`, `period` (TOTP-07).

**Kryteria akceptacji:**

- **Zakładając** tekst `otpauth://totp/Przyklad:test?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Przyklad`, **gdy** jest parsowany, **wtedy** wynikiem jest sekret równy bajtom ASCII `12345678901234567890`.
- **Zakładając** ten sam tekst z wielkimi literami `OTPAUTH://TOTP/...` i spacją oraz znakiem nowej linii na końcu, **gdy** jest parsowany, **wtedy** wynik jest ten sam.
- **Zakładając** tekst z `secret=MZXW6%3D%3D%3D`, **gdy** jest parsowany, **wtedy** sekret to bajty ASCII `foo`.
- **Zakładając** tekst `https://example.com/?secret=GEZDGNBV`, **gdy** jest parsowany, **wtedy** wynikiem jest błąd „to nie jest URI otpauth”.
- **Zakładając** tekst `otpauth://hotp/Przyklad:test?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&counter=0`, **gdy** jest parsowany, **wtedy** wynikiem jest błąd „nieobsługiwany typ”.
- **Zakładając** tekst `otpauth://totp/Przyklad:test?issuer=Przyklad` (bez `secret`), **gdy** jest parsowany, **wtedy** wynikiem jest błąd „brak sekretu”.
- **Zakładając** tekst z dwoma parametrami `secret`, **gdy** jest parsowany, **wtedy** wynikiem jest błąd „zduplikowany parametr”.
- **Zakładając** tekst z `secret=GEZ1`, **gdy** jest parsowany, **wtedy** wynikiem jest błąd „nieprawidłowy Base32”.
- **Zakładając** tekst z `secret=%ZZ`, **gdy** jest parsowany, **wtedy** wynikiem jest błąd „nieprawidłowy URI otpauth”.
- **Zakładając** tekst `otpauth://totp/Przyklad:test?secret`, **gdy** jest parsowany, **wtedy** wynikiem jest błąd „nieprawidłowy URI otpauth”.
- **Zakładając** tekst `otpauth://totp/Przyklad:test?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&image=https%3A%2F%2Fexample.com%2Flogo.png&nieznany=1`, **gdy** jest parsowany, **wtedy** wynikiem jest sekret równy bajtom ASCII `12345678901234567890` (nieznane parametry pominięte, bez błędu).

### TOTP-07: Walidacja parametrów algorytmu

Aplikacja obsługuje tylko HMAC-SHA1, 6 cyfr i krok 30 s. Brakujący parametr przyjmuje wartość domyślną z formatu Key Uri (Google Authenticator): `algorithm=SHA1`, `digits=6`, `period=30`. Podany parametr z inną wartością daje jawny błąd; parser nigdy nie zwraca sekretu z cichym przyjęciem innych ustawień.

**Kryteria akceptacji:**

- **Zakładając** URI z `secret` i bez `algorithm`, `digits`, `period`, **gdy** jest parsowany, **wtedy** wynikiem jest sekret.
- **Zakładając** URI z `algorithm=SHA1&digits=6&period=30`, **gdy** jest parsowany, **wtedy** wynikiem jest sekret.
- **Zakładając** URI z `algorithm=sha1`, **gdy** jest parsowany, **wtedy** wynikiem jest sekret (wartość algorytmu bez względu na wielkość liter).
- **Zakładając** URI z `algorithm=SHA256` albo `algorithm=SHA512` albo `algorithm=` (pusta wartość), **gdy** jest parsowany, **wtedy** wynikiem jest błąd „nieobsługiwany algorytm”.
- **Zakładając** URI z `digits=8`, `digits=06`, `digits=` albo `digits=abc`, **gdy** jest parsowany, **wtedy** wynikiem jest błąd „nieobsługiwana liczba cyfr”.
- **Zakładając** URI z `period=60`, `period=030`, `period=` albo `period=-30`, **gdy** jest parsowany, **wtedy** wynikiem jest błąd „nieobsługiwany okres”.

### TOTP-08: Brak minimalnej długości sekretu

Każdy niepusty sekret (co najmniej 1 bajt po dekodowaniu Base32) jest przyjmowany. Aplikacja nie narzuca minimalnej długości, żeby nie zablokować sekretu wygenerowanego przez AAPS. Pusty sekret odrzuca TOTP-05.

**Kryteria akceptacji:**

- **Zakładając** URI z `secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ` (20 bajtów), **gdy** jest parsowany, **wtedy** wynikiem jest sekret.
- **Zakładając** URI z sekretem 16 bajtów (`secret=GEZDGNBVGY3TQOJQGEZDGNBVGY======`, czyli pierwsze 16 bajtów sekretu z RFC: ASCII `1234567890123456`), **gdy** jest parsowany, **wtedy** wynikiem jest sekret.
- **Zakładając** URI z `secret=MZXW6` (3 bajty), **gdy** jest parsowany, **wtedy** wynikiem jest sekret równy bajtom ASCII `foo`.
- **Zakładając** URI z `secret=====`, **gdy** jest parsowany, **wtedy** wynikiem jest błąd „pusty sekret”.

### TOTP-09: Brak ujawniania sekretu i kodów

Sekret i kody nie trafiają do logów, wyjątków, `toString()` ani wyników błędów.

- Typ sekretu i typ kodu nadpisują `toString()` stałym tekstem bez wartości (np. `TotpSecret(***)`).
- Błędy parsera i dekodera Base32 nie zawierają żadnego fragmentu wejściowego tekstu (ani sekretu, ani całego URI, ani wartości parametrów).
- Wyjątki rzucane przez pakiet `otp` nie zawierają sekretu, kodu ani wejściowego URI; wyjątki z bibliotek, które mogłyby je zawierać, nie są przepuszczane dalej.
- Pakiet `otp` nie wywołuje żadnego API logowania (`android.util.Log`, `println`, `System.out`/`err`, loggerów).
- Typ sekretu kopiuje bajty przy tworzeniu i nie udostępnia swojej wewnętrznej tablicy; jedyny dostęp do bajtów to nowa kopia (dla zapisu przez `security`), którą wywołujący zeruje po użyciu.

**Kryteria akceptacji:**

- **Zakładając** sekret z TOTP-01, **gdy** wywołano `toString()` na sekrecie, **wtedy** wynik nie zawiera `GEZDGNBV`, `12345678901234567890` ani żadnego bajtu sekretu w postaci szesnastkowej.
- **Zakładając** kod `287082`, **gdy** wywołano `toString()` na kodzie, **wtedy** wynik nie zawiera `287082`.
- **Zakładając** każdy błędny URI z TOTP-06, TOTP-07 i TOTP-08, **gdy** jest parsowany, **wtedy** `toString()` wyniku nie zawiera wartości parametru `secret` ani pełnego wejściowego tekstu.
- **Zakładając** tablicę bajtów przekazaną do utworzenia sekretu, **gdy** po utworzeniu tablica zostanie wyzerowana, **wtedy** kod liczony z sekretu się nie zmienia.

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Chwila ujemna lub większa niż `Long.MAX_VALUE - 30_000` | `IllegalArgumentException` bez sekretu i kodu w komunikacie | TOTP-04 |
| Zegar po czekaniu nadal w końcówce kroku | dalsze czekanie, nigdy zwrot kodu z < 5000 ms ważności | TOTP-02 |
| Anulowanie podczas czekania | `CancellationException`, brak kodu | TOTP-02 |
| Tekst nie jest URI `otpauth://` | błąd „to nie jest URI otpauth” | TOTP-06 |
| Typ inny niż `totp` (np. `hotp`) | błąd „nieobsługiwany typ” | TOTP-06 |
| Brak `secret` | błąd „brak sekretu” | TOTP-06 |
| Zduplikowany `secret`/`algorithm`/`digits`/`period` | błąd „zduplikowany parametr” | TOTP-06 |
| Złe kodowanie procentowe | błąd „nieprawidłowy URI otpauth” | TOTP-06 |
| Niepoprawny Base32 | błąd „nieprawidłowy Base32” | TOTP-05 |
| Pusty sekret | błąd „pusty sekret” | TOTP-05 |
| `algorithm` ≠ SHA1 | błąd „nieobsługiwany algorytm” | TOTP-07 |
| `digits` ≠ 6 | błąd „nieobsługiwana liczba cyfr” | TOTP-07 |
| `period` ≠ 30 | błąd „nieobsługiwany okres” | TOTP-07 |

Teksty w cudzysłowach to nazwy rodzajów błędu, nie gotowe komunikaty UI. Komunikaty dla użytkownika określi specyfikacja konfiguracji.

## Bezpieczeństwo

Zasady z [AGENTS.md](../../../AGENTS.md#bezpieczeństwo), które dotyczą tej funkcji:

- **Nigdy nie loguj sekretu TOTP ani kodów** – TOTP-09: redagowane `toString()`, błędy bez fragmentów wejścia, zakaz API logowania w pakiecie `otp`.
- **W testach TOTP tylko wektory z RFC 6238** – kody w testach pochodzą z RFC 6238 Dodatek B; sekret to sekret z RFC. Wektor Base32 `MZXW6` (`foo`) pochodzi z RFC 4648 i służy tylko do testów dekodera i odrzucania krótkiego sekretu, nie do liczenia kodów.
- **Żadnych prawdziwych sekretów w repo** – URI w testach mają fikcyjną etykietę `Przyklad:test` i sekret z RFC.
- **PIN AAPS** – pakiet `otp` nie przyjmuje ani nie przechowuje PIN-u; doklejanie PIN-u należy do `session`.
- **Brak omijania limitów i zegara** – aplikacja nie koryguje zegara i nie generuje kodów z przesunięciem czasu.

## Dane testowe

Źródło: RFC 6238, Dodatek B (wiersze dla SHA1). Sekret: ASCII `12345678901234567890` (20 bajtów), w Base32 `GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ`. Kod 6-cyfrowy = ostatnie 6 cyfr kodu z RFC.

| Czas (s) | Chwila (ms) | T (hex) | Kod RFC (8 cyfr) | Kod 6 cyfr |
| --- | --- | --- | --- | --- |
| 59 | 59000 | 0000000000000001 | 94287082 | 287082 |
| 1111111109 | 1111111109000 | 00000000023523EC | 07081804 | 081804 |
| 1111111111 | 1111111111000 | 00000000023523ED | 14050471 | 050471 |
| 1234567890 | 1234567890000 | 000000000273EF07 | 89005924 | 005924 |
| 2000000000 | 2000000000000 | 0000000003F940AA | 69279037 | 279037 |
| 20000000000 | 20000000000000 | 0000000027BC86AA | 65353130 | 353130 |

Wektory Base32 z RFC 4648 rozdz. 10: `MZXW6===` → `foo`.

Fikcyjne URI do testów parsera (etykieta i wydawca zmyślone):

```text
otpauth://totp/Przyklad:test?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Przyklad
otpauth://totp/Przyklad:test?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&algorithm=SHA1&digits=6&period=30
```

Dane są na tyle krótkie, że leżą w tej specyfikacji; katalog `dane/` nie jest potrzebny.

## Otwarte pytania

- [x] **1. Minimalna długość sekretu (TOTP-08).** Rozstrzygnięte: brak minimum, wystarczy niepusty sekret.
- [x] **2. Nieznane parametry URI (TOTP-06).** Rozstrzygnięte: nieznane nazwy parametrów są ignorowane; jawny błąd tylko dla nieobsługiwanych wartości `algorithm`, `digits`, `period` (TOTP-07).
- [ ] **3. Format QR z AAPS.** Czy URI z QR AAPS ma postać `otpauth://totp/...?...secret=...` i czy zawiera `algorithm`/`digits`/`period`? Wystarczy struktura z zamazanym sekretem – pozwoli potwierdzić, że TOTP-06 i TOTP-07 przyjmą prawdziwy QR.
