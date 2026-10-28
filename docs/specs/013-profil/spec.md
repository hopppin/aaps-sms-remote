# Spec 013: Profil – stan, lista i zmiana profilu

- **Status:** zatwierdzona
- **Prefiks wymagań:** `PROF`
- **Pakiety:** `commands`, `parser`, `session`, `data` / `data.platform` (historia), `ui.commands`, `ui.history`
- **Powiązane:** [Spec 002: Parser](../002-parser-odpowiedzi/spec.md); [Spec 003: Katalog](../003-katalog-polecen/spec.md); [Spec 004: Sesja](../004-sesja-polecenia/spec.md) (A1 – A7, SESSION-06, SESSION-08, SESSION-11, SESSION-13, SESSION-22); [Spec 007: Dane](../007-dane/spec.md); [Spec 009: UI – polecenia](../009-ui-polecenia/spec.md); [Spec 010: UI – historia](../010-ui-historia/spec.md); [Spec 012: Bolus](../012-bolus/spec.md) (interfejsy znacznikowe parsera, migracja 1 → 2)

## Cel

Użytkownik sprawdza z aplikacji aktualny profil AAPS (`PROFILE STATUS`), pobiera listę profili (`PROFILE LIST`), wybiera z niej profil i opcjonalnie procent, a po podsumowaniu z PIN-em aplikacja wysyła `PROFILE <n> [procent]` i odsyła kod tylko na prośbę AAPS o dokładnie ten profil i procent.

## Zakres

- Wpisy katalogu: `PROFILE STATUS` i `PROFILE LIST` (bez kodu, jednym dotknięciem), zmiana profilu `PROFILE <n> [procent]` (z kodem, podsumowanie z PIN-em).
- Parser: odpowiedź na `PROFILE STATUS`, lista profili, prośba o kod zmiany profilu (porównanie nazwy i procentu), „Zly kod”.
- Sesja: `PROFILE STATUS` / `PROFILE LIST` jak `BG`; zmiana profilu jak polecenie z kodem.
- UI: przyciski w panelu, wybór profilu z wyniku listy, formularz procentu, podsumowanie z PIN-em, przebieg i wynik.
- Historia: nowe polecenia, numer i procent profilu, migracja bazy 2 → 3.

**Poza zakresem:**

- Zmiana profilu z czasem trwania albo przesunięciem czasu (AAPS i tak tworzy zmianę bez czasu trwania).
- Rozpoznawanie odpowiedzi AAPS po poprawnym kodzie zmiany profilu (treść nieznana) – wynik „nieznany – sprawdź w AAPS” z treścią SMS (P4).
- Zapamiętywanie listy profili między uruchomieniami i automatyczne pobieranie listy.
- Limit procentu jako ustawienie (stała w aplikacji; P3).

## Fakty o AAPS (źródło)

Źródło: SMS-y z telefonu użytkownika (pliki w [dane/](dane/README.md)) i kod `SmsCommunicatorPlugin` przekazany przez użytkownika.

| Nr | Fakt | Źródło | Skutek |
| --- | --- | --- | --- |
| R1 | `PROFILE LIST` → lista numerowana `N. nazwa`, jedna pozycja w linii (`1. LocalProfile1`, `2. LocalProfile2`); bez kodu. | SMS (podział linii – P1) | PROF-07. |
| R2 | `PROFILE STATUS` → `LocalProfile1 (115%)`; bez kodu. | SMS | PROF-06. |
| R3 | `PROFILE 1` → `Aby przelaczyc profil na LocalProfile1 100% odpowiedz kodem z aplikacji Authenticator dla: <nazwa>, z doklejonym PIN-em`. Nazwa profilu w prośbie pochodzi z tej samej listy co `PROFILE LIST`. | SMS, kod | PROF-08: porównanie nazwy i procentu. |
| R4 | `PROFILE 99` (numer spoza listy) i `PROFILE 1 0` (procent 0) → `Bledny format`. Numer 0 też daje błąd formatu; numer ujemny może nie być obsłużony przez AAPS. | SMS, kod | Aplikacja nie wysyła takich poleceń (PROF-02); odpowiedź = nierozpoznana (PROF-10). |
| R5 | Po kodzie AAPS tworzy zmianę profilu (`createProfileSwitch`, czas trwania 0, procent). Procent **nie** przechodzi w AAPS przez żadne ograniczenia. | kod | Aplikacja sama ogranicza procent (PROF-02, P3). |
| R6 | Każde nowe polecenie nadpisuje oczekującą w AAPS prośbę o kod (A6). | kod | Aplikacja ma jedną aktywną sesję (SESSION-22), więc `PROFILE LIST`/`STATUS` nie trafia w środek innego polecenia z aplikacji. |

