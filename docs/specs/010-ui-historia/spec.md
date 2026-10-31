# Spec 010: UI – historia poleceń

- **Status:** zatwierdzona
- **Prefiks wymagań:** `HISTUI`
- **Pakiety:** `ui` (`ui.history`; drobna zmiana w `ui.commands` i `ui.navigation`)
- **Powiązane:** [docs/plan.md](../../plan.md#ekrany-aplikacji) (ekran „Historia i ustawienia”), [docs/plan.md](../../plan.md#bezpieczeństwo) („FLAG_SECURE na ekranach z danymi”); [Spec 007: Dane](../007-dane/spec.md) (`CommandHistory`, `HistorySnapshot`, `HistoryEntry`, `HistoryOutcome`, `ClearHistoryResult`, `HistoryLimits`, DATA-07 – DATA-12, W3 – W6); [Spec 008: UI – ustawienia](../008-ui-ustawienia/spec.md) (nawigacja, UI-02, UI-03, UI-20, UI-21, `SecureWindowEffect`, podział weryfikacji P3); [Spec 009: UI – polecenia](../009-ui-polecenia/spec.md) (`ResultKind`, `ResultMessage`, `ResultMapper`, teksty wyniku, CMDUI-19 – CMDUI-21, CMDUI-23, P7); [stan prac](../../stan-prac.md)

## Cel

Użytkownik widzi w zakładce Historia ostatnie polecenia wysłane z aplikacji (do 200) z wynikiem opisanym tymi samymi słowami co ekran wyniku ze Spec 009, sprawdza szczegóły wpisu (czasy, treść odpowiedzi AAPS) i może wyczyścić całą historię po potwierdzeniu. Historia jest też jedynym miejscem, w którym widać wynik polecenia zakończonego przy zamkniętej aplikacji po ponownym uruchomieniu procesu (P7 w Spec 009).

## Zakres

- Zakładka Historia zamiast zaślepki ze Spec 008 (UI-03): stany „wczytywanie”, „pusto”, „błąd odczytu” z ponowieniem, lista wpisów.
- Wiersz listy: polecenie (`BG`, `PUMP`, `CARBS X g`, `BOLUS X j.`, `PROFILE STATUS`, `PROFILE LIST`, zmiana profilu), czas, rodzaj wyniku i komunikat.
- Szczegóły wpisu po dotknięciu (P1): wynik z dodatkami, cztery czasy, treść odpowiedzi AAPS, liczba wiadomości nieoczekiwanych.
- Wspólne odwzorowanie powodów wyniku na komunikaty z `ui.commands` (bez kopii tabeli ze Spec 009) i bezpieczne pokazanie wpisów nierozpoznanych (DATA-09).
- Czyszczenie całej historii z potwierdzeniem i komunikatem wyniku.
- `FLAG_SECURE` na zakładce Historia (P3).
- Teksty po polsku w `strings.xml`.
- Ręczna weryfikacja: W3, W4, W5 i W6 (część historii) ze Spec 007 oraz własne punkty W1–W8.

**Poza zakresem:**

- Zmiany w `data` (zapis, retencja, schemat, kodek) – Spec 007; ta specyfikacja tylko czyta `CommandHistory`.
- Usuwanie pojedynczego wpisu, filtrowanie, wyszukiwanie, eksport, udostępnianie wpisu (DATA-12: tylko czyszczenie całości).
- Ponowne wysłanie polecenia z historii (AGENTS.md: bez automatycznego ponawiania; ponowienie tylko z zakładki Polecenia).
- Pokazywanie identyfikatora wpisu, numeru telefonu, treści wiadomości nieoczekiwanych (DATA-08).
- Testy Robolectric i Compose UI (decyzja użytkownika); kompozycje sprawdzane ręcznie.

## Założenia i zależności

- **Spec 007 zaimplementowana** (stan prac): `CommandHistory` z wiązaniem Hilt (`HistoryRepository`), strumień `entries` emituje `Entries` od najnowszego albo `Unavailable` i wtedy się kończy (DATA-11); `clearAll()` zwraca `Cleared`/`Failed` i nie rzuca (DATA-12, DATA-15); kodek nie rzuca i daje `UNRECOGNIZED`/`Unrecognized`/powód = `null` (DATA-09).
- **Spec 009 częściowo zaimplementowana przed 010:** `ResultKind`, `ResultMessage`, `ResultMapper` (zadanie 2 design 009), funkcje tekstów wyniku i klucze `result_*`, `run_unexpected` (zadanie 7), licznik `FLAG_SECURE` (zadanie 6). Szczegóły kolejności – design, „Podział na zadania”.
- **Spec 008:** nawigacja (`AppScaffold`, `AppDestination.HISTORY`), `SecureWindowEffect`, `MainDispatcherExtension`, `UiNoLoggingTest` (obejmuje `ui/`).
- **Testy (decyzja użytkownika):** tylko JVM – ViewModel, odwzorowania, formatowanie czasu, testy plików (`strings.xml`, źródła); JUnit 5, MockK, Turbine, `kotlinx-coroutines-test`. Kompozycje, okno i nawigacja – weryfikacja ręczna i `assembleDebug`.

## Pojęcia

- **Wpis** – `HistoryEntry` ze Spec 007.
- **Wiersz** – pozycja listy w zakładce Historia odpowiadająca jednemu wpisowi.
- **Szczegóły** – tryb zakładki Historia (bez osobnej trasy nawigacji) z pełną zawartością jednego wpisu.
- **Komunikat** – jednorazowa informacja (Snackbar) jako wartość wyliczeniowa w stanie ViewModelu, kasowana po pokazaniu (jak w Spec 008 i 009).
- **Wynik wpisu** – rodzaj (`ResultKind` ze Spec 009 albo „nierozpoznany”), komunikat (`ResultMessage` ze Spec 009 albo „brak szczegółów”) i dodatki.

## Wymagania

Wspólne dane kryteriów (o ile kryterium nie mówi inaczej): `T0 = 1111111051000`; wpisy budowane bezpośrednio jako `HistoryEntry`; atrapa `CommandHistory` z kontrolowanym strumieniem (design); dyspozytor główny = `StandardTestDispatcher`; strefa czasowa w testach formatowania podawana jawnie (`Europe/Warsaw`, `UTC`). Treści odpowiedzi AAPS: pliki z [`../002-parser-odpowiedzi/dane/`](../002-parser-odpowiedzi/dane/README.md).

### HISTUI-01: Ekran Historia zamiast zaślepki

Zakładka Historia pokazuje ekran historii zamiast zaślepki z UI-03 (zaślepka Historia i jej tekst są usuwane; UI-03 nadal dotyczy tylko zaślepki Polecenia, dopóki nie zastąpi jej Spec 009). Ekran ma nagłówek „Historia” (klucz `nav_history`) i jeden z czterech stanów treści wyliczanych ze strumienia `CommandHistory.entries`:

| Strumień | Stan treści |
| --- | --- |
| przed pierwszą emisją | wczytywanie |
| `Entries([])` | pusto |
| `Entries(lista ≥ 1)` | lista |
| `Unavailable` | błąd odczytu |

**Kryteria akceptacji:**

- **Zakładając** strumień, który jeszcze nic nie wyemitował, **gdy** utworzono ViewModel, **wtedy** stan treści = wczytywanie.
- **Zakładając** strumień emitujący kolejno `Entries([])`, `Entries([A])`, `Entries([B, A])`, **gdy** ViewModel go zbierał (Turbine), **wtedy** stany treści to kolejno: wczytywanie, pusto, lista (1 wiersz), lista (2 wiersze).
- **Zakładając** źródła aplikacji i `strings.xml`, **gdy** test JVM je przeczytał, **wtedy** nie ma pliku `HistoryPlaceholderScreen.kt` ani klucza `placeholder_history_text`, a `AppScaffold.kt` dla trasy `history` wywołuje `HistoryScreen`.

### HISTUI-02: Lista od najnowszego

Lista zawiera wszystkie wpisy ze stanu `Entries` w tej samej kolejności (od najnowszego – malejący identyfikator, DATA-11), bez ponownego sortowania i bez obcinania; limit 200 zapewnia `data` (DATA-10). Nad listą jest tekst „Aplikacja przechowuje 200 ostatnich poleceń.” z liczbą z `HistoryLimits.MAX_ENTRIES`. Nowy wpis (zapisany np. po zakończeniu polecenia na zakładce Polecenia) pojawia się na początku listy bez ponownego wejścia na zakładkę.

**Kryteria akceptacji:**

- **Zakładając** `Entries` z wpisami o identyfikatorach 3, 2, 1, gdzie wpis 3 ma rozpoczęcie `T0`, a wpis 1 – `T0 + 120000`, **gdy** odczytano stan, **wtedy** wiersze mają identyfikatory 3, 2, 1.
- **Zakładając** `Entries` z 200 wpisami, **gdy** odczytano stan, **wtedy** jest 200 wierszy.
- **Zakładając** listę z wpisem A, **gdy** strumień wyemitował `Entries([B, A])`, **wtedy** pierwszy wiersz = B.
- **Zakładając** telefon (W1), **gdy** zakończono `BG` i przełączono na Historię, **wtedy** wpis `BG` jest pierwszy.

### HISTUI-03: Polecenie we wpisie

Wiersz i szczegóły pokazują polecenie według tabeli. Ilość pochodzi z `HistoryEntry.carbsGrams` (`CARBS`) albo `HistoryEntry.bolusCentiUnits` (`BOLUS`, zapisywana w setnych jednostki, pokazywana z przecinkiem i dwoma miejscami; BOL-17), numer i procent profilu z `HistoryEntry.profileNumber` i `profilePercent` (PROF-17). Nazwa profilu nie jest we wpisie.

| Wpis | Tekst |
| --- | --- |
| `BG` | „BG” |
| `PUMP` | „PUMP” |
| `CARBS`, ilość X | „CARBS X g” |
| `CARBS`, ilość = `null` | „CARBS (ilość nieznana)” |
| `BOLUS`, ilość X | „BOLUS X j.” |
| `BOLUS`, ilość = `null` | „BOLUS (ilość nieznana)” |
| `PROFILE_STATUS` | „PROFILE STATUS” |
| `PROFILE_LIST` | „PROFILE LIST” |
| `PROFILE_SWITCH`, numer N i procent P | „Zmiana profilu N, P%” |
| `PROFILE_SWITCH`, brak numeru lub procentu | „Zmiana profilu (szczegóły nieznane)” |
| `UNRECOGNIZED` | „Nieznane polecenie” |

**Kryteria akceptacji:**

- **Zakładając** wpisy `BG`, `PUMP`, `CARBS` z ilością 20, `CARBS` z ilością `null` i `UNRECOGNIZED`, **gdy** odwzorowano je na wiersze, **wtedy** etykieta polecenia to kolejno `BG`, `PUMP`, `CARBS` z argumentem 20, `CARBS` bez ilości, nieznane polecenie.
- **Zakładając** wpisy `BOLUS` 120, `BOLUS` bez ilości, `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH` (2, 115) i `PROFILE_SWITCH` bez numeru, **gdy** odwzorowano je na wiersze, **wtedy** etykiety to kolejno `BOLUS` z argumentem 120, `BOLUS` bez ilości, `PROFILE STATUS`, `PROFILE LIST`, zmiana profilu z argumentami 2 i 115, zmiana profilu bez szczegółów (BOL-17, PROF-17).
- **Zakładając** wpis `BOLUS` 120, **gdy** go odwzorowano, **wtedy** tekst to dokładnie „BOLUS 1,20 j.”; dla ilości 1 – „BOLUS 0,01 j.”.
- **Zakładając** wpisy `PROFILE_SWITCH` (numer 2, procent `null`) i (numer `null`, procent 115), **gdy** je odwzorowano, **wtedy** w obu tekst to dokładnie „Zmiana profilu (szczegóły nieznane)”.

### HISTUI-04: Wynik wpisu – wspólne odwzorowanie ze Spec 009

Wynik wpisu jest wyliczany jedną czystą funkcją z wyczerpującym `when` po `HistoryOutcome` (bez `else`). Rodzaje, komunikaty, argumenty minut i ilości oraz ich teksty są te same co w CMDUI-19 (Spec 009): powód `FailureReason`/`UnknownReason` i faza `TimedOut` są odwzorowywane tymi samymi funkcjami co w `ResultMapper` (design), a teksty – tymi samymi kluczami `result_*`. Przypadki, których nie ma w Spec 009 (wartości `null` i nierozpoznane z DATA-09), mają tabelę poniżej.

| Wynik wpisu | Rodzaj | Komunikat |
| --- | --- | --- |
| `Completed`, polecenie `BG` / `PUMP` / `PROFILE_STATUS` | sukces | jak CMDUI-19 („Odpowiedź AAPS na BG” / „… na PUMP” / „… na PROFILE STATUS”) |
| `Completed`, polecenie `PROFILE_LIST` | sukces | „Odpowiedź AAPS na PROFILE LIST.” (inny tekst niż na ekranie wyniku, gdzie jest „Lista profili z AAPS.”; PROF-17) |
| `Completed`, polecenie `CARBS`, `BOLUS`, `PROFILE_SWITCH` albo `UNRECOGNIZED` | sukces | „Brak szczegółów wyniku (wpis z innej wersji aplikacji).” |
| `CarbsConfirmed`, polecenie `CARBS` z ilością X | sukces | „AAPS potwierdził wprowadzenie X g” |
| `CarbsConfirmed`, ilość `null` albo polecenie inne niż `CARBS` | sukces | „Brak szczegółów wyniku.” |
| `BolusConfirmed`, polecenie `BOLUS` z ilością X | sukces | „AAPS potwierdził podanie X j.” (BOL-17) |
| `BolusConfirmed`, ilość `null` albo polecenie inne niż `BOLUS` | sukces | „Brak szczegółów wyniku.” |
| `Failed(r)` | błąd | jak CMDUI-19 dla `r` (dla `BOLUS` i zmiany profilu teksty niezgodności i nierozpoznanej odpowiedzi jak w BOL-16 i PROF-16) |
| `Failed(null)` | błąd | „Brak szczegółów wyniku.” |
| `TimedOut(p)` (także `p = null`) | brak odpowiedzi | jak CMDUI-19 (`SENDING_COMMAND` → „Telefon nie potwierdził wysłania SMS w ciągu 3 min.”; inna faza i `null` → „Brak odpowiedzi AAPS w ciągu 3 min.”) |
| `Unknown(r)` | nieznany | jak CMDUI-19 dla `r` (dla `BOLUS` i zmiany profilu – jak w BOL-16 i PROF-16) |
| `Unknown(null)` | nieznany | „Brak szczegółów wyniku.” |
| `Cancelled` | anulowane | „Polecenie anulowane.” |
| `Unrecognized` | nierozpoznany → nagłówek „Nierozpoznany wynik” | „Brak szczegółów wyniku.” |

`Completed` dla `CARBS` ma odpowiednik na ekranie wyniku Spec 009 (CMDUI-19, korekta 2026-10-21): ten sam rodzaj (sukces) i komunikat „brak szczegółów” (`ResultMessage.DETAILS_MISSING`, „Brak szczegółów wyniku.”). Historia zostawia dla tego wiersza własny tekst z wyjaśnieniem o wpisie z innej wersji aplikacji; HISTUI-04 i jego kryteria się nie zmieniają.

Dodatki (w szczegółach, P5) – reguły jak w CMDUI-19, z ograniczeniem do danych pewnych:

- „Kod nie został wysłany.” – polecenie z kodem (`CARBS`, `BOLUS`, `PROFILE_SWITCH`) i wynik `Failed` z powodem innym niż `WRONG_CODE` i różnym od `null`, `TimedOut` albo `Cancelled`.
- „Nie ponawiaj polecenia, zanim nie sprawdzisz w AAPS, czy zostało wykonane.” – każdy `Unknown`, także z powodem `null`; dla `BOLUS` ostrzeżenie bolusowe „Nie ponawiaj bolusa, zanim nie sprawdzisz w AAPS, czy został podany.” (BOL-16).

**Kryteria akceptacji:**

- **Zakładając** każdy `FailureReason` (8), każdy `UnknownReason` (7) i `TimedOut` z fazą `SENDING_COMMAND`, `AWAITING_REPLY`, `AWAITING_CODE_REQUEST`, **gdy** odwzorowano wpis `CARBS 20` z tym wynikiem oraz `SessionResult` z tym samym wynikiem przez `ResultMapper` ze Spec 009, **wtedy** rodzaj, komunikat i argument są równe w obu odwzorowaniach.
- **Zakładając** wpisy `BOLUS` 120/`BolusConfirmed`, `BOLUS` bez ilości/`BolusConfirmed`, `PROFILE_STATUS`/`Completed`, `PROFILE_LIST`/`Completed`, `PROFILE_SWITCH`/`Completed`, **gdy** odwzorowano, **wtedy** odpowiednio: sukces/„AAPS potwierdził podanie 1,20 j.”, sukces/brak szczegółów („Brak szczegółów wyniku.”), sukces/„Odpowiedź AAPS na PROFILE STATUS”, sukces/„Odpowiedź AAPS na PROFILE LIST.” (bez wezwania do wyboru profilu), sukces/„Brak szczegółów wyniku (wpis z innej wersji aplikacji).”.
- **Zakładając** wpisy `BOLUS` z `Failed(AMOUNT_MISMATCH)` i `Unknown(UNRECOGNIZED_REPLY)` oraz `PROFILE_SWITCH` z `Failed(AMOUNT_MISMATCH)`, `Unknown(AMOUNT_MISMATCH)` i `Unknown(UNRECOGNIZED_REPLY)`, **gdy** odwzorowano, **wtedy** komunikaty są takie jak w BOL-16 i PROF-16, „Kod nie został wysłany” = `true` tylko dla `Failed`, a ostrzeżenie bolusowe tylko dla `BOLUS`.
- **Zakładając** wpisy `BG`/`Completed`, `PUMP`/`Completed`, `CARBS 1`/`CarbsConfirmed`, `BG`/`Cancelled`, **gdy** odwzorowano, **wtedy** rodzaj i komunikat są jak w CMDUI-19, a dla `CarbsConfirmed` argument = 1.
- **Zakładając** wpisy `Failed(null)`, `Unknown(null)`, `TimedOut(null)`, `Unrecognized`, `CARBS`/`Completed`, `CARBS` z ilością `null`/`CarbsConfirmed`, **gdy** odwzorowano, **wtedy** odpowiednio: błąd/brak szczegółów, nieznany/brak szczegółów, brak odpowiedzi/„Brak odpowiedzi AAPS” z argumentem 3, nierozpoznany/brak szczegółów, sukces/brak szczegółów, sukces/brak szczegółów – bez wyjątku.
- **Zakładając** wpis `CARBS 20` z wynikami `Failed(AMOUNT_MISMATCH)`, `TimedOut(AWAITING_CODE_REQUEST)`, `Cancelled`, **gdy** odwzorowano, **wtedy** „Kod nie został wysłany” = `true`; dla `Failed(WRONG_CODE)`, `Failed(null)`, `CarbsConfirmed`, `Unknown(CONFIRMATION_TIMEOUT)` = `false`; dla `BG` z `Cancelled` = `false`.
- **Zakładając** każdy `UnknownReason` i `Unknown(null)`, **gdy** odwzorowano, **wtedy** ostrzeżenie = `true`; dla pozostałych rodzajów = `false`.

### HISTUI-05: Czasy wpisu

Czasy są pokazywane w strefie czasowej telefonu w chwili wyświetlenia (`ZoneId.systemDefault()`), według wzorców (P4): w szczegółach `dd.MM.yyyy HH:mm:ss`, w wierszu `dd.MM.yyyy HH:mm`. Wiersz pokazuje czas rozpoczęcia, a gdy go brak – zakończenia; gdy brak obu – „brak”. Szczegóły pokazują cztery czasy z etykietami „Rozpoczęcie”, „Wysłanie polecenia”, „Wysłanie kodu”, „Zakończenie”; czas `null` → „brak”. Formatowanie jest czystą funkcją z jawną strefą.

**Kryteria akceptacji:**

- **Zakładając** chwilę `T0` i strefę `Europe/Warsaw`, **gdy** sformatowano czas szczegółów i wiersza, **wtedy** wyniki = `18.03.2005 02:57:31` i `18.03.2005 02:57`; w strefie `UTC` – `18.03.2005 01:57:31` i `18.03.2005 01:57`.
- **Zakładając** chwilę `1120000000000` (czas letni) i strefę `Europe/Warsaw`, **gdy** sformatowano czas szczegółów, **wtedy** wynik = `29.06.2005 01:06:40`.
- **Zakładając** wpis z rozpoczęciem `null` i zakończeniem `T0 + 5000`, **gdy** wyliczono czas wiersza, **wtedy** jest to `T0 + 5000`; dla wpisu `Cancelled` ze wszystkimi czasami `null` – brak czasu.
- **Zakładając** wpis `BG` z czasami `T0`, `T0 + 1000`, `null`, `T0 + 5000`, **gdy** odwzorowano szczegóły, **wtedy** cztery czasy = `T0`, `T0 + 1000`, brak, `T0 + 5000`.

### HISTUI-06: Treść odpowiedzi AAPS i wiadomości nieoczekiwane

Szczegóły pokazują treść odpowiedzi AAPS (`HistoryEntry.replyText`) w całości, bez skracania, pod etykietą „Treść SMS z AAPS:” (klucz ze Spec 009), gdy treść nie jest `null`. Treść jest wyświetlana jako zwykły tekst: bez interpretacji znaczników, bez automatycznych linków i bez akcji (także dla wpisów nierozpoznanych). Gdy liczba wiadomości nieoczekiwanych N > 0, szczegóły pokazują tekst `run_unexpected` ze Spec 009 z liczbą N; przy N = 0 – brak tekstu. Treść wiadomości nieoczekiwanych nie istnieje we wpisie (DATA-08).

**Kryteria akceptacji:**

- **Zakładając** wpis `BG` z treścią `bg.txt`, **gdy** odwzorowano szczegóły, **wtedy** treść = zawartość `bg.txt` znak w znak; dla treści 2000 znaków `a` – 2000 znaków; dla `TimedOut` z treścią `null` – brak treści.
- **Zakładając** wpisy z liczbą nieoczekiwanych 0 i 2, **gdy** odwzorowano szczegóły, **wtedy** liczba do pokazania = brak i 2.
- **Zakładając** źródła `ui/history/`, **gdy** test JVM je przeczytał, **wtedy** nie zawierają `AnnotatedString.fromHtml`, `Html.fromHtml`, `LinkAnnotation`, `Linkify` ani `autoLink`.

### HISTUI-07: Szczegóły wpisu

Dotknięcie wiersza otwiera szczegóły tego wpisu w zakładce Historia (P1). Szczegóły pokazują polecenie (HISTUI-03), wynik z dodatkami (HISTUI-04), cztery czasy (HISTUI-05), treść i liczbę nieoczekiwanych (HISTUI-06) oraz przycisk „Zamknij” (klucz `result_close` ze Spec 009), który wraca do listy. Gdy otwarty wpis znika ze strumienia (czyszczenie, retencja) albo strumień daje `Unavailable`, szczegóły się zamykają. Szczegóły są stanem ViewModelu (identyfikator wpisu w pamięci), nie trasą nawigacji.

**Kryteria akceptacji:**

- **Zakładając** listę z wpisami 2 i 1, **gdy** wywołano `onEntryClicked(1)`, **wtedy** stan ma szczegóły wpisu 1; **gdy** wywołano `onDetailsClosed()`, **wtedy** szczegółów brak.
- **Zakładając** otwarte szczegóły wpisu 1, **gdy** strumień wyemitował `Entries([3, 2])`, **wtedy** szczegółów brak; **gdy** zamiast tego wyemitował `Entries([3, 2, 1])`, **wtedy** szczegóły wpisu 1 zostają.
- **Zakładając** listę z wpisami 2 i 1, **gdy** wywołano `onEntryClicked(7)`, **wtedy** szczegółów brak.

### HISTUI-08: Stan pusty

Gdy historia jest pusta, ekran pokazuje tekst „Brak poleceń w historii. Wynik każdego polecenia pojawi się tutaj po jego zakończeniu.”, a przycisk „Wyczyść historię” jest nieaktywny.

**Kryteria akceptacji:**

- **Zakładając** strumień `Entries([])`, **gdy** odczytano stan, **wtedy** stan treści = pusto, czyszczenie niedostępne.

### HISTUI-09: Błąd odczytu i ponowienie

Gdy strumień daje `Unavailable`, ekran pokazuje tekst „Nie udało się wczytać historii.” i przycisk „Spróbuj ponownie”; lista, szczegóły i czyszczenie są niedostępne. „Spróbuj ponownie” ustawia stan wczytywania i ponownie zbiera `CommandHistory.entries` (strumień po `Unavailable` jest zakończony – DATA-11). Brak automatycznego ponawiania.

**Kryteria akceptacji:**

- **Zakładając** atrapę, której pierwsze zbieranie daje `Unavailable`, a drugie `Entries([A])`, **gdy** ViewModel zebrał strumień, **wtedy** stan treści = błąd odczytu, a atrapa była zbierana 1 raz (także po 10 min czasu wirtualnego); **gdy** wywołano `onRetry()`, **wtedy** stany to wczytywanie, lista (1 wiersz), a atrapa była zbierana 2 razy.
- **Zakładając** stan listy, **gdy** wywołano `onRetry()`, **wtedy** stan bez zmian i brak nowego zbierania.
- **Zakładając** stan błędu odczytu, **gdy** wywołano `onClearClicked()` albo `onEntryClicked(1)`, **wtedy** stan bez zmian.

### HISTUI-10: Czyszczenie z potwierdzeniem

Przycisk „Wyczyść historię” jest aktywny tylko w stanie listy i gdy nie trwa czyszczenie. Dotknięcie otwiera okno „Wyczyścić historię?” z tekstem „Wszystkie wpisy (N) zostaną usunięte. Tej operacji nie można cofnąć.” (N = liczba wierszy w chwili otwarcia) i przyciskami „Wyczyść” i „Anuluj” (klucz `action_cancel` ze Spec 008). „Anuluj” i Wstecz zamykają okno bez wywołania `clearAll()`. „Wyczyść” zamyka okno i wywołuje `CommandHistory.clearAll()` dokładnie raz. W trakcie czyszczenia przycisk jest nieaktywny, a kolejne wywołania są ignorowane. Polecenie w toku nie blokuje czyszczenia (P2).

**Przypadek brzegowy – wpis zapisany przy otwartym oknie (świadomie przyjęty, P6):** liczba N w oknie nie jest aktualizowana, gdy przy otwartym oknie strumień przyniesie nowy wpis (np. polecenie zakończone w tle zapisze wynik, DATA-13). „Wyczyść” wywołuje `clearAll()`, które usuwa wszystkie wpisy w bazie, także ten nowy – więc usuniętych może być więcej niż N. Powód: najprostsze rozwiązanie; `clearAll()` nie przyjmuje listy wpisów (Spec 007), tekst okna już mówi o usunięciu wszystkich wpisów („Wszystkie wpisy (N) zostaną usunięte. Tej operacji nie można cofnąć.” – bez zmiany treści; N to liczba z chwili otwarcia), a wynik polecenia był widoczny na zakładce Polecenia (CMDUI-19).

**Kryteria akceptacji:**

- **Zakładając** listę z 3 wpisami, **gdy** wywołano `onClearClicked()`, **wtedy** okno jest otwarte z liczbą 3, a `clearAll()` nie był wywołany; **gdy** wywołano `onClearDismissed()`, **wtedy** okno zamknięte, `clearAll()` nie był wywołany.
- **Zakładając** otwarte okno i `clearAll()` wstrzymany bramką, **gdy** wywołano `onClearConfirmed()` dwa razy oraz `onClearClicked()`, **wtedy** `clearAll()` wywołano 1 raz, okno zamknięte, czyszczenie trwa, czyszczenie niedostępne.
- **Zakładając** listę z 3 wpisami i otwarte okno z liczbą 3, **gdy** strumień wyemitował `Entries` z 4 wpisami (nowy na początku), **wtedy** okno jest nadal otwarte z liczbą 3, a lista ma 4 wiersze; **gdy** wywołano `onClearConfirmed()`, **wtedy** `clearAll()` wywołano dokładnie 1 raz (bez argumentów – usuwa wszystkie wpisy, także czwarty).
- **Zakładając** ViewModel historii, **gdy** sprawdzono parametry jego konstruktora refleksją, **wtedy** nie ma wśród nich `SessionCoordinator` (czyszczenie nie zależy od polecenia w toku – P2).

### HISTUI-11: Wynik czyszczenia

Po `Cleared` ekran pokazuje komunikat „Historia wyczyszczona.”, a stan treści wynika ze strumienia (`Entries([])` → pusto). Po `Failed` – komunikat „Nie udało się wyczyścić historii. Spróbuj ponownie.”, a lista zostaje bez zmian. W obu przypadkach czyszczenie kończy się i przycisk znów jest aktywny, gdy lista nie jest pusta. Brak automatycznego ponawiania.

**Kryteria akceptacji:**

- **Zakładając** listę z 3 wpisami i atrapę, która przy `clearAll()` zwraca `Cleared` i emituje `Entries([])`, **gdy** potwierdzono czyszczenie, **wtedy** komunikat = wyczyszczono, stan treści = pusto, czyszczenie nie trwa.
- **Zakładając** atrapę zwracającą `Failed`, **gdy** potwierdzono czyszczenie, **wtedy** komunikat = błąd czyszczenia, lista ma 3 wiersze, czyszczenie dostępne, `clearAll()` wywołano 1 raz.
- **Zakładając** pokazany komunikat, **gdy** wywołano `onMessageShown()`, **wtedy** komunikatu brak.

### HISTUI-12: Przycisk Wstecz na zakładce Historia

| Sytuacja | Działanie |
| --- | --- |
| okno „Wyczyścić historię?” otwarte | zamyka okno (jak „Anuluj”) |
| szczegóły otwarte | zamyka szczegóły |
| żadne z powyższych | jak UI-02 (powrót do zakładki Polecenia) |

**Kryteria akceptacji:**

- **Zakładając** otwarte szczegóły, **gdy** wywołano `onBack()`, **wtedy** szczegółów brak; przy otwartym oknie czyszczenia – okno zamknięte, `clearAll()` niewywołany.
- **Zakładając** listę bez okna i szczegółów, **gdy** odczytano stan, **wtedy** przechwytywanie Wstecz jest wyłączone.

### HISTUI-13: Ochrona ekranu

Gdy zakładka Historia jest w kompozycji (każdy stan treści, lista i szczegóły), okno `Activity` ma `FLAG_SECURE` przez `SecureWindowEffect` z licznikiem ze Spec 009 (P3: historia zawiera te same dane co chroniony wynik – CMDUI-23). Po przejściu na inną zakładkę żądanie Historii jest wycofywane, a flaga jest zdejmowana dopiero, gdy żaden ekran ani przebieg jej nie wymaga (licznik żądań): w szczególności w trakcie polecenia i do zamknięcia jego wyniku flagę utrzymuje CMDUI-23 (Spec 009, zmiana P11) na każdej zakładce, także po zejściu z Historii.

**Kryteria akceptacji:**

- **Zakładając** źródło `ui/history/HistoryScreen.kt`, **gdy** test JVM je przeczytał, **wtedy** zawiera wywołanie `SecureWindowEffect(true)`.
- **Zakładając** telefon (W6), **gdy** otwarto Historię (lista, potem szczegóły) i wykonano zrzut ekranu oraz otwarto listę ostatnich aplikacji, **wtedy** zrzut się nie wykonuje, a podgląd jest pusty; po przejściu na panel Polecenia (bez przebiegu i wyniku) zrzut działa.
- **Zakładając** telefon w trakcie `BG` (W6), **gdy** z zakładki Historia przełączono na Ustawienia i wykonano zrzut ekranu, **wtedy** zrzut się nie wykonuje; po zamknięciu wyniku `BG` zrzut na Ustawieniach (bez skanera) działa.

### HISTUI-14: Teksty po polsku w zasobach

Wszystkie teksty z tej specyfikacji są w `app/src/main/res/values/strings.xml`, po polsku, pod kluczami z tabeli tekstów w design; treści podane w tej spec są wiążące (drobne zmiany redakcyjne w recenzji PR nie wymagają zmiany spec). Teksty wyniku, etykieta treści, tekst wiadomości nieoczekiwanych, „Zamknij” i „Anuluj” są używane ponownie ze Spec 008 i 009, nie kopiowane. ViewModel i odwzorowania zwracają wartości wyliczeniowe, liczby i chwile, nie teksty; odwzorowanie wartość → zasób to jedna funkcja na typ. Kod Compose nie zawiera literałów tekstowych widocznych dla użytkownika.

**Kryteria akceptacji:**

- **Zakładając** każde wyliczenie tekstów z tej spec (komunikaty, etykiety polecenia, etykiety czasów, stany treści z tekstem), **gdy** test JVM odwzorował każdą wartość na zasób, **wtedy** każdy identyfikator jest niezerowy, a identyfikatory w obrębie typu są różne.
- **Zakładając** `strings.xml`, **gdy** test JVM go przeczytał, **wtedy** zawiera niepusty tekst dla każdego nowego klucza z tabeli tekstów w design, każdy klucz z argumentem zawiera `%1$d`, a żaden klucz `history_*` nie ma tej samej treści co klucz `result_*` (brak kopii).
- **Zakładając** ekrany na telefonie (W1–W8), **gdy** przejrzano wszystkie stany, **wtedy** każdy tekst jest po polsku.

### HISTUI-15: Brak ujawniania danych

`ui.history` nie wywołuje API logowania (UI-21, `UiNoLoggingTest`). `toString()` stanu ViewModelu i typów wiersza oraz szczegółów nie zawiera ilości gramów, treści odpowiedzi ani czasów (stały tekst `***` w miejscu wartości). Treść odpowiedzi nie trafia do komunikatów ani wyjątków.

**Kryteria akceptacji:**

- **Zakładając** stan z listą wpisu `CARBS 37` `CarbsConfirmed` z treścią `Pomyslnie wprowadzono 37 g weglowodanow` i otwartymi jego szczegółami, **gdy** wywołano `toString()` stanu, wiersza i szczegółów, **wtedy** tekst nie zawiera `37`, `Pomyslnie` ani `1111111`.
- **Zakładając** źródła `ui/`, **gdy** test JVM je przeczytał (`UiNoLoggingTest`), **wtedy** `ui/history/` nie zawiera wywołań API logowania.

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Błąd odczytu bazy (`Unavailable`) | tekst błędu, „Spróbuj ponownie”, lista i czyszczenie niedostępne | HISTUI-09 |
| Nieznane polecenie / rodzaj / powód w wierszu bazy (DATA-09) | „Nieznane polecenie”, „Nierozpoznany wynik”, „Brak szczegółów wyniku.”, bez wyjątku | HISTUI-03, HISTUI-04 |
| `CARBS` bez ilości w wierszu | „CARBS (ilość nieznana)”, brak dodatku o ilości | HISTUI-03, HISTUI-04 |
| Wszystkie czasy `null` (`Cancelled` przed startem) | „brak” | HISTUI-05 |
| Treść z niecodziennymi znakami / bardzo długa | zwykły tekst w całości, bez linków | HISTUI-06 |
| Otwarty wpis usunięty (czyszczenie, retencja) | szczegóły zamknięte | HISTUI-07 |
| Podwójne potwierdzenie czyszczenia | jedno `clearAll()` | HISTUI-10 |
| `clearAll()` = `Failed` | komunikat, lista bez zmian, brak ponawiania | HISTUI-11 |
| Czyszczenie w trakcie polecenia | dozwolone; wpis bieżącego polecenia powstanie po jego zakończeniu (DATA-13) | HISTUI-10 (P2) |
| Nowy wpis zapisany przy otwartym oknie czyszczenia | N w oknie bez zmian; „Wyczyść” usuwa wszystkie wpisy, także nowy (świadomie przyjęte) | HISTUI-10 (P6) |
| Zmiana strefy czasowej telefonu | czasy pokazywane w strefie z chwili wyświetlenia | HISTUI-05 |

## Bezpieczeństwo

Zasady z [AGENTS.md](../../../AGENTS.md#bezpieczeństwo):

- **Sekret, kod TOTP i PIN** nie występują we wpisie (DATA-08), więc `ui.history` ich nie widzi i nie pokazuje.
- **Brak logowania danych:** `ui.history` nie loguje; `toString()` stanów bez ilości, treści i czasów (HISTUI-15).
- **Treść odpowiedzi AAPS i ilość CARBS to dane zdrowotne:** `FLAG_SECURE` na zakładce Historia (HISTUI-13, P3), bez kopii zapasowej (DATA-16, W5 tej spec). Treść pokazywana jako zwykły tekst bez linków i akcji (HISTUI-06), bo nierozpoznany SMS z numeru AAPS trafia do historii w całości (ryzyko opisane w Spec 007).
- **Bez automatycznego ponawiania:** z historii nie da się wysłać polecenia ponownie; ponowienie odczytu i czyszczenia tylko na żądanie użytkownika (HISTUI-09, HISTUI-11). Wynik „nieznany” ma w szczegółach to samo ostrzeżenie przed ponowieniem co w Spec 009 (HISTUI-04).
- **Wpisy nierozpoznane** pokazywane bez zgadywania znaczenia („Brak szczegółów wyniku”) – aplikacja nie twierdzi, że polecenie się udało albo nie, gdy wpis tego nie mówi; „Kod nie został wysłany” tylko przy znanym powodzie (HISTUI-04).
- **Brak prawdziwych danych:** wpisy fikcyjne, treści z `../002-parser-odpowiedzi/dane/` (fikcyjne imię `Jan`), chwile fikcyjne.

## Dane testowe

- Chwile: `T0 = 1111111051000` (18.03.2005 01:57:31 UTC), `1120000000000` (28.06.2005 23:06:40 UTC, czas letni w `Europe/Warsaw`) – fikcyjne.
- Treści: `bg.txt`, `pump.txt`, `carbs-sukces.txt`, `zly-kod.txt` z [`../002-parser-odpowiedzi/dane/`](../002-parser-odpowiedzi/dane/README.md) (kopie testowe w `app/src/test/resources/parser/aaps-3.4.2.6-pl/`); syntetyczne: `Pomyslnie wprowadzono 37 g weglowodanow`, tekst 2000 znaków `a`.
- Wpisy budowane jako `HistoryEntry`; `SessionResult` do porównania z `ResultMapper` – jak w danych testowych Spec 009.
- Brak plików w `dane/`.

## Weryfikacja na urządzeniu

Kompozycje, okno, `FLAG_SECURE` i nawigacja nie mają testów automatycznych (decyzja użytkownika). Wyniki (telefon, wersja Androida, wersja AAPS, wynik każdego punktu) opisuje PR zadania ekranu (design, „Podział na zadania”). Wynik niezgodny ze spec = zmiana spec, nie obejście w kodzie.

| Nr | Co sprawdzić | Wymagania |
| --- | --- | --- |
| W1 | Po `BG`, `PUMP` i `CARBS 1` (wirtualna pompa albo za zgodą użytkownika) lista od najnowszego; wiersze z poleceniem, czasem i wynikiem; szczegóły z czterema czasami, treścią SMS i komunikatem jak na ekranie wyniku; nowy wpis pojawia się bez ponownego wejścia na zakładkę; teksty po polsku | HISTUI-01 – HISTUI-07, HISTUI-14 |
| W2 | Po `BG` jest `databases/aaps_remote.db`; po zabiciu procesu i ponownym starcie zakładka Historia zawiera wpis (W3 ze Spec 007) | HISTUI-01, HISTUI-02 |
| W3 | Retencja wg W4 ze Spec 007 (205 wierszy wstawionych narzędziem `sqlite3`); po `BG` zakładka Historia pokazuje 200 wierszy, a ostatni wiersz to najstarszy zachowany wpis | HISTUI-02 |
| W4 | „Wyczyść historię” → okno z liczbą wpisów; „Anuluj” i Wstecz nic nie usuwają; „Wyczyść” → komunikat i stan pusty; po ponownym starcie historia pusta, a nowy wpis ma w bazie (`sqlite3` na kopii) identyfikator większy od wcześniejszych (W5 ze Spec 007) | HISTUI-08, HISTUI-10, HISTUI-11, HISTUI-12 |
| W5 | `adb shell bmgr backupnow pl.twojanazwa.aapsremote` przy niepustej historii: kopia nie jest wykonywana (W6 ze Spec 007, część historii) | – (DATA-16) |
| W6 | `FLAG_SECURE`: zrzut zablokowany na liście i szczegółach, podgląd w ostatnich aplikacjach pusty; po przejściu na panel Polecenia (bez przebiegu i wyniku) zrzut działa; w trakcie `BG` przejście z Historii na Ustawienia → zrzut nadal zablokowany, po zamknięciu wyniku działa (sprawdzane w PR zadania 9 Spec 009) | HISTUI-13 |
| W7 | Wpis nierozpoznany: w kopii bazy zmienić w jednym wierszu `command` na `TBR`, w drugim `outcome` na `PARTIAL`, w trzecim `reason` na `NO_SIGNAL` (rodzaj `FAILED`), zapisać bazę jak w W4 ze Spec 007; aplikacja pokazuje „Nieznane polecenie”, „Nierozpoznany wynik”, „Brak szczegółów wyniku.”, bez awarii | HISTUI-03, HISTUI-04 |
| W8 | Wynik po zamknięciu aplikacji (P7 w Spec 009): w trakcie `BG` Wstecz → „Wyjdź”, po odpowiedzi AAPS zabić proces, uruchomić aplikację – wynik tylko w Historii; zmiana strefy czasowej telefonu → po ponownym wejściu na zakładkę czasy w nowej strefie | HISTUI-02, HISTUI-05 |

Stanu „błąd odczytu” (HISTUI-09) nie da się wywołać ręcznie bez uszkadzania bazy – sprawdza go tylko test JVM.

**Punkty weryfikacji z wcześniejszych specyfikacji** (podział P3 ze Spec 008): Spec 007 W3 – W2, W4 – W3, W5 – W4, W6 (część historii) – W5 tej spec.

## Otwarte pytania

Pytania rozstrzygnięte przez użytkownika (przyjęto wszystkie propozycje bez zmian, 2026-10-21):

- [x] **P1. Szczegóły po dotknięciu.** Rozstrzygnięte: wiersz listy jest zwarty (polecenie, czas bez sekund, rodzaj i komunikat), a dotknięcie otwiera szczegóły w tej samej zakładce (wynik z dodatkami, cztery czasy z sekundami, treść SMS, liczba nieoczekiwanych) – HISTUI-07 [przyjęta propozycja, 2026-10-21].
- [x] **P2. Czyszczenie w trakcie polecenia.** Rozstrzygnięte: bez blokady. Czyszczenie nie wpływa na sesję ani na wysyłanie kodu, a wpis bieżącego polecenia powstaje dopiero po jego zakończeniu (DATA-13), więc nie zginie. Blokady z UI-15 chronią numer i sekret, od których zależy, dokąd idzie `<kod><PIN>` – tu takiego ryzyka nie ma [przyjęta propozycja, 2026-10-21].
- [x] **P3. `FLAG_SECURE` na zakładce Historia.** Rozstrzygnięte: tak, na całej zakładce (lista i szczegóły), bo historia zawiera te same dane co chroniony ekran wyniku (CMDUI-23) i plan mówi „FLAG_SECURE na ekranach z danymi” – HISTUI-13 [przyjęta propozycja, 2026-10-21].
- [x] **P4. Format czasu.** Rozstrzygnięte: strefa telefonu, stały wzorzec polski `dd.MM.yyyy HH:mm:ss` w szczegółach i `dd.MM.yyyy HH:mm` w wierszu; w wierszu czas rozpoczęcia, a gdy go brak – zakończenia (HISTUI-05) [przyjęta propozycja, 2026-10-21].
- [x] **P5. Dodatki w historii.** Rozstrzygnięte: szczegóły pokazują „Kod nie został wysłany.” i ostrzeżenie przed ponowieniem dla wyniku „nieznany” na tych samych zasadach co Spec 009, przy czym „Kod nie został wysłany” tylko przy znanym powodzie (HISTUI-04) [przyjęta propozycja, 2026-10-21].
- [x] **P6. Wpis zapisany przy otwartym oknie czyszczenia** (2026-10-28, po przeglądzie jakości UI). Rozstrzygnięte: bez zmian zachowania – okno pokazuje N z chwili otwarcia, a `clearAll()` usuwa także wpis zapisany przy otwartym oknie; zapisane jawnie jako przypadek brzegowy HISTUI-10 (najprostsze rozwiązanie).

## Historia zmian

- **Planowane zmiany (Spec 012, 013, 2026-10-31).** HISTUI-04: teksty wyników wpisów `Failed(AAPS_BOLUS_INTERVAL)`, `Failed(AAPS_BOLUS_QUEUE)`, `Unknown(BOLUS_FAILED)` ([BOL-22](../012-bolus/spec.md#bol-22-teksty-wyniku-i-historia-odmów-i-błędu-bolusa)) i `PROFILE_SWITCH_CONFIRMED` ([PROF-21](../013-profil/spec.md#prof-21-wynik-i-historia-potwierdzenia-zmiany-profilu)). Treść zostanie przepisana w zadaniu 4 zmiany 2026-10-31 (design 013); do tego czasu obowiązują Spec 012 i 013.

- **2026-10-29 – scalenie zmian ze Spec 011 – 013 (po wdrożeniu).**
  - HISTUI-03: etykiety „BOLUS X j.”, „PROFILE STATUS”, „PROFILE LIST”, „Zmiana profilu N, P%” (BOL-17, PROF-17).
  - HISTUI-04: wynik `BolusConfirmed` → „AAPS potwierdził podanie X j.” (BOL-17); komunikaty niezgodności i nierozpoznanej odpowiedzi jak w BOL-16 i PROF-16; wpis `PROFILE_LIST` ma w historii tekst „Odpowiedź AAPS na PROFILE LIST.” (bez wezwania do wyboru profilu); „Kod nie został wysłany” i ostrzeżenie bolusowe dla nowych poleceń z kodem.
- **2026-10-28 – doprecyzowanie po przeglądzie jakości UI (razem ze zmianą Spec 009, P11, P12).**
  - HISTUI-10: jawny przypadek brzegowy (wpis zapisany przy otwartym oknie) z kryterium JVM i wierszem w „Przypadkach błędów”; zachowanie bez zmian (P6).
  - HISTUI-13: flaga po zejściu z Historii w trakcie polecenia zostaje (CMDUI-23 na każdej zakładce); nowe kryterium i rozszerzony W6. `HistoryScreen` nadal zgłasza `SecureWindowEffect(true)`.
  - Design: decyzja o przypadku brzegowym, mapowanie testów, zadanie 5.
