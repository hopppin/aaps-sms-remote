# Spec 013: Profil – stan i zmiana profilu

- **Status:** zatwierdzona
- **Prefiks wymagań:** `PROF`
- **Pakiety:** `commands`, `parser`, `session`, `data` / `data.platform` (historia), `ui.commands`, `ui.history`
- **Powiązane:** [Spec 002: Parser](../002-parser-odpowiedzi/spec.md); [Spec 003: Katalog](../003-katalog-polecen/spec.md); [Spec 004: Sesja](../004-sesja-polecenia/spec.md) (A1 – A7, SESSION-06, SESSION-08, SESSION-11, SESSION-13, SESSION-22); [Spec 007: Dane](../007-dane/spec.md); [Spec 009: UI – polecenia](../009-ui-polecenia/spec.md); [Spec 010: UI – historia](../010-ui-historia/spec.md); [Spec 012: Bolus](../012-bolus/spec.md) (interfejsy znacznikowe parsera, migracja 1 → 2)

## Cel

Użytkownik sprawdza z aplikacji aktualny profil AAPS (`PROFILE STATUS`) i zmienia procent profilu 1: wpisuje procent, po podsumowaniu z PIN-em aplikacja wysyła `PROFILE 1 <procent>` i odsyła kod tylko na prośbę AAPS o zmianę profilu z dokładnie tym procentem.

## Zakres

- Wpisy katalogu: `PROFILE STATUS` (bez kodu, jednym dotknięciem), zmiana profilu `PROFILE 1 <procent>` (z kodem, formularz procentu i podsumowanie z PIN-em). Wpis `PROFILE LIST` zostaje w katalogu, parserze i sesji, ale aplikacja nie ma przycisku, który go wysyła (zmiana 2026-10-30).
- Parser: odpowiedź na `PROFILE STATUS`, lista profili (bez użycia w UI), prośba o kod zmiany profilu (porównanie procentu), „Zly kod”, potwierdzenie `Zmiana profilu wykonana` i powiadomienie `Profil bazowy w pompie uaktualniony` (zmiana 2026-10-31).
- Sesja: `PROFILE STATUS` jak `BG`; zmiana profilu jak polecenie z kodem, z potwierdzeniem po kodzie (PROF-19, PROF-20).
- UI: przyciski „Aktualny profil” i „Zmień profil” w panelu, formularz procentu, podsumowanie z PIN-em, przebieg i wynik.
- Historia: polecenia profilu, numer i procent profilu, migracja bazy 2 → 3.

**Poza zakresem:**

- Wybór profilu innego niż 1 i pobieranie listy profili z UI (decyzja użytkownika, 2026-10-30).
- Zmiana profilu z czasem trwania albo przesunięciem czasu (AAPS i tak tworzy zmianę bez czasu trwania).
- Rozpoznawanie odmów i porażek innych niż `Zly kod` (`Nieprawidlowy profil!` po kodzie, `Nie skonfigurowano`, `Nie zaladowano profilu z NS`, `Zdalne komendy nie sa dozwolone`, przekroczony czas kolejki pompy po kodzie) – dają „nierozpoznana” (przed kodem stop, po kodzie „nieznany”); „Przyszłe zmiany”.
- Limit procentu jako ustawienie (stała w aplikacji; P3).
- Usunięcie nieużywanego polecenia `PROFILE LIST` z katalogu, parsera i sesji („Przyszłe zmiany”).

## Fakty o AAPS (źródło)

Źródło: SMS-y z telefonu użytkownika (pliki w [dane/](dane/README.md)), kod `SmsCommunicatorPlugin` przekazany przez użytkownika oraz analiza kodu AndroidAPS 3.4.2.6 (tag `3.4.2.6`, commit `598e2eb`) z 2026-10-31 – [dane/aaps-kod-zrodlowy.md](dane/aaps-kod-zrodlowy.md). Fakty oznaczone „z kodu AAPS 3.4.2.6 (nie z telefonu)” wynikają z kodu i zasobów `values-pl-rPL` po `stripAccents` (AAPS wysyła SMS bez znaków diakrytycznych, `ł` → `l`); nie były obserwowane na telefonie.

| Nr | Fakt | Źródło | Skutek |
| --- | --- | --- | --- |
| R1 | `PROFILE LIST` → lista numerowana `N. nazwa`, jedna pozycja w linii (`1. LocalProfile1`, `2. LocalProfile2`); bez kodu. | SMS (podział linii – P1) | PROF-07 (parser bez użycia w UI). |
| R2 | `PROFILE STATUS` → `LocalProfile1 (115%)`; bez kodu. | SMS | PROF-06. |
| R3 | `PROFILE 1` → `Aby przelaczyc profil na LocalProfile1 100% odpowiedz kodem z aplikacji Authenticator dla: <nazwa>, z doklejonym PIN-em`. Nazwa profilu w prośbie pochodzi z listy profili AAPS. | SMS, kod | PROF-08: porównanie procentu; nazwa tylko pokazywana (w treści SMS), nie porównywana. |
| R4 | `PROFILE 99` (numer spoza listy) i `PROFILE 1 0` (procent 0) → `Bledny format`. Numer 0 też daje błąd formatu; numer ujemny może nie być obsłużony przez AAPS. | SMS, kod | Aplikacja wysyła tylko numer 1 i procent 50–200 (PROF-02); odpowiedź `Bledny format` = nierozpoznana (PROF-10). |
| R5 | Po kodzie AAPS tworzy zmianę profilu (`createProfileSwitch`, czas trwania 0, procent). Poza odrzuceniem procentu 0 (R4, błąd formatu) AAPS **nie** ogranicza zakresu procentu. | kod | Aplikacja sama ogranicza procent do 50–200 (PROF-02, P3). |
| R6 | Oczekującą w AAPS prośbę o kod nadpisuje tylko polecenie, na które AAPS tworzy nową prośbę (`CARBS`, `BOLUS`, `PROFILE <nr>`); `PROFILE STATUS`, `PROFILE LIST`, `BG`, `PUMP` i odmowy jej nie ruszają (A6, poprawione 2026-10-31). | z kodu AAPS 3.4.2.6 (nie z telefonu) | Aplikacja ma jedną aktywną sesję (SESSION-22), więc `PROFILE STATUS` nie trafia w środek innego polecenia z aplikacji; nie zmienia też oczekującej prośby. |
| R7 | `PROFILE 1 100` jest równoważne `PROFILE 1`: procent domyślnie 100, trzeci token tylko go nadpisuje; prośba o kod ma identyczną treść (`… LocalProfile1 100% …`). | z kodu AAPS 3.4.2.6 (nie z telefonu), `Plugin.kt:685-699` | PROF-04 bez planu awaryjnego (O1 zamknięte). |
| R8 | Po poprawnym kodzie i udanym `createProfileSwitch` AAPS odsyła `Zmiana profilu wykonana` (jedna linia, bez stanu pompy) **tylko do nadawcy polecenia**; przy porażce – `Nieprawidlowy profil!`, też tylko do nadawcy. Prośba o kod idzie tylko do nadawcy. | z kodu AAPS 3.4.2.6 (nie z telefonu), `Plugin.kt:701-718`, `AuthRequest.kt:39` | PROF-19 (O2 zamknięte); `Nieprawidlowy profil!` po kodzie = nierozpoznana. |
| R9 | Zły kod: `Zly kod. Polecenie anulowano.` do nadawcy; prośba zużyta. Zły kod jest sprawdzany przed czasem ważności. | z kodu AAPS 3.4.2.6 (nie z telefonu), `AuthRequest.kt:51-56` | PROF-10, PROF-12 bez zmian (O3 zamknięte). |
| R10 | `PROFILE LIST` przy pustej liście → `Nieprawidlowy profil!`; pozycje listy rozdzielone LF, bez nagłówka. | z kodu AAPS 3.4.2.6 (nie z telefonu), `Plugin.kt:673-683` | PROF-07 bez zmian; `Nieprawidlowy profil!` = nierozpoznana (O4 zamknięte). |
| R11 | Osobna ścieżka kolejki pompy (`CommandSetProfile.kt:52-57`) wysyła do wszystkich numerów `Profil bazowy w pompie uaktualniony` po ustawieniu profilu w pompie, gdy zmiana pochodzi z Nightscout. Czy zmiana z SMS też ją uruchamia – wywnioskowane, nie sprawdzone. | z kodu AAPS 3.4.2.6 (nie z telefonu) | PROF-20: powiadomienie nie jest potwierdzeniem zmiany profilu. |

Od 2026-10-31 otwarte punkty O1 – O4 rozstrzyga kod AAPS 3.4.2.6 (R7 – R10). Nie były obserwowane na telefonie; wzorce oparte na nich sprawdza W5.

## Pojęcia

- **Profil 1** – pierwsza pozycja listy profili w AAPS (numer 1 w `PROFILE <n>`). Aplikacja nie zna jego nazwy; nazwę podaje AAPS w prośbie o kod.
- **Procent** – liczba całkowita, domyślnie 100.
- **Lista profili** – pozycje (numer, nazwa) z odpowiedzi na `PROFILE LIST`, numery 1…n po kolei (tylko parser i sesja; UI z nich nie korzysta).

## Wymagania