**Niezweryfikowane na telefonie:** odpowiedź po poprawnym kodzie zmiany profilu, „Zly kod” dla profilu, odpowiedź przy pustej liście profili.

## Pojęcia

- **Lista profili** – pozycje (numer, nazwa) z odpowiedzi na `PROFILE LIST`, numery 1…n po kolei.
- **Wybór profilu** – pozycja listy wybrana przez użytkownika, trzymana w pamięci `ui` do wysłania polecenia albo zamknięcia formularza.
- **Procent** – liczba całkowita, domyślnie 100.

## Wymagania

Wspólne dane kryteriów: jak w Spec 004 i 012 (sekret RFC 6238, PIN `4321`, `T0`, kod `050471` w chwili 60 000); wiadomości z [dane/](dane/README.md) oraz `zly-kod.txt` ze Spec 002; wybór profilu (1, `LocalProfile1`).

### Katalog

### PROF-01: Wpisy katalogu

| Identyfikator | Słowo kluczowe | Parametr | Wymaga kodu | Zatwierdzenie |
| --- | --- | --- | --- | --- |
| `PROFILE_STATUS` | `PROFILE STATUS` | brak | nie | jedno dotknięcie |
| `PROFILE_LIST` | `PROFILE LIST` | brak | nie | jedno dotknięcie |
| `PROFILE_SWITCH` | `PROFILE` | numer, nazwa z listy, procent | tak | podsumowanie z PIN-em |

Kolejność katalogu: `BG`, `PUMP`, `CARBS`, `BOLUS`, `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH`. Zmiana CMD-01: słowo kluczowe składa się z wielkich liter `A`–`Z` i pojedynczych spacji między słowami; słowa kluczowe są unikalne.

**Kryteria akceptacji:**

- **Zakładając** katalog, **gdy** pobrano wpisy, **wtedy** jest ich 7 w podanej kolejności, a dane wpisów profilu są jak w tabeli.
- **Zakładając** katalog, **gdy** sprawdzono słowa kluczowe, **wtedy** są unikalne i pasują do `[A-Z]+( [A-Z]+)*`.

### PROF-02: Polecenie zmiany profilu

Polecenie zmiany profilu powstaje tylko z wyboru profilu (numer, nazwa) i procentu przez walidację:

- numer 1–99 (z listy; aplikacja nigdy nie wysyła 0, liczby ujemnej ani numeru spoza listy, R4);
- nazwa niepusta, bez LF (z listy);
- procent 50–200 włącznie (decyzja użytkownika, P3) – „poniżej minimum” / „powyżej maksimum”.

Brak wyjątku dla danych od użytkownika; nie ma innej drogi utworzenia polecenia.

**Kryteria akceptacji:**

- **Zakładając** wybór (1, `LocalProfile1`), **gdy** utworzono polecenie z procentem 100, 50 i 200, **wtedy** każde jest poprawne.
- **Zakładając** ten wybór, **gdy** utworzono z procentem 49, 201, 0, −10, `Int.MAX_VALUE`, **wtedy** wyniki: poniżej minimum, powyżej maksimum, poniżej minimum, poniżej minimum, powyżej maksimum, bez wyjątku.
- **Zakładając** wybór z numerem 0 albo 100 albo pustą nazwą, **gdy** utworzono polecenie, **wtedy** wynik = błędny wybór, bez wyjątku.

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

`PROFILE STATUS` → `PROFILE STATUS`; `PROFILE LIST` → `PROFILE LIST`; zmiana profilu → `PROFILE <n>` dla procentu 100, w przeciwnym razie `PROFILE <n> <p>` (pojedyncze spacje, liczby bez zer wiodących). Nazwa profilu nie trafia do SMS-a.

**Kryteria akceptacji:**

- **Zakładając** polecenia `PROFILE STATUS`, `PROFILE LIST`, zmianę (1, 100), (2, 115), (12, 50), **gdy** zbudowano treści, **wtedy** są to kolejno `PROFILE STATUS`, `PROFILE LIST`, `PROFILE 1`, `PROFILE 2 115`, `PROFILE 12 50`.

### PROF-05: Kontekst parsera i brak ujawniania

`PROFILE STATUS` → kontekst „stan profilu”, `PROFILE LIST` → „lista profili”, zmiana profilu → „zmiana profilu (nazwa, procent)”. `toString()` poleceń, wyników walidacji, kontekstów i wyników parsera nie zawiera nazwy profilu, numeru, procentu ani treści SMS.

**Kryteria akceptacji:**

