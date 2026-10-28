# Spec 009: UI – polecenia i przebieg polecenia

- **Status:** zatwierdzona
- **Prefiks wymagań:** `CMDUI`
- **Pakiety:** `ui` (`ui.commands`, `ui.platform`, zmiany w `ui.navigation`)
- **Powiązane:** [docs/plan.md](../../plan.md#przepływ-działania), [docs/plan.md](../../plan.md#ekrany-aplikacji) (ekrany „Polecenia”, „CARBS”, „Przebieg polecenia”), [docs/plan.md](../../plan.md#parsowanie-odpowiedzi-i-obsługa-błędów); [Spec 003: Katalog poleceń](../003-katalog-polecen/spec.md) (`Command`, `CarbsLimits`, CMD-02 – CMD-06); [Spec 004: Sesja polecenia](../004-sesja-polecenia/spec.md) (`SessionCoordinator`, `CommandSession`, `SessionPhase`, `SessionOutcome`, SESSION-14, SESSION-17, SESSION-22); [Spec 005: SMS](../005-sms/spec.md) (założenie „aplikacja na pierwszym planie”, `SmsPermissions`, W1–W7); [Spec 006: Sekret TOTP](../006-sekret-totp/spec.md) (`TotpSecretProvider.load`, `LoadResult`, SECRET-14, W1–W10); [Spec 007: Dane](../007-dane/spec.md) (`AapsNumberState`, `SessionHistoryRecorder`, DATA-02, DATA-13, W1, W7); [Spec 008: UI – ustawienia](../008-ui-ustawienia/spec.md) (nawigacja, `SessionModule`, `SmsPermissionsViewModel`, `SecureWindowEffect`, UI-15, UI-23, podział weryfikacji P3); [stan prac](../../stan-prac.md); następna specyfikacja `ui`: 010 (ekran Historia)

## Cel

Użytkownik wysyła z zakładki Polecenia `BG` i `PUMP` jednym dotknięciem, a `CARBS` po wpisaniu ilości i PIN-u AAPS, i na jednym ekranie widzi przebieg polecenia na żywo oraz jednoznaczny wynik. Ta specyfikacja opisuje ekran Polecenia (zastępuje zaślepkę ze Spec 008), ekran podsumowania z PIN-em, ekran przebiegu i wyniku oraz uruchamianie sesji w zakresie korutyn aplikacji z zapisem do historii.

## Zakres

- Zakładka Polecenia: przyciski `BG`, `PUMP`, sekcja `CARBS` (pole ilości, szybkie wartości 10/20/30 g, przycisk „Dalej”), banery gotowości (numer AAPS, uprawnienia SMS, sekret TOTP) z przejściem do Ustawień.
- Dostępność przycisków: polecenie w toku, uruchamianie polecenia, stan numeru AAPS, uprawnienia SMS, stan sekretu (dla `CARBS`).
- Dodatkowe pytanie „Na pewno X g?” dla ilości > 40 g.
- Podsumowanie `CARBS` z polem PIN-u AAPS (maskowane), PIN tylko w pamięci; `FLAG_SECURE` na oknie „Na pewno X g?”, podsumowaniu oraz od startu polecenia do zamknięcia wyniku (na każdej zakładce).
- Odczyt sekretu (`TotpSecretProvider.load`) tuż przed startem polecenia z kodem i komunikaty jego wyników.
- Start przez `SessionCoordinator` w zakresie korutyn aplikacji (moduł Hilt), `SessionHistoryRecorder.track` zaraz po starcie.
- Ekran przebiegu: kroki według fazy sesji, anulowanie (przed kodem i po kodzie), wynik z komunikatem dla każdego `SessionOutcome`, treść odpowiedzi AAPS, liczba wiadomości nieoczekiwanych, zamknięcie wyniku.
- Ekran włączony w trakcie polecenia i komunikat zachęcający do pozostania w aplikacji; obsługa przycisku Wstecz w trakcie polecenia.
- Teksty po polsku w `strings.xml`.
- Ręczna weryfikacja: W1–W5 i W7 ze Spec 005, punkty Spec 006 i Spec 007 przypisane do 009 w Spec 008 (P3), ręczne sprawdzenie UI-15 ze Spec 008 i własne punkty W1–W12.

**Poza zakresem:**

- Ekran Historia (lista wpisów, czyszczenie) – spec 010.
- Usługa pierwszoplanowa i odbiór SMS w tle albo przy wygaszonym ekranie – przyszła osobna specyfikacja (Spec 005, P2).
- Ostatnia znana glikemia na ekranie Polecenia (plan) – poza zakresem 009 (decyzja użytkownika, P5).
- Zmiany w pakietach `session`, `commands`, `sms`, `security`, `data` (logika) – ta specyfikacja ich nie zmienia; braki są w „Przyszłych zmianach”.
- Przyczyna błędu wysyłania SMS (`SendResult.Failed` jej nie niesie – „Przyszłe zmiany” w Spec 005).
- Polecenia spoza MVP, ustawienia limitów czasu.
- Testy Robolectric i testy Compose UI (decyzja użytkownika); kompozycje sprawdzane ręcznie (sekcja „Weryfikacja na urządzeniu”).

## Założenia i zależności

- **Spec 008 zaimplementowana przed 009** (co najmniej zadania 1, 2, 3, 8 i 10 z design 008): nawigacja (`AppScaffold`, `AppDestination`), `SessionModule` (jeden `SessionCoordinator`), `SmsPermissionsViewModel` i `SmsPermissionsSection`, `SecureWindowEffect`, `MainDispatcherExtension`, `SessionDoubles`. 009 zastępuje `CommandsPlaceholderScreen` i klucz `placeholder_commands_text` (UI-03 ze Spec 008 przewiduje to zastąpienie).
- **Polecenia i walidacja ilości:** jedynym źródłem poleceń jest `commands` (`Command.Bg`, `Command.Pump`, `Command.Carbs.parse`/`of`, `requiresExtraConfirmation`, `CarbsLimits.QUICK_VALUES`, `definition.requiresCode`); `ui` nie powtarza reguł 1–50 g ani progu 40 g.
- **Sesja:** `ui` uruchamia polecenia wyłącznie przez `SessionCoordinator.start` (SESSION-22, UI-19) i nie zmienia `session`. Reguły anulowania i wyników – Spec 004 (SESSION-14, SESSION-17).
- **Sekret:** `TotpSecretProvider.load()` nie rzuca poza `CancellationException` (SECRET-06); zwrócony `TotpSecret` należy do wywołującego i trafia do `CodeCredentials`. `TotpSecret` nie ma metody czyszczenia („Przyszłe zmiany” w Spec 006); tablica wewnątrz zostaje do odśmiecenia.
- **Numer:** `AapsNumberSettingsStore.state` (DATA-02). Zmiana numeru w trakcie polecenia jest zablokowana w Ustawieniach (UI-15).
- **Pierwszy plan (Spec 005, P2):** odbiór SMS i limity sesji są gwarantowane tylko, gdy aplikacja jest na pierwszym planie, a ekran włączony. 009 trzyma ekran włączony i zachęca do pozostania w aplikacji, ale nie uniemożliwia wyjścia.
- **Testy (decyzja użytkownika):** tylko JVM – ViewModele, `CommandLauncher`, czyste funkcje odwzorowań, testy plików (`strings.xml`, źródła); JUnit 5, MockK, Turbine, `kotlinx-coroutines-test`. Kompozycje, okna, `FLAG_SECURE`, `keepScreenOn`, nawigacja i Hilt – weryfikacja ręczna i `assembleDebug`.

## Pojęcia

- **Panel poleceń** – zawartość zakładki Polecenia, gdy nie jest pokazany przebieg ani wynik: banery gotowości, `BG`, `PUMP`, sekcja `CARBS`.
- **Podsumowanie** – tryb zakładki Polecenia (bez osobnej trasy nawigacji) z pytaniem „Wysłać CARBS X g do AAPS?”, polem PIN-u i przyciskami „Wyślij”/„Anuluj”.
- **Polecenie w toku** – `SessionCoordinator.activeSession.value != null`.
- **Uruchamianie** – czas od dotknięcia `BG`/`PUMP`/„Wyślij” do wyniku `CommandLauncher.launch` (sprawdzenia, odczyt sekretu, start).
- **Przebieg** – widok sesji w fazie innej niż `FINISHED`; **wynik** – widok zakończonej sesji, aż użytkownik go zamknie.
- **Fazy przed kodem / po kodzie** – jak w Spec 004 (`NOT_STARTED` … `PREPARING_CODE` / `SENDING_CODE`, `AWAITING_CONFIRMATION`).
- **Komunikat** – jednorazowa informacja (Snackbar) jako wartość wyliczeniowa w stanie ViewModelu, kasowana po pokazaniu (jak w Spec 008).
- **Baner** – stały tekst nad przyciskami panelu, widoczny dopóki trwa jego przyczyna.

## Wymagania

Wspólne dane kryteriów (o ile kryterium nie mówi inaczej): numer AAPS w stanie `Configured(+447700900123)` (fikcyjny, zakres Ofcom), `SmsPermissions` zwraca `true` dla obu uprawnień, `SecretState.PRESENT`, `load()` zwraca `Loaded` z sekretem RFC 6238 (`12345678901234567890`), fikcyjny PIN `4321`, prawdziwy `SessionCoordinator` z atrapami `FakeSmsSender`/`FakeIncomingSmsSource` ze Spec 004 i zegarem `T0 = 1111111051000` + czas wirtualny (albo `activeSession` z MockK, gdy kryterium dotyczy tylko blokady), jeden wspólny `CurrentRun` dla launchera i obu ViewModeli, zakres uruchamiania sesji = `backgroundScope` testu, dyspozytor główny = `StandardTestDispatcher`. Treści wiadomości AAPS: pliki z [`../002-parser-odpowiedzi/dane/`](../002-parser-odpowiedzi/dane/README.md). „Brak startu” = `FakeSmsSender` nie dostał żadnego SMS-a i `activeSession.value == null`. „Uruchomiono przez launcher” = `CommandLauncher.launch` zakończone `Started` albo pomocnik testowy `startAndPublish` (`coordinator.start` + `currentRun.publish`, design – „Mapowanie wymagań na testy”). Samo `coordinator.start` albo samo ustawienie `activeSession` nie publikuje `CurrentRun`, więc nie daje przebiegu ani wyniku w `CommandRunViewModel`.

### Panel poleceń i gotowość

### CMDUI-01: Ekran Polecenia zamiast zaślepki

Zakładka Polecenia pokazuje (od góry): banery gotowości (CMDUI-02 – CMDUI-04), duże przyciski „BG” i „PUMP” (pełna szerokość), sekcję „CARBS” z polem ilości, trzema przyciskami szybkich wartości i przyciskiem „Dalej”. Gdy jest przebieg albo wynik (CMDUI-16), zakładka pokazuje go zamiast panelu; gdy otwarte jest podsumowanie – podsumowanie. Stan zakładki należy do `CommandsViewModel` i `CommandRunViewModel` tworzonych w zakresie `Activity` (nie wpisu nawigacji), więc przełączenie zakładek i obrót ekranu nie gubią stanu poza wyjątkami z CMDUI-11. `CommandsPlaceholderScreen` i klucz `placeholder_commands_text` są usuwane.

**Kryteria akceptacji:**

- **Zakładając** źródła `ui/`, **gdy** test JVM je przeczytał, **wtedy** nie ma pliku `CommandsPlaceholderScreen.kt`, a `strings.xml` nie zawiera klucza `placeholder_commands_text`.
- **Zakładając** `CommandsViewModel` z panelem w stanie spoczynku, **gdy** odczytano stan, **wtedy** podsumowanie jest zamknięte, okno „Na pewno” zamknięte, tekst pola ilości = `""`, a uruchamianie = `false`.
- **Zakładając** telefon (W1), **gdy** uruchomiono aplikację, **wtedy** zakładka Polecenia pokazuje przyciski „BG”, „PUMP” i sekcję „CARBS”; po przejściu na Ustawienia i powrót tekst pola ilości jest zachowany.

### CMDUI-02: Gotowość – numer AAPS

Panel pokazuje stan numeru z `AapsNumberSettingsStore.state` i od niego zależy dostępność wszystkich przycisków poleceń (`BG`, `PUMP`, szybkie wartości, „Dalej”):

| Stan numeru | Baner | Przycisk banera | Przyciski poleceń |
| --- | --- | --- | --- |
| `Loading` | „Wczytywanie ustawień…” | – | niedostępne |
| `NotConfigured` | „Nie ustawiono numeru AAPS. Bez niego aplikacja nie wyśle poleceń.” | „Przejdź do Ustawień” | niedostępne |
| `LoadFailed` | „Nie udało się wczytać numeru AAPS. Ustaw go ponownie w Ustawieniach.” | „Przejdź do Ustawień” | niedostępne |
| `Configured` | brak (numer nie jest pokazywany) | – | zależne od CMDUI-03 – CMDUI-05 |

„Przejdź do Ustawień” przełącza na zakładkę Ustawienia tak samo jak dotknięcie zakładki w pasku (UI-02).

**Kryteria akceptacji:**

- **Zakładając** magazyn numeru w stanie `Loading`, **gdy** utworzono ViewModel, **wtedy** gotowość numeru = wczytywanie, a `BG`, `PUMP` i `CARBS` są niedostępne.
- **Zakładając** magazyn, którego stan zmienia się kolejno na `NotConfigured`, `LoadFailed`, `Configured(+447700900123)` (Turbine), **gdy** ViewModel zbiera stan, **wtedy** stan ekranu ma kolejno: brak numeru (przyciski niedostępne), błąd wczytania (niedostępne), numer gotowy (`BG`, `PUMP`, `CARBS` dostępne).
- **Zakładając** stan ekranu z gotowym numerem, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `900123` ani `7700`.
- **Zakładając** zimny start z zapisanym numerem (W2, W7 ze Spec 007), **gdy** ekran się pojawił, **wtedy** przyciski są nieaktywne do końca wczytania, a potem `BG` wysyła polecenie.

### CMDUI-03: Gotowość – uprawnienia SMS

Gdy brakuje `SEND_SMS` albo `RECEIVE_SMS`, panel pokazuje sekcję uprawnień SMS ze Spec 008 (`SmsPermissionsSection` z własną instancją `SmsPermissionsViewModel`: lista brakujących, „Zezwól”, tekst odmowy, „Otwórz ustawienia aplikacji” – UI-17, UI-18, te same teksty), a przyciski poleceń są niedostępne. `CommandsViewModel` odczytuje `SmsPermissions.canSend()` i `canReceive()` przy utworzeniu i w `refreshPermissions()` (wołane przy każdym `ON_RESUME` zakładki Polecenia i po wyniku prośby o uprawnienia); nie zapamiętuje stanu poza ostatnim odczytem.

**Kryteria akceptacji:**

- **Zakładając** `canSend` = `true`, `canReceive` = `false`, **gdy** utworzono ViewModel, **wtedy** stan: uprawnienia SMS = brak, `BG`, `PUMP`, `CARBS` niedostępne.
- **Zakładając** brak obu uprawnień, **gdy** atrapa zaczęła zwracać `true` dla obu i wywołano `refreshPermissions()`, **wtedy** uprawnienia SMS = przyznane, a `BG` dostępne.
- **Zakładając** utworzony ViewModel, **gdy** wywołano 2 razy `refreshPermissions()`, **wtedy** `canSend()` i `canReceive()` wywołano po 3 razy.
- **Zakładając** telefon (W3), **gdy** odebrano uprawnienia SMS w ustawieniach systemu i wrócono do aplikacji, **wtedy** zakładka Polecenia pokazuje sekcję uprawnień, a przyciski są nieaktywne; „Zezwól” prosi o uprawnienia.

### CMDUI-04: Gotowość – sekret TOTP dla CARBS

Przy utworzeniu `CommandsViewModel` wywołuje `TotpSecretStore.refreshState()` dokładnie raz i dalej obserwuje `TotpSecretStore.state`. Stan sekretu wpływa tylko na `CARBS` (szybkie wartości i „Dalej”); `BG` i `PUMP` nie zależą od sekretu.

| `SecretState` | Baner | Przycisk banera | `CARBS` |
| --- | --- | --- | --- |
| `UNKNOWN`, `PRESENT` | brak | – | dostępne (o ile CMDUI-02, CMDUI-03, CMDUI-05 pozwalają); ostateczne sprawdzenie przy starcie (CMDUI-12) |
| `ABSENT` | „CARBS wymaga sekretu TOTP. Zeskanuj kod QR z AAPS w Ustawieniach.” | „Przejdź do Ustawień” | niedostępne |
| `INVALIDATED` | „Sekret TOTP przestał działać (np. po zmianie blokady ekranu). Zeskanuj ponownie kod QR z AAPS w Ustawieniach.” | „Przejdź do Ustawień” | niedostępne |

`UNKNOWN` nie blokuje `CARBS`, bo `refreshState()` przy błędzie odczytu zostawia `UNKNOWN` (SECRET-12). `CommandsViewModel` nie wywołuje `load()` poza uruchamianiem `CARBS` (CMDUI-12).

**Kryteria akceptacji:**

- **Zakładając** atrapę magazynu w stanie `UNKNOWN`, **gdy** utworzono ViewModel i wykonano zadania dyspozytora, **wtedy** `refreshState()` wywołano dokładnie raz, a `load()` – ani razu.
- **Zakładając** magazyn, którego stan zmienia się kolejno na `ABSENT`, `INVALIDATED`, `PRESENT`, `UNKNOWN` (Turbine), **gdy** ViewModel zbiera stan, **wtedy** `CARBS` jest kolejno: niedostępne, niedostępne, dostępne, dostępne, a `BG` i `PUMP` są dostępne we wszystkich czterech stanach.

### CMDUI-05: Blokada w trakcie polecenia i uruchamiania

Gdy polecenie jest w toku albo trwa uruchamianie, wszystkie przyciski poleceń są niedostępne, a ich wywołanie jest ignorowane (podwójne dotknięcie nie uruchamia drugiego polecenia). Każda akcja startu (`BG`, `PUMP`, „Wyślij”) sprawdza `activeSession.value` w chwili wywołania: polecenie w toku → komunikat „Trwa polecenie – spróbuj po jego zakończeniu.” (klucz `msg_command_active` ze Spec 008), brak wywołania `CommandLauncher` i – przy „Wyślij” – PIN wyczyszczony (CMDUI-11). Gdy mimo to koordynator zwróci `Busy` (wyścig), wynik jest taki sam (CMDUI-13). Po zakończeniu polecenia dostępność wraca według CMDUI-02 – CMDUI-04.

**Kryteria akceptacji:**

- **Zakładając** `activeSession` = sesja (MockK), **gdy** odczytano stan, **wtedy** `BG`, `PUMP` i `CARBS` są niedostępne, a znacznik „polecenie w toku” = `true`.
- **Zakładając** `activeSession` ustawione na sesję bez wykonania zadań dyspozytora (ViewModel nie zebrał zmiany), **gdy** wywołano `onBgClicked()`, **wtedy** `CommandLauncher` nie dostał wywołania, a komunikat = polecenie w toku.
- **Zakładając** `FakeSmsSender`, którego wysłanie zawiesza się do zwolnienia przez test, **gdy** wywołano `onBgClicked()` dwa razy i `onPumpClicked()` raz przed zwolnieniem, **wtedy** atrapa dostała dokładnie jeden SMS (`BG`).
- **Zakładając** `TotpSecretProvider.load`, który zawiesza się do zwolnienia przez test, **gdy** w podsumowaniu wywołano „Wyślij”, **wtedy** do zwolnienia znacznik uruchamiania = `true`, a `BG`, `PUMP` i `CARBS` są niedostępne.
- **Zakładając** `activeSession` = sesja, **gdy** zmienił się na `null`, **wtedy** `BG`, `PUMP` i `CARBS` są dostępne.

### Polecenia bez kodu

### CMDUI-06: BG i PUMP jednym dotknięciem

Dotknięcie „BG” albo „PUMP” (gdy dostępne) od razu uruchamia polecenie `Command.Bg` / `Command.Pump` przez `CommandLauncher.launch(command, pin = null)` – bez okna potwierdzenia i bez PIN-u (CMD-02). Po wyniku `Started` zakładka pokazuje przebieg (CMDUI-16); po odrzuceniu – komunikat z CMDUI-12/CMDUI-13, panel bez zmian.

**Kryteria akceptacji:**

- **Zakładając** wspólne dane, **gdy** wywołano `onBgClicked()` i wykonano zadania, **wtedy** `FakeSmsSender` dostał dokładnie jeden SMS `BG`, `activeSession.value` jest sesją polecenia `Command.Bg`, a `TotpSecretProvider.load()` nie był wywołany.
- **Zakładając** wspólne dane, **gdy** wywołano `onPumpClicked()`, **wtedy** atrapa dostała dokładnie jeden SMS `PUMP`.
- **Zakładając** telefon z AAPS (W4), **gdy** dotknięto „BG”, **wtedy** bez dodatkowych dotknięć wychodzi SMS `BG` na numer AAPS, a po odpowiedzi widać jej treść.

### Polecenie CARBS

### CMDUI-07: Pole ilości i walidacja

Sekcja „CARBS” ma pole tekstowe „Węglowodany (g)” z klawiaturą numeryczną (`KeyboardType.Number`). Tekst pola jest w stanie `CommandsViewModel` dokładnie tak, jak wpisany. „Dalej” wywołuje `Command.Carbs.parse(tekst)` (bez przycinania, CMD-04): `Valid` → CMDUI-09; `Invalid(error)` → błąd pod polem według tabeli, bez zmiany tekstu. Zmiana tekstu usuwa błąd. ViewModel nie stosuje innych reguł ilości niż `commands`.

| `CarbsAmountError` | Błąd pod polem |
| --- | --- |
| `EMPTY` | „Podaj ilość węglowodanów (1–50 g).” |
| `INVALID_CHARACTERS` | „Podaj liczbę całkowitą – tylko cyfry.” |
| `BELOW_MIN` | „Najmniejsza ilość to 1 g.” |
| `ABOVE_MAX` | „Największa ilość to 50 g.” |

Liczby 1 i 50 w tekstach pochodzą z `CarbsLimits.MIN_GRAMS`/`MAX_GRAMS` (argumenty formatowania).

**Kryteria akceptacji:**

- **Zakładając** tekst pola kolejno `""`, `"2.5"`, `"0"`, `"51"`, **gdy** za każdym razem wywołano `onCarbsNextClicked()`, **wtedy** błąd = kolejno `EMPTY`, `INVALID_CHARACTERS`, `BELOW_MIN`, `ABOVE_MAX`, podsumowanie zamknięte, tekst pola bez zmian.
- **Zakładając** błąd `ABOVE_MAX` przy tekście `"51"`, **gdy** zmieniono tekst na `"5"`, **wtedy** błąd = brak.
- **Zakładając** tekst `" 20"` (spacja na początku), **gdy** wywołano „Dalej”, **wtedy** błąd = `INVALID_CHARACTERS`.
- **Zakładając** tekst `"020"`, **gdy** wywołano „Dalej”, **wtedy** podsumowanie jest otwarte dla 20 g.

### CMDUI-08: Szybkie wartości

Sekcja „CARBS” ma przyciski „10 g”, „20 g”, „30 g” z `CarbsLimits.QUICK_VALUES`, w tej kolejności. Dotknięcie szybkiej wartości wpisuje ją w pole ilości (np. `"20"`), usuwa błąd pola i od razu otwiera podsumowanie dla tej ilości (szybkie wartości nie przekraczają 40 g, CMD-06). (Decyzja użytkownika, P1.)

**Kryteria akceptacji:**

- **Zakładając** stan ekranu, **gdy** odczytano listę szybkich wartości, **wtedy** to dokładnie `[10, 20, 30]`.
- **Zakładając** tekst pola `"7"` z błędem brak, **gdy** wywołano `onQuickValueClicked(20)`, **wtedy** tekst pola = `"20"`, podsumowanie otwarte dla 20 g, okno „Na pewno” zamknięte, `FakeSmsSender` nie dostał SMS-a.

### CMDUI-09: Dodatkowe potwierdzenie powyżej 40 g

Gdy poprawne polecenie ma `requiresExtraConfirmation` = `true` (41–50 g, CMD-05), „Dalej” najpierw otwiera okno „Na pewno X g?” z tekstem „To więcej niż 40 g. Sprawdź ilość, zanim wpiszesz PIN.” i przyciskami „Tak, X g” i „Nie”. „Tak” zamyka okno i otwiera podsumowanie dla X g; „Nie” zamyka okno bez podsumowania, tekst pola bez zmian. Dla 1–40 g podsumowanie otwiera się od razu. Okno pokazuje ilość `CARBS`, więc przez cały czas otwarcia jest chronione (decyzja użytkownika, P11): dialog jest osobnym oknem systemowym, którego `FLAG_SECURE` okna `Activity` nie obejmuje, dlatego sam dialog ma `DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)` (jak okno wklejania w Spec 008, UI-23), a okno `Activity` pod nim (panel z polem ilości) ma `FLAG_SECURE` (CMDUI-23).

**Kryteria akceptacji:**

- **Zakładając** tekst pola `"41"`, **gdy** wywołano „Dalej”, **wtedy** okno „Na pewno” jest otwarte dla 41 g, a podsumowanie zamknięte; **gdy** wywołano „Tak”, **wtedy** okno zamknięte, podsumowanie otwarte dla 41 g.
- **Zakładając** okno „Na pewno” dla 50 g, **gdy** wywołano „Nie”, **wtedy** okno i podsumowanie są zamknięte, tekst pola = `"50"`, `FakeSmsSender` nie dostał SMS-a.
- **Zakładając** tekst pola `"40"`, **gdy** wywołano „Dalej”, **wtedy** okno „Na pewno” nie jest otwierane, a podsumowanie jest otwarte dla 40 g.
- **Zakładając** tekst pola `"45"`, **gdy** wywołano „Dalej”, **wtedy** znacznik „ekran chroniony” `CommandsViewModel` = `true` (okno otwarte, podsumowanie zamknięte); **gdy** wywołano „Nie”, **wtedy** = `false`; **gdy** ponownie wywołano „Dalej” i „Tak”, **wtedy** = `true` w każdej emisji `uiState` (Turbine) od otwarcia okna do otwarcia podsumowania.
- **Zakładając** otwarte okno „Na pewno” dla 45 g, **gdy** wywołano `onStop()`, **wtedy** okno zamknięte, a znacznik „ekran chroniony” = `false`.
- **Zakładając** źródło `ui/commands/CommandDialogs.kt`, **gdy** test JVM je przeczytał, **wtedy** wywołanie `AlertDialog` w `LargeAmountDialog` ma `properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)`.

### CMDUI-10: Podsumowanie z PIN-em

Podsumowanie zajmuje całą treść zakładki Polecenia (bez osobnej trasy nawigacji) i pokazuje: „Wysłać CARBS X g do AAPS?”, wyjaśnienie „Aplikacja wyśle CARBS X g, a kod z PIN-em odeśle dopiero, gdy AAPS poprosi o kod dla X g.”, pole „PIN AAPS” (jedna linia, znaki maskowane, klawiatura `KeyboardType.NumberPassword`, bez autokorekty i podpowiedzi) oraz przyciski „Wyślij” i „Anuluj”. Przycisk Wstecz działa jak „Anuluj”. Pole przyjmuje tylko cyfry: zmiana na tekst z innym znakiem jest odrzucana (tekst pola bez zmian); długość najwyżej 12 znaków – dłuższy tekst jest odrzucany (decyzja użytkownika, P2). „Wyślij” jest dostępne, gdy pole jest niepuste i nie trwa uruchamianie. „Anuluj” zamyka podsumowanie, czyści pole PIN-u i nie wywołuje `CommandLauncher`; tekst pola ilości zostaje.

**Kryteria akceptacji:**

- **Zakładając** otwarte podsumowanie dla 20 g, **gdy** odczytano stan, **wtedy** ilość podsumowania = 20, tekst PIN-u = `""`, „Wyślij” niedostępne.
- **Zakładając** otwarte podsumowanie, **gdy** wywołano `onPinChanged("43")`, potem `onPinChanged("43a")`, potem `onPinChanged("4321")`, **wtedy** tekst PIN-u = kolejno `"43"`, `"43"`, `"4321"`, a „Wyślij” jest dostępne.
- **Zakładając** tekst PIN-u `"123456789012"`, **gdy** wywołano `onPinChanged("1234567890123")`, **wtedy** tekst PIN-u = `"123456789012"`.
- **Zakładając** podsumowanie z PIN-em `4321`, **gdy** wywołano „Anuluj”, **wtedy** podsumowanie zamknięte, tekst PIN-u = `""`, `CommandLauncher` nie dostał wywołania, tekst pola ilości bez zmian.
- **Zakładając** telefon z Gboard (W6), **gdy** wpisano PIN, **wtedy** znaki są maskowane, klawiatura jest numeryczna, a pasek podpowiedzi nie pokazuje cyfr PIN-u.

### CMDUI-11: PIN AAPS tylko w pamięci

Tekst PIN-u istnieje wyłącznie w stanie `CommandsViewModel` w pamięci (nie w `SavedStateHandle`, nie w `rememberSaveable`, nie w argumentach nawigacji). Jest czyszczony (`""`): przy „Wyślij” – przed wywołaniem `CommandLauncher` (znaki są kopiowane do tablicy, z niej powstaje `AapsPin.fromChars`, który zeruje tablicę), przy „Anuluj”/Wstecz, gdy zakładka Polecenia jest zatrzymywana (`ON_STOP`: przejście w tło, obrót ekranu) albo opuszcza kompozycję (zmiana zakładki, w tym akcja „Ustawienia” w Snackbarze) – w obu przypadkach `CommandsScreen` woła `onStop()`, a podsumowanie i okno „Na pewno” są zamykane – oraz gdy polecenie jest w toku w chwili „Wyślij” (CMDUI-05). `AapsPin` przekazany do `CommandLauncher` jest czyszczony na każdej ścieżce bez startu sesji (CMDUI-12, CMDUI-13); po starcie czyści go sesja (SESSION-19). Ograniczenie: obiekty `String` tekstu pola (Compose, ViewModel) nie dają się wyzerować w JVM i zostają w pamięci do odśmiecenia (jak UI-14).

**Kryteria akceptacji:**

- **Zakładając** podsumowanie z PIN-em `4321` i `load()`, który zawiesza się do zwolnienia przez test, **gdy** wywołano „Wyślij”, **wtedy** przed zwolnieniem tekst PIN-u = `""`.
- **Zakładając** podsumowanie z PIN-em `4321` i otwarte okno „Na pewno” (osobno), **gdy** wywołano `onStop()`, **wtedy** tekst PIN-u = `""`, podsumowanie i okno zamknięte, tekst pola ilości bez zmian, `CommandLauncher` nie dostał wywołania.
- **Zakładając** podsumowanie dla 20 g z PIN-em `4321`, **gdy** wywołano `onStop()` dwa razy z rzędu (obrót ekranu: `ON_STOP`, a potem opuszczenie kompozycji), **wtedy** po drugim wywołaniu stan `CommandsViewModel` jest równy stanowi po pierwszym (tekst PIN-u = `""`, podsumowanie zamknięte, tekst pola ilości `20`), `CommandLauncher` nie dostał wywołania. `onStop()` zamyka też okno „Na pewno”, tak jak w kryterium wyżej.
- **Zakładając** `CommandsViewModel` i `CommandRunViewModel` ze wspólnym `CurrentRun` i przebieg `CARBS 1` uruchomiony przez launcher („Wyślij” z PIN-em `4321`, `load()` → `Loaded`, faza czekania na prośbę o kod), **gdy** wywołano `onStop()` w `CommandsViewModel` i wykonano zadania dyspozytora, **wtedy** `CurrentRun` nadal zawiera to uruchomienie, `activeSession.value` jest tą samą sesją (nieanulowaną), `run.uiState.value.view` jest `RunView.Active` z `commandGrams = 1` i tym samym `commandId` co przed `onStop()`, a `commands.uiState.value.secureWindow || run.uiState.value.secureWindow` = `true`.
- **Zakładając** źródło `ui/commands/CommandsScreen.kt` bez komentarzy, **gdy** test JVM je przeczytał, **wtedy** zawiera `LifecycleEventEffect(Lifecycle.Event.ON_STOP) { commandsViewModel.onStop() }` oraz `DisposableEffect(Unit) { onDispose { commandsViewModel.onStop() } }`, a blok `onDispose` nie zawiera `runViewModel`.
- **Zakładając** konstruktory `CommandsViewModel` i `CommandRunViewModel`, **gdy** test sprawdził je refleksją, **wtedy** nie mają parametru typu `SavedStateHandle`; **zakładając** źródła `ui/commands/`, **gdy** test JVM je przeczytał, **wtedy** nie zawierają `rememberSaveable` ani `SavedStateHandle`.
- **Zakładając** `CommandsViewModel` i jego stan ekranu, **gdy** sprawdzono pola refleksją, **wtedy** żadne nie ma typu `AapsPin`, `CharArray`, `TotpSecret`, `LoadResult` ani `CodeCredentials`.
- **Zakładając** telefon (W6), **gdy** w podsumowaniu wpisano PIN i przełączono na inną aplikację, a potem wrócono, **wtedy** podsumowanie jest zamknięte, a po ponownym otwarciu pole PIN-u jest puste.
- **Zakładając** telefon (W6), **gdy** w podsumowaniu wpisano PIN, przełączono na zakładkę Historia albo Ustawienia, a potem wrócono na Polecenia, **wtedy** widać panel (podsumowanie zamknięte), a po ponownym otwarciu podsumowania pole PIN-u jest puste.

### Uruchamianie polecenia

### CMDUI-12: Sprawdzenia i odczyt sekretu przed startem

`CommandLauncher.launch(command, pin)` wykonuje kolejno i przerywa na pierwszym niespełnionym warunku (zwraca `Rejected` z powodem, bez startu sesji, a przekazany `pin` jest czyszczony):

0. Niepoprawny zestaw: `pin != null` różne od `command.definition.requiresCode` → `START_FAILED` (błąd programistyczny; bez wyjątku).
1. `activeSession.value != null` → `COMMAND_ACTIVE`.
2. Stan numeru: `Loading` → `NUMBER_LOADING`; `NotConfigured` albo `LoadFailed` → `NUMBER_MISSING`.
3. `SmsPermissions.canSend()` albo `canReceive()` = `false` (odczyt w tej chwili) → `SMS_PERMISSIONS_MISSING`.
4. Tylko dla polecenia z kodem (`definition.requiresCode`): `TotpSecretProvider.load()` dokładnie raz; `Absent` → `SECRET_ABSENT`, `Invalidated` → `SECRET_INVALIDATED`, `Locked` → `SECRET_LOCKED`, `Unavailable` → `SECRET_UNAVAILABLE`, `Loaded(secret)` → `CodeCredentials(pin, secret)`.
5. Start (CMDUI-13).

`CommandsViewModel` odwzorowuje powód na komunikat według tabeli i po każdym odrzuceniu zamyka podsumowanie (tekst pola ilości zostaje). Komunikaty z „Ustawienia” mają w Snackbarze akcję „Ustawienia”, która przełącza na zakładkę Ustawienia.

| Powód | Komunikat | Akcja |
| --- | --- | --- |
| `COMMAND_ACTIVE` | „Trwa polecenie – spróbuj po jego zakończeniu.” | – |
| `NUMBER_LOADING` | „Trwa wczytywanie ustawień. Spróbuj za chwilę.” | – |
| `NUMBER_MISSING` | „Ustaw numer telefonu AAPS w Ustawieniach.” | Ustawienia |
| `SMS_PERMISSIONS_MISSING` | „Bez uprawnień SMS aplikacja nie wyśle polecenia ani nie odbierze odpowiedzi AAPS.” | – |
| `SECRET_ABSENT` | „Brak sekretu TOTP. Zeskanuj kod QR z AAPS w Ustawieniach.” | Ustawienia |
| `SECRET_INVALIDATED` | „Sekret TOTP przestał działać. Zeskanuj ponownie kod QR z AAPS w Ustawieniach.” | Ustawienia |
| `SECRET_LOCKED` | „Odblokuj telefon i spróbuj ponownie.” | – |
| `SECRET_UNAVAILABLE` | „Nie udało się odczytać sekretu TOTP. Spróbuj ponownie.” | – |
| `START_FAILED` | „Nie udało się uruchomić polecenia. Spróbuj ponownie.” | – |

**Kryteria akceptacji:**

- **Zakładając** `load()` zwracający kolejno `Absent`, `Invalidated`, `Locked`, `Unavailable`, **gdy** czterokrotnie wywołano `launch(CARBS 20, AapsPin 4321)` (za każdym razem nowy PIN), **wtedy** wyniki = kolejno `SECRET_ABSENT`, `SECRET_INVALIDATED`, `SECRET_LOCKED`, `SECRET_UNAVAILABLE`, każdy PIN jest wyczyszczony, brak startu.
- **Zakładając** stan numeru `Loading` (osobno `NotConfigured`, `LoadFailed`), **gdy** wywołano `launch(CARBS 20, PIN)`, **wtedy** wynik = `NUMBER_LOADING` (`NUMBER_MISSING`, `NUMBER_MISSING`), PIN wyczyszczony, `load()` nie był wywołany.
- **Zakładając** `canReceive` = `false`, **gdy** wywołano `launch(BG, null)`, **wtedy** wynik = `SMS_PERMISSIONS_MISSING`, brak startu.
- **Zakładając** `launch(BG, null)` i `launch(PUMP, null)`, **gdy** się zakończyły, **wtedy** `load()` nie był wywołany.
- **Zakładając** `load()`, który zawiesza się do zwolnienia, **gdy** korutynę wywołującą `launch(CARBS 20, PIN)` anulowano przed zwolnieniem, **wtedy** PIN jest wyczyszczony i brak startu (także po zwolnieniu `load()`).
- **Zakładając** podsumowanie dla 20 g z PIN-em `4321` i `load()` zwracający `Absent`, **gdy** wywołano „Wyślij”, **wtedy** komunikat = brak sekretu z akcją „Ustawienia”, podsumowanie zamknięte, tekst pola ilości = `"20"`.
- **Zakładając** każdą wartość `LaunchRejection`, **gdy** odwzorowano ją na komunikat, **wtedy** komunikat i jego akcja odpowiadają wierszowi tabeli.

### CMDUI-13: Start w zakresie aplikacji

Po sprawdzeniach `CommandLauncher` wywołuje `SessionCoordinator.start(scope, command, credentials)`, gdzie `scope` to zakres korutyn aplikacji `@CommandRunScope` (`SupervisorJob` + `Dispatchers.Default`, singleton Hilt, nigdy nie anulowany przez `ui`), a nie `viewModelScope` – sesja trwa dalej po zamknięciu `Activity`, obrocie i odtworzeniu ViewModeli. `credentials` = `null` dla `BG`/`PUMP`. Wynik: `Started(session)` → CMDUI-14, potem `LaunchResult.Started`; `Busy` → `Rejected(COMMAND_ACTIVE)`; `IllegalArgumentException` → `Rejected(START_FAILED)`. PIN czyści wtedy koordynator (SESSION-22), a `CommandLauncher` dodatkowo wywołuje `pin.clear()` na każdej ścieżce `Rejected` (czyszczenie jest idempotentne), więc niezmiennik brzmi: każdy wynik `Rejected` = PIN wyczyszczony. Nie ma automatycznego ponawiania startu.

**Kryteria akceptacji:**

- **Zakładając** wspólne dane i `launch(CARBS 1, PIN 4321)`, **gdy** w chwili 60 000 przychodzi `carbs-prosba-o-kod.txt`, a w chwili 120 000 `carbs-sukces.txt`, **wtedy** wynik `launch` = `Started`, `FakeSmsSender` dostał `CARBS 1` i `0504714321`, a stan sesji kończy się `CarbsConfirmed` z ilością 1.
- **Zakładając** sesję `BG` uruchomioną przez `CommandsViewModel` (zakres uruchamiania = `backgroundScope`), **gdy** wyczyszczono ViewModel (`ViewModelStore.clear()`, zakres ViewModelu anulowany), a potem przyszedł `bg.txt`, **wtedy** sesja kończy się `Completed` (nie `Cancelled`).
- **Zakładając** koordynator (MockK) z `activeSession.value == null` i `start` zwracającym `Busy` (wyścig: inna sesja wystartowała między sprawdzeniem a startem), **gdy** wywołano `launch(CARBS 20, PIN)`, **wtedy** wynik = `COMMAND_ACTIVE`, PIN wyczyszczony, a `track` nie był wywołany.
- **Zakładając** `launch(BG, AapsPin 4321)` (osobno `launch(CARBS 20, null)`) – niepoprawny zestaw, **gdy** się zakończył, **wtedy** wynik = `START_FAILED`, PIN (jeśli był) wyczyszczony, `load()` nie był wywołany, brak startu, bez wyjątku na zewnątrz.
- **Zakładając** moduł Hilt z zakresem uruchamiania, **gdy** test JVM sprawdził metodę dostarczającą refleksją, **wtedy** ma adnotacje `@Provides`, `@Singleton` i `@CommandRunScope`, a zwrócony zakres ma `SupervisorJob` (dziecko zakończone wyjątkiem nie anuluje zakresu) i dyspozytor `Dispatchers.Default` (`coroutineContext[ContinuationInterceptor]`).

### CMDUI-14: Zapis do historii zaraz po starcie

Zaraz po `StartResult.Started(session)`, w tym samym wywołaniu `launch` i przed jego powrotem, `CommandLauncher` wywołuje `SessionHistoryRecorder.track(session.state)` dokładnie raz dla tej sesji (DATA-13). Przy `Rejected` `track` nie jest wywoływany.

**Kryteria akceptacji:**

- **Zakładając** rejestrator (MockK albo prawdziwy z atrapą historii), **gdy** `launch(BG, null)` zwrócił `Started`, **wtedy** `track` wywołano dokładnie raz, z obiektem `state` sesji z `activeSession` (ta sama referencja), przed powrotem z `launch`.
- **Zakładając** każde odrzucenie z CMDUI-12 i CMDUI-13, **gdy** `launch` się zakończył, **wtedy** `track` nie był wywołany.
- **Zakładając** prawdziwy rejestrator z atrapą historii, **gdy** sesja `BG` uruchomiona przez `launch` zakończyła się `Completed` po `bg.txt`, **wtedy** historia dostała dokładnie jeden wpis.

### CMDUI-15: Sekret i PIN nie zostają w warstwie ui

`CommandLauncher` ma wyłącznie pola niezmienne z zależnościami (koordynator, magazyny, uprawnienia, rejestrator, `CurrentRun`, zakres) – żadne pole nie jest typu `TotpSecret`, `LoadResult`, `CodeCredentials`, `AapsPin`, `CharArray`, `ByteArray` ani `CommandSession`. Sekret istnieje tylko jako zmienna lokalna wywołania `launch` do zbudowania `CodeCredentials` przekazanego koordynatorowi (SECRET-14). ViewModele nie dostają `TotpSecret` ani `CodeCredentials`.

**Kryteria akceptacji:**

- **Zakładając** klasę `CommandLauncher`, **gdy** sprawdzono jej pola refleksją, **wtedy** wszystkie są `final`, a żadne nie ma typu z listy.
- **Zakładając** atrapę `TotpSecretProvider` zapamiętującą zwrócony `TotpSecret` i wykonane `launch(CARBS 20, PIN)` zakończone `Started`, **gdy** sprawdzono refleksją pola `CommandLauncher`, `CommandsViewModel` i ich stanów (jeden poziom w głąb), **wtedy** żadne nie jest referencją do tego `TotpSecret`.

### Przebieg i wynik

### CMDUI-16: Pokazanie przebiegu aktywnej sesji

`CommandRunViewModel` obserwuje `CurrentRun` (polecenie i stan ostatnio uruchomionej sesji, publikowany przez `CommandLauncher` po `Started`, przed powrotem z `launch`), a nie `activeSession` (ten mógłby przejść `sesja` → `null`, zanim kolektor ją odbierze); dla niepustego uruchomienia zbiera jego `state` i pokazuje przebieg (polecenie, kroki, liczba wiadomości nieoczekiwanych, tryb anulowania), a po fazie `FINISHED` – wynik z `SessionResult` (CMDUI-19). Działa to także dla sesji, która była w toku albo już się zakończyła, zanim ViewModel powstał (np. po „Wyjdź” i ponownym otwarciu aplikacji, gdy proces żyje – P7, CMDUI-21). Przełączenie zakładek i obrót ekranu nie zmieniają widoku (ViewModel w zakresie `Activity`). Nagłówek przebiegu i wyniku: „Polecenie BG”, „Polecenie PUMP”, „Polecenie CARBS X g”.

**Kryteria akceptacji:**

- **Zakładając** wspólne dane, **gdy** uruchomiono `BG` przez launcher (`CommandLauncher.launch` albo `startAndPublish`), **wtedy** stan `CommandRunViewModel` = przebieg polecenia `BG` w fazie `SENDING_COMMAND` albo `AWAITING_REPLY`; **gdy** przyszedł `bg.txt`, **wtedy** stan = wynik.
- **Zakładając** sesję `CARBS 20` w `AWAITING_CODE_REQUEST` uruchomioną przez launcher przed utworzeniem `CommandRunViewModel` (`CurrentRun` już ją zawiera), **gdy** utworzono ViewModel, **wtedy** stan = przebieg `CARBS` dla 20 g z bieżącym krokiem „czekanie na prośbę o kod”.
- **Zakładając** sesję `BG` uruchomioną przez launcher i zakończoną `Completed` po `bg.txt` przed utworzeniem `CommandRunViewModel` (wynik niezamknięty, „Wyjdź” i ponowne otwarcie przy żyjącym procesie), **gdy** utworzono ViewModel, **wtedy** stan = wynik `Completed` polecenia `BG`.
- **Zakładając** sesję kończącą się natychmiast (np. `SEND_FAILED`), której `activeSession` kolektor `CommandRunViewModel` nigdy nie zobaczył jako niepustej (CMDUI-19), **gdy** `CommandLauncher` opublikował jej stan w `CurrentRun`, **wtedy** ViewModel i tak pokazuje wynik tej sesji; **gdy** wynik zamknięto, `CurrentRun` jest pusty.
- **Zakładając** telefon (W8), **gdy** w trakcie `CARBS` przełączono na Ustawienia i z powrotem oraz obrócono ekran, **wtedy** zakładka Polecenia pokazuje ten sam przebieg; w Ustawieniach widać baner „Trwa polecenie” (UI-15 ze Spec 008).

### CMDUI-17: Kroki przebiegu

Przebieg pokazuje listę kroków zależną od polecenia; każdy krok ma status: wykonany, bieżący albo oczekujący. Krok bieżący wynika z fazy; kroki przed nim są wykonane, po nim – oczekujące.

| Polecenie | Kroki (w kolejności) |
| --- | --- |
| `BG`, `PUMP` | 1 „Wysyłanie SMS z poleceniem”, 2 „Czekanie na odpowiedź AAPS (do 3 min)” |
| `CARBS` | 1 „Wysyłanie SMS z poleceniem”, 2 „Czekanie na prośbę AAPS o kod (do 3 min)”, 3 „Przygotowanie kodu”, 4 „Wysyłanie kodu”, 5 „Czekanie na potwierdzenie AAPS (do 6 min)” |

| Faza | Krok bieżący |
| --- | --- |
| `NOT_STARTED`, `SENDING_COMMAND` | 1 |
| `AWAITING_REPLY`, `AWAITING_CODE_REQUEST` | 2 |
| `PREPARING_CODE` | 3 |
| `SENDING_CODE` | 4 |
| `AWAITING_CONFIRMATION` | 5 |

Liczby minut pochodzą z domyślnych `SessionTimeouts` (`reply`, `confirmation`). Tabela faz jest wspólna dla wszystkich poleceń. Gdy krok bieżący z tabeli wykracza poza listę kroków polecenia (dla `BG`/`PUMP`: fazy `PREPARING_CODE`, `SENDING_CODE`, `AWAITING_CONFIRMATION`) albo faza to `FINISHED`, wszystkie kroki są oczekujące. Faza nietypowa dla polecenia, ale z krokiem bieżącym w zakresie jego listy (np. `AWAITING_CODE_REQUEST` dla `BG`, `AWAITING_REPLY` dla `CARBS`), daje krok bieżący z tabeli (krok 2). Takie fazy nie występują w działaniu; przebieg w `FINISHED` jest zastąpiony wynikiem.

**Status dla czytnika ekranu** (decyzja użytkownika, P12): status kroku nie jest przekazywany wyłącznie ikoną ani kolorem. Każdy krok jest dla usług dostępności (TalkBack) jednym elementem, który czyta nazwę kroku i opis stanu (`stateDescription`) według tabeli; ikony kroków nie mają własnego opisu (`contentDescription = null`), a wskaźnik postępu kroku bieżącego nie wnosi własnych semantyk postępu do scalonego elementu, żeby status nie był czytany dwa razy.

| Status kroku | Opis stanu (`stateDescription`) |
| --- | --- |
| wykonany | „wykonany” |
| bieżący | „w toku” |
| oczekujący | „oczekuje” |

Krok nie ma statusu „błąd”: błąd, brak odpowiedzi i anulowanie pokazuje wynik (CMDUI-19), który zastępuje przebieg.

**Kryteria akceptacji:**

- **Zakładając** `CARBS` i każdą z faz `NOT_STARTED` … `AWAITING_CONFIRMATION`, **gdy** wyliczono kroki, **wtedy** jest 5 kroków, a statusy odpowiadają tabeli (np. dla `SENDING_CODE`: wykonany, wykonany, wykonany, bieżący, oczekujący).
- **Zakładając** `BG` (osobno `PUMP`) i fazy `SENDING_COMMAND`, `AWAITING_REPLY`, **gdy** wyliczono kroki, **wtedy** są 2 kroki ze statusami kolejno (bieżący, oczekujący) i (wykonany, bieżący).
- **Zakładając** `BG` w fazie `AWAITING_CONFIRMATION` (osobno `PREPARING_CODE`), **gdy** wyliczono kroki, **wtedy** oba są oczekujące.
- **Zakładając** `BG` w fazie `AWAITING_CODE_REQUEST` i (osobno) `CARBS` w fazie `AWAITING_REPLY`, **gdy** wyliczono kroki, **wtedy** krok 2 jest bieżący: odpowiednio (wykonany, bieżący) i (wykonany, bieżący, oczekujący, oczekujący, oczekujący).
- **Zakładając** domyślne `SessionTimeouts`, **gdy** odczytano argumenty minut kroków, **wtedy** to 3 i 6.
- **Zakładając** każdy status kroku (wykonany, bieżący, oczekujący), **gdy** test JVM odwzorował go na zasób opisu stanu, **wtedy** każdy identyfikator jest niezerowy, trzy identyfikatory są różne, a `strings.xml` zawiera dla nich teksty „wykonany”, „w toku”, „oczekuje”.
- **Zakładając** źródło `ui/commands/RunScreen.kt`, **gdy** test JVM je przeczytał, **wtedy** zawiera przypisanie `stateDescription` i odwołanie do funkcji odwzorowania statusu kroku na zasób.
- **Zakładając** telefon z włączonym TalkBack (W13), **gdy** w trakcie `CARBS 1` w fazie czekania na prośbę o kod przesunięto fokus po krokach, **wtedy** TalkBack czyta kolejno: krok 1 z „wykonany”, krok 2 z „w toku”, kroki 3–5 z „oczekuje”, a każdy status jest czytany raz.

### CMDUI-18: Anulowanie

W przebiegu jest jeden przycisk anulowania, zależny od fazy (SESSION-17):

- Fazy przed kodem: „Anuluj polecenie” – od razu wywołuje `cancel()` aktywnej sesji (bez okna).
- Fazy po kodzie (`SENDING_CODE`, `AWAITING_CONFIRMATION`): „Przestań czekać” – najpierw okno „Przestać czekać?” z tekstem „Kod mógł już dotrzeć do AAPS. AAPS może wykonać polecenie mimo to. Wynik będzie nieznany – sprawdź w AAPS.” i przyciskami „Przestań czekać” i „Czekaj dalej”; dopiero potwierdzenie wywołuje `cancel()`.

`cancel()` jest wywoływane na `activeSession.value` w chwili akcji (ViewModel nie trzyma referencji do sesji w polu); brak aktywnej sesji → nic. Okno jest zamykane, gdy sesja się kończy. Wynik anulowania pokazuje CMDUI-19 (`Cancelled` albo `Unknown(CANCELLED_AFTER_CODE)` – zależnie od fazy w chwili przetworzenia przez sesję).

**Kryteria akceptacji:**

- **Zakładając** przebieg `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** wywołano `onCancelClicked()`, **wtedy** tryb anulowania = „Anuluj polecenie”, okno nie jest otwierane, a sesja kończy się `Cancelled`; gdy potem przychodzi `carbs-prosba-o-kod.txt`, `FakeSmsSender` nie dostaje kodu.
- **Zakładając** przebieg `CARBS 1` w `AWAITING_CONFIRMATION`, **gdy** wywołano `onCancelClicked()`, **wtedy** okno „Przestać czekać?” jest otwarte, a sesja nadal jest w `AWAITING_CONFIRMATION`; **gdy** wywołano „Czekaj dalej”, **wtedy** okno zamknięte, faza bez zmian; **gdy** ponownie otwarto okno i potwierdzono, **wtedy** wynik = `Unknown(CANCELLED_AFTER_CODE)`.
- **Zakładając** otwarte okno „Przestać czekać?”, **gdy** przyszedł `carbs-sukces.txt` (sesja zakończona), **wtedy** okno jest zamknięte, a potwierdzenie wywołane później nie zmienia wyniku `CarbsConfirmed`.
- **Zakładając** przebieg `BG` w `AWAITING_REPLY`, **gdy** wywołano `onCancelClicked()`, **wtedy** wynik = `Cancelled`.

### CMDUI-19: Wynik polecenia

Wynik ma rodzaj (do wyglądu: ikona, kolor), nagłówek, komunikat i opcjonalne dodatki. Odwzorowanie każdego `SessionOutcome` jest jedną czystą funkcją z wyczerpującym `when` (bez `else`):

| Wynik sesji | Rodzaj → nagłówek | Komunikat |
| --- | --- | --- |
| `Completed` (`BG`) | sukces → „Gotowe” | „Odpowiedź AAPS na BG” |
| `Completed` (`PUMP`) | sukces → „Gotowe” | „Odpowiedź AAPS na PUMP” |
| `Completed` (`CARBS`; nie występuje – Spec 004 kończy `CARBS` wynikiem `CarbsConfirmed`) | sukces → „Gotowe” | „Brak szczegółów wyniku.” |
| `CarbsConfirmed(X)` | sukces → „Gotowe” | „AAPS potwierdził wprowadzenie X g” |
| `Failed(SEND_FAILED)` | błąd → „Błąd” | „Nie udało się wysłać SMS. Sprawdź zasięg, kartę SIM i uprawnienia SMS.” |
| `Failed(UNRECOGNIZED_REPLY)` | błąd | „Nierozpoznana odpowiedź AAPS.” |
| `Failed(AMOUNT_MISMATCH)` | błąd | „AAPS podał inną ilość niż wysłana (np. obciętą przez limit w AAPS).” |
| `Failed(UNEXPECTED_REPLY)` | błąd | „Odpowiedź AAPS nie pasuje do etapu polecenia.” |
| `Failed(WRONG_CODE)` | błąd | „AAPS odrzucił kod. Sprawdź PIN i to, czy zegar telefonu ustawia się automatycznie.” |
| `Failed(CODE_REQUEST_EXPIRED)` | błąd | „Prośba AAPS o kod jest zbyt stara.” |
| `Failed(CODE_PREPARATION_TIMEOUT)` | błąd | „Nie udało się przygotować kodu na czas. Sprawdź zegar telefonu.” |
| `Failed(INTERNAL_ERROR)` | błąd | „Błąd wewnętrzny aplikacji.” |
| `TimedOut(AWAITING_REPLY \| AWAITING_CODE_REQUEST)` | brak odpowiedzi → „AAPS nie odpowiada” | „Brak odpowiedzi AAPS w ciągu 3 min.” |
| `TimedOut(SENDING_COMMAND)` | brak odpowiedzi | „Telefon nie potwierdził wysłania SMS w ciągu 3 min.” |
| `TimedOut` (inna faza) | brak odpowiedzi | „Brak odpowiedzi AAPS w ciągu 3 min.” |
| `Unknown(CONFIRMATION_TIMEOUT)` | nieznany → „Nieznany – sprawdź w AAPS” | „Kod przekazany do wysłania, ale AAPS nie potwierdził w ciągu 6 min.” |
| `Unknown(CANCELLED_AFTER_CODE)` | nieznany | „Przestano czekać po przekazaniu kodu do wysłania.” |
| `Unknown(CODE_SEND_FAILED)` | nieznany | „Wysyłanie kodu zgłosiło błąd, ale SMS mógł dotrzeć do AAPS.” |
| `Unknown(UNRECOGNIZED_REPLY)` | nieznany | „Po wysłaniu kodu przyszła nierozpoznana odpowiedź AAPS (np. o kolejce pompy).” |
| `Unknown(AMOUNT_MISMATCH)` | nieznany | „Po wysłaniu kodu AAPS podał inną ilość niż wysłana.” |
| `Unknown(UNEXPECTED_REPLY)` | nieznany | „Po wysłaniu kodu AAPS ponownie poprosił o kod. Kod nie został wysłany drugi raz.” |
| `Unknown(INTERNAL_ERROR)` | nieznany | „Błąd wewnętrzny aplikacji po wysłaniu kodu.” |
| `Cancelled` | anulowane → „Anulowano” | „Polecenie anulowane.” |

Dodatki:

- **„Kod nie został wysłany.”** – dla polecenia z kodem, gdy wynik to `Failed` z powodem innym niż `WRONG_CODE`, `TimedOut` albo `Cancelled`.
- **Ostrzeżenie** „Nie ponawiaj polecenia, zanim nie sprawdzisz w AAPS, czy zostało wykonane.” – dla każdego `Unknown`.
- Wynik sukcesu nie mówi, że wykonano polecenie z aplikacji, tylko co potwierdził AAPS (SESSION-12).

Liczby minut (3, 6) pochodzą z domyślnych `SessionTimeouts`, ilość X z `CarbsConfirmed.grams` (nie z polecenia). Argument liczbowy mają tylko komunikaty z X albo z minutami. Wiersz `Completed` dla `CARBS` nie twierdzi, że AAPS potwierdził ilość, i ma ten sam rodzaj (sukces) i ten sam rodzaj komunikatu („brak szczegółów”, bez argumentu) co wpis historii z tym wynikiem (Spec 010, HISTUI-04); historia dodaje do tekstu wyjaśnienie o wpisie z innej wersji aplikacji.

**Kryteria akceptacji:**

- **Zakładając** `SessionResult` dla każdego wiersza tabeli (wszystkie 8 `FailureReason`, wszystkie 7 `UnknownReason`, `TimedOut` z fazami `SENDING_COMMAND`, `AWAITING_REPLY`, `AWAITING_CODE_REQUEST`, `Completed` dla `BG`, `PUMP` i `CARBS 20`, `CarbsConfirmed(1)`, `Cancelled`), **gdy** odwzorowano go, **wtedy** rodzaj i komunikat odpowiadają wierszowi, a dla `CarbsConfirmed(1)` argument ilości = 1; dla `Completed` z `CARBS 20` – sukces, „brak szczegółów”, argument pusty, bez wyjątku.
- **Zakładając** polecenie `CARBS 1` i syntetyczny wynik `CarbsConfirmed(37)`, **gdy** odwzorowano, **wtedy** argument ilości = 37 (z wyniku, nie z polecenia).
- **Zakładając** każdy wynik z pierwszego kryterium, **gdy** odwzorowano, **wtedy** argument komunikatu jest niepusty dokładnie wtedy, gdy klucz komunikatu w tabeli tekstów design jest oznaczony jako tekst z argumentem (`d`).
- **Zakładając** polecenie `CARBS 20` i każdy z 8 `FailureReason`, `TimedOut` z fazami `SENDING_COMMAND`, `AWAITING_REPLY`, `AWAITING_CODE_REQUEST` oraz `Cancelled`, **gdy** odwzorowano, **wtedy** dodatek „Kod nie został wysłany” = `true` dla wszystkich poza `Failed(WRONG_CODE)` (= `false`); dla każdego z 7 `UnknownReason`, dla `CarbsConfirmed` i dla `Completed` = `false`; dla `BG` z każdym wynikiem z pierwszego kryterium = `false`.
- **Zakładając** każdy `UnknownReason`, **gdy** odwzorowano, **wtedy** ostrzeżenie = `true`; dla wyników innych niż `Unknown` = `false`.
- **Zakładając** telefon z AAPS (W4, W5), **gdy** `BG` i `CARBS 1` zakończyły się, **wtedy** widać nagłówek „Gotowe” i komunikaty z tabeli.

### CMDUI-20: Treść odpowiedzi AAPS i wiadomości nieoczekiwane

Wynik pokazuje treść SMS z AAPS pod etykietą „Treść SMS z AAPS:”, gdy wynik ją niesie: `Completed.text`, `CarbsConfirmed.text`, `Failed.text` i `Unknown.text` (gdy nie `null`). Przebieg i wynik pokazują liczbę wiadomości nieoczekiwanych (`SessionState.unexpectedMessages.size` / `SessionResult.unexpectedMessages.size`) jako „Wiadomości od AAPS niezwiązane z tym poleceniem: N (np. potwierdzenie polecenia wysłanego z innego telefonu).”, tylko gdy N > 0; treść wiadomości nieoczekiwanych nie jest pokazywana (decyzja użytkownika, P9).

**Kryteria akceptacji:**

- **Zakładając** wynik `Completed` po `bg.txt`, **gdy** odwzorowano, **wtedy** treść = zawartość `bg.txt`; dla `Failed(UNRECOGNIZED_REPLY)` z treścią `Czesc, kupisz chleb?` – ta treść; dla `Failed(WRONG_CODE)` z treścią `zly-kod.txt` i `Unknown(UNRECOGNIZED_REPLY)` z treścią `Czesc, kupisz chleb?` – te treści; dla `TimedOut`, `Cancelled`, `Failed(SEND_FAILED)` i `Unknown(CONFIRMATION_TIMEOUT)` – brak.
- **Zakładając** przebieg `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** w chwilach 10 000 i 20 000 przyszedł `carbs-sukces.txt`, **wtedy** liczba nieoczekiwanych w przebiegu = 2; po `TimedOut` – w wyniku = 2.
- **Zakładając** stan przebiegu i wyniku, **gdy** sprawdzono typy pól refleksją, **wtedy** nie ma listy `IncomingSms` (tylko liczba).

### CMDUI-21: Zamknięcie wyniku i brak referencji do sesji

Wynik zostaje na ekranie, aż użytkownik dotknie „Zamknij” (albo Wstecz, CMDUI-24); wtedy zakładka wraca do panelu poleceń. Stan ekranu `CommandRunViewModel` (`RunUiState`) po fazie `FINISHED` zawiera tylko dane wyniku (polecenie, rodzaj, komunikat, treść, liczby) – bez `StateFlow`, bez `CommandSession` i bez `CodeCredentials` (sesja trzyma `CodeCredentials` z sekretem). `CommandRunViewModel` nie ma pola typu `CommandSession`. `CurrentRun` (singleton) trzyma polecenie i `StateFlow<SessionState>` sesji aż do zamknięcia wyniku; jest to dozwolone, bo ten `StateFlow` nie wskazuje na `CommandSession` ani na `CodeCredentials` (w Spec 004 `CommandSession.state` to samodzielny `MutableStateFlow` ze stanem). Zamknięcie wyniku czyści `CurrentRun`. Wynik jest pokazywany, dopóki żyje proces aplikacji i użytkownik go nie zamknął – także po „Wyjdź” (CMDUI-24) i ponownym otwarciu aplikacji; po nowym uruchomieniu procesu (np. `Activity` zamknięte i proces zabity) nie jest pokazywany – jest tylko w Historii (spec 010; decyzja użytkownika, P7). Gdy wynik jest pokazany, panel poleceń jest niewidoczny, więc nowe polecenie można uruchomić dopiero po zamknięciu wyniku.

**Kryteria akceptacji:**

- **Zakładając** wynik `Completed` na ekranie, **gdy** wywołano `onResultDismissed()`, **wtedy** stan `CommandRunViewModel` = brak przebiegu i wyniku, a `CurrentRun.state.value == null`.
- **Zakładając** przebieg, **gdy** wywołano `onResultDismissed()`, **wtedy** stan bez zmian (przebiegu nie da się zamknąć bez anulowania).
- **Zakładając** sesję `BG` uruchomioną przez launcher i zakończoną `Completed` (wynik na ekranie), **gdy** sprawdzono refleksją pola `CommandRunViewModel` oraz pola jego stanu i `RunView` (rekurencyjnie po typach z `ui.commands`), **wtedy** żadne pole nie ma typu `CommandSession`, `CodeCredentials`, `TotpSecret` ani `AapsPin`, pola stanu i `RunView` nie mają typu `StateFlow` (ani `Flow`), a żadne pole stanu nie jest referencją do tej `CommandSession` ani jej `state`.
- **Zakładając** ten sam wynik, **gdy** wywołano `onResultDismissed()` i sprawdzono refleksją pola `CommandRunViewModel`, jego stanu i `CurrentRun` (jeden poziom w głąb), **wtedy** żadne nie jest referencją do tej `CommandSession` ani jej `state`.

### CMDUI-22: Pierwszy plan i ekran włączony

Gdy polecenie jest w toku (`activeSession.value != null`), okno `Activity` ma włączone utrzymywanie ekranu (`keepScreenOn` widoku głównego albo `FLAG_KEEP_SCREEN_ON`) – niezależnie od wybranej zakładki; po zakończeniu polecenia (także wynikiem) utrzymywanie jest wyłączane. Przebieg pokazuje tekst „Nie wychodź z aplikacji i nie blokuj telefonu do końca polecenia. Ekran pozostanie włączony.” Aplikacja nie uruchamia usługi pierwszoplanowej i nie blokuje wyjścia (Wstecz – CMDUI-24).

**Kryteria akceptacji:**

- **Zakładając** `CommandRunViewModel` bez uruchomienia, **gdy** uruchomiono `BG` przez launcher (`activeSession`: `null` → sesja `BG`), a potem przyszedł `bg.txt` (`activeSession` → `null`), **wtedy** znacznik „ekran włączony” (z `activeSession`) ma kolejno wartości `false`, `true`, `false`, a po zakończeniu stan = wynik (z `CurrentRun`).
- **Zakładając** telefon z czasem wygaszania ekranu 15 s (W9), **gdy** uruchomiono `CARBS 1` i czekano na prośbę o kod dłużej niż 15 s, **wtedy** ekran się nie wygasza, także po przełączeniu na zakładkę Ustawienia; po wyniku ekran wygasa po 15 s.

### CMDUI-23: Ochrona ekranu (okno „Na pewno”, podsumowanie, przebieg, wynik)

Okno `Activity` ma `FLAG_SECURE` (brak zrzutów ekranu i podglądu w ostatnich aplikacjach), zgodnie z planem („FLAG_SECURE na ekranach z danymi”), gdy:

- na zakładce Polecenia otwarte jest okno „Na pewno X g?” (CMDUI-09; decyzja użytkownika, P11; dialog ma dodatkowo własne `securePolicy = SecureOn`) albo podsumowanie z polem PIN-u, lub
- trwa uruchamianie (od „Wyślij”, `BG` albo `PUMP` do wyniku `CommandLauncher.launch`, w tym odczyt sekretu) – także wtedy, gdy w tym czasie `onStop()` zamknął podsumowanie, lub
- istnieje przebieg albo niezamknięty wynik polecenia – od `LaunchResult.Started` do zamknięcia wyniku (CMDUI-21) – **niezależnie od wybranej zakładki** (Polecenia, Ustawienia, Historia; decyzja użytkownika, P11). Ochrona wynika ze stanu uruchomienia (`CurrentRun` w `CommandRunViewModel` i `runHandoff` w `CommandsViewModel`, oba w zakresie `Activity`), a nie z tego, że ekran zakładki Polecenia jest w kompozycji.

Po zamknięciu okna „Na pewno” („Nie”), podsumowania albo wyniku flaga jest zdejmowana, chyba że chroni ją też inny ekran (UI-23 – skaner albo okno wklejania; HISTUI-13 – zakładka Historia). Flaga jest zdejmowana dopiero, gdy żaden ekran ani przebieg jej nie wymaga (licznik żądań). Panel Polecenia bez okna „Na pewno”, bez podsumowania i bez przebiegu albo wyniku nie ma `FLAG_SECURE` (decyzja użytkownika, P3, zmieniona po recenzji). Skutek: w trakcie polecenia także zakładka Ustawienia nie pozwala na zrzut ekranu.

Okno ma flagę, gdy znacznik „ekran chroniony” jest włączony w `CommandsViewModel` albo w `CommandRunViewModel`, a żądanie ochrony z tych znaczników zgłasza kompozycja wspólna dla wszystkich zakładek (poza zawartością zakładki Polecenia):

- `CommandRunViewModel`: znacznik = przebieg albo wynik jest w stanie ekranu (`view != null`). Ekran przebiegu i wyniku jest pokazywany z tego samego pola, więc nigdy bez flagi.
- `CommandsViewModel`: znacznik = okno „Na pewno” otwarte, podsumowanie otwarte, trwa uruchamianie (`launching`) albo trwa przekazanie do przebiegu. `launching` gaśnie w tej samej zmianie stanu, w której przy `Started` włącza się `runHandoff`, więc między „Wyślij” a przebiegiem nie ma chwili bez znacznika, nawet gdy `onStop()` zamknie podsumowanie w trakcie `load()`.

Zmiana zakładki woła `onStop()` przy opuszczeniu kompozycji przez `CommandsScreen` (CMDUI-11), ale nie ma gwarancji, że `ON_STOP` cyklu życia wtedy dotrze (jak w Spec 008, UI-14), a `onStop()` nie zmienia `launching` ani `runHandoff`. Ochrona przebiegu i wyniku nie zależy od `onStop()` ani od obecności `CommandsScreen` w kompozycji. Po `LaunchResult.Started` ViewModel w jednej zmianie stanu zamyka podsumowanie i włącza znacznik przekazania (`runHandoff`) – dla każdego polecenia. Znacznik przekazania wyłącza dopiero kolektor `CurrentRun` w `CommandsViewModel`, gdy zobaczy pusty `CurrentRun` (po zamknięciu wyniku, CMDUI-21). `CurrentRun` jest publikowany przed powrotem z `launch` (CMDUI-16), więc w chwili włączenia znacznika nie jest pusty. Przy `Rejected` znacznik przekazania nie jest włączany.

Dzięki temu flaga nie spada między zamknięciem podsumowania a pokazaniem przebiegu, niezależnie od kolejności wykonania kolektorów obu ViewModeli; po zamknięciu wyniku może zostać chwilę dłużej (opóźnienie działa w bezpieczną stronę). Po ponownym otwarciu `Activity` z niezamkniętym wynikiem (P7) flaga włącza się razem z pokazaniem wyniku; wcześniej widoczny jest tylko panel, który nie wymaga ochrony (P3).

**Kryteria akceptacji:**

- **Zakładając** `CommandsViewModel` ze wspólnym `CurrentRun` i launcherem, **gdy** otwarto podsumowanie, zamknięto je „Anuluj”, otwarto ponownie, wywołano „Wyślij” (start udany), a potem wywołano `currentRun.clear()` i wykonano zadania dyspozytora, **wtedy** znacznik „ekran chroniony” ma kolejno wartości `true`, `false`, `true`, `true` (podsumowanie zamknięte, przekazanie), `false`; żadna emisja `uiState` (Turbine) między „Wyślij” a `clear()` nie ma znacznika `false`, a pierwsza emisja z zamkniętym podsumowaniem po „Wyślij” ma znacznik `true`.
- **Zakładając** podsumowanie z PIN-em `4321` i `load()` zwracający `Absent`, **gdy** wywołano „Wyślij”, **wtedy** po zamknięciu podsumowania znacznik „ekran chroniony” = `false` (bez przekazania).
- **Zakładając** `CommandRunViewModel` bez uruchomienia, **gdy** uruchomiono `BG` przez launcher, przyszedł `bg.txt`, a potem wynik zamknięto, **wtedy** znacznik „ekran chroniony” ma kolejno wartości `false`, `true`, `true` (wynik widoczny), `false` (po zamknięciu wyniku), a w każdym stanie jest równy `view != null`.
- **Zakładając** `CommandsViewModel` i `CommandRunViewModel` ze wspólnym `CurrentRun` i podsumowanie dla 1 g z PIN-em `4321`, **gdy** wywołano „Wyślij” (start udany), w chwili 60 000 przyszedł `carbs-prosba-o-kod.txt`, w chwili 120 000 `carbs-sukces.txt`, a potem wywołano `onResultDismissed()`, i od „Wyślij” do `onResultDismissed()` po każdym kroku dyspozytora (`runCurrent()`, `advanceTimeBy`, także przed pierwszym wykonaniem kolektora `CommandRunViewModel`) odczytano `commands.uiState.value.secureWindow || run.uiState.value.secureWindow`, **wtedy** każda odczytana wartość = `true`; po `onResultDismissed()` i wykonaniu zadań = `false`.
- **Zakładając** `CommandsViewModel` ze wspólnym `CurrentRun` bez żadnego `CommandRunViewModel` (żaden kolektor nie ustawia `view`), **gdy** wywołano „Wyślij” (start udany) i wykonano wszystkie zadania dyspozytora, **wtedy** znacznik „ekran chroniony” `CommandsViewModel` = `true`, dopóki `CurrentRun` nie jest pusty.
- **Zakładając** licznik ochrony okna, **gdy** ochronę włączyły dwa źródła, a wyłączyło jedno, **wtedy** flaga jest nadal włączona; po wyłączeniu przez drugie – wyłączona.
- **Zakładając** `CommandsViewModel` i `CommandRunViewModel` ze wspólnym `CurrentRun` i przebieg `BG` uruchomiony przez launcher, **gdy** wywołano `onStop()` w `CommandsViewModel` (przejście w tło) i wykonano zadania dyspozytora, a potem przyszedł `bg.txt`, **wtedy** `commands.uiState.value.secureWindow || run.uiState.value.secureWindow` = `true` po każdym kroku aż do `onResultDismissed()`, a po nim i wykonaniu zadań = `false`.
- **Zakładając** podsumowanie dla 1 g z PIN-em `4321` i `TotpSecretProvider.load`, który zawiesza się do zwolnienia przez test, **gdy** wywołano „Wyślij”, potem `onStop()` (podsumowanie zamknięte), a potem zwolniono `load()` z wynikiem `Loaded`, **wtedy** znacznik „ekran chroniony” `CommandsViewModel` = `true` w każdej emisji `uiState` (Turbine) od „Wyślij” do `Started` i po nim (`runHandoff`); **gdy** zamiast tego `load()` zwrócił `Absent`, **wtedy** znacznik = `true` do zwolnienia, a po odmowie = `false`.
- **Zakładając** źródła `ui/navigation/AppScaffold.kt`, `ui/commands/CommandsScreen.kt`, `RunScreen.kt` i `ResultCard.kt`, **gdy** test JVM je przeczytał, **wtedy** `AppScaffold.kt` zawiera wywołanie `SecureWindowEffect(` z wyrażeniem zawierającym `secureWindow` stanu obu ViewModeli, a pozostałe trzy pliki nie zawierają `SecureWindowEffect(`.
- **Zakładając** otwarte podsumowanie na telefonie (W10), **gdy** wykonano zrzut ekranu i otwarto listę ostatnich aplikacji, **wtedy** zrzut się nie wykonuje, a podgląd jest pusty; po zamknięciu podsumowania zrzut działa.
- **Zakładając** przebieg `CARBS 1` na telefonie (W10), **gdy** przełączono na zakładkę Ustawienia, potem Historia, potem znów Ustawienia i za każdym razem wykonano zrzut ekranu, **wtedy** żaden zrzut się nie wykonuje; po wyniku i jego zamknięciu zrzut na zakładce Ustawienia (bez skanera) działa.
- **Zakładając** okno „Na pewno 45 g?” na telefonie (W10), **gdy** wykonano zrzut ekranu, **wtedy** zrzut się nie wykonuje; po „Nie” zrzut panelu działa.
- **Zakładając** podsumowanie `CARBS 1` na telefonie (W10), **gdy** dotknięto „Wyślij” i od razu przycisk ekranu głównego, a po ok. 10 s otwarto listę ostatnich aplikacji i wrócono do aplikacji, **wtedy** podgląd w ostatnich aplikacjach jest pusty, a zrzut wykonany zaraz po powrocie się nie wykonuje.

### CMDUI-24: Przycisk Wstecz na zakładce Polecenia

Na zakładce Polecenia przycisk Wstecz:

| Sytuacja | Działanie |
| --- | --- |
| okno „Na pewno” otwarte | zamyka okno („Nie”) |
| podsumowanie otwarte | „Anuluj” podsumowania (CMDUI-10) |
| wynik na ekranie | „Zamknij” (CMDUI-21) |
| polecenie w toku | okno „Trwa polecenie” z tekstem „Wyjście z aplikacji może przerwać odbiór odpowiedzi AAPS. Polecenie nie zostanie anulowane.” i przyciskami „Zostań” i „Wyjdź”; „Wyjdź” zamyka `Activity` (sesja trwa dalej w zakresie aplikacji, CMDUI-13; `CurrentRun` nie jest czyszczony), „Zostań” zamyka okno |
| żadne z powyższych | jak UI-02 (zamyka aplikację) |

(Okno „Trwa polecenie” – decyzja użytkownika, P8.) Po „Wyjdź” i ponownym otwarciu aplikacji, gdy proces żyje, zakładka Polecenia pokazuje przebieg albo wynik z `CurrentRun` (P7, CMDUI-16, CMDUI-21); panel – dopiero po „Zamknij”. Panel bez wyniku widać po ponownym otwarciu tylko wtedy, gdy proces został w międzyczasie zabity (wynik jest wtedy tylko w Historii, Spec 010, W8).

**Kryteria akceptacji:**

- **Zakładając** otwarte podsumowanie z PIN-em `4321`, **gdy** wywołano `onBack()` w `CommandsViewModel`, **wtedy** podsumowanie zamknięte, tekst PIN-u = `""`.
- **Zakładając** wynik na ekranie, **gdy** wywołano `onBack()` w `CommandRunViewModel`, **wtedy** stan = brak wyniku.
- **Zakładając** przebieg `BG` uruchomiony przez launcher, **gdy** wywołano `onBack()`, **wtedy** okno „Trwa polecenie” jest otwarte, a sesja nadal aktywna; **gdy** wywołano „Zostań”, **wtedy** okno zamknięte; **gdy** wywołano „Wyjdź”, **wtedy** stan zawiera żądanie zamknięcia `Activity`, `cancel()` sesji nie był wywołany, a `CurrentRun` nadal zawiera uruchomienie.
- **Zakładając** przebieg `BG` po „Wyjdź” i wyczyszczeniu `CommandRunViewModel` (`ViewModelStore.clear()`), **gdy** przyszedł `bg.txt` i utworzono nowy `CommandRunViewModel` z tym samym `CurrentRun`, **wtedy** stan = wynik `Completed` polecenia `BG`; **gdy** wywołano `onResultDismissed()`, **wtedy** stan = brak przebiegu i wyniku.
- **Zakładając** panel bez okien, przebiegu i wyniku, **gdy** odczytano stan `CommandsViewModel` (osobno `CommandRunViewModel`), **wtedy** przechwytywanie Wstecz jest w nim wyłączone.
- **Zakładając** telefon (W11), **gdy** w trakcie `BG` dotknięto Wstecz i wybrano „Wyjdź”, a po odpowiedzi AAPS otwarto aplikację (bez zabijania procesu), **wtedy** zakładka Polecenia pokazuje wynik polecenia `BG`; po „Zamknij” – panel; Historia (010) zawiera wpis polecenia.

### Reguły wspólne

### CMDUI-25: Teksty po polsku w zasobach

Wszystkie teksty widoczne w UI z tej specyfikacji (panel, banery, błędy pola ilości, okna, podsumowanie, komunikaty, kroki, wyniki, dodatki) są w `app/src/main/res/values/strings.xml`, po polsku, pod kluczami z tabeli tekstów w design; treści podane w tej spec są wiążące (drobne zmiany redakcyjne w recenzji PR nie wymagają zmiany spec). ViewModele i odwzorowania zwracają wartości wyliczeniowe i liczby, nie teksty; odwzorowanie wartość → zasób to jedna funkcja na typ. Kod Compose nie zawiera literałów tekstowych widocznych dla użytkownika. Teksty wspólne ze Spec 008 (`msg_command_active`, `sms_denied`, `secret_msg_locked`, `action_cancel`) są używane ponownie, nie kopiowane.

**Kryteria akceptacji:**

- **Zakładając** każde wyliczenie tekstów (`CommandMessage`, `ResultMessage`, `ResultKind`, `ProgressStepId`, `CarbsAmountError`, gotowość numeru i sekretu), **gdy** test JVM odwzorował każdą wartość na zasób, **wtedy** każda wartość z tekstem ma niezerowy identyfikator, identyfikatory w obrębie typu są różne, a wartości bez tekstu (np. gotowość „numer ustawiony” – brak banera) dają `null`.
- **Zakładając** `strings.xml`, **gdy** test JVM go przeczytał, **wtedy** zawiera niepusty tekst dla każdego klucza z tabeli tekstów w design, a każdy klucz oznaczony w tabeli jako tekst z argumentem zawiera `%1$d` (z dwoma argumentami – także `%2$d`).
- **Zakładając** ekrany na telefonie (W1–W12), **gdy** przejrzano wszystkie stany, **wtedy** każdy tekst jest po polsku.

### CMDUI-26: Brak ujawniania danych

Pakiet `ui` nie wywołuje API logowania (reguła UI-21 obejmuje też `ui.commands`). `toString()` stanów `CommandsViewModel` i `CommandRunViewModel` oraz typów wyniku nie zawiera tekstu PIN-u, tekstu pola ilości, ilości gramów ani treści SMS (stały tekst `***` w miejscu wartości). PIN, kod TOTP, sekret i treść SMS nie trafiają do komunikatów ani wyjątków.

**Kryteria akceptacji:**

- **Zakładając** źródła `ui/commands/` i `ui/platform/`, **gdy** test JVM je przeczytał, **wtedy** nie zawierają wywołań API logowania (wzorce jak w `UiNoLoggingTest`).
- **Zakładając** stan `CommandsViewModel` z tekstem PIN-u `4321`, tekstem pola ilości `"37"` i podsumowaniem dla 37 g, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `4321` ani `37`.
- **Zakładając** stan `CommandRunViewModel` z wynikiem `Completed` po `bg.txt` i (osobno) `CarbsConfirmed(37)`, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `210`, `37` ani żadnej linii treści.

### CMDUI-27: Zakres uruchamiania sesji w Hilt

Moduł Hilt `ui.platform.CommandsModule` (`SingletonComponent`) dostarcza dokładnie jeden `CoroutineScope` z kwalifikatorem `@CommandRunScope` (`CoroutineScope(SupervisorJob() + Dispatchers.Default)`, `@Singleton`). `ui.commands.CurrentRun` jest singletonem Hilt (`@Singleton` + `@Inject constructor()`, bez metody w module). `CommandLauncher` wstrzykuje ten zakres, `CurrentRun` oraz istniejące wiązania: `SessionCoordinator` (UI-19), `AapsNumberSettingsStore`, `SmsPermissions`, `TotpSecretProvider`, `SessionHistoryRecorder`. `CommandsViewModel` i `CommandRunViewModel` wstrzykują ten sam `CurrentRun` (CMDUI-16, CMDUI-23). Pakiety `session`, `data`, `security`, `sms` nie zmieniają się.

**Kryteria akceptacji:**

- **Zakładając** klasę `CommandsModule`, **gdy** test JVM sprawdził ją refleksją, **wtedy** ma dokładnie jedną metodę dostarczającą `CoroutineScope` z adnotacjami jak w CMDUI-13.
- **Zakładając** klasę `CurrentRun`, **gdy** test JVM sprawdził ją refleksją, **wtedy** ma adnotację `@Singleton` i jedyny konstruktor bez parametrów z `@Inject`; konstruktory `CommandLauncher`, `CommandsViewModel` i `CommandRunViewModel` mają parametr typu `CurrentRun`.
- **Zakładając** zbudowaną aplikację (`assembleDebug`), **gdy** Hilt wygenerował graf, **wtedy** `CommandsViewModel`, `CommandRunViewModel`, `AapsNumberViewModel` i `TotpSecretViewModel` dostają ten sam `SessionCoordinator`, a `CommandLauncher`, `CommandsViewModel` i `CommandRunViewModel` – ten sam `CurrentRun`.

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Numer AAPS wczytuje się / brak / błąd wczytania | przyciski nieaktywne, baner, przejście do Ustawień | CMDUI-02, CMDUI-12 |
| Brak uprawnień SMS | przyciski nieaktywne, sekcja uprawnień ze Spec 008 | CMDUI-03, CMDUI-12 |
| Brak / unieważniony sekret (stan magazynu) | `CARBS` nieaktywne, baner | CMDUI-04 |
| `load()`: `Absent`, `Invalidated`, `Locked`, `Unavailable` | komunikat, PIN wyczyszczony, brak startu, podsumowanie zamknięte | CMDUI-12 |
| Ilość pusta, z innymi znakami, < 1, > 50 | błąd pod polem, brak podsumowania | CMDUI-07 |
| Ilość 41–50 | okno „Na pewno X g?” przed PIN-em | CMDUI-09 |
| PIN z innym znakiem niż cyfra / dłuższy niż 12 | znak odrzucony | CMDUI-10 |
| Podwójne dotknięcie / dotknięcie w trakcie uruchamiania | jedno polecenie | CMDUI-05 |
| Polecenie w toku (także wyścig ze startem) | komunikat „Trwa polecenie”, PIN wyczyszczony | CMDUI-05, CMDUI-13 |
| Niepoprawny zestaw polecenie/PIN | `START_FAILED`, PIN wyczyszczony, bez wyjątku | CMDUI-13 |
| Anulowanie korutyny `launch` (np. zamknięcie ViewModelu w trakcie odczytu sekretu) | PIN wyczyszczony, brak startu | CMDUI-12 |
| Przejście w tło / obrót / zmiana zakładki z otwartym podsumowaniem albo oknem „Na pewno” | PIN wyczyszczony, podsumowanie i okno zamknięte; trwający przebieg i wynik bez zmian | CMDUI-11 |
| Zamknięcie `Activity` w trakcie polecenia | sesja trwa dalej, wynik w Historii; po ponownym otwarciu przy żyjącym procesie – przebieg albo wynik na zakładce Polecenia (do „Zamknij”), po zabiciu procesu – panel | CMDUI-13, CMDUI-16, CMDUI-21, CMDUI-24 |
| Anulowanie po wysłaniu kodu | okno potwierdzenia, wynik „nieznany” | CMDUI-18, CMDUI-19 |
| Każdy wynik sesji (w tym błąd wysyłania, brak odpowiedzi, zły kod) | komunikat z tabeli, treść SMS, dodatki | CMDUI-19, CMDUI-20 |

Ograniczenie: `onDispose` odpala się dopiero po animacji wyjścia z `NavHost`, więc PIN i podsumowanie (oraz związany z nimi `FLAG_SECURE`) zostają jeszcze na krótko po dotknięciu zakładki.

## Bezpieczeństwo

Zasady z [AGENTS.md](../../../AGENTS.md#bezpieczeństwo) i sposób ich spełnienia:

- **Nigdy nie logujemy sekretu, kodów ani PIN-u.** `ui` nie wywołuje API logowania; `toString()` stanów nie zawiera PIN-u, ilości ani treści SMS (CMDUI-26). Komunikaty są stałymi wartościami wyliczeniowymi (CMDUI-12, CMDUI-25).
- **PIN AAPS nigdy nie jest zapisywany.** Tekst PIN-u jest tylko w stanie ViewModelu w pamięci, bez `SavedStateHandle` i `rememberSaveable`, więc nie trafia do stanu instancji zapisywanego przez system; jest czyszczony przy „Wyślij” (przed odczytem sekretu), „Anuluj”, Wstecz, `ON_STOP` i opuszczenie zakładki Polecenia (CMDUI-11). `AapsPin` powstaje z tablicy, którą `fromChars` zeruje; na każdej ścieżce bez startu czyści go `CommandLauncher` albo koordynator, po starcie – sesja (CMDUI-12, CMDUI-13, SESSION-19). Pole jest maskowane, z klawiaturą typu hasło (CMDUI-10), a okno „Na pewno X g?”, podsumowanie oraz przebieg i wynik (na każdej zakładce, od startu do zamknięcia wyniku) mają `FLAG_SECURE` (CMDUI-23). Ograniczenie: `String` tekstu pola nie da się wyzerować w JVM. Wyjątek poza kontrolą aplikacji: system zapisuje wysłany SMS `<kod><PIN>` w skrzynce wysłanych (Spec 005, W7 ze Spec 005 w W5 tej spec).
- **Sekret tylko do zbudowania `CodeCredentials`.** Odczyt tuż przed startem polecenia z kodem, po wszystkich innych sprawdzeniach; sekret jest zmienną lokalną `launch`, `CommandLauncher` i ViewModele nie mają pól z sekretem (CMDUI-12, CMDUI-15, SECRET-14). `CommandRunViewModel` i jego stan nie trzymają referencji do sesji, która przechowuje `CodeCredentials`; `CurrentRun` trzyma do zamknięcia wyniku tylko polecenie i `StateFlow` stanu sesji, który nie wskazuje na sesję ani na `CodeCredentials` (CMDUI-21). Ograniczenie: `TotpSecret` nie ma czyszczenia („Przyszłe zmiany” w Spec 006).
- **Kod tylko po zgodnej odpowiedzi AAPS** – decyduje `session` (Spec 004); `ui` nie ma ścieżki wysłania kodu. Ilość jest zatwierdzana przez użytkownika dwa razy dla > 40 g (CMDUI-09) i widoczna w podsumowaniu (CMDUI-10).
- **Bez automatycznego ponawiania.** `ui` nie ponawia startu ani polecenia po żadnym wyniku (CMDUI-13); wynik „nieznany” ma ostrzeżenie przed ponowieniem bez sprawdzenia AAPS (CMDUI-19), bo powtórzone `CARBS` mogłoby wprowadzić węglowodany dwa razy.
- **Potwierdzenie CARBS nie dowodzi wykonania polecenia z aplikacji** (SESSION-12): komunikat „AAPS potwierdził wprowadzenie X g” (CMDUI-19).
- **Odbiór tylko na pierwszym planie** (Spec 005, P2): ekran włączony w trakcie polecenia, tekst zachęcający, okno przy Wstecz (CMDUI-22, CMDUI-24). Ryzyko szczątkowe: użytkownik wyjdzie albo zablokuje telefon – odpowiedź może nie dotrzeć, sesja zakończy się limitem czasu (`TimedOut` / `Unknown(CONFIRMATION_TIMEOUT)`), nigdy wysłaniem kodu bez zgodnej odpowiedzi.
- **Brak prawdziwych danych:** numer fikcyjny Ofcom, sekret RFC 6238, PIN fikcyjny `4321`, wiadomości AAPS z fikcyjnym imieniem `Jan`.

## Dane testowe

- Numer fikcyjny: `+44 7700 900123` (`+447700900123`), jak w Spec 005, 007 i 008.
- Sekret RFC 6238 (`12345678901234567890`), `T0 = 1111111051000`, kod `050471` w oknie wektora `1111111111` (Spec 004, dane kryteriów SESSION-09), odpowiedź z kodem `0504714321`.
- PIN fikcyjny: `4321`; do testów długości: `123456789012`, `1234567890123`.
- Wiadomości AAPS: `bg.txt`, `pump.txt`, `carbs-prosba-o-kod.txt` (1 g), `carbs-sukces.txt` (1 g), `zly-kod.txt` z [`../002-parser-odpowiedzi/dane/`](../002-parser-odpowiedzi/dane/README.md) (kopie testowe w `app/src/test/resources/parser/aaps-3.4.2.6-pl/`), syntetyczne `Czesc, kupisz chleb?` i `Pomyslnie wprowadzono 37 g weglowodanow` (Spec 004).
- Atrapy: `FakeSmsSender`, `FakeIncomingSmsSource` (Spec 004), `FakeSmsPermissions` (Spec 005), `FakeAapsNumberSettingsStore`, `FakeTotpSecretStore`, `SessionDoubles` (Spec 008), `FakeCommandHistory` (Spec 007).
- Do weryfikacji na telefonie: AAPS z wirtualną pompą (zalecane) albo – za zgodą użytkownika – `CARBS 1`; nigdy prawdziwy PIN, numer ani sekret w repo i opisie PR.
- Brak plików w `dane/`.

## Weryfikacja na urządzeniu

Kompozycje, okna, `FLAG_SECURE`, utrzymanie ekranu, nawigacja i Hilt nie mają testów automatycznych (decyzja użytkownika). Wyniki (telefon, wersja Androida, wersja AAPS, wynik każdego punktu) opisuje PR zadania, które wprowadza dany ekran (design, „Podział na zadania”). Wynik niezgodny ze spec = zmiana spec, nie obejście w kodzie.

| Nr | Co sprawdzić | Wymagania |
| --- | --- | --- |
| W1 | Zakładka Polecenia: panel z `BG`, `PUMP`, `CARBS`; przełączanie zakładek zachowuje tekst pola ilości; teksty po polsku | CMDUI-01, CMDUI-25 |
| W2 | Zimny start z zapisanym numerem: przyciski nieaktywne do końca wczytania (W7 ze Spec 007); usunięcie numeru w Ustawieniach → baner i „Przejdź do Ustawień”; brak sekretu → baner `CARBS`, `BG` działa | CMDUI-02, CMDUI-04 |
| W3 | Odebranie uprawnień SMS w ustawieniach systemu → sekcja uprawnień na zakładce Polecenia, przyciski nieaktywne; „Zezwól” i powrót → przyciski aktywne | CMDUI-03 |
| W4 | `BG` i `PUMP` z prawdziwym AAPS na API 28 i najnowszym dostępnym: SMS z domyślnej karty (W1 ze Spec 005), wynik wysłania (W2), odbiór (W3), długa odpowiedź `PUMP` w całości (W4); numer po zabiciu procesu (W1 ze Spec 007, część `BG`); dual SIM bez domyślnej karty – zanotować wynik (W5 ze Spec 005) | CMDUI-06, CMDUI-16, CMDUI-19, CMDUI-20 |
| W5 | `CARBS 1` (wirtualna pompa albo za zgodą użytkownika): pole, podsumowanie, PIN, kroki na żywo, wynik „AAPS potwierdził wprowadzenie 1 g”; kod zgodny z Authenticatorem (W1 ze Spec 006, odczyt); skrzynka wysłanych zawiera `<kod><PIN>` – zanotować (W7 ze Spec 005); zły PIN → „AAPS odrzucił kod” | CMDUI-07, CMDUI-10, CMDUI-12, CMDUI-13, CMDUI-17, CMDUI-19 |
| W6 | PIN: maskowanie, klawiatura numeryczna bez podpowiedzi (Gboard); przejście do innej aplikacji z otwartym podsumowaniem i powrót → podsumowanie zamknięte, pole puste; to samo po przejściu z otwartego podsumowania (PIN wpisany) na Historię i na Ustawienia i powrocie na Polecenia; w trakcie `CARBS 1` przełączenie na Historię i powrót → ten sam przebieg (bez anulowania), zrzut ekranu nadal zablokowany; szybka wartość 20 g → podsumowanie dla 20 g; 45 g → „Na pewno 45 g?”, „Nie” → nic nie wysłane | CMDUI-08, CMDUI-09, CMDUI-10, CMDUI-11 |
| W7 | Sekret przy starcie `CARBS`: zmiana blokady ekranu i `CARBS` działa (W3 ze Spec 006); wyłączenie blokady ekranu → zanotować wynik (`Loaded` albo komunikat „zeskanuj ponownie”, W4 ze Spec 006); zabicie procesu zaraz po zamianie sekretu (kroki: zamienić sekret w Ustawieniach i od razu zabić proces – `adb shell am kill` albo wymuszone zatrzymanie; uruchomić aplikację ponownie i wykonać `CARBS 1` z poprawnym PIN-em) → polecenie startuje, a kod zgadza się ze starym albo nowym sekretem (W9 ze Spec 006); błąd (niezaliczone): komunikat „zeskanuj ponownie” (`Invalidated`) albo awaria aplikacji; Smart Lock → nigdy „zeskanuj ponownie” (W10 ze Spec 006); W2 ze Spec 006 nie jest sprawdzane w 009 (P4) | CMDUI-12 |
| W8 | W trakcie `CARBS`: przełączenie zakładek i obrót → ten sam przebieg; w Ustawieniach baner „Trwa polecenie” i nieaktywne „Zmień”/„Usuń”/„Skanuj QR”/„Wklej URI” (ręczne sprawdzenie UI-15 ze Spec 008); „Anuluj polecenie” przed prośbą o kod → „Anulowano”, późniejsza prośba AAPS bez kodu; „Przestań czekać” po kodzie → okno, potwierdzenie → „Nieznany – sprawdź w AAPS” | CMDUI-16, CMDUI-18, CMDUI-21 |
| W9 | Ekran włączony: czas wygaszania 15 s, `CARBS` czeka > 15 s → ekran nie gaśnie (także na zakładce Ustawienia); po wyniku gaśnie | CMDUI-22 |
| W10 | `FLAG_SECURE`: zrzut ekranu zablokowany w oknie „Na pewno 45 g?”, na podsumowaniu, na przebiegu i na wyniku (także zrzut wykonany zaraz po „Wyślij”), podgląd w ostatnich aplikacjach pusty; w trakcie `CARBS 1` po przejściu na Ustawienia i na Historię, a potem z Historii na Ustawienia – zrzut nadal zablokowany; po zamknięciu wyniku na panelu Polecenia i na Ustawieniach (bez skanera) zrzut znów działa; po „Nie” w oknie „Na pewno” zrzut panelu działa; „Wyślij” i od razu ekran główny, po ok. 10 s lista ostatnich aplikacji i powrót → podgląd pusty, zrzut po powrocie zablokowany; skaner QR w Ustawieniach nadal chroniony (UI-23) | CMDUI-09, CMDUI-23 |
| W11 | Wstecz: w podsumowaniu zamyka je; na wyniku zamyka wynik; w trakcie `BG` okno „Trwa polecenie”, „Wyjdź” → aplikacja zamknięta, po odpowiedzi AAPS i ponownym otwarciu (bez zabijania procesu) wynik `BG`, po „Zamknij” panel, w Historii wpis; brak polecenia → Wstecz zamyka aplikację | CMDUI-21, CMDUI-24 |
| W12 | Brak zasięgu (tryb samolotowy) przy `BG` → „Nie udało się wysłać SMS…”; AAPS wyłączony / numer spoza listy AAPS → po 3 min „AAPS nie odpowiada” | CMDUI-19 |
| W13 | TalkBack w trakcie `CARBS 1` (czekanie na prośbę o kod) i `BG` (czekanie na odpowiedź): każdy krok czytany jako jeden element – nazwa i „wykonany” / „w toku” / „oczekuje”; status czytany raz; ikona nie ma osobnego fokusu; przy kroku bieżącym brak dodatkowego opisu postępu ze wskaźnika (`CircularProgressIndicator`, np. „w toku” dwa razy albo „ładowanie”) | CMDUI-17 |

**Punkty weryfikacji z wcześniejszych specyfikacji** (podział P3 ze Spec 008):

- **Spec 005:** W1–W5 i W7 – W4, W5 tej spec (W6 sprawdzone w Spec 008, W7).
- **Spec 006:** W1 w części odczytu i zgodności z Authenticatorem – W5; W3, W4, W9, W10 – W7; W2 – tylko test JVM komunikatu `Locked` (CMDUI-12), ręcznie w przyszłej spec usługi pierwszoplanowej (P4).
- **Spec 007:** W1 w części `BG` – W4; W7 – W2.
- **Spec 008:** UI-15 (blokada ustawień w trakcie polecenia) – W8.

## Otwarte pytania

Brak. Decyzje użytkownika przyjęte przy tej specyfikacji (wszystkie propozycje zaakceptowane):

- [x] **P1. Szybkie wartości.** Rozstrzygnięte: dotknięcie „10 g”/„20 g”/„30 g” wpisuje wartość w pole i od razu otwiera podsumowanie z PIN-em (CMDUI-08).
- [x] **P2. Format PIN-u AAPS.** Rozstrzygnięte: tylko cyfry, 1–12 znaków, klawiatura numeryczna typu hasło (CMDUI-10).
- [x] **P3. `FLAG_SECURE`.** Rozstrzygnięte: na podsumowaniu z PIN-em, przebiegu i wyniku; panel Polecenia bez `FLAG_SECURE` (CMDUI-23). Pierwotnie tylko podsumowanie; zmienione decyzją użytkownika po recenzji, zgodnie z planem („FLAG_SECURE na ekranach z danymi”).
- [x] **P4. W2 ze Spec 006 (`Locked` przy odczycie).** Rozstrzygnięte: komunikat `Locked` sprawdza tylko test JVM (CMDUI-12); ręczne sprawdzenie W2 – w przyszłej specyfikacji usługi pierwszoplanowej.
- [x] **P5. Ostatnia znana glikemia na ekranie Polecenia** (plan). Rozstrzygnięte: poza zakresem 009.
- [x] **P6. Anulowanie.** Rozstrzygnięte: przed kodem „Anuluj polecenie” bez okna; po kodzie „Przestań czekać” z oknem potwierdzenia (CMDUI-18).
- [x] **P7. Wynik sesji zakończonej przy zamkniętej `Activity`.** Rozstrzygnięte: wynik jest pokazywany, dopóki żyje proces aplikacji i użytkownik go nie zamknął (także po ponownym otwarciu `Activity`); po nowym uruchomieniu procesu – tylko w Historii, spec 010 (CMDUI-21).
- [x] **P8. Wstecz w trakcie polecenia.** Rozstrzygnięte: okno „Trwa polecenie” z „Zostań”/„Wyjdź”; rozszerza UI-02 ze Spec 008 tylko na czas polecenia (CMDUI-24).
- [x] **P9. Wiadomości nieoczekiwane.** Rozstrzygnięte: pokazywana tylko liczba, bez treści (CMDUI-20), spójnie z historią (DATA-08).
- [x] **P10. Błąd wysyłania bez przyczyny.** Rozstrzygnięte: ogólny komunikat „Nie udało się wysłać SMS. Sprawdź zasięg, kartę SIM i uprawnienia SMS.” (CMDUI-19); przyczyna – osobna zmiana Spec 004/005 („Przyszłe zmiany”).
- [x] **P11. `FLAG_SECURE` przy zmianie zakładki i w oknie „Na pewno”** (2026-10-28, po przeglądzie jakości UI). Rozstrzygnięte: ochrona trwa od startu polecenia do zamknięcia wyniku także po przejściu na inną zakładkę (wcześniej chroniona była tylko zakładka Polecenia); okno „Na pewno X g?” pokazuje ilość `CARBS`, więc też ma `FLAG_SECURE` na czas otwarcia (CMDUI-09, CMDUI-23).
- [x] **P12. Status kroków dla czytnika ekranu** (2026-10-28, po przeglądzie jakości UI). Rozstrzygnięte: każdy krok ma `stateDescription` „wykonany” / „w toku” / „oczekuje”, żeby status nie był przekazywany samą ikoną (CMDUI-17).
- [x] **P13. PIN przy zmianie zakładki** (2026-10-28, po przeglądzie kodu). Rozstrzygnięte: nie ma gwarancji, że `ON_STOP` dotrze przy zmianie zakładki, a `CommandsViewModel` żyje w zakresie `Activity`, więc PIN wpisany w podsumowaniu zostawał w pamięci (i podsumowanie otwarte) po przejściu na inną zakładkę. `CommandsScreen` woła `onStop()` także przy opuszczeniu kompozycji, jak `SettingsScreen` dla pola URI TOTP (design 008, UI-14); trwający przebieg, wynik i `FLAG_SECURE` (CMDUI-23) bez zmian (CMDUI-11).

## Przyszłe zmiany (nie blokują)

- **Usługa pierwszoplanowa** na czas polecenia (Spec 005, P2) – wtedy CMDUI-22/CMDUI-24 można złagodzić.
- **Przyczyna błędu wysyłania** w wyniku (`SendResult.Failed(reason)`, zmiana Spec 004 i 005) – osobne komunikaty dla braku zasięgu, SIM, uprawnień.
- **`TotpSecret.clear()`** po zakończeniu polecenia (zmiana Spec 001 i 004, „Przyszłe zmiany” w Spec 006).
- **Odliczanie czasu** do limitu w przebiegu (np. „zostało 2:15”), jeśli stały tekst „do 3 min” okaże się niewystarczający.
- **`CommandSession.state` jako `asStateFlow()`** (zmiana Spec 004): dziś sesja wystawia swój `MutableStateFlow` z typem `StateFlow`, więc posiadacz referencji (np. `CurrentRun`) mógłby go rzutować i zmienić; opakowanie tylko do odczytu usunie tę możliwość.

## Historia zmian

- **2026-10-28 – Planowane zmiany (Spec 011 – 013, zatwierdzone; obowiązują dla kodu tych spec, treść poniższych wymagań zostanie scalona zadaniem 6 z [design 013](../013-profil/design.md#podział-na-zadania)).**
  - CMDUI-01: panel ma też sekcje „Bolus” (BOL-13) i „Profil” (PROF-13).
  - CMDUI-04: sekret wymagany dla każdego polecenia z kodem (`CARBS`, `BOLUS`, zmiana profilu); CMDUI-05 obejmuje nowe przyciski.
  - CMDUI-07 – CMDUI-09: granica i próg `CARBS` z limitów (LIM-06 – LIM-08); dostępność zależna od stanu limitów (LIM-05).
  - CMDUI-10, CMDUI-11: podsumowanie z PIN-em uogólnione na `BOLUS` i zmianę profilu (BOL-15, PROF-15).
  - CMDUI-12: nowe powody odrzucenia `LIMITS_UNAVAILABLE`, `AMOUNT_ABOVE_LIMIT`, `EXTRA_CONFIRMATION_REQUIRED` (LIM-09; `launch` dostaje znacznik `extraConfirmed`), `BOLUS_INTERVAL`, `BOLUS_HISTORY_UNAVAILABLE` (BOL-12).
  - CMDUI-16, CMDUI-17, CMDUI-19: nagłówki, kroki (5 kroków dla każdego polecenia z kodem) i komunikaty nowych poleceń (BOL-16, PROF-16); wynik listy profili z wyborem pozycji (PROF-14).
  - CMDUI-23: `FLAG_SECURE` także dla okna „Na pewno X j.?” i formularza profilu (BOL-14, PROF-15).
- **2026-10-28 – czyszczenie PIN-u przy zmianie zakładki (P13).**
  - CMDUI-11: opuszczenie zakładki Polecenia (zmiana zakładki) czyści PIN i zamyka podsumowanie oraz okno „Na pewno” tak jak `ON_STOP`; nowe kryteria: dwukrotne `onStop()` (obrót), `onStop()` w trakcie `CARBS 1` nie rusza przebiegu ani ochrony, źródło `CommandsScreen.kt` (`DisposableEffect`); kryterium W6 dla zmiany zakładki.
  - CMDUI-23: opis zmiany zakładki – `onStop()` jest wołane, ale nie zmienia `launching` ani `runHandoff`; kryteria bez zmian.
  - „Przypadki błędów”, „Bezpieczeństwo”, W6; design: „Ekrany”, tabela stanów, „Decyzje”, mapowanie testów, zadanie 11.
- **2026-10-28 – dostępność i `FLAG_SECURE` po przeglądzie jakości UI (decyzje użytkownika P11, P12).**
  - CMDUI-17: `stateDescription` kroków („wykonany”, „w toku”, „oczekuje”), ikony bez opisu; kryteria JVM (zasoby, źródło `RunScreen.kt`) i W13 (TalkBack).
  - CMDUI-23: ochrona od `Started` do zamknięcia wyniku na każdej zakładce – żądanie `SecureWindowEffect` przeniesione z `CommandsScreen` do `AppScaffold` (poza `NavHost`), ten sam licznik; znacznik `CommandsViewModel` obejmuje okno „Na pewno”. Nowe kryteria (zmiana zakładki w trakcie, położenie wywołania), rozszerzony W10.
  - CMDUI-09: okno „Na pewno X g?” z `FLAG_SECURE` (kryteria znacznika, `onStop`) i z `DialogProperties(securePolicy = SecureOn)` – dialog to osobne okno (kryterium źródłowe).
  - Po recenzji PR #207: znacznik `CommandsViewModel` obejmuje `launching` (luka między „Wyślij” a `Started`, gdy `onStop()` zamknie podsumowanie w trakcie `load()`); `onStop()` opisane jako `ON_STOP` (przejście w tło), nie zmiana zakładki; opis wstrzymania `collectAsStateWithLifecycle` w tle; kryterium położenia `SecureWindowEffect` bez liczenia wywołań; W13 – semantyka wskaźnika postępu.
  - Design: `CommandsUiState.secureWindow`, „Ekrany”, „Decyzje”, mapowanie testów, zadania 9 i 10. Konsekwencje dla Spec 010: HISTUI-13 (zakładka Historia nadal chroniona; zejście z Historii w trakcie polecenia nie zdejmuje flagi).
- **2026-10-21 – korekta spójności po przeglądzie (bez zmian decyzji P1–P10).** Po wprowadzeniu `CurrentRun` ujednolicono spec i design:
  - CMDUI-24, W11 i „Przypadki błędów”: po „Wyjdź” i ponownym otwarciu przy żyjącym procesie widać przebieg albo wynik, a panel dopiero po „Zamknij” (zgodnie z P7 i CMDUI-21).
  - `CurrentRun` w CMDUI-15 i CMDUI-27 (z kryterium refleksji), w konstruktorach `CommandRunViewModel` i `CommandsViewModel`, w układzie plików i „Decyzjach” design.
  - Kryteria CMDUI-16, CMDUI-22 – CMDUI-24 uruchamiają sesję przez launcher albo pomocnik `startAndPublish`, a nie samym `coordinator.start`/`activeSession`.
  - CMDUI-23: ciągłość `FLAG_SECURE` przez znacznik przekazania `runHandoff` w `CommandsViewModel` zamiast pola `runPublished` ustawianego kolektorem (nie dawało się go sprawdzić testem).
  - CMDUI-19: wiersz `Completed` dla `CARBS` („Brak szczegółów wyniku.”, jak HISTUI-04 w Spec 010) i pełniejsze kryteria („Kod nie został wysłany” dla każdego powodu, ilość z wyniku, argument komunikatu); CMDUI-20: treść `Failed(WRONG_CODE)` i `Unknown`.
  - CMDUI-21: dozwolony `StateFlow` w `CurrentRun`, usunięte niewykonalne zdanie o zastępowaniu wyniku nowym poleceniem; CMDUI-17: doprecyzowane fazy spoza listy kroków; CMDUI-13: sprawdzenie `Dispatchers.Default`.
  - Teksty `run_stop_text`, `result_unknown_confirmation_timeout`, `result_unknown_cancelled_after_code`: w fazie `SENDING_CODE` port mógł jeszcze nie potwierdzić wysłania kodu (limit potwierdzenia liczony od rozpoczęcia wysyłania, SESSION-15), więc teksty nie twierdzą, że kod został wysłany.
  - Design: zależności zadań (części łączące oba ViewModele), wymagane przypadki w `ResultMapperTest` i `RunTextsTest`.
- **2026-10-21 – doprecyzowanie po implementacji (bez zmian wymagań).** Design (`CommandLauncher`, `ResultMapper`): `command.definition.requiresCode` zastąpione zapisem z kodu `CommandCatalog.definitionOf(command.id).requiresCode` (`Command` nie ma pola `definition`; to samo daje rozszerzenie `Command.definition` z `commands`).