Wspólne dane kryteriów: jak w Spec 004 i 012 (sekret RFC 6238, PIN `4321`, `T0`, kod `050471` w chwili 60 000); wiadomości z [dane/](dane/README.md) oraz `zly-kod.txt` ze Spec 002.

### Katalog

### PROF-01: Wpisy katalogu

| Identyfikator | Słowo kluczowe | Parametr | Wymaga kodu | Zatwierdzenie |
| --- | --- | --- | --- | --- |
| `PROFILE_STATUS` | `PROFILE STATUS` | brak | nie | jedno dotknięcie |
| `PROFILE_LIST` | `PROFILE LIST` | brak | nie | jedno dotknięcie (bez przycisku w UI, PROF-13) |
| `PROFILE_SWITCH` | `PROFILE` | procent (numer zawsze 1) | tak | podsumowanie z PIN-em |

Kolejność katalogu: `BG`, `PUMP`, `CARBS`, `BOLUS`, `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH`. Zmiana CMD-01: słowo kluczowe składa się z wielkich liter `A`–`Z` i pojedynczych spacji między słowami; słowa kluczowe są unikalne.

**Kryteria akceptacji:**

- **Zakładając** katalog, **gdy** pobrano wpisy, **wtedy** jest ich 7 w podanej kolejności, a dane wpisów profilu są jak w tabeli.
- **Zakładając** katalog, **gdy** sprawdzono słowa kluczowe, **wtedy** są unikalne i pasują do `[A-Z]+( [A-Z]+)*`.

### PROF-02: Polecenie zmiany profilu

Polecenie zmiany profilu powstaje tylko z procentu przez walidację; numer profilu jest stały i równy 1 (decyzja użytkownika, 2026-10-30). Procent 50–200 włącznie (P3) – „poniżej minimum” / „powyżej maksimum”. Brak wyjątku dla danych od użytkownika; nie ma innej drogi utworzenia polecenia. Aplikacja nigdy nie wysyła numeru innego niż 1, procentu 0 ani ujemnego (R4).

**Kryteria akceptacji:**

- **Zakładając** procent 100, 50 i 200, **gdy** utworzono polecenie, **wtedy** każde jest poprawne, ma numer 1 i ten procent.
- **Zakładając** procent 49, 201, 0, −10, `Int.MAX_VALUE`, **gdy** utworzono polecenie, **wtedy** wyniki: poniżej minimum, powyżej maksimum, poniżej minimum, poniżej minimum, powyżej maksimum, bez wyjątku.

### PROF-03: Procent z tekstu

Pole procentu jest wypełnione tekstem `100` przy otwarciu formularza. Walidacja jak CMD-04 (bez przycinania): pusty → „pusta”; znak inny niż cyfra ASCII (także `%`, spacja, `-`, `,`) → „niedozwolone znaki”; więcej niż 3 cyfry znaczące → „powyżej maksimum”; wartość według PROF-02.

**Kryteria akceptacji:**

| Tekst | Wynik |
| --- | --- |
| `100` | poprawna, 100 |
| `115` | poprawna, 115 |
| `050` | poprawna, 50 |
| (pusty) | pusta |
| `49` | poniżej minimum |
| `201` | powyżej maksimum |
| `99999999999999999999` | powyżej maksimum, bez wyjątku |
| `110%` | niedozwolone znaki |
| `1,5` | niedozwolone znaki |
| ` 100` | niedozwolone znaki |

- **Zakładając** każdy wiersz, **gdy** zwalidowano tekst, **wtedy** wynik jest dokładnie taki.

### PROF-04: Treść SMS

`PROFILE STATUS` → `PROFILE STATUS`; `PROFILE LIST` → `PROFILE LIST`; zmiana profilu → zawsze `PROFILE 1 <p>`, także dla procentu 100 (pojedyncze spacje, liczby bez zer wiodących; decyzja użytkownika, 2026-10-30).

**Kryteria akceptacji:**

- **Zakładając** polecenia `PROFILE STATUS`, `PROFILE LIST`, zmianę z procentem 100, 115 i 50, **gdy** zbudowano treści, **wtedy** są to kolejno `PROFILE STATUS`, `PROFILE LIST`, `PROFILE 1 100`, `PROFILE 1 115`, `PROFILE 1 50`.

**Plan awaryjny (O1) – nieaktualny od 2026-10-31:** z kodu AAPS 3.4.2.6 (nie z telefonu) `PROFILE 1 100` jest równoważne `PROFILE 1`, a prośba o kod ma identyczną treść (R7). Wysyłanie `PROFILE 1` dla procentu 100 nie jest potrzebne.

### PROF-05: Kontekst parsera i brak ujawniania

`PROFILE STATUS` → kontekst „stan profilu”, `PROFILE LIST` → „lista profili”, zmiana profilu → „zmiana profilu (procent)”. Kontekst zmiany profilu nie zawiera nazwy ani numeru profilu. `toString()` poleceń, wyników walidacji, kontekstów i wyników parsera nie zawiera nazwy profilu, numeru, procentu ani treści SMS.

**Kryteria akceptacji:**

- **Zakładając** zmianę z procentem 115, **gdy** odwzorowano ją na kontekst, **wtedy** kontekst = zmiana profilu z procentem 115.
- **Zakładając** to polecenie, jego kontekst i wynik walidacji, **gdy** wywołano `toString()`, **wtedy** żaden tekst nie zawiera `115`.
- **Zakładając** wynik parsera każdego pliku z `dane/` w kontekście, w którym jest rozpoznawany, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `LocalProfile`, `115%`, `Jan` ani żadnej linii treści.

### Parser

### PROF-06: Odpowiedź na PROFILE STATUS

W kontekście „stan profilu” wynik „stan profilu” (z treścią) powstaje, gdy cała treść po normalizacji to jedna linia `<nazwa> (<P>%)`, gdzie `<nazwa>` to ≥ 1 znak inny niż LF, a `<P>` – liczba 1–9999 bez zer wiodących. Inaczej – „nierozpoznana”.

**Kryteria akceptacji:**

- **Zakładając** `profile-status.txt`, **gdy** kontekst = stan profilu, **wtedy** wynik = stan profilu z treścią równą wejściu.
- **Zakładając** wariant syntetyczny `Moj profil (90%)`, **gdy** kontekst = stan profilu, **wtedy** stan profilu.
- **Zakładając** `LocalProfile1` (bez procentu), `LocalProfile1 (115 %)`, `LocalProfile1 (115%)` z drugą linią, ` (115%)`, **gdy** kontekst = stan profilu, **wtedy** nierozpoznana.
- **Zakładając** `profile-status.txt`, **gdy** kontekst = `BG`, `PUMP`, lista profili albo `Carbs(1)`, **wtedy** nierozpoznana.

### PROF-07: Lista profili

Parser listy zostaje bez zmian, ale UI z niego nie korzysta (aplikacja nie wysyła `PROFILE LIST`, PROF-13).

W kontekście „lista profili” wynik „lista profili” powstaje, gdy treść po normalizacji ma n ≥ 1 linii, linia i (od 1) ma postać `<i>. <nazwa>` (numer dokładnie równy i, kropka, spacja, nazwa ≥ 1 znak), n ≤ 99, a nazwy są parami różne. Nazwa to cała reszta linii po `<i>. ` (do końca linii, także gdy zawiera spacje, cyfry i kropki). Wynik zawiera listę (numer, nazwa) i treść. Inaczej – „nierozpoznana”. Lista podana w jednej linii (np. `1. A 2. B`) daje jedną pozycję (1, `A 2. B`).

Ograniczenie: treść dłuższa niż 2000 znaków jest od razu nierozpoznana (PARSER-02, `ReplyNormalizer`).

**Kryteria akceptacji:**

- **Zakładając** `profile-list.txt`, **gdy** kontekst = lista profili, **wtedy** wynik = lista [(1, `LocalProfile1`), (2, `LocalProfile2`)] z treścią równą wejściu.
- **Zakładając** `profile-list.txt` z końcami linii `\r\n`, **gdy** kontekst = lista profili, **wtedy** ta sama lista.
- **Zakładając** wariant syntetyczny `1. Dzien roboczy` (jedna linia), **gdy** kontekst = lista profili, **wtedy** lista [(1, `Dzien roboczy`)].
- **Zakładając** warianty `2. A` (zaczyna od 2), `1. A\n3. B` (luka), `1. A\n1. B`, `1.A`, `1. A\n2. A` (powtórzona nazwa), `Brak profili`, **gdy** kontekst = lista profili, **wtedy** nierozpoznana.
- **Zakładając** wariant `1. A 2. B` (jedna linia), **gdy** kontekst = lista profili, **wtedy** lista [(1, `A 2. B`)].
- **Zakładając** listę o długości 2001 znaków (np. 99 pozycji z długimi nazwami), **gdy** kontekst = lista profili, **wtedy** nierozpoznana, bez wyjątku.

### PROF-08: Prośba o kod zmiany profilu

W kontekście „zmiana profilu (procent p)” wiadomość ma postać prośby o kod, gdy cała treść po normalizacji to jedna linia:

```text
Aby przelaczyc profil na <nazwa> <P>% odpowiedz kodem z aplikacji Authenticator dla: <konto>, z doklejonym PIN-em
```