- **Zakładając** zmianę (2, `LocalProfile2`, 115), **gdy** odwzorowano ją na kontekst, **wtedy** kontekst = zmiana profilu z nazwą `LocalProfile2` i procentem 115.
- **Zakładając** to polecenie, jego kontekst i wynik walidacji, **gdy** wywołano `toString()`, **wtedy** żaden tekst nie zawiera `LocalProfile` ani `115`.
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

W kontekście „lista profili” wynik „lista profili” powstaje, gdy treść po normalizacji ma n ≥ 1 linii, linia i (od 1) ma postać `<i>. <nazwa>` (numer dokładnie równy i, kropka, spacja, nazwa ≥ 1 znak), n ≤ 99, a nazwy są parami różne. Nazwa to cała reszta linii po `<i>. ` (do końca linii, także gdy zawiera spacje, cyfry i kropki). Wynik zawiera listę (numer, nazwa) i treść. Inaczej – „nierozpoznana”.

Skutek dla listy podanej w jednej linii (np. `1. A 2. B`): parser zwraca jedną pozycję (1, `A 2. B`). Jest to bezpieczne – po wyborze tej pozycji AAPS poprosi o kod dla profilu `A`, nazwa się nie zgodzi i sesja kończy się bez kodu (PROF-09, PROF-12).

Ograniczenie: treść dłuższa niż 2000 znaków jest od razu nierozpoznana (PARSER-02, `ReplyNormalizer`), więc bardzo długa lista profili da wynik „nierozpoznana” z treścią i bez wyboru.

**Kryteria akceptacji:**

- **Zakładając** `profile-list.txt`, **gdy** kontekst = lista profili, **wtedy** wynik = lista [(1, `LocalProfile1`), (2, `LocalProfile2`)] z treścią równą wejściu.
- **Zakładając** `profile-list.txt` z końcami linii `\r\n`, **gdy** kontekst = lista profili, **wtedy** ta sama lista.
- **Zakładając** wariant syntetyczny `1. Dzien roboczy` (jedna linia), **gdy** kontekst = lista profili, **wtedy** lista [(1, `Dzien roboczy`)].
- **Zakładając** warianty `2. A` (zaczyna od 2), `1. A\n3. B` (luka), `1. A\n1. B`, `1.A`, `1. A\n2. A` (powtórzona nazwa), `Brak profili`, **gdy** kontekst = lista profili, **wtedy** nierozpoznana.
- **Zakładając** wariant `1. A 2. B` (jedna linia), **gdy** kontekst = lista profili, **wtedy** lista [(1, `A 2. B`)]; **zakładając** sesję zmiany (1, `A 2. B`, 100) i syntetyczną prośbę `Aby przelaczyc profil na A 100% odpowiedz kodem z aplikacji Authenticator dla: Jan, z doklejonym PIN-em`, **gdy** prośba przyszła, **wtedy** `Failed(AMOUNT_MISMATCH)` i bez kodu.
- **Zakładając** listę o długości 2001 znaków (np. 99 pozycji z długimi nazwami), **gdy** kontekst = lista profili, **wtedy** nierozpoznana, bez wyjątku.

### PROF-08: Prośba o kod zmiany profilu

W kontekście „zmiana profilu (nazwa N, procent p)” wiadomość ma postać prośby o kod, gdy cała treść po normalizacji to jedna linia:

```text
Aby przelaczyc profil na <nazwa> <P>% odpowiedz kodem z aplikacji Authenticator dla: <konto>, z doklejonym PIN-em
```

`<P>` – liczba 1–9999 bez zer wiodących; `<konto>` – jak `<nazwa>` w PARSER-05. Gdy `<nazwa>` = N (znak w znak) i `<P>` = p → „prośba o kod (profil)” z treścią; inaczej PROF-09.

**Kryteria akceptacji:**

- **Zakładając** `profile-prosba-o-kod.txt`, **gdy** kontekst = zmiana (`LocalProfile1`, 100), **wtedy** prośba o kod z treścią równą wejściu.
- **Zakładając** wariant syntetyczny dla `Dzien roboczy 115%`, **gdy** kontekst = zmiana (`Dzien roboczy`, 115), **wtedy** prośba o kod.
- **Zakładając** `profile-prosba-o-kod.txt` z drugą linią albo z `przełączyć`, **gdy** kontekst = zmiana (`LocalProfile1`, 100), **wtedy** nierozpoznana.
- **Zakładając** `profile-prosba-o-kod.txt`, **gdy** kontekst = `Carbs(1)`, `Bolus(10)`, `BG` albo lista profili, **wtedy** nierozpoznana.

### PROF-09: Niezgodny profil albo procent

Wiadomość w postaci prośby o kod (PROF-08), w której nazwa albo procent różni się od kontekstu, daje wynik „niezgodny profil” z treścią (bez wartości w `toString()`). Taki wynik nigdy nie jest „prośbą o kod”.

