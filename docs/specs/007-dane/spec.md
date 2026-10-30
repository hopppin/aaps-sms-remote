# Spec 007: Dane – ustawienia i historia poleceń

- **Status:** zatwierdzona
- **Prefiks wymagań:** `DATA`
- **Pakiety:** `data` (logika czysta), `data.platform` (cienka warstwa Androida: `SharedPreferences`, Room, Hilt)
- **Powiązane:** [docs/plan.md](../../plan.md#architektura-i-moduły) (`data`: ustawienia, historia w Room), [docs/plan.md](../../plan.md#ekrany-aplikacji) (ekran „Historia i ustawienia”); [Spec 004: Sesja polecenia](../004-sesja-polecenia/spec.md) (`SessionResult`, `SessionOutcome`, SESSION-19, SESSION-21); [Spec 005: SMS](../005-sms/spec.md) (`AapsNumber`, `AapsNumberProvider`, SMS-01, SMS-04, SMS-09, SMS-12, decyzja P3); [Spec 006: Sekret TOTP](../006-sekret-totp/spec.md) (wzorzec `SharedPreferences` + `commit()`, SECRET-13, SECRET-16); [stan prac – ustalenia](../../stan-prac.md#ustalenia-z-użytkownikiem-decyzje); przyszła specyfikacja `ui` (ekran ustawień i historii, wywołanie rejestratora)

## Cel

Pakiet `sms` wysyła i odbiera SMS-y tylko wtedy, gdy zna numer telefonu z AAPS, a wynik każdego polecenia ma zostać w historii. Ta specyfikacja opisuje przechowywanie numeru AAPS (zapis, walidacja, usunięcie, dostęp bez blokowania wątku głównego, wiązanie Hilt `AapsNumberProvider`) oraz historię poleceń w Room: co trafia do wpisu z `SessionResult`, czego nigdy tam nie ma, ile wpisów zostaje, jak historię czytać i czyścić i kto zapisuje wynik sesji.

## Zakres

- Numer AAPS: osobny plik `SharedPreferences`, walidacja przez `sms.AapsNumber.parse`, zapis postaci znormalizowanej, usunięcie, wczytanie do pamięci podręcznej (`StateFlow`), operacje na dyspozytorze I/O.
- Implementacja `sms.AapsNumberProvider` (bez blokującego I/O) i jej wiązanie Hilt.
- Historia poleceń: wpis z `session.SessionResult`, zapis tekstowy wartości wyliczeniowych, odczyt wartości nieznanych, retencja 200 wpisów, odczyt jako `Flow` od najnowszego, czyszczenie całej historii.
- Rejestrator wyników sesji: zapis jednego wpisu na każdą śledzoną sesję po jej zakończeniu.
- Baza Room: schemat w wersji 3 (wersja 1 + migracje 1 → 2 i 2 → 3), eksport schematu, brak migracji destrukcyjnych.
- Limity poleceń w osobnym pliku `SharedPreferences` `command_limits` – opisuje [Spec 011](../011-limity-polecen/spec.md) (LIM-04); plik `settings` (DATA-01) bez zmian.
- Potwierdzenie, że plik ustawień i baza nie trafiają do kopii zapasowej ani transferu urządzenia (stan obecny manifestu i `data_extraction_rules.xml`).
- Brak ujawniania numeru, ilości węglowodanów, ilości bolusa, danych profilu i treści odpowiedzi AAPS.
- Wiązania Hilt pakietu `data`.

**Poza zakresem:**

- Ekrany i komunikaty (wpisywanie numeru, lista historii, potwierdzenie „wyczyścić historię?”) – spec `ui`.
- Wybór karty SIM (decyzja ze Spec 005) i inne ustawienia, m.in. „limit oczekiwania” z [planu](../../plan.md#ekrany-aplikacji) – limity sesji są stałe (Spec 004); ewentualne ustawienie to osobna specyfikacja. Limity poleceń (`CARBS`, `BOLUS`) to [Spec 011](../011-limity-polecen/spec.md).
- Usuwanie pojedynczych wpisów historii, eksport historii, synchronizacja, kopia zapasowa historii.
- Szyfrowanie bazy i pliku ustawień (np. SQLCipher) – uzasadnienie w sekcji Bezpieczeństwo i w [design.md](design.md#decyzje).
- Zapis wyniku sesji przerwanej zabiciem procesu (sesja przepada razem z procesem – Spec 005, Poza zakresem).
- Sekret TOTP (Spec 006) i PIN AAPS (`security.AapsPin`) – nie są częścią `data`.

## Założenia i zależności

- **`AapsNumber`** (Spec 005, SMS-01) jest jedyną regułą poprawności numeru; ta specyfikacja jej nie zmienia ani nie powtarza.
- **`SessionResult`** (Spec 004) jest jedynym źródłem wpisu historii; ta specyfikacja nie zmienia `session`.
- **Wymagania dla spec `ui`** (nie są realizowane w `data`, wynikają z tej specyfikacji):
  - przyciski poleceń są nieaktywne, dopóki stan numeru AAPS to `Loading` (DATA-02);
  - zaraz po `StartResult.Started(session)` `ui` wywołuje `SessionHistoryRecorder.track(session.state)` dla tej sesji (DATA-13);
  - zmiana i usunięcie numeru AAPS są niedostępne, gdy `SessionCoordinator.activeSession.value != null` (sekcja Bezpieczeństwo);
  - czyszczenie historii jest poprzedzone potwierdzeniem użytkownika (DATA-12).
- **Testy:** logika czysta w testach JVM (JUnit 5, Turbine, `kotlinx-coroutines-test`); cienka warstwa Androida (`data.platform`) bez Robolectric i `androidTest`, sprawdzana ręcznie według listy W1–W7 (sekcja „Weryfikacja na urządzeniu”), jak decyzja P3 w Spec 005. Testy plików (manifest, reguły kopii, wyeksportowany schemat, źródła) są zwykłymi testami JVM.

## Pojęcia

- **Numer wpisany** – tekst podany do zapisu przez `ui` (dowolny `String`).
- **Postać znormalizowana** – `AapsNumber.destination` numeru wpisanego, dla którego `AapsNumber.parse` nie zwraca `null` (np. `+44 7700 900123` → `+447700900123`).
- **Magazyn ustawień** – plik `SharedPreferences` z DATA-01; w kryteriach logiki czystej zastępowany atrapą (port `SettingsStorage` w design).
- **Stan numeru** – wartość `StateFlow<AapsNumberState>`: `Loading`, `NotConfigured`, `Configured(number)`, `LoadFailed`.
- **Wynik sesji** – `SessionResult` z fazy `FINISHED` (Spec 004).
- **Wpis** – jeden rekord historii utworzony z jednego wyniku sesji (DATA-07); **wiersz** – jego postać zapisana w bazie (teksty i liczby).
- **Magazyn historii** – tabela Room z DATA-14; w kryteriach logiki czystej zastępowana atrapą (port `HistoryStorage` w design).
- **Kolejność zapisu** – identyfikator wpisu nadawany rosnąco przy zapisie; „najnowszy” = największy identyfikator.

## Wymagania

Wspólne dane kryteriów (o ile kryterium nie mówi inaczej): numery fikcyjne `+44 7700 900123` (postać znormalizowana `+447700900123`) i `+44 7700 900999` (`+447700900999`); treści odpowiedzi z [`../002-parser-odpowiedzi/dane/`](../002-parser-odpowiedzi/dane/README.md); chwile `T0 = 1111111051000` i przesunięcia; dyspozytory testowe (`StandardTestDispatcher`) w miejscu `Dispatchers.IO` i zakresu aplikacji.

### DATA-01: Plik ustawień i postać zapisu

Numer AAPS jest zapisywany w osobnym pliku `SharedPreferences` o nazwie `settings` (`Context.MODE_PRIVATE`) pod kluczem `aaps_number`, jako tekst w postaci znormalizowanej. Zapis i usunięcie używają `commit()` i jego wyniku; `apply()` nie jest używane. Plik nie zawiera innych kluczy.

**Kryteria akceptacji:**

- **Zakładając** pusty magazyn ustawień, **gdy** zapisano numer wpisany `+44 7700 900123`, **wtedy** magazyn otrzymał dokładnie jeden zapis z wartością `+447700900123`.
- **Zakładając** numer wpisany `+44 (0)7700 900123`, **gdy** go zapisano, **wtedy** zapisana wartość = `+4407700900123`.
- **Zakładając** telefon z wersją debug i zapisanym numerem (sprawdzenie na urządzeniu, W2), **gdy** odczytano `shared_prefs/settings.xml` przez `adb shell run-as`, **wtedy** plik zawiera tylko klucz `aaps_number` z postacią znormalizowaną.

### DATA-02: Wczytanie numeru i stan

Stan numeru zaczyna jako `Loading`. Wczytanie z magazynu ustawień rusza raz, przy utworzeniu obiektu ustawień (singleton), jako zadanie w zakresie korutyn aplikacji, z wywołaniem magazynu na dyspozytorze I/O; konstruktor nie wykonuje I/O na wątku wywołującym. Wynik wczytania:

| Zawartość magazynu | Stan po wczytaniu |
| --- | --- |
| brak klucza | `NotConfigured` |
| tekst, dla którego `AapsNumber.parse` ≠ `null` | `Configured(number)` |
| tekst, dla którego `AapsNumber.parse` = `null` | `NotConfigured` (wartość w pliku bez zmian) |
| odczyt rzucił wyjątek (poza `CancellationException`) | `LoadFailed` |

Wczytanie nie zmienia stanu i nie czyta magazynu, gdy przed jego wykonaniem zakończył się zapis albo usunięcie (DATA-03, DATA-04). Nie ma ponownego wczytania w czasie życia procesu.

**Kryteria akceptacji:**

- **Zakładając** magazyn z wartością `+447700900123`, **gdy** utworzono obiekt ustawień i nie uruchomiono jeszcze dyspozytorów, **wtedy** stan = `Loading`, a magazyn nie był czytany; **gdy** dyspozytory wykonały zadania, **wtedy** stan = `Configured` z `destination` = `+447700900123`, a magazyn był czytany dokładnie raz.
- **Zakładając** pusty magazyn, **gdy** wczytanie się zakończyło, **wtedy** stan = `NotConfigured`.
- **Zakładając** magazyn z wartością `70090012`, **gdy** wczytanie się zakończyło, **wtedy** stan = `NotConfigured`, a magazyn nie otrzymał zapisu ani usunięcia.
- **Zakładając** magazyn rzucający `ClassCastException` przy odczycie, **gdy** wczytanie się zakończyło, **wtedy** stan = `LoadFailed`, bez wyjątku na zewnątrz.
- **Zakładając** magazyn z wartością `+447700900999` i zakres aplikacji, który nie uruchomił jeszcze zadania wczytania, **gdy** zapis `+44 7700 900123` się zakończył, a potem zakres wykonał zadanie wczytania, **wtedy** stan = `Configured` z `+447700900123`, a magazyn nie był czytany.
- **Zakładając** kolektor stanu (Turbine) i magazyn z `+447700900123`, **gdy** wczytanie się zakończyło, **wtedy** kolektor dostał kolejno `Loading`, `Configured`.

### DATA-03: Zapis numeru z walidacją

Zapis przyjmuje numer wpisany. Gdy `AapsNumber.parse` zwraca `null`, wynik to `Invalid`: magazyn nie jest wywoływany, stan bez zmian. W przeciwnym razie postać znormalizowana jest zapisywana (DATA-01); `commit()` = `true` → wynik `Saved`, stan `Configured(number)`; `commit()` = `false` albo wyjątek (poza `CancellationException`) → wynik `Failed`, stan bez zmian. Zapis jest dozwolony w każdym stanie (także `Loading` i `LoadFailed`). Pusty tekst jest `Invalid` (usunięcie numeru to osobna operacja, DATA-04).

**Kryteria akceptacji:**

- **Zakładając** stan `Configured(+447700900999)`, **gdy** zapisano `+44 7700 900123`, **wtedy** wynik = `Saved`, stan = `Configured` z `destination` = `+447700900123`.
- **Zakładając** stan `Configured(+447700900999)`, **gdy** zapisano kolejno `70090012`, `""`, `"   "`, `+44 7700 9OO123`, `44+7700900123`, **wtedy** każdy wynik = `Invalid`, magazyn nie otrzymał żadnego zapisu, a stan = `Configured` z `+447700900999`.
- **Zakładając** stan `Configured(+447700900999)` i magazyn zwracający `false` z `commit()`, **gdy** zapisano `+44 7700 900123`, **wtedy** wynik = `Failed`, stan = `Configured` z `+447700900999`.
- **Zakładając** magazyn rzucający `RuntimeException("x")` przy zapisie, **gdy** zapisano `+44 7700 900123`, **wtedy** wynik = `Failed` bez wyjątku, stan bez zmian.
- **Zakładając** stan `LoadFailed`, **gdy** zapisano `+44 7700 900123`, **wtedy** wynik = `Saved`, stan = `Configured`.
- **Zakładając** zapis, po którym (gdy `commit()` = `true`) test anuluje korutynę zapisu, **gdy** anulowanie dotarło po zapisie, **wtedy** wywołujący dostaje `CancellationException`, a stan = `Configured` z zapisaną wartością.

### DATA-04: Usunięcie numeru

Usunięcie kasuje klucz `aaps_number` (`commit()`). `commit()` = `true` → wynik `Cleared`, stan `NotConfigured`; `false` albo wyjątek (poza `CancellationException`) → wynik `Failed`, stan bez zmian. Usunięcie przy braku numeru też wywołuje magazyn i daje `Cleared`. Anulowanie po zatwierdzonym `commit()` – jak w DATA-03 (stan = `NotConfigured`).

**Kryteria akceptacji:**

- **Zakładając** stan `Configured(+447700900123)`, **gdy** usunięto numer, **wtedy** wynik = `Cleared`, stan = `NotConfigured`, magazyn nie zawiera klucza.
- **Zakładając** stan `Configured(+447700900123)` i magazyn zwracający `false` przy usunięciu, **gdy** usunięto numer, **wtedy** wynik = `Failed`, stan = `Configured` z `+447700900123`.
- **Zakładając** stan `NotConfigured`, **gdy** usunięto numer, **wtedy** wynik = `Cleared`, stan = `NotConfigured`.

### DATA-05: Dostawca numeru dla `sms` bez I/O

Obiekt ustawień implementuje `sms.AapsNumberProvider`. `currentNumber()` zwraca `destination` ze stanu `Configured`, a w stanach `Loading`, `NotConfigured` i `LoadFailed` zwraca `null`. Nie wywołuje magazynu ustawień, nie zawiesza się, nie czeka na blokadę i nie rzuca; można go wołać z dowolnego wątku (także głównego, z `onReceive`). Wiązanie Hilt `AapsNumberProvider` → ten sam singleton, który obsługuje zapis i usunięcie (moduł w `data.platform`; `SmsModule` bez nowych wiązań).

**Kryteria akceptacji:**

- **Zakładając** stan `Loading` (wczytanie nieuruchomione), **gdy** wywołano `currentNumber()`, **wtedy** wynik = `null`, a magazyn nie był czytany.
- **Zakładając** stan `Configured(+447700900123)`, **gdy** wywołano `currentNumber()` 1000 razy, **wtedy** każdy wynik = `+447700900123`, a liczba wywołań magazynu się nie zmieniła.
- **Zakładając** stan `Configured(+447700900999)` i zapis `+44 7700 900123` zablokowany w magazynie do zwolnienia przez test (`CountDownLatch`), **gdy** z innego wątku wywołano `currentNumber()`, **wtedy** zwraca `+447700900999`, zanim test zwolni zapis; po zakończeniu zapisu zwraca `+447700900123`.
- **Zakładając** stany `NotConfigured` i `LoadFailed`, **gdy** wywołano `currentNumber()`, **wtedy** wynik = `null`.
- **Zakładając** zbudowaną aplikację (`assembleDebug`), **gdy** Hilt wygenerował graf, **wtedy** `AapsNumberProvider` ma dokładnie jedno wiązanie, w module `data.platform`.

### DATA-06: Serializacja i wątek I/O ustawień

Wczytanie, zapis i usunięcie są wzajemnie wykluczające się: najwyżej jedna operacja wywołuje magazyn ustawień w danej chwili. Każde wywołanie magazynu odbywa się na wstrzykiwanym dyspozytorze (w aplikacji `Dispatchers.IO`), nigdy na wątku wywołującego. Zapis i usunięcie są funkcjami `suspend` i są anulowalne; anulowanie nie cofa zatwierdzonego `commit()`, a stan odpowiada zawartości magazynu (DATA-03, DATA-04).

**Kryteria akceptacji:**

- **Zakładając** dyspozytor testowy zapisujący nazwę wątku przy każdym wywołaniu magazynu, **gdy** wykonano wczytanie, zapis i usunięcie, **wtedy** każde wywołanie magazynu odbyło się na wątku dyspozytora.
- **Zakładając** 50 równoczesnych wywołań naprzemiennie zapisu `+44 7700 900123` i `+44 7700 900999` na `Dispatchers.Default`, **gdy** wszystkie się zakończyły, **wtedy** liczba równocześnie trwających wywołań magazynu nigdy nie przekroczyła 1, a stan = `Configured` z wartością, która jest w magazynie.

### DATA-07: Zawartość wpisu historii

Wpis powstaje z wyniku sesji według tabeli. Pola czasu są kopiowane bez zmian, także `null` (SESSION-19). Ilość węglowodanów pochodzi z `Command.Carbs.grams` (wynik `CarbsConfirmed` ma zawsze tę samą ilość – Spec 004), ilość bolusa z `Command.Bolus.centiUnits` (BOL-17), numer i procent profilu z `Command.ProfileSwitch` (PROF-17; nazwa profilu nie jest osobnym polem). Treść odpowiedzi jest zapisywana w całości, bez skracania.

| Pole wpisu | Źródło |
| --- | --- |
| identyfikator | nadawany przy zapisie (kolejność zapisu) |
| polecenie | `command.id`: `BG`, `PUMP`, `CARBS`, `BOLUS`, `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH` |
| ilość (g) | `Command.Carbs.grams` dla `CARBS`, inaczej brak |
| ilość bolusa (setne części jednostki) | `Command.Bolus.centiUnits` dla `BOLUS`, inaczej brak |
| numer i procent profilu | `Command.ProfileSwitch.number` i `.percent` dla `PROFILE_SWITCH`, inaczej brak (dwa osobne pola) |
| rodzaj wyniku | `Completed`, `CarbsConfirmed`, `BolusConfirmed`, `Failed`, `TimedOut`, `Unknown`, `Cancelled` |
| powód | `Failed.reason` (`FailureReason`), `Unknown.reason` (`UnknownReason`), `TimedOut.phase` (`SessionPhase`); dla pozostałych brak |
| treść odpowiedzi AAPS | `text` z `Completed`, `CarbsConfirmed`, `BolusConfirmed`, `Failed`, `Unknown` (dla `Failed`/`Unknown` może być brak); dla `TimedOut` i `Cancelled` brak |
| rozpoczęcie, wysłanie polecenia, wysłanie kodu, zakończenie | `startedAtEpochMillis`, `commandSentAtEpochMillis`, `codeSentAtEpochMillis`, `finishedAtEpochMillis` |
| liczba wiadomości nieoczekiwanych | `unexpectedMessages.size` |

**Kryteria akceptacji:**

- **Zakładając** wynik `BG`: `Completed` z treścią `bg.txt`, rozpoczęcie `T0`, wysłanie polecenia `T0 + 1000`, wysłanie kodu `null`, zakończenie `T0 + 5000`, 0 wiadomości nieoczekiwanych, **gdy** utworzono wpis, **wtedy** polecenie = `BG`, ilość = brak, rodzaj = `Completed`, powód = brak, treść = `bg.txt`, czasy = `T0`, `T0 + 1000`, `null`, `T0 + 5000`, liczba = 0.
- **Zakładając** wynik `CARBS 20`: `CarbsConfirmed(20, carbs-sukces.txt)` z czasami `T0`, `T0 + 1000`, `T0 + 61000`, `T0 + 120000` i 2 wiadomościami nieoczekiwanymi, **gdy** utworzono wpis, **wtedy** polecenie = `CARBS`, ilość = 20, rodzaj = `CarbsConfirmed`, treść = `carbs-sukces.txt`, czasy jak w wyniku, liczba = 2.
- **Zakładając** wynik `BOLUS 1,20 j.` `BolusConfirmed(120, bolus-sukces-1-20.txt)` i wynik zmiany profilu (2, `LocalProfile2`, 115) `Unknown(UNRECOGNIZED_REPLY)`, **gdy** utworzono wpisy, **wtedy** pierwszy ma polecenie `BOLUS`, ilość bolusa 120, brak ilości węglowodanów i rodzaj `BolusConfirmed`, a drugi – polecenie `PROFILE_SWITCH`, numer 2, procent 115, przy czym żadne pole poza treścią nie zawiera nazwy profilu (BOL-17, PROF-17).
- **Zakładając** wyniki `Failed(WRONG_CODE, zly-kod.txt)`, `Failed(SEND_FAILED, null)`, `Unknown(CONFIRMATION_TIMEOUT, null)`, `Unknown(AMOUNT_MISMATCH, carbs-prosba-o-kod.txt)`, `TimedOut(AWAITING_REPLY)`, `Cancelled`, **gdy** utworzono wpisy, **wtedy** rodzaje i powody to kolejno `Failed`/`WRONG_CODE`, `Failed`/`SEND_FAILED`, `Unknown`/`CONFIRMATION_TIMEOUT`, `Unknown`/`AMOUNT_MISMATCH`, `TimedOut`/`AWAITING_REPLY`, `Cancelled`/brak, a treść jest tylko w pierwszym i czwartym wpisie.
- **Zakładając** wynik `Cancelled` z wszystkimi czasami `null` (SESSION-19), **gdy** utworzono wpis, **wtedy** wszystkie cztery czasy = `null`.
- **Zakładając** wynik `Failed(UNRECOGNIZED_REPLY, <2000 znaków "a">)`, **gdy** zamieniono go na wiersz i odczytano wpis, **wtedy** treść ma 2000 znaków i jest identyczna.

### DATA-08: Dane wykluczone z historii

Wpis i wiersz mają wyłącznie pola z tabeli DATA-07. Nie zawierają PIN-u, kodu TOTP, sekretu TOTP, numeru telefonu (AAPS ani nadawców), treści ani chwil odbioru wiadomości nieoczekiwanych, ani przetworzonej odpowiedzi parsera (`Completed.reply`).

**Kryteria akceptacji:**

- **Zakładając** typy wpisu i wiersza, **gdy** sprawdzono ich pola refleksją, **wtedy** zbiór nazw pól jest równy liście z design, a żadne pole nie jest typu `List`, `Collection`, `Array`, `CharArray`, `ByteArray`, `AapsReply`, `Command`, `IncomingSms` ani `SessionResult`.
- **Zakładając** wynik `CARBS 20` `CarbsConfirmed` z wiadomościami nieoczekiwanymi o treściach `Pomyslnie wprowadzono 7 g weglowodanow` i `Inny tekst 4321`, **gdy** utworzono wpis i wiersz, **wtedy** żadne pole tekstowe nie zawiera `wprowadzono 7 g` ani `Inny tekst`, a liczba = 2.

### DATA-09: Stabilne wartości tekstowe i wartości nieznane

Polecenie, rodzaj wyniku i powód są zapisywane jako stałe teksty z tabeli w design (dla poleceń, powodów i faz równe obecnym nazwom wartości wyliczeniowych; dla rodzajów wyniku: `COMPLETED`, `CARBS_CONFIRMED`, `BOLUS_CONFIRMED`, `FAILED`, `TIMED_OUT`, `UNKNOWN`, `CANCELLED`). Zmiana nazwy wartości wyliczeniowej w kodzie nie zmienia zapisywanego tekstu. Odczyt wiersza nigdy nie rzuca:

| Wiersz | Odczytany wpis |
| --- | --- |
| nieznany tekst polecenia | polecenie `UNRECOGNIZED`, ilość = brak |
| polecenie inne niż `CARBS` z ilością | ilość = brak |
| polecenie inne niż `BOLUS` z ilością bolusa | ilość bolusa = brak |
| polecenie inne niż `PROFILE_SWITCH` z numerem lub procentem profilu | numer i procent = brak |
| nieznany tekst rodzaju wyniku | rodzaj `Unrecognized`, powód = brak |
| `FAILED`, `UNKNOWN`, `TIMED_OUT` z nieznanym albo brakującym powodem | ten rodzaj z powodem = brak |
| `COMPLETED`, `CARBS_CONFIRMED`, `BOLUS_CONFIRMED`, `CANCELLED` z powodem | powód pominięty |

Pozostałe pola (identyfikator, czasy, treść, liczba) są odczytywane bez zmian.

**Kryteria akceptacji:**

- **Zakładając** każdą wartość `CommandId`, `FailureReason`, `UnknownReason`, `SessionPhase` i każdy rodzaj wyniku, **gdy** zapisano ją do tekstu i odczytano, **wtedy** odczyt = wartość wyjściowa, a tekst = wartość z tabeli w design.
- **Zakładając** wiersz z poleceniem `TBR`, **gdy** go odczytano, **wtedy** polecenie = `UNRECOGNIZED`, bez wyjątku.
- **Zakładając** wiersz z rodzajem `PARTIAL` i powodem `SEND_FAILED`, **gdy** go odczytano, **wtedy** rodzaj = `Unrecognized`, powód = brak.
- **Zakładając** wiersz `FAILED` z powodem `NO_SIGNAL`, **gdy** go odczytano, **wtedy** rodzaj = `Failed`, powód = brak, pozostałe pola bez zmian.
- **Zakładając** wiersz `BG` z ilością 20, **gdy** go odczytano, **wtedy** ilość = brak.
- **Zakładając** wiersz `CARBS` z ilością bolusa 50 oraz wiersz `PROFILE_LIST` z numerem 3 i procentem 100, **gdy** je odczytano, **wtedy** ilość bolusa, numer i procent profilu = brak (BOL-17, PROF-17).
- **Zakładając** wiersz `PROFILE_LIST` z samym numerem profilu 3 (procent `NULL`) oraz wiersz `PROFILE_STATUS` z samym procentem 100 (numer `NULL`), **gdy** je odczytano, **wtedy** w obu numer i procent profilu = brak (PROF-17).

### DATA-10: Zapis z retencją 200 wpisów

Zapis wpisu i usunięcie najstarszych wpisów ponad limit 200 odbywają się w jednej transakcji bazy: po udanym zapisie historia ma najwyżej 200 wpisów i zawiera nowy wpis; usuwane są wpisy z najmniejszymi identyfikatorami. Gdy którykolwiek krok transakcji się nie powiedzie, historia jest bez zmian, a wynik zapisu to `Failed`; po udanym – `Recorded`. Brak ponawiania zapisu.

**Kryteria akceptacji:**

- **Zakładając** historię z 199 wpisami, **gdy** zapisano wpis, **wtedy** wynik = `Recorded`, historia ma 200 wpisów, żaden nie został usunięty.
- **Zakładając** historię z 200 wpisami o identyfikatorach 1–200, **gdy** zapisano wpis, **wtedy** historia ma 200 wpisów o identyfikatorach 2–201.
- **Zakładając** historię z 205 wpisami, **gdy** zapisano wpis, **wtedy** historia ma 200 wpisów, usunięto 6 najstarszych.
- **Zakładając** magazyn historii z 200 wpisami rzucający `RuntimeException("x")` przy usuwaniu najstarszych, **gdy** zapisano wpis, **wtedy** wynik = `Failed` bez wyjątku, historia ma te same 200 wpisów, a magazyn otrzymał jedno wywołanie transakcji.
- **Zakładając** atrapę magazynu notującą, czy wywołanie odbywa się wewnątrz transakcji, **gdy** zapisano wpis, **wtedy** wstawienie, liczenie i usuwanie odbyły się wewnątrz jednej transakcji.
- **Zakładając** bazę na telefonie z 205 wierszami wstawionymi ręcznie (sprawdzenie na urządzeniu, W4), **gdy** zakończono jedno polecenie `BG`, **wtedy** tabela ma 200 wierszy, bez 6 o najmniejszych identyfikatorach.

### DATA-11: Odczyt historii

Historia jest dostępna jako `Flow`, który przy zbieraniu emituje bieżący stan, a potem nowy stan po każdej zmianie (zapis, retencja, czyszczenie). Stan to `Entries(lista)` – wszystkie wpisy od najnowszego (malejący identyfikator) – albo `Unavailable`, gdy odczyt bazy rzucił wyjątek (poza `CancellationException`); po `Unavailable` strumień kończy się bez wyjątku. Kolejność nie zależy od pól czasu (mogą być `null` albo niemonotoniczne przy zmianie zegara).

**Kryteria akceptacji:**

- **Zakładając** pustą historię i kolektor (Turbine), **gdy** zapisano kolejno wpisy A, B, C, **wtedy** kolektor dostał `Entries([])`, `Entries([A])`, `Entries([B, A])`, `Entries([C, B, A])`.
- **Zakładając** wpis A z rozpoczęciem `T0 + 120000` zapisany przed wpisem B z rozpoczęciem `T0`, **gdy** odczytano historię, **wtedy** kolejność = B, A.
- **Zakładając** magazyn, którego strumień rzuca `RuntimeException("x")`, **gdy** zbierano historię, **wtedy** kolektor dostał `Unavailable`, a strumień zakończył się bez wyjątku.

### DATA-12: Czyszczenie historii

Czyszczenie usuwa wszystkie wpisy jedną operacją; wynik `Cleared` po sukcesie, `Failed` przy wyjątku (poza `CancellationException`). Nie ma operacji usunięcia pojedynczego wpisu. Potwierdzenie użytkownika należy do `ui`. Zapis po czyszczeniu działa tak samo jak przed nim; identyfikatory nie są używane ponownie (DATA-14).

**Kryteria akceptacji:**

- **Zakładając** historię z 3 wpisami i kolektor, **gdy** wyczyszczono historię, **wtedy** wynik = `Cleared`, kolektor dostał `Entries([])`.
- **Zakładając** historię wyczyszczoną po wpisach o identyfikatorach 1–3, **gdy** zapisano wpis, **wtedy** historia ma 1 wpis o identyfikatorze większym niż 3 (w testach JVM – atrapa z tą regułą; na urządzeniu – W5).
- **Zakładając** magazyn rzucający przy czyszczeniu, **gdy** wyczyszczono historię, **wtedy** wynik = `Failed` bez wyjątku.
- **Zakładając** publiczny interfejs historii, **gdy** sprawdzono jego metody refleksją, **wtedy** są to dokładnie: strumień historii, zapis wpisu z wyniku sesji, czyszczenie.

### DATA-13: Rejestrowanie wyniku sesji

Rejestrator (`SessionHistoryRecorder`, singleton) przyjmuje stan sesji (`StateFlow<SessionState>` z `CommandSession.state`) i w zakresie korutyn aplikacji (nie `ui`) czeka na fazę `FINISHED`, po czym zapisuje jeden wpis z wyniku (DATA-10). Dla każdego przyjętego obiektu stanu powstaje najwyżej jeden wpis, także gdy:

- sesja zakończyła się przed przekazaniem stanu (faza `FINISHED` już w stanie) – wpis powstaje;
- ten sam obiekt stanu przekazano więcej niż raz – drugie i kolejne przekazanie są ignorowane;
- zapis się nie udał (`Failed` albo wyjątek) – brak ponownej próby, brak wyjątku na zewnątrz.

Rejestrator nie wpływa na sesję: nie anuluje jej, nie zmienia jej stanu i nie wymaga zbierania stanu przez `ui`. Obiekty stanu pamięta wyłącznie przez słabe referencje (sesja trzyma `CodeCredentials` z sekretem TOTP – rejestrator nie może przedłużać jej życia), a silną referencję trzyma tylko zadanie czekające na `FINISHED`, do zapisu. Anulowanie zakresu rejestratora przed `FINISHED` = brak wpisu. Zmiana `session` (Spec 004) nie jest potrzebna.

**Kryteria akceptacji:**

- **Zakładając** stan sesji w `AWAITING_REPLY` przekazany rejestratorowi, **gdy** stan przeszedł do `FINISHED` z wynikiem `Completed` (`bg.txt`), **wtedy** historia otrzymała dokładnie jeden zapis z tym wynikiem.
- **Zakładając** stan już w `FINISHED` z wynikiem `Cancelled`, **gdy** przekazano go rejestratorowi, **wtedy** historia otrzymała dokładnie jeden zapis.
- **Zakładając** ten sam obiekt stanu przekazany 3 razy przed i 1 raz po `FINISHED`, **gdy** sesja się zakończyła, **wtedy** historia otrzymała dokładnie jeden zapis.
- **Zakładając** dwa obiekty stanu dwóch kolejnych sesji, **gdy** obie się zakończyły, **wtedy** historia otrzymała dwa zapisy w kolejności zakończenia.
- **Zakładając** historię zwracającą `Failed`, a w drugim przebiegu rzucającą `RuntimeException("x")`, **gdy** sesja się zakończyła, **wtedy** historia otrzymała dokładnie jedno wywołanie, rejestrator nie rzucił, a kolejna sesja jest zapisywana normalnie.
- **Zakładając** stan w `AWAITING_REPLY` i anulowany zakres rejestratora, **gdy** stan przeszedł potem do `FINISHED`, **wtedy** historia nie otrzymała zapisu.
- **Zakładając** klasę rejestratora, **gdy** sprawdzono jej pola refleksją, **wtedy** wszystkie są `final`, a jedynym polem przechowującym obiekty stanu jest `WeakHashMap` (słabe klucze, design), bez pól typu `StateFlow`, `SessionState`, `SessionResult`, `CommandSession` ani list.

### DATA-14: Schemat bazy i migracje

Baza Room o nazwie pliku `aaps_remote.db` w domyślnym katalogu baz aplikacji (`databases/`) ma wersję schematu 3 i jedną tabelę `command_history` z kolumnami z design. Wersja 2 (migracja 1 → 2) dodaje kolumnę `bolus_centi_units INTEGER` ([Spec 012](../012-bolus/spec.md), BOL-17), wersja 3 (migracja 2 → 3) – kolumny `profile_number INTEGER` i `profile_percent INTEGER` ([Spec 013](../013-profil/spec.md), PROF-17); wszystkie nowe kolumny dopuszczają `NULL`, istniejące wiersze zostają bez zmian. Obie migracje są rejestrowane jednym wywołaniem `addMigrations(MIGRATION_1_2, MIGRATION_2_3)`, więc baza w wersji 1 przechodzi do 3 kolejno przez 2. Identyfikator jest kluczem głównym z `AUTOINCREMENT` (identyfikatory nie są używane ponownie, także po czyszczeniu). Schemat jest eksportowany (`exportSchema = true`) do katalogu `app/schemas/`, a pliki schematów wersji 1, 2 i 3 są w repo (`app/schemas/pl.twojanazwa.aapsremote.data.platform.AapsRemoteDatabase/1.json`, `2.json`, `3.json`). Baza nie używa `fallbackToDestructiveMigration` w żadnym wariancie; każda przyszła zmiana schematu wymaga zmiany tej specyfikacji z migracją i jej testem (bez testu migracji na `androidTest` – patrz kryteria: sprawdzenie plików schematu w JVM i ręczne W3 na urządzeniu).

**Kryteria akceptacji:**

- **Zakładając** wyeksportowany plik schematu `app/schemas/pl.twojanazwa.aapsremote.data.platform.AapsRemoteDatabase/1.json`, **gdy** test JVM go przeczytał, **wtedy** wersja = 1 (schemat wyjściowy), jest dokładnie jedna tabela `command_history`, nazwy kolumn, ich typy i `NOT NULL` są zgodne z design, a zapytanie tworzące tabelę zawiera `AUTOINCREMENT`.
- **Zakładając** pliki `2.json` i `3.json`, **gdy** test JVM je przeczytał, **wtedy** wersje = 2 i 3, tabela ma kolumny wersji poprzedniej i nowe kolumny (`bolus_centi_units`; `profile_number`, `profile_percent`) typu `INTEGER` bez `NOT NULL`; migracja 1 → 2 zawiera dokładnie jedno `ALTER TABLE command_history ADD COLUMN bolus_centi_units INTEGER`, a migracja 2 → 3 dokładnie dwa `ALTER TABLE command_history ADD COLUMN …` (BOL-17, PROF-17).
- **Zakładając** źródło `AapsRemoteDatabase.kt`/`DataModule.kt`, **gdy** test JVM je przeczytał, **wtedy** jest dokładnie jedno wywołanie `addMigrations(` z `MIGRATION_1_2` i `MIGRATION_2_3`.
- **Zakładając** źródła `data.platform`, **gdy** test JVM przeszukał je jako tekst, **wtedy** nie zawierają `fallbackToDestructiveMigration`.
- **Zakładając** telefon z historią w wersji 1 albo 2 (sprawdzenie na urządzeniu, W3 w Spec 012 i 013), **gdy** zainstalowano nową wersję, **wtedy** dotychczasowe wpisy są zachowane, a nowe wpisy bolusa i profilu mają swoje pola.
- **Zakładając** telefon po pierwszym poleceniu (sprawdzenie na urządzeniu, W3), **gdy** wyświetlono `databases/` przez `adb shell run-as`, **wtedy** jest plik `aaps_remote.db`, a po zabiciu procesu i ponownym starcie historia zawiera wpis.

### DATA-15: Błędy bez wyjątków

Operacje publiczne `data` (zapis i usunięcie numeru, `currentNumber()`, zapis wpisu, czyszczenie, strumień historii, rejestrator) nigdy nie rzucają wyjątku innego niż `CancellationException`: wyjątki magazynów są zamieniane na wyniki z DATA-02, DATA-03, DATA-04, DATA-10, DATA-11, DATA-12, DATA-13. `CancellationException` jest zawsze przepuszczany. Wyniki nie przenoszą wyjątków ani ich komunikatów.

**Kryteria akceptacji:**

- **Zakładając** magazyn ustawień i magazyn historii rzucające `RuntimeException("x")` w każdej metodzie, **gdy** wywołano kolejno zapis numeru, usunięcie numeru, `currentNumber()`, zapis wpisu, czyszczenie i zbieranie historii, **wtedy** żadne wywołanie nie rzuciło, a wyniki to `Failed`, `Failed`, `null`, `Failed`, `Failed`, `Unavailable`.
- **Zakładając** magazyn historii rzucający `CancellationException` przy wstawianiu, **gdy** zapisano wpis, **wtedy** wywołujący dostaje `CancellationException`.

### DATA-16: Brak kopii zapasowej ustawień i historii

Pliki ustawień (`settings` i `command_limits` – Spec 011; domena `sharedpref`) i baza historii (domena `database`) nie trafiają do kopii zapasowej ani transferu urządzenia. Stan obecny zostaje bez zmian: w `AndroidManifest.xml` `android:allowBackup="false"`, `android:fullBackupContent="false"`, `android:dataExtractionRules="@xml/data_extraction_rules"`, a `res/xml/data_extraction_rules.xml` w sekcjach `cloud-backup` i `device-transfer` wyklucza domeny `database` i `sharedpref`. Ta specyfikacja nie zmienia tych plików.

**Kryteria akceptacji:**

- **Zakładając** pliki `app/src/main/AndroidManifest.xml` i `app/src/main/res/xml/data_extraction_rules.xml`, **gdy** test JVM je przeczytał, **wtedy** `allowBackup` = `false`, `fullBackupContent` = `false`, `dataExtractionRules` = `@xml/data_extraction_rules`, a obie sekcje `cloud-backup` i `device-transfer` zawierają `<exclude domain="database" />` i `<exclude domain="sharedpref" />`.
- **Zakładając** telefon z wersją debug, zapisanym numerem i historią (sprawdzenie na urządzeniu, W6), **gdy** wywołano `adb shell bmgr backupnow pl.twojanazwa.aapsremote`, **wtedy** kopia aplikacji nie jest wykonywana.

### DATA-17: Brak ujawniania danych

Numer telefonu, ilość węglowodanów, ilość bolusa, numer i procent profilu oraz treść odpowiedzi AAPS (a także limity poleceń – LIM-12) nie trafiają do `toString()` typów z `data` i `data.platform` (stały tekst `NazwaTypu(***)` albo tekst bez tych danych), do komunikatów wyjątków ani do logów. Pakiety `data` i `data.platform` nie wywołują API logowania (`android.util.Log`, `println`, `System.out`/`err`, loggerów). Komunikaty wyjątków rzucanych przez `data` są stałymi tekstami.

**Kryteria akceptacji:**

- **Zakładając** stan `Configured(+447700900123)`, wyniki zapisu i usunięcia numeru oraz obiekt ustawień, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `7700`, `900123` ani `700900123`.
- **Zakładając** wpis i wiersz z DATA-07 (kryterium 2: `CARBS 20`, `carbs-sukces.txt`, identyfikator 1) oraz stan `Entries([wpis])`, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `20`, `Pomyslnie`, `89%` ani żadnej linii treści.
- **Zakładając** wpis `BG` z treścią `bg.txt`, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `210` ani `IOB`.
- **Zakładając** źródła `data` i `data.platform`, **gdy** test JVM przeszukał je jako tekst, **wtedy** nie zawierają `android.util.Log`, `println(`, `System.out` ani `System.err`.

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Numer niepoprawny według `AapsNumber.parse` (także pusty) | `Invalid`, magazyn niewywołany, stan bez zmian | DATA-03 |
| `commit()` = `false` / wyjątek przy zapisie numeru | `Failed`, stan bez zmian | DATA-03, DATA-15 |
| `commit()` = `false` / wyjątek przy usunięciu numeru | `Failed`, stan bez zmian | DATA-04, DATA-15 |
| Odczyt pliku ustawień rzuca | `LoadFailed`; `currentNumber()` = `null`; zapis dozwolony | DATA-02, DATA-05 |
| W pliku niepoprawny numer | `NotConfigured`, plik bez zmian | DATA-02 |
| `currentNumber()` przed wczytaniem | `null` – `sms` nie wysyła (`Failed`) i nic nie dostarcza (SMS-04, SMS-12) | DATA-05 |
| Zapis lub usunięcie przed wczytaniem | wczytanie pominięte, stan z zapisu | DATA-02 |
| Równoczesne operacje na numerze | wykonywane po kolei | DATA-06 |
| Anulowanie po `commit()` | `CancellationException`, stan = zawartość pliku | DATA-03, DATA-04, DATA-06 |
| Błąd w transakcji zapisu wpisu | `Failed`, historia bez zmian, brak ponawiania | DATA-10, DATA-15 |
| Więcej niż 200 wpisów przed zapisem | po zapisie 200, usunięte najstarsze | DATA-10 |
| Błąd odczytu bazy | `Unavailable`, koniec strumienia | DATA-11 |
| Błąd czyszczenia | `Failed` | DATA-12 |
| Nieznany tekst w wierszu (np. po zmianie wartości wyliczeniowych) | `UNRECOGNIZED` / `Unrecognized` / powód = brak, bez wyjątku | DATA-09 |
| Stan sesji przekazany wiele razy / po zakończeniu | jeden wpis | DATA-13 |
| Zapis wpisu nieudany w rejestratorze | brak wpisu, brak ponawiania, bez wyjątku | DATA-13 |
| Proces zabity w trakcie sesji | brak wpisu (poza zakresem) | – |
| Zmiana schematu bez migracji | niedozwolona (zmiana spec) | DATA-14 |

## Bezpieczeństwo

Zasady z [AGENTS.md](../../../AGENTS.md#bezpieczeństwo), które dotyczą tej funkcji:

- **PIN, kod TOTP i sekret nigdy nie trafiają do historii.** Wynik sesji ich nie zawiera (SESSION-21), a wpis ma zamkniętą listę pól (DATA-08). Treść odpowiedzi to wyłącznie tekst SMS-a od numeru AAPS (po filtrze `sms`): odpowiedź BG/PUMP, stan i lista profili, potwierdzenie CARBS lub bolusa, „zły kod”, prośba o kod (w `Failed` z `AMOUNT_MISMATCH`, `UNEXPECTED_REPLY`, `CODE_REQUEST_EXPIRED` i w `Unknown` z `UNEXPECTED_REPLY`, `AMOUNT_MISMATCH`) albo tekst nierozpoznany. Prośba o kod z AAPS 3.4.2.6 nie zawiera kodu ani PIN-u – zawiera godzinę, ilość i imię ustawione w AAPS (`dla: Jan` w danych testowych). Odpowiedź z kodem wysyłana przez aplikację nigdy nie jest w wyniku sesji. Ryzyko szczątkowe: nierozpoznany SMS z numeru AAPS (np. prywatna wiadomość, gdy telefon z AAPS służy też do innych celów) trafi do historii w całości – zapis treści to decyzja użytkownika; historię można wyczyścić (DATA-12).
- **Z wiadomości nieoczekiwanych tylko liczba** (DATA-08). Wiadomość nieoczekiwana to m.in. potwierdzenie CARBS wysłane przez AAPS do wszystkich dozwolonych numerów (polecenie z innego telefonu); jej treść nie dotyczy polecenia z aplikacji, a liczba wystarcza do ostrzeżenia w `ui`.
- **Numer telefonu nigdy nie jest logowany ani pokazywany w `toString()`** (DATA-17); wpis historii nie zawiera numeru (DATA-08). W testach i dokumentacji tylko numery fikcyjne.
- **Ilość CARBS, ilość bolusa, profil i treść odpowiedzi to dane zdrowotne:** redagowane `toString()`, bez logowania (DATA-17), bez kopii zapasowej i transferu urządzenia (DATA-16).
- **Brak szyfrowania pliku ustawień i bazy** (dla numeru – decyzja użytkownika; dla historii – decyzja tej specyfikacji): dane leżą w prywatnym katalogu aplikacji, niedostępnym dla innych aplikacji bez roota, i nie opuszczają telefonu przez kopię (DATA-16). Szyfrowanie kluczem z Keystore wymagającym odblokowanego telefonu blokowałoby zapis historii przy zablokowanym ekranie, a SQLCipher dodałby zależność natywną; korzyść jest mała wobec danych, które i tak pokazuje AAPS. Root i modyfikacje systemu są poza zakresem (jak w Spec 006).
- **Nieznany numer = nic nie jest wysyłane ani przyjmowane.** Przed wczytaniem i przy błędzie odczytu `currentNumber()` = `null`, a `sms` wtedy odpowiada `Failed` bez wysyłania i nie dostarcza wiadomości (SMS-04, SMS-12) – bezpieczny stop (DATA-05).
- **Zmiana numeru w trakcie polecenia.** `sms` czyta numer przy każdym broadcaście i każdym wysłaniu (SMS-04, SMS-09), więc zmiana numeru po przyjęciu prośby o kod, a przed wysłaniem odpowiedzi skierowałaby `<kod><PIN>` na nowy numer. Dlatego zmiana i usunięcie numeru są niedostępne w `ui`, gdy jest aktywne polecenie (wymaganie dla `ui`, sekcja „Założenia i zależności”). Rozwiązanie po stronie sesji (numer ustalany raz na polecenie) – „Przyszłe zmiany”.
- **Brak ponawiania.** Zapis wpisu nie jest ponawiany (DATA-10, DATA-13); `data` nie wysyła SMS-ów i nie wpływa na sesję.
- **Kopia zapasowa:** `allowBackup=false` i reguły wykluczeń bez zmian, pilnowane testem (DATA-16).

## Dane testowe

- Numery telefonu: wyłącznie z zakresu Ofcom dla fikcji („drama numbers”, jak w Spec 005): `+44 7700 900123` (`+447700900123`), `+44 7700 900999` (`+447700900999`), wariant `+44 (0)7700 900123`; niepoprawne: `70090012`, `+44 7700 9OO123` (litera `O`), `44+7700900123`, `""`, `"   "`.
- Treści odpowiedzi: pliki z [`../002-parser-odpowiedzi/dane/`](../002-parser-odpowiedzi/dane/README.md) (`bg.txt`, `pump.txt`, `carbs-prosba-o-kod.txt`, `carbs-sukces.txt`, `zly-kod.txt`); syntetyczne: `Pomyslnie wprowadzono 7 g weglowodanow`, `Inny tekst 4321`, tekst 2000 znaków `a`.
- Wyniki sesji budowane bezpośrednio jako `SessionResult` (bez uruchamiania sesji); `Command.Carbs.of(20)`; stany sesji jako `MutableStateFlow<SessionState>`.
- Chwile: `T0 = 1111111051000` i przesunięcia (fikcyjne).
- Fikcyjne nieznane wartości tekstowe: `TBR`, `PARTIAL`, `NO_SIGNAL`.
- Brak nowych plików w `dane/`.

## Weryfikacja na urządzeniu

Fakty o Androidzie i Room przyjęte w tej specyfikacji, do potwierdzenia ręcznie na telefonie, gdy powstanie `ui` (ekran ustawień i historii; decyzja jak P3 w Spec 005: bez Robolectric i `androidTest`). Wyniki (telefon, wersja Androida, wynik każdego punktu) opisuje pierwszy PR `ui`, który zapisuje numer i pokazuje historię. Wynik niezgodny z założeniem = zmiana spec, nie obejście w kodzie.

| Nr | Do sprawdzenia | Wymaganie |
| --- | --- | --- |
| W1 | Zapis numeru, zabicie procesu (`adb shell am kill`), ponowny start: stan `Configured` z tym samym numerem; polecenie `BG` wychodzi na ten numer | DATA-02, DATA-03, DATA-05 |
| W2 | `shared_prefs/settings.xml` (wersja debug, `adb shell run-as`) zawiera tylko `aaps_number` z postacią znormalizowaną; po usunięciu numeru – brak klucza | DATA-01, DATA-04 |
| W3 | Po poleceniu `BG` jest `databases/aaps_remote.db`; po zabiciu procesu i ponownym starcie historia zawiera wpis | DATA-11, DATA-14 |
| W4 | Retencja: przy zatrzymanej aplikacji kopia bazy pobrana przez `adb exec-out run-as pl.twojanazwa.aapsremote cat databases/aaps_remote.db`, 205 wierszy wstawionych narzędziem `sqlite3` na komputerze, baza zapisana z powrotem (`run-as … sh -c 'cat > databases/aaps_remote.db'`, bez plików `-wal`/`-shm`); po jednym poleceniu `BG` tabela ma 200 wierszy bez 6 o najmniejszych identyfikatorach | DATA-10 |
| W5 | Czyszczenie historii, ponowny start: historia pusta; nowy wpis ma identyfikator większy od wcześniejszych | DATA-12, DATA-14 |
| W6 | `adb shell bmgr backupnow pl.twojanazwa.aapsremote`: kopia nie jest wykonywana | DATA-16 |
| W7 | Zimny start aplikacji z zapisanym numerem: przyciski poleceń (`ui`) nieaktywne do końca wczytania, potem polecenie `BG` wychodzi | DATA-02, DATA-05 |

## Otwarte pytania

Brak. Decyzje przyjęte przy tej specyfikacji:

- [x] Numer AAPS w zwykłym `SharedPreferences`, osobny plik, bez szyfrowania (decyzja użytkownika); zapis przez `commit()` (DATA-01).
- [x] Zapisywana postać znormalizowana (`AapsNumber.destination`), walidacja tylko przez `AapsNumber.parse` (DATA-01, DATA-03; uzasadnienie w design).
- [x] Zawartość wpisu historii: polecenie, ilość CARBS, rodzaj wyniku z powodem, cztery czasy, treść odpowiedzi AAPS, liczba wiadomości nieoczekiwanych (decyzja użytkownika); bez treści i nadawców wiadomości nieoczekiwanych (DATA-07, DATA-08).
- [x] Retencja 200 wpisów w jednej transakcji; czyszczenie tylko całej historii (decyzja użytkownika; DATA-10, DATA-12).
- [x] Zapis wyniku przez rejestrator (singleton) wywoływany przez `ui` po starcie sesji, bez zmiany Spec 004 (DATA-13; alternatywy w design).
- [x] Testy: logika czysta w JVM, warstwa Androida ręcznie W1–W7 (jak P3 w Spec 005).

## Przyszłe zmiany (nie blokują)

- **Numer ustalany raz na polecenie.** Sesja (albo `sms`) mogłaby pobrać numer raz przy starcie polecenia i używać go także do wysłania odpowiedzi z kodem, zamiast czytać go przy każdym wysłaniu. Usuwa to zależność od blokady w `ui` (sekcja Bezpieczeństwo); wymaga zmiany Spec 004 albo Spec 005.
- **Testy automatyczne DAO** (Robolectric albo `androidTest` z bazą w pamięci), jeśli zapytania Room się rozrosną; dziś retencję i kolejność sprawdzają testy logiki z atrapą, test wyeksportowanego schematu i W3–W5.

## Historia zmian

- **Planowane zmiany (Spec 013, 2026-10-30).** DATA-07: kryterium zmiany profilu z numerem 1 (aplikacja zmienia tylko profil 1, [PROF-17](../013-profil/spec.md#prof-17-historia)); wpisy z innym numerem mogą istnieć tylko sprzed zmiany. Schemat i kolumny bez zmian. Treść kryterium zostanie przepisana w zadaniu 4 design 013 po wdrożeniu; do tego czasu obowiązuje Spec 013 (PROF-17: nowe wpisy mają numer 1).

- **2026-10-29 – scalenie zmian ze Spec 011 – 013 (po wdrożeniu).**
  - Nowy plik ustawień `command_limits` (LIM-04); DATA-01 i plik `settings` bez zmian; DATA-16 obejmuje oba pliki (domena `sharedpref`).
  - DATA-07, DATA-09: nowe polecenia i rodzaj wyniku `BOLUS_CONFIRMED`, pola ilości bolusa, numeru i procentu profilu (BOL-17, PROF-17).
  - DATA-14: baza w wersji 2 (migracja 1 → 2, kolumna `bolus_centi_units`) i 3 (migracja 2 → 3, `profile_number`, `profile_percent`), bez migracji destrukcyjnych.