`<nazwa>` – ≥ 1 znak inny niż LF; `<P>` – liczba 1–9999 bez zer wiodących, stojąca bezpośrednio przed `% odpowiedz kodem`; `<konto>` – jak `<nazwa>` w PARSER-05. Gdy `<P>` = p → „prośba o kod (profil)” z treścią; inaczej PROF-09.

**Nazwa profilu nie jest porównywana** (decyzja użytkownika, 2026-10-30): aplikacja wysyła zawsze profil 1 i nie zna jego nazwy, więc o odesłaniu kodu decyduje wyłącznie zgodność rodzaju wiadomości (prośba o zmianę profilu) i procentu. Nazwa z prośby trafia tylko do treści SMS pokazanej w wyniku i zapisanej w historii.

**Kryteria akceptacji:**

- **Zakładając** `profile-prosba-o-kod.txt`, **gdy** kontekst = zmiana (100), **wtedy** prośba o kod z treścią równą wejściu.
- **Zakładając** wariant syntetyczny `Aby przelaczyc profil na LocalProfile2 100% odpowiedz kodem z aplikacji Authenticator dla: Jan, z doklejonym PIN-em` (inna nazwa, ten sam procent), **gdy** kontekst = zmiana (100), **wtedy** prośba o kod.
- **Zakładając** wariant syntetyczny dla `Dzien roboczy 115%`, **gdy** kontekst = zmiana (115), **wtedy** prośba o kod.
- **Zakładając** wariant syntetyczny z nazwą `Zima 200` i prośbą o `100%`, **gdy** kontekst = zmiana (100), **wtedy** prośba o kod; **gdy** kontekst = zmiana (200), **wtedy** niezgodny profil (liczba w nazwie nie jest procentem).
- **Zakładając** wariant syntetyczny z nazwą `Plan 50%` i prośbą o `100%` (treść `… na Plan 50% 100% odpowiedz kodem …`), **gdy** kontekst = zmiana (100), **wtedy** prośba o kod; **gdy** kontekst = zmiana (50), **wtedy** niezgodny profil.
- **Zakładając** `profile-prosba-o-kod.txt` z drugą linią albo z `przełączyć`, **gdy** kontekst = zmiana (100), **wtedy** nierozpoznana.
- **Zakładając** `profile-prosba-o-kod.txt`, **gdy** kontekst = `Carbs(1)`, `Bolus(10)`, `BG` albo lista profili, **wtedy** nierozpoznana.

### PROF-09: Niezgodny procent

Wiadomość w postaci prośby o kod (PROF-08), w której procent różni się od kontekstu, daje wynik „niezgodny profil” z treścią (bez wartości w `toString()`). Taki wynik nigdy nie jest „prośbą o kod”.

**Kryteria akceptacji:**

- **Zakładając** `profile-prosba-o-kod.txt` (100%), **gdy** kontekst = zmiana (115), **wtedy** niezgodny profil.
- **Zakładając** `profile-prosba-o-kod.txt`, **gdy** kontekst = zmiana (50) albo (200), **wtedy** niezgodny profil.
- **Zakładając** wariant syntetyczny z `LocalProfile1 1000%`, **gdy** kontekst = zmiana (100), **wtedy** niezgodny profil.

### PROF-10: Zły kod i odmowy

W kontekście zmiany profilu `Zly kod. Polecenie anulowano.` → „zły kod” (z kodu AAPS 3.4.2.6, R9). `Bledny format`, `Nieprawidlowy profil!` (R8, R10) i każda inna treść poza PROF-19 i PROF-20 → „nierozpoznana” z treścią (PARSER-09).

**Kryteria akceptacji:**

- **Zakładając** `zly-kod.txt`, **gdy** kontekst = zmiana (100), **wtedy** zły kod.
- **Zakładając** `profile-bledny-format.txt`, **gdy** kontekst = zmiana profilu, stan profilu albo lista profili, **wtedy** nierozpoznana z treścią `Bledny format`.
- **Zakładając** `profile-nieprawidlowy.txt`, **gdy** kontekst = zmiana (100), stan profilu albo lista profili, **wtedy** nierozpoznana z treścią `Nieprawidlowy profil!`.

### Sesja

### PROF-11: PROFILE STATUS i PROFILE LIST

Sesje `PROFILE STATUS` i `PROFILE LIST` przebiegają jak `BG` (SESSION-03, SESSION-06, SESSION-07): w `AWAITING_REPLY` wynik „stan profilu” albo „lista profili” kończy sesję `Completed` z tym wynikiem i treścią; każdy inny – `Failed(UNRECOGNIZED_REPLY)` z treścią. Nie przyjmują danych do kodu (SESSION-02). Uruchamiane wyłącznie przez koordynatora, więc nigdy w trakcie innej sesji (SESSION-22). Sesji `PROFILE LIST` nie uruchamia żaden przycisk UI (PROF-13); zachowanie sesji zostaje bez zmian.

**Kryteria akceptacji:**