**Kryteria akceptacji:**

- **Zakładając** `profile-prosba-o-kod.txt`, **gdy** kontekst = zmiana (`LocalProfile2`, 100), **wtedy** niezgodny profil.
- **Zakładając** `profile-prosba-o-kod.txt`, **gdy** kontekst = zmiana (`LocalProfile1`, 115), **wtedy** niezgodny profil.
- **Zakładając** `profile-prosba-o-kod.txt`, **gdy** kontekst = zmiana (`LocalProfile`, 100) albo (`localprofile1`, 100), **wtedy** niezgodny profil.

### PROF-10: Zły kod i odmowy

W kontekście zmiany profilu `Zly kod. Polecenie anulowano.` → „zły kod”. `Bledny format` i każda inna treść → „nierozpoznana” z treścią (PARSER-09).

**Kryteria akceptacji:**

- **Zakładając** `zly-kod.txt`, **gdy** kontekst = zmiana (`LocalProfile1`, 100), **wtedy** zły kod.
- **Zakładając** `profile-bledny-format.txt`, **gdy** kontekst = zmiana profilu, stan profilu albo lista profili, **wtedy** nierozpoznana z treścią `Bledny format`.

### Sesja

### PROF-11: PROFILE STATUS i PROFILE LIST

Sesje `PROFILE STATUS` i `PROFILE LIST` przebiegają jak `BG` (SESSION-03, SESSION-06, SESSION-07): w `AWAITING_REPLY` wynik „stan profilu” albo „lista profili” kończy sesję `Completed` z tym wynikiem i treścią; każdy inny – `Failed(UNRECOGNIZED_REPLY)` z treścią. Nie przyjmują danych do kodu (SESSION-02). Uruchamiane wyłącznie przez koordynatora, więc nigdy w trakcie innej sesji (SESSION-22).

**Kryteria akceptacji:**

- **Zakładając** sesję `PROFILE LIST`, **gdy** przychodzi `profile-list.txt`, **wtedy** wynik = `Completed` z listą 2 pozycji; port wysyłania dostał dokładnie `PROFILE LIST`.
- **Zakładając** sesję `PROFILE STATUS`, **gdy** przychodzi `profile-status.txt`, **wtedy** `Completed`; gdy przychodzi `profile-bledny-format.txt` – `Failed(UNRECOGNIZED_REPLY)` z tą treścią.
- **Zakładając** sesję `PROFILE STATUS`, **gdy** przychodzi `profile-list.txt`, **wtedy** `Failed(UNRECOGNIZED_REPLY)` (lista nie jest odpowiedzią na stan); **zakładając** sesję `PROFILE LIST`, **gdy** przychodzi `profile-status.txt`, **wtedy** `Failed(UNRECOGNIZED_REPLY)`.
- **Zakładając** aktywną sesję `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** wystartowano `PROFILE LIST`, **wtedy** wynik startu = „zajęte”, port nie dostał `PROFILE LIST`, a sesja `CARBS` trwa dalej.

### PROF-12: Zmiana profilu – przed kodem i po kodzie

Sesja zmiany profilu przebiega jak `CARBS`/`BOLUS` (fazy, limity, SESSION-08 – SESSION-10, SESSION-15 – SESSION-20):

| Wynik parsera | Przed kodem (`AWAITING_CODE_REQUEST` / `PREPARING_CODE`) | Po kodzie (`AWAITING_CONFIRMATION`) |
| --- | --- | --- |
| prośba o kod (profil) | SESSION-08 / `Failed(UNEXPECTED_REPLY)` | `Unknown(UNEXPECTED_REPLY)`, bez drugiego kodu |
| niezgodny profil | `Failed(AMOUNT_MISMATCH)`, bez kodu | `Unknown(AMOUNT_MISMATCH)` |
| zły kod | `Failed(UNEXPECTED_REPLY)` | `Failed(WRONG_CODE)` (P4) |
| nierozpoznana (np. `Bledny format`, nieznane potwierdzenie) | `Failed(UNRECOGNIZED_REPLY)`, bez kodu | `Unknown(UNRECOGNIZED_REPLY)` z treścią |

Brak potwierdzenia w 6 min → `Unknown(CONFIRMATION_TIMEOUT)`. Powód `AMOUNT_MISMATCH` obejmuje dla profilu niezgodną nazwę albo procent (bez nowego powodu w `FailureReason`). Kod jest odsyłany tylko na `ProfileCodeRequest` w sesji zmiany profilu; prośba o kod innego polecenia daje stop bez kodu (BOL-19).

**Kryteria akceptacji:**

- **Zakładając** sesję zmiany (1, `LocalProfile1`, 100), **gdy** w chwili 60 000 przychodzi `profile-prosba-o-kod.txt`, **wtedy** port dostał `PROFILE 1`, a w chwili 60 000 `0504714321`.
- **Zakładając** sesję zmiany (1, `LocalProfile2`, 100) (lista zmieniła się w AAPS po jej pobraniu), **gdy** przychodzi `profile-prosba-o-kod.txt`, **wtedy** `Failed(AMOUNT_MISMATCH)` i bez kodu.
- **Zakładając** sesję zmiany (1, `LocalProfile1`, 115), **gdy** przychodzi `profile-bledny-format.txt`, **wtedy** `Failed(UNRECOGNIZED_REPLY)` i bez kodu.
- **Zakładając** sesję zmiany po kodzie, **gdy** przychodzi syntetyczny `Profil zmieniony`, **wtedy** `Unknown(UNRECOGNIZED_REPLY)` z tą treścią; gdy `zly-kod.txt` – `Failed(WRONG_CODE)`.
- **Zakładając** sesję zmiany (1, `LocalProfile1`, 100), **gdy** przychodzi `bolus-prosba-o-kod-0-10.txt` albo `carbs-prosba-o-kod.txt`, **wtedy** `Failed(UNRECOGNIZED_REPLY)` i bez kodu (BOL-19).

### UI

### PROF-13: Przyciski profilu w panelu

Panel poleceń ma sekcję „Profil” (pod „Bolus”) z przyciskami „Aktualny profil” (`PROFILE STATUS`) i „Zmień profil…” (`PROFILE LIST`). Oba działają jednym dotknięciem jak `BG` (CMDUI-06): `launch(command, pin = null)`, przebieg, wynik. Dostępność jak `BG` (numer, uprawnienia SMS, brak polecenia w toku i uruchamiania); nie zależą od sekretu ani limitów.

**Kryteria akceptacji:**

- **Zakładając** wspólne dane, **gdy** wywołano `onProfileStatusClicked()` (osobno `onProfileListClicked()`), **wtedy** `FakeSmsSender` dostał dokładnie `PROFILE STATUS` (`PROFILE LIST`), a `load()` sekretu nie był wywołany.
- **Zakładając** stan sekretu `ABSENT` i limity `LoadFailed`, **gdy** odczytano stan, **wtedy** oba przyciski profilu są dostępne.

### PROF-14: Wybór profilu z wyniku listy

Wynik `Completed` polecenia `PROFILE LIST` pokazuje komunikat „Lista profili z AAPS. Wybierz profil, aby go zmienić.”, treść SMS i przycisk dla każdej pozycji z tekstem „N. nazwa”. Dotknięcie pozycji zamyka wynik (jak „Zamknij”, CMDUI-21) i otwiera na zakładce Polecenia formularz zmiany profilu dla tej pozycji. Wybór jest dostępny tylko, gdy sekret nie jest `ABSENT` ani `INVALIDATED` (CMDUI-04); inaczej pozycje są nieaktywne, a wynik pokazuje baner sekretu. Lista i wybór istnieją tylko w pamięci (bez `SavedStateHandle`), a `RunUiState` zawiera tylko pary (numer, nazwa).

**Kryteria akceptacji:**

- **Zakładając** wynik `PROFILE LIST` po `profile-list.txt`, **gdy** odczytano stan wyniku, **wtedy** pozycje = [(1, `LocalProfile1`), (2, `LocalProfile2`)].
- **Zakładając** ten wynik, **gdy** wywołano `onProfileChosen(2)`, **wtedy** `CurrentRun` jest pusty, a formularz w `CommandsViewModel` jest otwarty dla (2, `LocalProfile2`) z tekstem procentu `"100"`.
- **Zakładając** wynik `PROFILE STATUS` albo `Failed` dla `PROFILE LIST`, **gdy** odczytano stan, **wtedy** brak pozycji do wyboru.

### PROF-15: Formularz procentu i podsumowanie z PIN-em

Formularz pokazuje „Profil: N. nazwa”, pole „Procent (%)” (`KeyboardType.Number`, tekst początkowy `100`), „Dalej” i „Anuluj”. „Dalej” waliduje procent (PROF-03; błędy: „Podaj procent (50–200).”, „Tylko cyfry.”, „Najmniejszy procent to 50.”, „Największy procent to 200.”) i otwiera podsumowanie z PIN-em (zachowanie CMDUI-10, CMDUI-11): „Zmienić profil na nazwa (P%)?” i „Aplikacja wyśle PROFILE N[ P], a kod z PIN-em odeśle dopiero, gdy AAPS poprosi o kod dla nazwa P%.”. „Anuluj”, Wstecz i `onStop()` zamykają formularz i podsumowanie, czyszczą PIN i porzucają wybór (trzeba ponownie pobrać listę). Formularz, podsumowanie i wynik listy mają `FLAG_SECURE` (znacznik „ekran chroniony” obejmuje formularz; CMDUI-23).

**Kryteria akceptacji:**

- **Zakładając** formularz dla (1, `LocalProfile1`) i tekst `"115"`, **gdy** wywołano „Dalej”, a potem „Wyślij” z PIN-em `4321`, **wtedy** `launch` dostał zmianę (1, `LocalProfile1`, 115), PIN w stanie = `""`, a `FakeSmsSender` dostał `PROFILE 1 115`.
- **Zakładając** formularz z tekstem `"201"`, **gdy** wywołano „Dalej”, **wtedy** błąd = powyżej maksimum, podsumowanie zamknięte.
- **Zakładając** otwarte podsumowanie z PIN-em `4321`, **gdy** wywołano `onStop()`, **wtedy** PIN = `""`, formularz i podsumowanie zamknięte, wyboru brak.
- **Zakładając** otwarty formularz, **gdy** odczytano znacznik „ekran chroniony”, **wtedy** = `true`.

### PROF-16: Przebieg i wynik

Nagłówki: „Polecenie PROFILE STATUS”, „Polecenie PROFILE LIST”, „Zmiana profilu: nazwa P%”. Kroki: `PROFILE STATUS`/`LIST` – 2 kroki jak `BG`; zmiana profilu – 5 kroków jak `CARBS`. Komunikaty wyniku (pozostałe – CMDUI-19):

| Wynik | Rodzaj | Komunikat |
| --- | --- | --- |
| `Completed`, `PROFILE STATUS` | sukces | „Odpowiedź AAPS na PROFILE STATUS” |
| `Completed`, `PROFILE LIST` | sukces | „Lista profili z AAPS. Wybierz profil, aby go zmienić.” |
| `Failed(AMOUNT_MISMATCH)`, zmiana profilu | błąd | „AAPS podał inny profil lub procent niż wybrany. Pobierz listę ponownie.” |
| `Unknown(AMOUNT_MISMATCH)`, zmiana profilu | nieznany | „Po wysłaniu kodu AAPS podał inny profil lub procent.” |
| `Unknown(UNRECOGNIZED_REPLY)`, zmiana profilu | nieznany | „Po wysłaniu kodu przyszła odpowiedź AAPS, której aplikacja nie rozpoznaje. Przeczytaj treść SMS i sprawdź w AAPS.” |

**Kryteria akceptacji:**

- **Zakładając** każdy wiersz tabeli, **gdy** odwzorowano wynik, **wtedy** rodzaj i komunikat odpowiadają wierszowi, a treść SMS jest pokazana.
- **Zakładając** `PROFILE_SWITCH` i każdą fazę, **gdy** wyliczono kroki, **wtedy** są jak dla `CARBS`; dla `PROFILE_STATUS` – jak dla `BG`.

### PROF-17: Historia

Zmiana DATA-07, DATA-09, DATA-14, HISTUI-03: nowe stałe teksty poleceń `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH`; pola wpisu „numer profilu” i „procent profilu” (tylko `PROFILE_SWITCH`, inaczej brak). Nazwa profilu nie jest osobnym polem (jest w treści odpowiedzi). Baza w wersji 3: migracja 2 → 3 dodaje kolumny `profile_number INTEGER` i `profile_percent INTEGER` (obie dopuszczają `NULL`); plik `3.json` w repo. Obie migracje są rejestrowane jednym wywołaniem `addMigrations(MIGRATION_1_2, MIGRATION_2_3)`, więc baza w wersji 1 przechodzi do 3 kolejno przez 2. Zadanie z migracją można scalić dopiero po ręcznym sprawdzeniu W3 na prawdziwej bazie (wynik w opisie PR). Etykiety: „PROFILE STATUS”, „PROFILE LIST”, „Zmiana profilu N, P%”, przy braku pól „Zmiana profilu (szczegóły nieznane)”.

**Kryteria akceptacji:**

- **Zakładając** wynik zmiany (2, `LocalProfile2`, 115) `Unknown(UNRECOGNIZED_REPLY)`, **gdy** utworzono wpis, **wtedy** polecenie = `PROFILE_SWITCH`, numer = 2, procent = 115, a żadne pole poza treścią nie zawiera `LocalProfile2`.
- **Zakładając** wiersz `PROFILE_LIST` z numerem 3, **gdy** go odczytano, **wtedy** numer i procent = brak.
- **Zakładając** `3.json`, **gdy** test JVM go przeczytał, **wtedy** wersja = 3, kolumny wersji 2 i dwie nowe bez `NOT NULL`; migracja 2 → 3 ma dokładnie dwa `ALTER TABLE command_history ADD COLUMN …`.
- **Zakładając** źródło `AapsRemoteDatabase.kt`/`DataModule.kt`, **gdy** test JVM je przeczytał, **wtedy** jest dokładnie jedno wywołanie `addMigrations(` z `MIGRATION_1_2` i `MIGRATION_2_3`; **zakładając** listę migracji, **gdy** test wyliczył ścieżkę 1 → 3, **wtedy** to `MIGRATION_1_2`, `MIGRATION_2_3` (łącznie 3 polecenia `ALTER TABLE`).
- **Zakładając** telefon z bazą w wersji 2 (W3), **gdy** zainstalowano nową wersję, **wtedy** wpisy zachowane; **zakładając** telefon z bazą w wersji 1 (wersja aplikacji sprzed Spec 012), **gdy** zainstalowano od razu wersję z bazą 3, **wtedy** wpisy zachowane, a nowe wpisy bolusa i profilu mają swoje pola.

### PROF-18: Brak ujawniania danych

Nazwy profili, numer, procent, PIN, kod i treść SMS nie trafiają do logów, wyjątków ani `toString()` (zasady Spec 002 – 010 obejmują nowe typy, w tym `ProfileSelection`).

**Kryteria akceptacji:**

- **Zakładając** stan `CommandsViewModel` z formularzem dla (2, `LocalProfile2`), tekstem `"115"` i PIN-em `4321`, oraz stan wyniku listy, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `LocalProfile`, `115` ani `4321`.

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Odpowiedź na `PROFILE LIST`/`STATUS` w nieznanym formacie (w tym pusta lista, powtórzone nazwy) | `Failed(UNRECOGNIZED_REPLY)` z treścią, brak wyboru | PROF-06, PROF-07, PROF-11 |
| Procent pusty / zły / poza 50–200 | błąd pod polem | PROF-03, PROF-15 |
| Lista w AAPS zmieniona po pobraniu (inna nazwa pod numerem) | `Failed(AMOUNT_MISMATCH)`, kod nie jest wysyłany | PROF-09, PROF-12 |
| `Bledny format` | `Failed(UNRECOGNIZED_REPLY)` z treścią, bez kodu | PROF-10, PROF-12 |
| Zły kod | `Failed(WRONG_CODE)` | PROF-12 |
| Nieznana odpowiedź po kodzie | `Unknown(UNRECOGNIZED_REPLY)` z treścią i ostrzeżeniem | PROF-12, PROF-16 |
| `PROFILE LIST` w trakcie innego polecenia | „zajęte” / przycisk nieaktywny | PROF-11, CMDUI-05 |
| Wyjście z zakładki / tło z otwartym formularzem | wybór porzucony, PIN wyczyszczony | PROF-15 |

## Bezpieczeństwo

- **Kod tylko na zgodną prośbę:** nazwa i procent w prośbie AAPS muszą być identyczne z wybranymi (PROF-08, PROF-09); numer z nieaktualnej listy nie przejdzie, bo AAPS podaje nazwę. Inny nadawca, wiadomość sprzed wysłania, nierozpoznana treść – stop (Spec 004, 005).
- **Procent nieograniczany przez AAPS (R5):** aplikacja przyjmuje tylko 50–200% (P3); 0 i wartości ujemne nigdy nie są wysyłane.
- **Bez ponawiania:** najwyżej 1 SMS polecenia i 1 kod na sesję; aplikacja nie pobiera listy ani nie zmienia profilu sama.
- **PIN i kod:** zasady Spec 004 i 009; `FLAG_SECURE` na formularzu, podsumowaniu, przebiegu i wyniku (PROF-15, CMDUI-23).
- **`PROFILE STATUS`/`LIST` bez kodu** nadpisują w AAPS oczekującą prośbę o kod (A6), ale aplikacja nie wyśle ich w trakcie własnego polecenia (SESSION-22).
- **Dane w repo:** domyślne nazwy profili AAPS, imię `Jan`; brak numerów, PIN-ów, kodów.

## Dane testowe

- [dane/](dane/README.md): `profile-status.txt`, `profile-list.txt`, `profile-prosba-o-kod.txt`, `profile-bledny-format.txt` – prawdziwe SMS-y użytkownika (imię → `Jan`).
- Warianty syntetyczne tylko w kryteriach.
- Weryfikacja na telefonie: AAPS z wirtualną pompą; po teście przywrócić profil 100%.

## Weryfikacja na urządzeniu

| Nr | Co sprawdzić | Wymagania |
| --- | --- | --- |
| W1 | „Aktualny profil” i „Zmień profil…”: treść odpowiedzi, lista z przyciskami, `FLAG_SECURE` wyniku | PROF-11, PROF-13, PROF-14 |
| W2 | Zmiana na profil 2 z procentem 90 (wirtualna pompa): formularz, podsumowanie, kod, wynik; zanotować treść odpowiedzi po kodzie (dane do P4); zły PIN → zanotować | PROF-12, PROF-15, PROF-16 |
| W3 | Aktualizacja z bazy w wersji 2: wpisy zachowane, nowe wpisy profilu w Historii | PROF-17 |
| W4 | Przejście na Ustawienia z otwartym formularzem → po powrocie panel, wybór porzucony | PROF-15 |

## Otwarte pytania

Brak. Decyzje użytkownika przyjęte przy tej specyfikacji (2026-10-28):

- [x] **Literówka w `PROFILE STATUS`.** Rozstrzygnięte (użytkownik): treść to `LocalProfile1 (115%)`; „LocalPofile1” było literówką.
- [x] **P1. Podział linii w `PROFILE LIST`.** Potwierdzona jest tylko lista numerowana „N. nazwa”. Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – każda pozycja w osobnej linii (LF/CR/CRLF, PARSER-02); inny układ → nierozpoznana (treść pokazana, bez wyboru). Do sprawdzenia: czy jest dodatkowy nagłówek lub końcowy tekst, co przy pustej liście.
- [x] **P2. Format `PROFILE STATUS`.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – tylko `<nazwa> (<P>%)`. Nie wiemy, czy przy 100% AAPS pomija nawias i czy dopisuje przesunięcie czasu; inny format → `Failed(UNRECOGNIZED_REPLY)` z treścią (użytkownik i tak ją widzi).
- [x] **P3. Zakres procentu.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – 50–200%, liczby całkowite, bez dodatkowego pytania „Na pewno” (procent i nazwa są w podsumowaniu). Uzasadnienie: AAPS nie ogranicza procentu (R5); 50–200% obejmuje typowe zmiany (choroba, wysiłek), a wartości skrajne (np. 10% albo 500%) przez SMS byłyby groźne. Alternatywa: zakres jak w oknie zmiany profilu AAPS albo pytanie „Na pewno” poza 80–120%.
- [x] **P4. Odpowiedzi po kodzie.** Treść po poprawnym kodzie nieznana → `Unknown(UNRECOGNIZED_REPLY)` z treścią. „Zly kod” → `Failed(WRONG_CODE)` (wspólny kod AAPS, A2) – do potwierdzenia (W2). Po zebraniu SMS-a – wzorzec potwierdzenia jak dla bolusa. Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – zgodnie z opisem.
- [x] **P5. Powtórzone nazwy na liście.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – lista nierozpoznana (nie da się sprawdzić prośby o kod po nazwie).
- [x] **P6. Wersja AAPS.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – 3.4.2.6 jak w Spec 002.
- [x] **P7. Historia.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – numer i procent w kolumnach, nazwa tylko w treści odpowiedzi.
- [x] **P8. Wybór porzucany przy wyjściu z zakładki** (PROF-15) – propozycja dla prostoty i aktualności listy. Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – zgodnie z opisem.

## Przyszłe zmiany (nie blokują)

- Okno „Na pewno P%?” dla procentu spoza 80–120% (sugestia z recenzji PR #221; dziś procent i nazwa są tylko w podsumowaniu).
- Wzorzec potwierdzenia zmiany profilu po zebraniu SMS-a z W2 – zadanie uzupełniające (zmiana tej spec), zamiast wyniku „nieznany”.

## Historia zmian

- **2026-10-28 – poprawki po recenzji PR #221 i zatwierdzenie.** PROF-07: nazwa = cała reszta linii, lista w jednej linii daje jedną pozycję i kończy się niezgodnością nazwy (stop), ograniczenie 2000 znaków; PROF-11: kryteria odpowiedzi innego rodzaju; PROF-12: odwołanie do BOL-19; PROF-17: jedno `addMigrations`, ścieżka 1 → 3, W3 warunkiem scalenia; „Przyszłe zmiany”. Design: pełna lista plików zadania 1 (dodaje `PROFILE_*` do `CommandId`), zadanie końcowe scalenia wpisów planowanych w spec 002–010. Pytania P1–P8 zamknięte, status `zatwierdzona`.
- **2026-10-28 – pierwsza wersja (szkic).** Zmienia CMD-01 (Spec 003: 7 wpisów, słowa kluczowe ze spacją), PARSER-01 (Spec 002), SESSION-06, SESSION-08, SESSION-11, SESSION-13 (Spec 004), DATA-07, DATA-09, DATA-14 (Spec 007), CMDUI-01, CMDUI-06, CMDUI-16, CMDUI-17, CMDUI-19, CMDUI-21, CMDUI-23 (Spec 009), HISTUI-03 (Spec 010).