- **Zakładając** sesję `PROFILE LIST`, **gdy** przychodzi `profile-list.txt`, **wtedy** wynik = `Completed` z listą 2 pozycji; port wysyłania dostał dokładnie `PROFILE LIST`.
- **Zakładając** sesję `PROFILE STATUS`, **gdy** przychodzi `profile-status.txt`, **wtedy** `Completed`; gdy przychodzi `profile-bledny-format.txt` – `Failed(UNRECOGNIZED_REPLY)` z tą treścią.
- **Zakładając** sesję `PROFILE STATUS`, **gdy** przychodzi `profile-list.txt`, **wtedy** `Failed(UNRECOGNIZED_REPLY)` (lista nie jest odpowiedzią na stan); **zakładając** sesję `PROFILE LIST`, **gdy** przychodzi `profile-status.txt`, **wtedy** `Failed(UNRECOGNIZED_REPLY)`.
- **Zakładając** aktywną sesję `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** wystartowano `PROFILE STATUS`, **wtedy** wynik startu = „zajęte”, port nie dostał `PROFILE STATUS`, a sesja `CARBS` trwa dalej.

### PROF-12: Zmiana profilu – przed kodem i po kodzie

Sesja zmiany profilu przebiega jak `CARBS`/`BOLUS` (fazy, limity, SESSION-08 – SESSION-10, SESSION-15 – SESSION-20):

| Wynik parsera | Przed kodem (`AWAITING_CODE_REQUEST` / `PREPARING_CODE`) | Po kodzie (`AWAITING_CONFIRMATION`) |
| --- | --- | --- |
| prośba o kod (profil) | SESSION-08 / `Failed(UNEXPECTED_REPLY)` | `Unknown(UNEXPECTED_REPLY)`, bez drugiego kodu |
| niezgodny profil (inny procent) | `Failed(AMOUNT_MISMATCH)`, bez kodu | `Unknown(AMOUNT_MISMATCH)` |
| zły kod | `Failed(UNEXPECTED_REPLY)` | `Failed(WRONG_CODE)` (P4, R9) |
| wykonano zmianę profilu (PROF-19) | wiadomość nieoczekiwana (SESSION-18) | `ProfileSwitchConfirmed` (PROF-19) |
| powiadomienie o profilu bazowym (PROF-20) | `Failed(UNRECOGNIZED_REPLY)`, bez kodu (świadomie przerywa też poprawną sesję) | wiadomość nieoczekiwana, sesja czeka dalej (PROF-20) |
| nierozpoznana (np. `Bledny format`, `Nieprawidlowy profil!`) | `Failed(UNRECOGNIZED_REPLY)`, bez kodu | `Unknown(UNRECOGNIZED_REPLY)` z treścią |

Brak potwierdzenia w 6 min → `Unknown(CONFIRMATION_TIMEOUT)`. Powód `AMOUNT_MISMATCH` obejmuje dla profilu niezgodny procent (bez nowego powodu w `FailureReason`). Kod jest odsyłany tylko na `ProfileCodeRequest` w sesji zmiany profilu; prośba o kod innego polecenia daje stop bez kodu (BOL-19). Sesja niezależnie od parsera sprawdza w treści prośby, że procent jest równy wysłanemu (druga linia obrony, jak BOL-10); nazwy profilu nie sprawdza (PROF-08). Inny nadawca (SMS-04), wiadomość sprzed wysłania polecenia (SESSION-05) i nierozpoznana treść kończą się jak dotychczas – bez kodu; każdy z tych przypadków ma własne kryterium poniżej.

**Kryteria akceptacji:**

- **Zakładając** sesję zmiany (100), **gdy** w chwili 60 000 przychodzi `profile-prosba-o-kod.txt`, **wtedy** port dostał `PROFILE 1 100`, a w chwili 60 000 `0504714321`.
- **Zakładając** sesję zmiany (100), **gdy** przychodzi syntetyczna prośba `Aby przelaczyc profil na LocalProfile2 100% odpowiedz kodem z aplikacji Authenticator dla: Jan, z doklejonym PIN-em` (inna nazwa, zgodny procent), **wtedy** kod `0504714321` jest wysłany, a faza = `AWAITING_CONFIRMATION`.
- **Zakładając** sesję zmiany (115), **gdy** przychodzi `profile-prosba-o-kod.txt` (100%), **wtedy** `Failed(AMOUNT_MISMATCH)` i port nie dostał kodu.
- **Zakładając** sesję zmiany (115), **gdy** przychodzi `profile-bledny-format.txt`, **wtedy** `Failed(UNRECOGNIZED_REPLY)` i bez kodu.
- **Zakładając** sesję zmiany (100), **gdy** `profile-prosba-o-kod.txt` przychodzi z czasem odbioru wcześniejszym niż wysłanie polecenia, **wtedy** wiadomość jest pomijana (SESSION-05), faza = `AWAITING_CODE_REQUEST` i port nie dostał kodu.
- **Zakładając** sesję zmiany po kodzie, **gdy** przychodzi syntetyczny `Profil zmieniony`, **wtedy** `Unknown(UNRECOGNIZED_REPLY)` z tą treścią; gdy `zly-kod.txt` – `Failed(WRONG_CODE)`.
- **Zakładając** sesję zmiany (100), **gdy** przychodzi `bolus-prosba-o-kod-0-10.txt` albo `carbs-prosba-o-kod.txt`, **wtedy** `Failed(UNRECOGNIZED_REPLY)` i bez kodu (BOL-19).
- **Zakładając** sesję `CARBS 1` i osobno sesję `BOLUS 10` w `AWAITING_CODE_REQUEST`, **gdy** przychodzi `profile-prosba-o-kod.txt`, **wtedy** `Failed(UNRECOGNIZED_REPLY)` i port nie dostał kodu (odwrotny kierunek BOL-19).
- **Zakładając** sesję zmiany (115) i podstawiony parser, który dla `profile-prosba-o-kod.txt` (100%) zwraca `ProfileCodeRequest`, **gdy** przychodzi ta wiadomość, **wtedy** `Failed(AMOUNT_MISMATCH)` i port nie dostał kodu (druga linia obrony działa niezależnie od parsera, jak BOL-10).
- **Zakładając** sesję zmiany (200) i wariant syntetyczny z nazwą `Zima 200` i prośbą o `100%`, **gdy** przychodzi, **wtedy** `Failed(AMOUNT_MISMATCH)` i bez kodu; **zakładając** sesję zmiany (100) i ten sam wariant, **wtedy** kod `0504714321` jest wysłany.
- **Zakładając** sesję zmiany (100), **gdy** `profile-prosba-o-kod.txt` przychodzi z innego numeru niż numer AAPS, **wtedy** (SMS-04, test odbioru z trwającą sesją zmiany profilu) sesja nie dostaje wiadomości, faza = `AWAITING_CODE_REQUEST` i port nie dostał kodu.

### UI

### PROF-13: Przyciski profilu w panelu

Panel poleceń ma sekcję „Profil” (kolejność sekcji – CMDUI-01) z przyciskami:

- „Aktualny profil” – `PROFILE STATUS` jednym dotknięciem jak `BG` (CMDUI-06): `launch(command, pin = null)`, przebieg, wynik. Dostępność jak `BG` (numer, uprawnienia SMS, brak polecenia w toku i uruchamiania); nie zależy od sekretu ani limitów. Bez zmian.
- „Zmień profil” – otwiera formularz zmiany profilu (PROF-15) bez wysyłania SMS-a. Dostępność jak „Dalej” formularza: numer, uprawnienia SMS, brak polecenia w toku i uruchamiania, sekret nie `ABSENT` ani `INVALIDATED` (CMDUI-04); nie zależy od limitów. Wywołanie, gdy przycisk jest niedostępny, nic nie robi.

Panel nie ma przycisku wysyłającego `PROFILE LIST`.

**Kryteria akceptacji:**

- **Zakładając** wspólne dane, **gdy** wywołano `onProfileStatusClicked()`, **wtedy** `FakeSmsSender` dostał dokładnie `PROFILE STATUS`, a `load()` sekretu nie był wywołany.
- **Zakładając** wspólne dane, **gdy** wywołano `onProfileSwitchClicked()`, **wtedy** formularz zmiany profilu jest otwarty z tekstem procentu `"100"`, `FakeSmsSender` nie dostał SMS-a, a `load()` nie był wywołany.
- **Zakładając** stan sekretu `ABSENT` (osobno `INVALIDATED`), **gdy** odczytano stan i wywołano `onProfileSwitchClicked()`, **wtedy** „Zmień profil” jest niedostępny, a formularz zamknięty; „Aktualny profil” jest dostępny.
- **Zakładając** limity `LoadFailed`, **gdy** odczytano stan, **wtedy** oba przyciski profilu są dostępne.
- **Zakładając** źródła `ui/commands/`, **gdy** test JVM je przeczytał, **wtedy** nie zawierają `onProfileListClicked` ani `startNoCode(Command.ProfileList)`, a `strings.xml` nie zawiera klucza `commands_profile_list` (gałęzie `Command.ProfileList` w wyczerpujących `when` mogą zostać).

### PROF-14: Wybór profilu z wyniku listy (wycofane)

**Wycofane 2026-10-30** (decyzja użytkownika): aplikacja zawsze zmienia profil 1, więc nie pobiera listy i nie pokazuje pozycji do wyboru. Wynik `PROFILE LIST` nie ma przycisków pozycji, a mechanizm przekazania wyboru (`ProfileSelection`, `onProfileChosen`) jest usuwany.

### PROF-15: Formularz procentu i podsumowanie z PIN-em

Formularz pokazuje tytuł „Zmiana profilu 1”, pole „Procent (%)” (`KeyboardType.Number`, tekst początkowy `100`), „Dalej” i „Anuluj”. „Dalej” waliduje procent (PROF-03; błędy: „Podaj procent (50–200).”, „Tylko cyfry.”, „Najmniejszy procent to 50.”, „Największy procent to 200.”) i otwiera podsumowanie z PIN-em (zachowanie CMDUI-10, CMDUI-11, fokus pola PIN – CMDUI-28): „Zmienić profil 1 na P%?” i „Aplikacja wyśle PROFILE 1 P, a kod z PIN-em odeśle, dopiero gdy AAPS poprosi o kod dla zmiany profilu na P%. Nazwę profilu podaje AAPS – aplikacja jej nie sprawdza.”. „Anuluj”, Wstecz i `onStop()` zamykają formularz i podsumowanie i czyszczą PIN; ponowne „Zmień profil” otwiera formularz z tekstem `100`. Formularz i podsumowanie mają `FLAG_SECURE` (znacznik „ekran chroniony” obejmuje formularz; CMDUI-23).

**Kryteria akceptacji:**

- **Zakładając** formularz z tekstem `"115"`, **gdy** wywołano „Dalej”, a potem „Wyślij” z PIN-em `4321`, **wtedy** `launch` dostał zmianę (115), PIN w stanie = `""`, a `FakeSmsSender` dostał `PROFILE 1 115`.
- **Zakładając** formularz z tekstem `"201"`, **gdy** wywołano „Dalej”, **wtedy** błąd = powyżej maksimum, podsumowanie zamknięte.
- **Zakładając** otwarte podsumowanie z PIN-em `4321`, **gdy** wywołano `onStop()`, **wtedy** PIN = `""`, formularz i podsumowanie zamknięte; **gdy** potem wywołano `onProfileSwitchClicked()`, **wtedy** tekst procentu = `"100"`.
- **Zakładając** otwarty formularz, **gdy** odczytano znacznik „ekran chroniony”, **wtedy** = `true`.

### PROF-16: Przebieg i wynik

Nagłówki: „Polecenie PROFILE STATUS”, „Polecenie PROFILE LIST”, „Zmiana profilu 1, P%”. Kroki: `PROFILE STATUS`/`LIST` – 2 kroki jak `BG`; zmiana profilu – 5 kroków jak `CARBS`. Komunikaty wyniku (pozostałe – CMDUI-19):

| Wynik | Rodzaj | Komunikat |
| --- | --- | --- |
| `Completed`, `PROFILE STATUS` | sukces | „Odpowiedź AAPS na PROFILE STATUS” |
| `Completed`, `PROFILE LIST` (nieosiągalny z UI) | sukces | „Lista profili z AAPS.” (bez pozycji do wyboru; tekst w kodzie zmienia się w zadaniu 2 razem z usunięciem wyboru) |
| `Failed(AMOUNT_MISMATCH)`, zmiana profilu | błąd | „AAPS poprosił o kod dla innego procentu niż wysłany.” |
| `Unknown(AMOUNT_MISMATCH)`, zmiana profilu | nieznany | „Po wysłaniu kodu AAPS podał inny procent.” |
| `Unknown(UNRECOGNIZED_REPLY)`, zmiana profilu | nieznany | „Po wysłaniu kodu przyszła odpowiedź AAPS, której aplikacja nie rozpoznaje. Przeczytaj treść SMS i sprawdź w AAPS.” |
| `ProfileSwitchConfirmed` | sukces | PROF-21 |

Wynik każdej zmiany profilu z treścią SMS pokazuje tę treść (CMDUI-20). Dla `ProfileSwitchConfirmed` treść zawiera prośbę o kod z nazwą profilu (PROF-21), więc nazwa jest widoczna w wyniku i historii.

**Kryteria akceptacji:**

- **Zakładając** każdy wiersz tabeli, **gdy** odwzorowano wynik, **wtedy** rodzaj i komunikat odpowiadają wierszowi, a treść SMS jest pokazana.
- **Zakładając** zmianę z procentem 90, **gdy** wyliczono nagłówek, **wtedy** argumenty = 1 i 90.
- **Zakładając** `PROFILE_SWITCH` i każdą fazę, **gdy** wyliczono kroki, **wtedy** są jak dla `CARBS`; dla `PROFILE_STATUS` – jak dla `BG`.
- **Zakładając** wynik `Completed` polecenia `PROFILE LIST`, **gdy** odczytano stan wyniku, **wtedy** stan nie ma pola z pozycjami listy.

### PROF-17: Historia

Zmiana DATA-07, DATA-09, DATA-14, HISTUI-03: stałe teksty poleceń `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH` (`PROFILE_LIST` zostaje także dla wpisów zapisanych przed 2026-10-30); pola wpisu „numer profilu” i „procent profilu” (tylko `PROFILE_SWITCH`, inaczej brak; dla nowych wpisów numer = 1). Nazwa profilu nie jest osobnym polem (jest w treści odpowiedzi). Baza w wersji 3: migracja 2 → 3 dodaje kolumny `profile_number INTEGER` i `profile_percent INTEGER` (obie dopuszczają `NULL`); plik `3.json` w repo. Obie migracje są rejestrowane jednym wywołaniem `addMigrations(MIGRATION_1_2, MIGRATION_2_3)`, więc baza w wersji 1 przechodzi do 3 kolejno przez 2. Migracja jest już scalona i sprawdzona na prawdziwej bazie (zadanie 3 pierwszej wersji, W3); zmiana 2026-10-30 nie zmienia schematu. Etykiety: „PROFILE STATUS”, „PROFILE LIST”, „Zmiana profilu N, P%”, przy braku pól „Zmiana profilu (szczegóły nieznane)”.

**Kryteria akceptacji:**

- **Zakładając** wynik zmiany (115) `Unknown(UNRECOGNIZED_REPLY)` z treścią `profile-prosba-o-kod.txt`, **gdy** utworzono wpis, **wtedy** polecenie = `PROFILE_SWITCH`, numer = 1, procent = 115, a żadne pole poza treścią nie zawiera `LocalProfile1`.
- **Zakładając** wiersz `PROFILE_LIST` z numerem 3, **gdy** go odczytano, **wtedy** numer i procent = brak.
- **Zakładając** wiersz `PROFILE_SWITCH` z numerem 2 i procentem 115 (wpis sprzed zmiany 2026-10-30), **gdy** go odczytano i odwzorowano, **wtedy** etykieta = „Zmiana profilu 2, 115%”.
- **Zakładając** `3.json`, **gdy** test JVM go przeczytał, **wtedy** wersja = 3, kolumny wersji 2 i dwie nowe bez `NOT NULL`; migracja 2 → 3 ma dokładnie dwa `ALTER TABLE command_history ADD COLUMN …`.
- **Zakładając** źródło `AapsRemoteDatabase.kt`/`DataModule.kt`, **gdy** test JVM je przeczytał, **wtedy** jest dokładnie jedno wywołanie `addMigrations(` z `MIGRATION_1_2` i `MIGRATION_2_3`; **zakładając** listę migracji, **gdy** test wyliczył ścieżkę 1 → 3, **wtedy** to `MIGRATION_1_2`, `MIGRATION_2_3` (łącznie 3 polecenia `ALTER TABLE`).
- **Zakładając** telefon z bazą w wersji 2 (W3), **gdy** zainstalowano nową wersję, **wtedy** wpisy zachowane; **zakładając** telefon z bazą w wersji 1 (wersja aplikacji sprzed Spec 012), **gdy** zainstalowano od razu wersję z bazą 3, **wtedy** wpisy zachowane, a nowe wpisy bolusa i profilu mają swoje pola.

### PROF-18: Brak ujawniania danych

Nazwy profili, numer, procent, PIN, kod i treść SMS nie trafiają do logów, wyjątków ani `toString()` (zasady Spec 002 – 010 obejmują nowe typy).

**Kryteria akceptacji:**

- **Zakładając** stan `CommandsViewModel` z formularzem z tekstem `"115"` i PIN-em `4321`, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `115` ani `4321`.

### Odpowiedzi po kodzie (zmiana 2026-10-31)

Wymagania PROF-19 – PROF-21 opierają się na faktach z kodu AAPS 3.4.2.6 (R8, R11), nie na SMS-ach z telefonu (sprawdzenie – W5). Rozpoznanie potwierdzenia albo powiadomienia **nigdy nie prowadzi do wysłania kodu**: jedyną drogą do kodu zostaje „prośba o kod (profil)” w `AWAITING_CODE_REQUEST` (SESSION-08, PROF-12). Zmiana PARSER-01 (Spec 002: nowe wyniki w kontekście „zmiana profilu”), SESSION-12, SESSION-13 (Spec 004: tabela po kodzie), DATA-09 (Spec 007: nowy rodzaj wyniku), CMDUI-19 (Spec 009) i HISTUI-04 (Spec 010) – scalone 2026-10-31 (zadanie 4 zmiany 2026-10-31 w design).

### PROF-19: Potwierdzenie zmiany profilu

W kontekście „zmiana profilu (procent p)” wiadomość, której cała treść po normalizacji to dokładnie `Zmiana profilu wykonana` (jedna linia), daje wynik „wykonano zmianę profilu” z treścią. Wynik nie niesie procentu ani nazwy (AAPS ich nie podaje). W innych kontekstach ta treść jest „nierozpoznana”.

Sesja zmiany profilu przyjmuje ten wynik jako potwierdzenie tylko w `AWAITING_CONFIRMATION` (także dla wiadomości odebranych w `SENDING_CODE`, przetwarzanych po „wysłano”) i tylko, gdy `receivedAt` ≥ chwili wysłania kodu – wtedy wynik sesji `ProfileSwitchConfirmed(percent, treść)`, gdzie `percent` = procent wysłanego polecenia. Odebrany wcześniej albo w fazach przed kodem – wiadomość nieoczekiwana (SESSION-18), faza i limity bez zmian. Wiązanie z sesją zapewniają: nadawca = numer AAPS (SMS-04), faza po kodzie i chwila odbioru. AAPS wysyła tę treść tylko do nadawcy polecenia (R8), więc potwierdzenie zmiany profilu z innego numeru nie trafia do aplikacji. Jak `CarbsConfirmed` (SESSION-12) wynik oznacza potwierdzenie AAPS, nie dowód, że zmieniono profil 1 na p% (ryzyko szczątkowe – „Bezpieczeństwo”).

`ReplyMatch` (BOL-19): potwierdzenie „wykonano zmianę profilu” jest zgodne tylko z `PROFILE_SWITCH`.

**Kryteria akceptacji:**

- **Zakładając** `profile-sukces.txt`, **gdy** kontekst = zmiana (100), **wtedy** wynik = wykonano zmianę profilu z treścią równą wejściu; **gdy** kontekst = zmiana (115), **wtedy** to samo.
- **Zakładając** `profile-sukces.txt` z końcem linii `\r\n` na końcu, **gdy** kontekst = zmiana (100), **wtedy** wykonano zmianę profilu.
- **Zakładając** warianty syntetyczne `Zmiana profilu wykonana.` (kropka), `zmiana profilu wykonana`, `Zmiana profilu wykonana` z drugą linią `Bat.: 94%`, **gdy** kontekst = zmiana (100), **wtedy** nierozpoznana.
- **Zakładając** `profile-sukces.txt`, **gdy** kontekst = `BG`, `PUMP`, `Carbs(1)`, `Bolus(10)`, stan profilu albo lista profili, **wtedy** nierozpoznana.
- **Zakładając** sesję zmiany (90) z kodem wysłanym w chwili 60 000, **gdy** w chwili 120 000 przychodzi `profile-sukces.txt`, **wtedy** wynik = `ProfileSwitchConfirmed` z procentem 90 i treścią = treść przyjętej prośby o kod, pusta linia, treść pliku; port wysyłania dostał dokładnie 2 SMS-y: `PROFILE 1 90` i `0504714321`.
- **Zakładając** sesję zmiany (100) w `AWAITING_CODE_REQUEST`, **gdy** przychodzi `profile-sukces.txt`, **wtedy** wiadomość trafia do nieoczekiwanych, faza = `AWAITING_CODE_REQUEST` i port nie dostał kodu.
- **Zakładając** sesję zmiany (100), w której wiadomość `profile-sukces.txt` odebrano w `SENDING_CODE` z chwilą odbioru wcześniejszą niż chwila wysłania kodu, **gdy** port zwrócił „wysłano”, **wtedy** wiadomość trafia do nieoczekiwanych, a faza = `AWAITING_CONFIRMATION`.
- **Zakładając** sesję `CARBS 1` po kodzie i osobno sesję `BOLUS 0,10 j.` po kodzie, **gdy** przychodzi `profile-sukces.txt`, **wtedy** `Unknown(UNRECOGNIZED_REPLY)` (nie potwierdzenie).
- **Zakładając** sesję zmiany (100) po kodzie z podstawionym parserem zwracającym potwierdzenie `CarbsDone(1, …)` albo `BolusDone(10, …)`, **gdy** przyszła wiadomość, **wtedy** `Unknown(UNRECOGNIZED_REPLY)` (BOL-19).
- **Zakładając** każdą parę (polecenie, rodzaj potwierdzenia), **gdy** wywołano funkcję zgodności, **wtedy** wykonano zmianę profilu jest zgodne wyłącznie z `PROFILE_SWITCH`.
- **Zakładając** wynik parsera i wynik sesji z `profile-sukces.txt` (procent 115), **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `115` ani `Zmiana profilu wykonana`.

### PROF-20: Powiadomienie o profilu bazowym nie jest potwierdzeniem

W kontekście „zmiana profilu (procent p)” wiadomość, której cała treść po normalizacji to dokładnie `Profil bazowy w pompie uaktualniony`, daje wynik „powiadomienie o profilu bazowym” z treścią (R11). W innych kontekstach – „nierozpoznana” (bez zmian). Sesja zmiany profilu:

- w `AWAITING_CONFIRMATION` (i dla wiadomości z `SENDING_CODE`) – wiadomość nieoczekiwana (SESSION-18): sesja czeka dalej na potwierdzenie albo limit 6 min (SESSION-15); powiadomienie nigdy nie daje `ProfileSwitchConfirmed`;
- w `AWAITING_CODE_REQUEST` i `PREPARING_CODE` – jak „nierozpoznana”: `Failed(UNRECOGNIZED_REPLY)`, kod nie jest wysyłany (zmiana profilu z innego źródła przed kodem = stan AAPS inny, niż zakłada sesja; stop). Powiadomienie jest rozsyłane do wszystkich numerów niezależnie od polecenia aplikacji (R11), więc może przerwać poprawną sesję przed kodem; to świadome, ostrożne zachowanie (fail-closed), a ponowienie ręczne jest właściwe.

**Kryteria akceptacji:**

- **Zakładając** `profile-bazowy-uaktualniony.txt`, **gdy** kontekst = zmiana (100), **wtedy** wynik = powiadomienie o profilu bazowym; **gdy** kontekst = `BG`, `Carbs(1)`, `Bolus(10)` albo stan profilu, **wtedy** nierozpoznana.
- **Zakładając** sesję zmiany (100) z kodem wysłanym w chwili 60 000, **gdy** w chwili 90 000 przychodzi `profile-bazowy-uaktualniony.txt`, a w chwili 120 000 `profile-sukces.txt`, **wtedy** po pierwszej wiadomości faza = `AWAITING_CONFIRMATION` z 1 wiadomością nieoczekiwaną, a wynik końcowy = `ProfileSwitchConfirmed(100)`.
- **Zakładając** sesję zmiany (100) z kodem wysłanym w chwili 60 000, **gdy** przychodzi tylko `profile-bazowy-uaktualniony.txt`, **wtedy** po 360 000 ms od wysłania kodu wynik = `Unknown(CONFIRMATION_TIMEOUT)`.
- **Zakładając** sesję zmiany (100) w `AWAITING_CODE_REQUEST`, **gdy** przychodzi `profile-bazowy-uaktualniony.txt`, **wtedy** `Failed(UNRECOGNIZED_REPLY)` z tą treścią i port nie dostał kodu.

### PROF-21: Wynik i historia potwierdzenia zmiany profilu

Wynik `ProfileSwitchConfirmed` (CMDUI-19): rodzaj „sukces”, komunikat „AAPS potwierdził zmianę profilu (polecenie: profil 1, P%).” (P = procent z wyniku), treść SMS pokazana (CMDUI-20), bez ostrzeżenia „nieznany”. AAPS w potwierdzeniu nie podaje numeru, nazwy ani procentu, więc komunikat nie twierdzi więcej niż wiemy: „procent P” i „profil 1” to dane **polecenia wysłanego przez sesję** (procent był sprawdzony w prośbie o kod, PROF-08, PROF-12), nie fakt z odpowiedzi AAPS. Treść wyniku (`text`) to treść przyjętej prośby o kod (z nazwą profilu od AAPS), pusta linia i treść potwierdzenia; dzięki temu nazwa profilu jest widoczna w wyniku (CMDUI-20) i zapisana w historii (to samo pole „treść odpowiedzi”, DATA-07; bez zmiany schematu). Historia (zmiana DATA-09, HISTUI-04): nowy stały tekst rodzaju wyniku `PROFILE_SWITCH_CONFIRMED` (bez zmiany schematu bazy, rodzaj jest kolumną tekstową); wynik wpisu z procentem → „AAPS potwierdził zmianę profilu (polecenie: profil 1, P%).”; bez procentu → „Brak szczegółów wyniku …” (jak BOL-17). Numer i procent wpisu – jak PROF-17.

**Kryteria akceptacji:**

- **Zakładając** wynik `ProfileSwitchConfirmed(90)` z treścią (prośba o kod z `profile-prosba-o-kod.txt`, pusta linia, `profile-sukces.txt`), **gdy** odwzorowano wynik, **wtedy** rodzaj = sukces, komunikat „AAPS potwierdził zmianę profilu (polecenie: profil 1, 90%).”, argument procentu = 90, treść SMS pokazana, brak ostrzeżenia „nieznany”.
- **Zakładając** ten wynik, **gdy** utworzono wpis i wiersz historii, **wtedy** polecenie = `PROFILE_SWITCH`, numer = 1, procent = 90, rodzaj = `PROFILE_SWITCH_CONFIRMED`; **gdy** wiersz odczytano, **wtedy** wynik wpisu = potwierdzenie zmiany profilu, a procent wpisu = 90.
- **Zakładając** wiersz `PROFILE_SWITCH_CONFIRMED` bez procentu (osobno: przy poleceniu `CARBS`), **gdy** odczytano i odwzorowano wpis, **wtedy** tekst wyniku = „Brak szczegółów wyniku …”, bez wyjątku (procent jest odczytywany tylko dla `PROFILE_SWITCH`, PROF-17).

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Odpowiedź na `PROFILE STATUS` w nieznanym formacie | `Failed(UNRECOGNIZED_REPLY)` z treścią | PROF-06, PROF-11 |
| Procent pusty / zły / poza 50–200 | błąd pod polem | PROF-03, PROF-15 |
| Prośba AAPS o kod dla innego procentu niż wysłany | `Failed(AMOUNT_MISMATCH)`, kod nie jest wysyłany | PROF-09, PROF-12 |
| Prośba AAPS o kod dla zgodnego procentu, ale profilu o innej nazwie niż oczekiwał użytkownik | kod jest wysyłany; nazwa widoczna w treści SMS wyniku i historii (ryzyko – „Bezpieczeństwo”) | PROF-08, PROF-12, PROF-16 |
| `Bledny format` (np. brak profilu 1 w AAPS) | `Failed(UNRECOGNIZED_REPLY)` z treścią, bez kodu | PROF-10, PROF-12 |
| Zły kod | `Failed(WRONG_CODE)` | PROF-12 |
| `Zmiana profilu wykonana` po kodzie | `ProfileSwitchConfirmed` z procentem wysłanym | PROF-19, PROF-21 |
| `Zmiana profilu wykonana` przed kodem albo odebrana przed wysłaniem kodu | wiadomość nieoczekiwana, kod nie jest wysyłany | PROF-19 |
| `Profil bazowy w pompie uaktualniony` po kodzie / przed kodem | wiadomość nieoczekiwana, sesja czeka dalej / `Failed(UNRECOGNIZED_REPLY)` bez kodu | PROF-20 |
| `Nieprawidlowy profil!` po kodzie (porażka zmiany w AAPS) | `Unknown(UNRECOGNIZED_REPLY)` z treścią i ostrzeżeniem | PROF-10, PROF-12 |
| Nieznana odpowiedź po kodzie | `Unknown(UNRECOGNIZED_REPLY)` z treścią i ostrzeżeniem | PROF-12, PROF-16 |
| „Aktualny profil” albo „Zmień profil” w trakcie innego polecenia | „zajęte” / przycisk nieaktywny | PROF-11, PROF-13, CMDUI-05 |
| Wyjście z zakładki / tło z otwartym formularzem albo podsumowaniem | formularz i podsumowanie zamknięte, PIN wyczyszczony | PROF-15 |

## Bezpieczeństwo

- **Kod tylko na zgodną prośbę:** prośba AAPS musi być prośbą o zmianę profilu (nie o `CARBS`, `BOLUS` ani inne polecenie) z procentem identycznym z wysłanym (PROF-08, PROF-09); sesja sprawdza procent niezależnie od parsera (PROF-12). Niezgodny procent, nierozpoznana treść, inny nadawca, wiadomość sprzed wysłania – stop bez kodu (Spec 004, 005).
- **Ryzyko zaakceptowane przez użytkownika 2026-10-30: nazwa profilu nie jest sprawdzana.** Aplikacja zawsze wysyła profil 1, porównuje tylko procent i nie zna ani nie sprawdza nazwy profilu (brak pola z nazwą w Ustawieniach). Kod zostanie odesłany na każdą prośbę AAPS o zmianę profilu ze zgodnym procentem. Realne scenariusze: (a) zmiana kolejności profili w AAPS, (b) dodanie lub usunięcie profilu w AAPS, (c) każda inna prośba o zmianę profilu z tym samym procentem – np. pozostała po ręcznym `PROFILE 2 100` wysłanym z telefonu aplikacji (z kodu AAPS 3.4.2.6: prośba o kod idzie tylko do nadawcy polecenia, R8, więc prośba z innego numeru do aplikacji nie trafia); 100% to najczęstszy procent, więc kolizja jest najbardziej prawdopodobna przy powrocie na 100%. Skutek dla zdrowia: AAPS przełączy na inny profil z 50–200%, co zmienia bazę, ISF i IC, czyli dawkowanie insuliny. Ograniczenia ryzyka działają dopiero po wysłaniu kodu (wykrywają, nie zapobiegają): procent tylko 50–200 (P3), nazwa z prośby AAPS widoczna w treści SMS wyniku i w historii, `PROFILE STATUS` do sprawdzenia aktualnego profilu po zmianie; tekst podsumowania mówi, że nazwy nie sprawdza, ale nazwa jest znana dopiero po wysłaniu. Rozważane i odrzucone przez użytkownika: porównanie z nazwą z `PROFILE STATUS` albo z ustawieniem oraz potwierdzenie treści prośby przed kodem. Wcześniejsza ochrona (porównanie nazwy z pobranej listy) jest usuwana razem z PROF-14.
- **Zakres procentu poza 0 nie jest ograniczany przez AAPS (R5):** aplikacja przyjmuje tylko 50–200% (P3); 0 i wartości ujemne nigdy nie są wysyłane.
- **Bez ponawiania:** najwyżej 1 SMS polecenia i 1 kod na sesję; aplikacja nie zmienia profilu sama.
- **PIN i kod:** zasady Spec 004 i 009; fokus pola PIN nie zapisuje PIN-u (CMDUI-28); `FLAG_SECURE` na formularzu, podsumowaniu, przebiegu i wyniku (PROF-15, CMDUI-23).
- **`PROFILE STATUS` bez kodu** nie nadpisuje w AAPS oczekującej prośby o kod (A6, R6; poprawione 2026-10-31), a aplikacja i tak nie wyśle go w trakcie własnego polecenia (SESSION-22).
- **Potwierdzenie i powiadomienie nie dają kodu (PROF-19, PROF-20):** rozpoznanie `Zmiana profilu wykonana` albo `Profil bazowy w pompie uaktualniony` nigdy nie prowadzi do wysłania kodu ani drugiego polecenia. Potwierdzenie jest przyjmowane tylko po wysłaniu kodu, od numeru AAPS, z chwilą odbioru ≥ chwili wysłania kodu; powiadomienie o profilu bazowym nie jest potwierdzeniem. Ryzyko szczątkowe: `Zmiana profilu wykonana` nie zawiera procentu ani nazwy, więc gdy w oknie oczekiwania przyjdzie potwierdzenie innej zmiany profilu z numeru aplikacji (np. ręczne `PROFILE 2 100` wysłane z tego samego telefonu), aplikacja przyjmie je jako potwierdzenie; dlatego komunikat mówi o potwierdzeniu AAPS, a do sprawdzenia aktualnego profilu służy „Aktualny profil”.
- **Dane w repo:** domyślne nazwy profili AAPS, imię `Jan`; brak numerów, PIN-ów, kodów.

## Dane testowe

- [dane/](dane/README.md): `profile-status.txt`, `profile-list.txt`, `profile-prosba-o-kod.txt`, `profile-bledny-format.txt` – prawdziwe SMS-y użytkownika (imię → `Jan`).
- [dane/](dane/README.md): `profile-sukces.txt`, `profile-bazowy-uaktualniony.txt`, `profile-nieprawidlowy.txt` – treści z kodu AAPS 3.4.2.6 (zasoby `values-pl-rPL` po `stripAccents`), nie z telefonu; źródło: [dane/aaps-kod-zrodlowy.md](dane/aaps-kod-zrodlowy.md).
- Warianty syntetyczne tylko w kryteriach (np. prośba z `LocalProfile2 100%`).
- Weryfikacja na telefonie: AAPS z wirtualną pompą; po teście przywrócić profil 100%.

## Weryfikacja na urządzeniu

| Nr | Co sprawdzić | Wymagania |
| --- | --- | --- |
| W1 | „Aktualny profil”: treść odpowiedzi, `FLAG_SECURE` wyniku; „Zmień profil” otwiera formularz z `100` bez wysłania SMS-a; brak przycisku `PROFILE LIST` | PROF-11, PROF-13 |
| W2 | Zmiana profilu 1 na 90% (wirtualna pompa): formularz, podsumowanie, kod, wynik z treścią prośby (nazwa profilu); potem powrót na 100% (`PROFILE 1 100`). Od 2026-10-31 nie jest warunkiem scalenia: O1 – O3 rozstrzyga kod AAPS 3.4.2.6 (R7 – R9); zgodność z telefonem sprawdza W5 | PROF-04, PROF-12, PROF-15, PROF-16 |
| W3 | Historia po zadaniu 2: nowy wpis „Zmiana profilu 1, 90%”, wcześniejsze wpisy zachowane (migracja 2 → 3 sprawdzona w pierwszej wersji) | PROF-17 |
| W4 | Przejście na Ustawienia z otwartym formularzem → po powrocie panel; „Zmień profil” → tekst `100` | PROF-15 |
| W5 | Po zadaniu 3 zmiany 2026-10-31 (wynik w opisie PR, nie warunek scalenia): zmiana profilu 1 na 90% → wynik „AAPS potwierdził zmianę profilu (polecenie: profil 1, 90%).” i treść `Zmiana profilu wykonana`; zły PIN → „zły kod”; zanotować, czy przyszło też `Profil bazowy w pompie uaktualniony` (R11). Gdy treść z telefonu różni się od pliku z `dane/` – PR `Spec:` z poprawką | PROF-19 – PROF-21 |

## Otwarte pytania

Brak otwartych. Punkty O1 – O4 rozstrzygnięte 2026-10-31 z kodu AAPS 3.4.2.6 (nie z telefonu; [dane/aaps-kod-zrodlowy.md](dane/aaps-kod-zrodlowy.md)):

- [x] **O1. `PROFILE 1 100`.** Równoważne `PROFILE 1`, ta sama treść prośby o kod (R7). Plan awaryjny PROF-04 nieaktualny; W2 nie jest warunkiem scalenia.
- [x] **O2. Odpowiedź po poprawnym kodzie zmiany profilu.** `Zmiana profilu wykonana`, tylko do nadawcy (R8) → PROF-19, PROF-21.
- [x] **O3. „Zly kod” dla profilu.** `Zly kod. Polecenie anulowano.` (R9) → `Failed(WRONG_CODE)` bez zmian.
- [x] **O4. Pusta lista profili.** `Nieprawidlowy profil!` (R10) → nierozpoznana (PROF-10).

Decyzje użytkownika przyjęte przy tej specyfikacji (2026-10-28):

- [x] **Literówka w `PROFILE STATUS`.** Rozstrzygnięte (użytkownik): treść to `LocalProfile1 (115%)`; „LocalPofile1” było literówką.
- [x] **P1. Podział linii w `PROFILE LIST`.** Potwierdzona jest tylko lista numerowana „N. nazwa”. Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – każda pozycja w osobnej linii (LF/CR/CRLF, PARSER-02); inny układ → nierozpoznana (treść pokazana, bez wyboru). Do sprawdzenia: czy jest dodatkowy nagłówek lub końcowy tekst, co przy pustej liście.
- [x] **P2. Format `PROFILE STATUS`.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – tylko `<nazwa> (<P>%)`. Nie wiemy, czy przy 100% AAPS pomija nawias i czy dopisuje przesunięcie czasu; inny format → `Failed(UNRECOGNIZED_REPLY)` z treścią (użytkownik i tak ją widzi).
- [x] **P3. Zakres procentu.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – 50–200%, liczby całkowite, bez dodatkowego pytania „Na pewno” (procent jest w podsumowaniu). Uzasadnienie: AAPS nie ogranicza procentu (R5); 50–200% obejmuje typowe zmiany (choroba, wysiłek), a wartości skrajne (np. 10% albo 500%) przez SMS byłyby groźne. Alternatywa: zakres jak w oknie zmiany profilu AAPS albo pytanie „Na pewno” poza 80–120%.
- [x] **P4. Odpowiedzi po kodzie.** Treść po poprawnym kodzie nieznana → `Unknown(UNRECOGNIZED_REPLY)` z treścią. „Zly kod” → `Failed(WRONG_CODE)` (wspólny kod AAPS, A2) – do potwierdzenia (W2). Po zebraniu SMS-a – wzorzec potwierdzenia jak dla bolusa. Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – zgodnie z opisem.
- [x] **P5. Powtórzone nazwy na liście.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – lista nierozpoznana.
- [x] **P6. Wersja AAPS.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – 3.4.2.6 jak w Spec 002.
- [x] **P7. Historia.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – numer i procent w kolumnach, nazwa tylko w treści odpowiedzi.
- [x] **P8. Wybór porzucany przy wyjściu z zakładki.** Nieaktualne od 2026-10-30 (wybór z listy wycofany, PROF-14).

Decyzje użytkownika z 2026-10-30 (uproszczenie zmiany profilu):

- [x] **P9. Zmiana tylko profilu 1, bez listy.** „Zmień profil” otwiera formularz procentu (50–200, domyślnie 100); SMS zawsze `PROFILE 1 <procent>`; bez `PROFILE LIST`, wyniku listy z przyciskami i `ProfileSelection` (PROF-02, PROF-04, PROF-13 – PROF-15). „Aktualny profil” bez zmian.
- [x] **P10. Kod tylko po zgodnym procencie.** Kod jest odsyłany wyłącznie na prośbę o zmianę profilu z procentem równym wysłanemu; nazwa z prośby jest tylko pokazywana (PROF-08, PROF-09, PROF-12, ryzyko zaakceptowane przez użytkownika 2026-10-30, opisane w „Bezpieczeństwie”). Historia: numer 1 i procent (PROF-17).

Propozycje specyfikatora przyjęte w tej zmianie (scalenie PR `Spec: …` = akceptacja):

- [x] **P11. `PROFILE LIST` zostaje w katalogu, parserze i sesji bez przycisku w UI** – najmniejsza zmiana: bez zmian w Spec 003 (CMD-01: 7 wpisów), PARSER-01, SESSION-06 i bez migracji danych; stare wpisy historii `PROFILE_LIST` dalej się odczytują. Usunięcie martwego kodu – „Przyszłe zmiany”.
- [x] **P12. Teksty:** formularz „Zmiana profilu 1”, przycisk „Zmień profil” (bez wielokropka), podsumowanie i komunikaty niezgodności jak w PROF-15, PROF-16, nagłówek przebiegu „Zmiana profilu 1, P%” (jak etykieta historii).

Propozycje specyfikatora w zmianie 2026-10-31 (scalenie PR `Spec: …` = akceptacja):

- [x] **P13. Rozpoznawanie po kodzie (PROF-19 – PROF-21):** `Zmiana profilu wykonana` → `ProfileSwitchConfirmed` z procentem wysłanym, komunikat „AAPS potwierdził zmianę profilu (polecenie: profil 1, P%).”; `Profil bazowy w pompie uaktualniony` po kodzie → wiadomość nieoczekiwana, przed kodem → stop bez kodu. Alternatywa: zostawić `Unknown(UNRECOGNIZED_REPLY)` (P4) – bezpieczne, ale każdy udany wynik wymagał sprawdzenia w AAPS.

## Przyszłe zmiany (nie blokują)

- Okno „Na pewno P%?” dla procentu spoza 80–120% (sugestia z recenzji PR #221; dziś procent jest tylko w podsumowaniu).
- Rozpoznawanie odmów i porażek profilu z kodu AAPS 3.4.2.6 (R8, R10): `Nieprawidlowy profil!` po kodzie (np. jako `Unknown` z czytelnym powodem), `Nie skonfigurowano`, `Nie zaladowano profilu z NS`, `Zdalne komendy nie sa dozwolone` przed kodem, `Przekroczono limit czasu oczekiwania na zakonczenie poprzedniej komunikacji z pompa` po kodzie. Dziś „nierozpoznana” z treścią.
- Powiadomienie `Profil bazowy w pompie uaktualniony` w sesjach innych niż zmiana profilu (dziś „nierozpoznana”: w `BG` – `Failed(UNRECOGNIZED_REPLY)`, po kodzie `CARBS`/`BOLUS` – `Unknown`).
- Usunięcie nieużywanego `PROFILE LIST` (`CommandId.PROFILE_LIST`, `Command.ProfileList`, `ExpectedCommand.ProfileList`, `ProfileListReport`, wzorzec listy, gałęzie sesji i wyniku) – wymaga zmiany CMD-01 (Spec 003), PARSER-01 (Spec 002), SESSION-06 (Spec 004) oraz PROF-01, PROF-04, PROF-05, PROF-07, PROF-11, PROF-16; `HistoryCommand.PROFILE_LIST` musi zostać dla starych wpisów.

## Historia zmian

- **2026-10-31 – scalenie planowanych zmian po zmianach 2026-10-30 i 2026-10-31 (po wdrożeniu).** Wykonano zadanie 4 zmiany 2026-10-30 i zadanie 4 zmiany 2026-10-31 (design, jedno zadanie): treść PARSER-01 (Spec 002), CMD-01, CMD-07 – CMD-09 (Spec 003), SESSION-11 – SESSION-13 i „Bezpieczeństwo” (Spec 004), DATA-07, DATA-09 (Spec 007), CMDUI-19 (Spec 009), HISTUI-04 (Spec 010) przepisana zgodnie z tą specyfikacją i stanem kodu na `main`; wpisy „Planowane zmiany” zamienione na zwykłe wpisy historii. Wymagania Spec 013 bez zmian; w design 013 poprawiono opis kluczy tekstów historii wyniku `ProfileSwitchConfirmed` (wspólne `result_*`).

- **2026-10-31 – dane z kodu AAPS 3.4.2.6 (nie z telefonu).** Nowy plik [dane/aaps-kod-zrodlowy.md](dane/aaps-kod-zrodlowy.md) i pliki `profile-sukces.txt`, `profile-bazowy-uaktualniony.txt`, `profile-nieprawidlowy.txt`. Fakty: R6 poprawione (A6), nowe R7 – R11; O1 – O4 zamknięte; plan awaryjny PROF-04 nieaktualny; W2 nie jest warunkiem scalenia. Zmienione: „Zakres”, „Poza zakresem”, PROF-10 (`Nieprawidlowy profil!` = nierozpoznana), PROF-12 (wiersze tabeli dla PROF-19, PROF-20), PROF-16 (wiersz `ProfileSwitchConfirmed`), „Bezpieczeństwo” (A6, ryzyko (c), potwierdzenie bez kodu), W2, nowe W5. Nowe wymagania PROF-19 – PROF-21. Planowane zmiany treści PARSER-01 (Spec 002), SESSION-12, SESSION-13 (Spec 004), DATA-09 (Spec 007), CMDUI-19 (Spec 009), HISTUI-04 (Spec 010) – zadanie 4 zmiany 2026-10-31 w design (po #259).
- **2026-10-30 – poprawki po recenzji PR #255.** Ryzyko „nazwa nie jest sprawdzana” rozbudowane i zapisane jako zaakceptowane przez użytkownika; PROF-08 i PROF-12: kryteria nazwa z liczbą, sesja `CARBS`/`BOLUS` z prośbą o profil, druga linia obrony z podstawionym parserem, osobne kryterium „inny nadawca”; R5 bez sprzeczności z R4; plan awaryjny dla `PROFILE 1 100` (PROF-04), W2 warunkiem scalenia zadania 2; punkty niezweryfikowane jako otwarte (O1 – O4); wynik `PROFILE LIST` bez „Wybierz profil”. Planowany wpis także w Spec 004 (SESSION-11, „Bezpieczeństwo”).
- **2026-10-30 – uproszczenie zmiany profilu (decyzje użytkownika P9, P10).** Zmiana profilu dotyczy zawsze profilu 1: przycisk „Zmień profil” otwiera formularz procentu, SMS `PROFILE 1 <procent>` (także dla 100), kod tylko po prośbie o zmianę profilu ze zgodnym procentem – nazwa profilu nie jest porównywana (ryzyko opisane w „Bezpieczeństwie”). PROF-14 wycofane. Zmienione: PROF-01 (parametr), PROF-02 (bez wyboru, numer 1), PROF-04 (zawsze z procentem), PROF-05 (kontekst tylko z procentem), PROF-07 (bez użycia w UI, bez kryterium sesji z nazwą), PROF-08, PROF-09 (porównanie tylko procentu), PROF-11 (`PROFILE LIST` bez przycisku), PROF-12 (kryteria: inna nazwa przy zgodnym procencie → kod; inny procent → bez kodu; inny nadawca, wiadomość sprzed wysłania), PROF-13 („Zmień profil” otwiera formularz), PROF-15 (tytuł, teksty podsumowania, fokus PIN – CMDUI-28), PROF-16 (nagłówek, teksty niezgodności, wynik listy bez pozycji), PROF-17 (numer 1, stare wpisy), PROF-18. Planowane zmiany treści w Spec 002 (PARSER-01), 003 (CMD-01, CMD-07 – CMD-09), 004 (SESSION-11, „Bezpieczeństwo”) i 007 (DATA-07) – wpisy w ich „Historii zmian”. Design: nowe interfejsy, decyzje i zadania zmiany 2026-10-30 (1 – 4; zadania 1 – 6 pierwszej wersji są scalone).
- **2026-10-28 – poprawki po recenzji PR #221 i zatwierdzenie.** PROF-07: nazwa = cała reszta linii, lista w jednej linii daje jedną pozycję i kończy się niezgodnością nazwy (stop), ograniczenie 2000 znaków; PROF-11: kryteria odpowiedzi innego rodzaju; PROF-12: odwołanie do BOL-19; PROF-17: jedno `addMigrations`, ścieżka 1 → 3, W3 warunkiem scalenia; „Przyszłe zmiany”. Design: pełna lista plików zadania 1 (dodaje `PROFILE_*` do `CommandId`), zadanie końcowe scalenia wpisów planowanych w spec 002–010. Pytania P1–P8 zamknięte, status `zatwierdzona`.
- **2026-10-28 – pierwsza wersja (szkic).** Zmienia CMD-01 (Spec 003: 7 wpisów, słowa kluczowe ze spacją), PARSER-01 (Spec 002), SESSION-06, SESSION-08, SESSION-11, SESSION-13 (Spec 004), DATA-07, DATA-09, DATA-14 (Spec 007), CMDUI-01, CMDUI-06, CMDUI-16, CMDUI-17, CMDUI-19, CMDUI-21, CMDUI-23 (Spec 009), HISTUI-03 (Spec 010).
