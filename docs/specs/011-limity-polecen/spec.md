# Spec 011: Limity poleceń w ustawieniach

- **Status:** zatwierdzona
- **Prefiks wymagań:** `LIM`
- **Pakiety:** `commands` (limity jako dane, walidacja), `data` / `data.platform` (zapis limitów), `ui.settings` (sekcja „Limity poleceń”), `ui.commands` (użycie limitów przy `CARBS` i `BOLUS`, sprawdzenie przy starcie)
- **Powiązane:** [docs/plan.md](../../plan.md#ekrany-aplikacji); [Spec 003: Katalog poleceń](../003-katalog-polecen/spec.md) (CMD-01, CMD-03 – CMD-06 – zmieniane tą specyfikacją); [Spec 007: Dane](../007-dane/spec.md) (wzorzec `SharedPreferences` + `commit()`, DATA-06, DATA-15 – DATA-17); [Spec 008: UI – ustawienia](../008-ui-ustawienia/spec.md) (UI-15, UI-16, UI-20, UI-21); [Spec 009: UI – polecenia](../009-ui-polecenia/spec.md) (CMDUI-07 – CMDUI-09, CMDUI-12); [Spec 012: Bolus](../012-bolus/spec.md) (limit ilości i odstęp między bolusami – użycie); [Spec 013: Profil](../013-profil/spec.md)

## Cel

Użytkownik sam ustala w aplikacji dodatkowe limity poleceń, które wysyła przez SMS: największą ilość `CARBS`, największą ilość `BOLUS` i minimalny odstęp między bolusami. Limity aplikacji działają obok limitów AAPS i nigdy ich nie zastępują ani nie omijają. Ta specyfikacja opisuje wartości limitów, ich zakresy, zapis, sekcję w Ustawieniach i to, jak `CARBS` korzysta z limitu zamiast stałej 50 g.

## Zakres

- Trzy limity: największa ilość `CARBS` (g), największa ilość `BOLUS` (j.), minimalny odstęp między bolusami (min) – wartości domyślne, zakresy, walidacja tekstu z pól.
- Zapis limitów w osobnym pliku `SharedPreferences`, stan wczytania, błędy bez wyjątków.
- Sekcja „Limity poleceń” na ekranie Ustawienia: pola, zapis, błędy, blokada w trakcie polecenia.
- `CARBS`: górna granica z ustawień zamiast stałej 50 g, próg pytania „Na pewno” liczony z limitu, szybkie wartości ograniczone do progu.
- Dostępność `CARBS` i `BOLUS`, gdy limity nie są wczytane.
- Ponowne sprawdzenie ilości z aktualnym limitem tuż przed startem polecenia.

**Poza zakresem:**

- Walidacja ilości bolusa, pytanie „Na pewno X j?”, sprawdzanie odstępu między bolusami – [Spec 012](../012-bolus/spec.md) (korzysta z wartości z tej specyfikacji).
- Limity czasu sesji (3 min / 6 min) jako ustawienie – nadal stałe (Spec 004).
- Limit procentu profilu – stała w [Spec 013](../013-profil/spec.md), nie ustawienie.
- Dolna granica `CARBS` (zostaje 1 g) i szybkie wartości jako ustawienie (zostają 10, 20, 30 g).
- Odczyt limitów z AAPS – aplikacja ich nie zna; AAPS i tak stosuje własne (A3 w Spec 004).
- Ochrona hasłem lub PIN-em przed zmianą limitów.

## Założenia i zależności

- Limity są **dodatkowym bezpiecznikiem aplikacji**. AAPS stosuje własne limity (max bolus, max węglowodany, minimalny odstęp między zdalnymi bolusami) niezależnie od aplikacji. Ustawienie limitu aplikacji wyżej niż w AAPS niczego nie omija: AAPS obetnie ilość, a aplikacja zatrzyma polecenie bez kodu (niezgodna ilość, SESSION-11).
- Dane użytkownika (z rozmowy, nie z kodu): w AAPS użytkownika max bolus = 5 j., minimalny odstęp między zdalnymi bolusami = 15 min. Wartości domyślne aplikacji są im równe (bolus, odstęp) albo równe dzisiejszej stałej (`CARBS` 50 g), żeby po aktualizacji nic się nie zmieniło bez decyzji użytkownika.
- Plik `SharedPreferences` jest w domenie `sharedpref`, wykluczonej w całości z kopii i transferu (`data_extraction_rules.xml`, DATA-16); manifest ma `allowBackup=false`.
- Testy: logika w JVM (JUnit 5, MockK, Turbine, `kotlinx-coroutines-test`); kompozycje i plik na urządzeniu – weryfikacja ręczna (sekcja „Weryfikacja na urządzeniu”), jak w Spec 007 – 009.

## Pojęcia

- **Limity** – trójka wartości: `carbsMaxGrams` (liczba całkowita g), `bolusMaxCentiUnits` (setne części jednostki, np. 5,00 j. = 500), `bolusMinIntervalMinutes` (liczba całkowita minut; 0 = sprawdzanie wyłączone).
- **Próg „Na pewno” `CARBS`** – ilość, powyżej której `CARBS` wymaga dodatkowego pytania przed PIN-em (dziś stała 40 g).
- **Stan limitów** – `Loading`, `Loaded(limity)`, `LoadFailed`.
- **Pole liczby całkowitej** – pole tekstowe ustawień z liczbą całkowitą (limit `CARBS`, odstęp); **pole ilości insuliny** – pole z liczbą dziesiętną (limit `BOLUS`).

## Wymagania

Wspólne dane kryteriów (o ile kryterium nie mówi inaczej): limity domyślne, atrapa magazynu w pamięci, dyspozytory testowe (`StandardTestDispatcher`) w miejscu `Dispatchers.IO` i zakresu aplikacji, `activeSession.value == null`.

### LIM-01: Limity i wartości domyślne

Aplikacja ma dokładnie trzy limity poleceń z wartościami domyślnymi: `CARBS` 50 g, `BOLUS` 5,00 j. (500), odstęp między bolusami 15 min. Wartości domyślne obowiązują, gdy użytkownik nigdy nie zapisał limitów.

**Kryteria akceptacji:**

- **Zakładając** domyślne limity, **gdy** odczytano wartości, **wtedy** `carbsMaxGrams` = 50, `bolusMaxCentiUnits` = 500, `bolusMinIntervalMinutes` = 15.
- **Zakładając** pusty magazyn, **gdy** wczytanie się zakończyło, **wtedy** stan = `Loaded` z limitami domyślnymi.

### LIM-02: Zakresy limitów

Każdy limit ma zakres (włącznie z granicami) i krok. Utworzenie limitów z wartością spoza zakresu albo niezgodną z krokiem jest niemożliwe (wynik walidacji z rodzajem błędu, bez wyjątku dla danych od użytkownika).

| Limit | Najmniej | Najwięcej | Krok | Domyślnie |
| --- | --- | --- | --- | --- |
| `CARBS` (g) | 10 | 100 | 1 | 50 |
| `BOLUS` (j.) | 0,05 (5) | 10,00 (1000) | 0,05 (5) | 5,00 (500) |
| Odstęp między bolusami (min) | 0 | 60 | 1 | 15 |

Zakresy przyjęte decyzją użytkownika (P2).

**Kryteria akceptacji:**

- **Zakładając** `carbsMaxGrams` kolejno 9, 10, 100, 101, **gdy** utworzono limity, **wtedy** wyniki: poniżej minimum, poprawne, poprawne, powyżej maksimum.
- **Zakładając** `bolusMaxCentiUnits` kolejno 0, 5, 1000, 1005, 252, **gdy** utworzono limity, **wtedy** wyniki: poniżej minimum, poprawne, poprawne, powyżej maksimum, niezgodne z krokiem.
- **Zakładając** `bolusMinIntervalMinutes` kolejno −1, 0, 60, 61, **gdy** utworzono limity, **wtedy** wyniki: poniżej minimum, poprawne, poprawne, powyżej maksimum.
- **Zakładając** `Int.MIN_VALUE` i `Int.MAX_VALUE` w każdym polu, **gdy** utworzono limity, **wtedy** wynik to poniżej minimum / powyżej maksimum, bez wyjątku.

### LIM-03: Tekst pól ustawień

Tekst z pól jest walidowany bez przycinania. Reguły (pierwsza pasująca decyduje):

Pole liczby całkowitej (`CARBS`, odstęp) – jak CMD-04: pusty → „pusta”; znak inny niż cyfra ASCII → „niedozwolone znaki”; wartość po usunięciu zer wiodących oceniana według LIM-02 (więcej niż 3 cyfry znaczące → „powyżej maksimum”, bez przepełnienia).

Pole ilości insuliny (`BOLUS`) – reguły ilości bolusa z [BOL-03](../012-bolus/spec.md#bol-03-ilość-bolusa-z-tekstu) (przecinek albo kropka, najwyżej 2 miejsca po przecinku), oceniane według zakresu i kroku z LIM-02.

**Kryteria akceptacji:**

| Pole | Tekst | Wynik |
| --- | --- | --- |
| `CARBS` | `50` | poprawna, 50 |
| `CARBS` | `075` | poprawna, 75 |
| `CARBS` | (pusty) | pusta |
| `CARBS` | `5` | poniżej minimum |
| `CARBS` | `101` | powyżej maksimum |
| `CARBS` | `99999999999999999999` | powyżej maksimum, bez wyjątku |
| `CARBS` | `50 ` | niedozwolone znaki |
| `CARBS` | `50,5` | niedozwolone znaki |
| odstęp | `0` | poprawna, 0 |
| odstęp | `15` | poprawna, 15 |
| odstęp | `61` | powyżej maksimum |
| odstęp | `-1` | niedozwolone znaki |
| `BOLUS` | `5` | poprawna, 500 |
| `BOLUS` | `2,5` | poprawna, 250 |
| `BOLUS` | `0.05` | poprawna, 5 |
| `BOLUS` | `10,00` | poprawna, 1000 |
| `BOLUS` | `0` | poniżej minimum |
| `BOLUS` | `10,05` | powyżej maksimum |
| `BOLUS` | `2,52` | niezgodna z krokiem |
| `BOLUS` | `2,555` | za dużo miejsc po przecinku |
| `BOLUS` | `2,5 j` | niedozwolone znaki |

- **Zakładając** każdy wiersz tabeli, **gdy** zwalidowano tekst w danym polu, **wtedy** wynik jest dokładnie taki jak w tabeli, bez wyjątku.

### LIM-04: Zapis i wczytanie limitów

Limity są w osobnym pliku `SharedPreferences` o nazwie `command_limits` (`MODE_PRIVATE`), pod kluczami `carbs_max_grams`, `bolus_max_centi_units`, `bolus_min_interval_minutes` (wartości `Int`). Plik nie zawiera innych kluczy. Zapis zapisuje zawsze wszystkie trzy wartości jednym `commit()` (jedna transakcja edytora) i używa jego wyniku; `apply()` nie jest używane. Plik `settings` ze Spec 007 (DATA-01) się nie zmienia.

Stan zaczyna jako `Loading`; wczytanie rusza raz przy utworzeniu singletonu, w zakresie aplikacji, na dyspozytorze I/O (jak DATA-02). Wynik wczytania:

| Zawartość pliku | Stan |
| --- | --- |
| brak wszystkich trzech kluczy | `Loaded(domyślne)` |
| wszystkie trzy klucze z wartościami poprawnymi (LIM-02) | `Loaded(wartości z pliku)` |
| brak części kluczy (obecny co najmniej jeden, brak co najmniej jednego) | `LoadFailed` (zapis zawsze zapisuje trzy klucze naraz, więc taki plik jest uszkodzony) |
| którakolwiek wartość spoza zakresu, niezgodna z krokiem albo innego typu niż `Int` | `LoadFailed` |
| odczyt rzucił wyjątek (poza `CancellationException`) | `LoadFailed` |

Zapis przyjmuje poprawne limity (LIM-02): `commit()` = `true` → `Saved`, stan `Loaded(nowe)`; `false` albo wyjątek → `Failed`, stan bez zmian. Zapis jest dozwolony w każdym stanie (także `LoadFailed`) i jest jedynym sposobem wyjścia z `LoadFailed`. Wczytanie, zapis i stan są serializowane jak w DATA-06; operacje nie rzucają poza `CancellationException` (jak DATA-15).

**Kryteria akceptacji:**

- **Zakładając** magazyn z `carbs_max_grams` = 30, `bolus_max_centi_units` = 250, `bolus_min_interval_minutes` = 20, **gdy** wczytanie się zakończyło, **wtedy** stan = `Loaded` z tymi wartościami.
- **Zakładając** magazyn z samym `carbs_max_grams` = 30 (brak dwóch kluczy), **gdy** wczytanie się zakończyło, **wtedy** stan = `LoadFailed`, a `CARBS` i `BOLUS` są niedostępne (LIM-05).
- **Zakładając** magazyn z `bolus_max_centi_units` = 252 (osobno: `carbs_max_grams` = 500; osobno: `bolus_min_interval_minutes` zapisany jako `String`), **gdy** wczytanie się zakończyło, **wtedy** stan = `LoadFailed`, a magazyn nie otrzymał zapisu.
- **Zakładając** magazyn rzucający `ClassCastException` przy odczycie, **gdy** wczytanie się zakończyło, **wtedy** stan = `LoadFailed`, bez wyjątku.
- **Zakładając** stan `LoadFailed`, **gdy** zapisano limity (40 g, 300, 20 min), **wtedy** wynik = `Saved`, stan = `Loaded` z tymi wartościami, a magazyn otrzymał jeden zapis z trzema kluczami.
- **Zakładając** magazyn zwracający `false` z `commit()` (osobno: rzucający `RuntimeException("x")`), **gdy** zapisano limity, **wtedy** wynik = `Failed` bez wyjątku, stan bez zmian.
- **Zakładając** konstruktor obiektu limitów, **gdy** utworzono go bez wykonania zadań dyspozytora, **wtedy** stan = `Loading`, a magazyn nie był czytany.
- **Zakładając** telefon z wersją debug (W1), **gdy** zapisano limity i odczytano `shared_prefs/command_limits.xml` przez `adb shell run-as`, **wtedy** plik zawiera tylko trzy klucze z tej specyfikacji.

### LIM-05: Polecenia z ilością, gdy limity nie są wczytane

`CARBS` i `BOLUS` są dostępne tylko w stanie limitów `Loaded`. W stanie `Loading` ich przyciski są niedostępne (bez banera); w stanie `LoadFailed` są niedostępne, a panel poleceń pokazuje baner „Nie udało się wczytać limitów poleceń. Zapisz je ponownie w Ustawieniach.” z przyciskiem „Przejdź do Ustawień”. Aplikacja nigdy nie używa wartości domyślnych zamiast nieodczytanych limitów (mogłyby być wyższe niż ustawione przez użytkownika). `BG`, `PUMP`, `PROFILE STATUS`, `PROFILE LIST` i zmiana profilu nie zależą od limitów.

**Kryteria akceptacji:**

- **Zakładając** stan limitów kolejno `Loading`, `LoadFailed`, `Loaded` (Turbine), **gdy** `CommandsViewModel` zbiera stan, **wtedy** `CARBS` i `BOLUS` są kolejno: niedostępne bez banera, niedostępne z banerem limitów, dostępne; `BG` i `PUMP` są dostępne we wszystkich trzech stanach.

### LIM-06: Górna granica CARBS z ustawień

Zmiana CMD-03 i CMD-04: polecenie `CARBS` można utworzyć z ilością od 1 g do `carbsMaxGrams` włącznie (zamiast stałej 50). Walidacja liczby i tekstu przyjmuje limity jako argument; reguły i rodzaje błędów CMD-03/CMD-04 zostają, z „powyżej maksimum” liczonym od `carbsMaxGrams`. Teksty błędów pola ilości (CMDUI-07) biorą górną granicę z bieżących limitów: „Podaj ilość węglowodanów (1–X g).” i „Największa ilość to X g.”.

**Kryteria akceptacji:**

- **Zakładając** `carbsMaxGrams` = 30, **gdy** utworzono `CARBS` z 30 i z 31, **wtedy** wyniki: poprawna, powyżej maksimum.
- **Zakładając** `carbsMaxGrams` = 100, **gdy** zwalidowano tekst `100` i `0100`, **wtedy** oba dają poprawną ilość 100; tekst `101` – powyżej maksimum.
- **Zakładając** limity domyślne, **gdy** sprawdzono wszystkie kryteria CMD-03 i CMD-04, **wtedy** wyniki są takie same jak przed zmianą.
- **Zakładając** `carbsMaxGrams` = 30 i tekst pola `"35"`, **gdy** wywołano „Dalej”, **wtedy** błąd = powyżej maksimum z argumentem 30, podsumowanie zamknięte.

### LIM-07: Próg „Na pewno” dla CARBS

Zmiana CMD-05: `CARBS` wymaga dodatkowego potwierdzenia, gdy ilość > `⌊carbsMaxGrams × 4 / 5⌋` (80% limitu, zaokrąglone w dół). Przy limicie domyślnym 50 g próg = 40 g, czyli zachowanie jak dziś. Tekst okna (CMDUI-09) bierze próg z limitów: „To więcej niż P g. Sprawdź ilość, zanim wpiszesz PIN.” (decyzja użytkownika, P1).

**Kryteria akceptacji:**

| `carbsMaxGrams` | Próg | Ilość bez pytania | Ilość z pytaniem |
| --- | --- | --- | --- |
| 50 | 40 | 40 | 41 |
| 100 | 80 | 80 | 81 |
| 10 | 8 | 8 | 9 |
| 33 | 26 | 26 | 27 |

- **Zakładając** każdy wiersz tabeli, **gdy** sprawdzono oznaczenie dodatkowego potwierdzenia dla podanych ilości, **wtedy** jest ono fałszywe dla kolumny „bez pytania” i prawdziwe dla kolumny „z pytaniem”.

### LIM-08: Szybkie wartości CARBS a limit

Zmiana CMD-06 i CMDUI-08: panel pokazuje tylko te szybkie wartości z `10, 20, 30` g, które są ≤ progowi „Na pewno” (LIM-07) – dzięki temu szybka wartość nigdy nie wymaga dodatkowego pytania i zawsze mieści się w limicie.

**Kryteria akceptacji:**

- **Zakładając** `carbsMaxGrams` = 50 (próg 40), **gdy** odczytano szybkie wartości, **wtedy** lista = `[10, 20, 30]`.
- **Zakładając** `carbsMaxGrams` = 30 (próg 24), **gdy** odczytano szybkie wartości, **wtedy** lista = `[10, 20]`.
- **Zakładając** `carbsMaxGrams` = 10 (próg 8), **gdy** odczytano szybkie wartości, **wtedy** lista jest pusta, a panel nie pokazuje przycisków szybkich wartości.

### LIM-09: Ponowne sprawdzenie limitu przy starcie

`CommandLauncher.launch` dla `CARBS` i `BOLUS` sprawdza ilość z aktualnym stanem limitów, po sprawdzeniu uprawnień SMS (krok 3 CMDUI-12), a przed odczytem sekretu: stan inny niż `Loaded` → odrzucenie `LIMITS_UNAVAILABLE`; ilość > aktualnego limitu → odrzucenie `AMOUNT_ABOVE_LIMIT`; polecenie wymaga dodatkowego potwierdzenia według aktualnych limitów (LIM-07, BOL-04), a użytkownik go nie udzielił (`launch` dostaje znacznik `extraConfirmed` = okno „Na pewno” zatwierdzone przyciskiem „Tak”) → odrzucenie `EXTRA_CONFIRMATION_REQUIRED`. Ilość nie jest przycinana. Całe sprawdzenie (stan, limit, próg) korzysta z jednej migawki stanu limitów odczytanej raz na wywołanie `launch` – zmiana limitów w trakcie sprawdzenia nie daje mieszanki starych i nowych wartości. Przy odrzuceniu PIN jest czyszczony, brak startu (jak CMDUI-12). Komunikaty:

| Powód | Komunikat | Akcja |
| --- | --- | --- |
| `LIMITS_UNAVAILABLE` | „Nie udało się wczytać limitów poleceń. Zapisz je ponownie w Ustawieniach.” | Ustawienia |
| `AMOUNT_ABOVE_LIMIT` | „Ilość przekracza limit z Ustawień. Popraw ilość i spróbuj ponownie.” | – |
| `EXTRA_CONFIRMATION_REQUIRED` | „Limit w Ustawieniach się zmienił i ta ilość wymaga potwierdzenia. Sprawdź ilość i spróbuj ponownie.” | – |

Pozostałe polecenia nie przechodzą tego sprawdzenia.

**Kryteria akceptacji:**

- **Zakładając** polecenie `CARBS 40` utworzone przy limicie 50 i limit zmieniony potem na 30, **gdy** wywołano `launch(CARBS 40, PIN 4321)`, **wtedy** wynik = `AMOUNT_ABOVE_LIMIT`, PIN wyczyszczony, `load()` sekretu nie był wywołany, brak startu.
- **Zakładając** stan limitów `LoadFailed`, **gdy** wywołano `launch(CARBS 20, PIN)`, **wtedy** wynik = `LIMITS_UNAVAILABLE`, PIN wyczyszczony, brak startu.
- **Zakładając** stan limitów `LoadFailed`, **gdy** wywołano `launch(BG, null)`, **wtedy** wynik = `Started`.
- **Zakładając** polecenie `CARBS 35` zatwierdzone bez okna „Na pewno” przy limicie 50 (próg 40) i limit zmieniony potem na 40 (próg 32), **gdy** wywołano `launch(CARBS 35, PIN, extraConfirmed = false)`, **wtedy** wynik = `EXTRA_CONFIRMATION_REQUIRED`, PIN wyczyszczony, brak startu; z `extraConfirmed = true` – `Started`.
- **Zakładając** każdą nową wartość `LaunchRejection`, **gdy** odwzorowano ją na komunikat, **wtedy** komunikat i akcja odpowiadają tabeli.

### LIM-10: Sekcja „Limity poleceń” w Ustawieniach

Ekran Ustawienia ma sekcję „Limity poleceń” (pod sekcjami ze Spec 008) z trzema polami: „Największa ilość węglowodanów (g)” (`KeyboardType.Number`), „Największa ilość bolusa (j.)” (`KeyboardType.Decimal`), „Najkrótszy odstęp między bolusami (min, 0 = bez sprawdzania)” (`KeyboardType.Number`), tekstem „Limity aplikacji działają obok limitów AAPS i ich nie zastępują.” i przyciskiem „Zapisz limity”. Po wczytaniu (`Loaded`) pola pokazują bieżące wartości (bolus z przecinkiem i 2 miejscami, np. `5,00`); w stanie `Loading` pola i „Zapisz” są niedostępne; w stanie `LoadFailed` pola pokazują wartości domyślne i tekst „Nie udało się wczytać limitów. Sprawdź wartości i zapisz je ponownie.”. „Zapisz limity” waliduje wszystkie trzy pola (LIM-03); gdy którekolwiek jest błędne, pod każdym błędnym polem jest błąd według tabeli i nic nie jest zapisywane. Gdy wszystkie są poprawne – jeden zapis (LIM-04) i komunikat „Zapisano limity.” albo „Nie udało się zapisać limitów. Spróbuj ponownie.”. Zmiana tekstu pola usuwa jego błąd.

| Błąd | Tekst pod polem |
| --- | --- |
| pusta | „Podaj wartość (A–B).” |
| niedozwolone znaki | „Tylko cyfry” (pola liczb całkowitych) / „Podaj liczbę, np. 2,5.” (bolus) |
| poniżej minimum | „Najmniejsza wartość to A.” |
| powyżej maksimum | „Największa wartość to B.” |
| niezgodna z krokiem | „Podaj wielokrotność 0,05 j.” |
| za dużo miejsc po przecinku | „Najwyżej 2 miejsca po przecinku.” |

A i B to granice z LIM-02 dla danego pola (bolus: `0,05` i `10,00`).

**Kryteria akceptacji:**

- **Zakładając** stan `Loaded(30 g, 250, 20 min)`, **gdy** utworzono ViewModel sekcji, **wtedy** teksty pól = `"30"`, `"2,50"`, `"20"`.
- **Zakładając** teksty `"5"`, `"2,52"`, `"15"`, **gdy** wywołano „Zapisz limity”, **wtedy** błędy: `CARBS` poniżej minimum, bolus niezgodny z krokiem, odstęp brak; magazyn nie dostał zapisu.
- **Zakładając** teksty `"60"`, `"3"`, `"0"`, **gdy** wywołano „Zapisz limity”, **wtedy** magazyn dostał jeden zapis (60, 300, 0), komunikat = zapisano.
- **Zakładając** stan `Loading`, **gdy** odczytano stan sekcji, **wtedy** „Zapisz limity” jest niedostępne.
- **Zakładając** telefon (W2), **gdy** zmieniono limity, zabito proces i uruchomiono aplikację, **wtedy** sekcja pokazuje zapisane wartości, a panel `CARBS` stosuje nowy limit.

### LIM-11: Blokada w trakcie polecenia i jedna operacja naraz

Jak UI-15 i UI-16 w Spec 008: gdy polecenie jest w toku, „Zapisz limity” jest niedostępne, a nad sekcją jest tekst „Trwa polecenie. Limity możesz zmienić po jego zakończeniu.”; akcja zapisu sprawdza `activeSession.value` w chwili wywołania (polecenie w toku → komunikat `msg_command_active`, brak wywołania magazynu). Pola można edytować. Od wywołania zapisu do wyniku „Zapisz limity” jest niedostępne, a ponowne wywołanie ignorowane.

Zapis i start polecenia nie są wzajemnie blokowane (jak UI-15: operacja już przekazana do magazynu kończy się normalnie). Gdy start polecenia nastąpi w trakcie zapisu, `CommandLauncher` sprawdza ilość według stanu limitów w chwili sprawdzenia (LIM-09), czyli według limitów sprzed zakończenia zapisu, które użytkownik sam wcześniej ustawił; nowe limity dotyczą kolejnych poleceń. Okno trwa tyle, co `commit()` pliku: zapis jest wywoływany na zakładce Ustawienia, a start na zakładce Polecenia, po ponownym otwarciu podsumowania (zmiana zakładki je zamyka, CMDUI-11).

**Kryteria akceptacji:**

- **Zakładając** `activeSession` ustawione na sesję bez wykonania zadań dyspozytora, **gdy** wywołano „Zapisz limity” z poprawnymi tekstami, **wtedy** magazyn nie dostał zapisu, a komunikat = polecenie w toku.
- **Zakładając** magazyn, którego zapis zawiesza się do zwolnienia, **gdy** dotknięto „Zapisz limity” dwa razy, **wtedy** do zwolnienia magazyn dostał dokładnie jeden zapis.
- **Zakładając** `activeSession` = sesja, **gdy** zmienił się na `null`, **wtedy** „Zapisz limity” jest dostępne (przy stanie `Loaded`).

### LIM-12: Brak ujawniania danych i logowania

Pakiety `commands`, `data`, `ui` nie logują limitów ani tekstów pól (reguły CMD-09, DATA-17, UI-21). `toString()` typów limitów, wyników walidacji i stanu sekcji nie zawiera wartości limitów ani tekstu wejściowego (stały tekst `***`). Teksty widoczne w UI są w `strings.xml` po polsku (jak UI-20).

**Kryteria akceptacji:**

- **Zakładając** limity (37 g, 235, 23 min) i stan sekcji z tymi tekstami, **gdy** wywołano `toString()` na limitach, stanie limitów, wyniku walidacji i stanie sekcji, **wtedy** żaden tekst nie zawiera `37`, `235`, `2,35` ani `23`.
- **Zakładając** źródła nowych plików, **gdy** test JVM je przeczytał, **wtedy** nie zawierają wywołań API logowania (wzorce jak w `UiNoLoggingTest`).

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Wartość limitu spoza zakresu / niezgodna z krokiem / zły tekst | błąd pod polem, nic nie zapisane | LIM-02, LIM-03, LIM-10 |
| Plik bez limitów (pierwsze uruchomienie, aktualizacja) | limity domyślne (50 g, 5,00 j., 15 min) | LIM-01, LIM-04 |
| Plik z wartością niepoprawną albo błąd odczytu | `LoadFailed`; `CARBS` i `BOLUS` niedostępne z banerem; inne polecenia działają | LIM-04, LIM-05 |
| Błąd zapisu | komunikat, stan bez zmian | LIM-04, LIM-10 |
| Limit zmniejszony poniżej ilości wpisanej w polu `CARBS` | „Dalej” → błąd „powyżej maksimum”; ilość nie jest przycinana | LIM-06 |
| Limit zmniejszony po utworzeniu polecenia, przed startem | `AMOUNT_ABOVE_LIMIT`, PIN wyczyszczony, brak startu | LIM-09 |
| Limit `CARBS` < 30 | szybkie wartości powyżej progu ukryte | LIM-08 |
| Zapis w trakcie polecenia / podwójne dotknięcie | komunikat „Trwa polecenie” / jeden zapis | LIM-11 |

## Bezpieczeństwo

- **Bez omijania limitów AAPS.** Limity aplikacji tylko zawężają to, co aplikacja pozwoli wysłać. Ilość ponad limit aplikacji daje błąd – nigdy przycięcie ani wysłanie innej ilości (LIM-06, LIM-09). Ilość obcięta przez AAPS nadal kończy polecenie bez kodu (SESSION-11, BOL-10).
- **Bezpieczna strona przy błędzie.** Nieodczytane limity nie są zastępowane domyślnymi (LIM-05); polecenia z ilością są wtedy niedostępne.
- **Bez danych w logach i `toString()`** (LIM-12). Limity nie są sekretem, ale opisują leczenie, więc są traktowane jak ilości (CMD-09).
- **Brak kopii zapasowej** – plik w wykluczonej domenie `sharedpref` (DATA-16); kryterium W1.
- **Zmiana w trakcie polecenia** zablokowana (LIM-11); trwająca sesja i tak ma już ustaloną ilość.
- Brak prawdziwych danych w repo: wartości z kryteriów są fikcyjne.

## Dane testowe

Fikcyjne wartości z kryteriów. Brak plików w `dane/`.

## Weryfikacja na urządzeniu

| Nr | Co sprawdzić | Wymagania |
| --- | --- | --- |
| W1 | Po zapisie limitów `shared_prefs/command_limits.xml` (`adb shell run-as`) ma tylko 3 klucze; `settings.xml` bez zmian | LIM-04, LIM-12 |
| W2 | Sekcja „Limity poleceń”: wartości domyślne po aktualizacji aplikacji, zapis, błędy pól, wartości po zabiciu procesu; w trakcie `BG` „Zapisz limity” nieaktywne z tekstem „Trwa polecenie” | LIM-01, LIM-10, LIM-11 |
| W3 | Limit `CARBS` 30: pole `35` → błąd „Największa ilość to 30 g.”; `25` → okno „Na pewno” (próg 24); szybkie wartości tylko 10 i 20 g | LIM-06 – LIM-08 |

## Otwarte pytania

Brak. Decyzje użytkownika przyjęte przy tej specyfikacji (2026-10-28):

- [x] **P1. Próg „Na pewno” dla `CARBS` przy zmienionym limicie.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – 80% limitu zaokrąglone w dół (LIM-07) – przy 50 g zostaje 40 g jak dziś. Alternatywa: połowa limitu jak przy bolusie (przy 50 g pytanie już od 26 g – zmiana zachowania).
- [x] **P2. Zakresy ustawień.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – (LIM-02) `CARBS` 10–100 g co 1 g; `BOLUS` 0,05–10,00 j. co 0,05 j.; odstęp 0–60 min co 1 min (0 = bez sprawdzania w aplikacji). Górne granice 100 g i 10 j. są świadomie niewysokie dla zdalnego SMS, a dolna granica `CARBS` to 10 g; zmiana zakresów wymaga zmiany spec.
- [x] **P3. Limit zmniejszony poniżej wpisanej ilości.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – brak przycinania; błąd przy „Dalej” (LIM-06) i drugie sprawdzenie przy starcie (`AMOUNT_ABOVE_LIMIT`, LIM-09). Otwarte podsumowanie i tak zamyka się przy przejściu do Ustawień (CMDUI-11).
- [x] **P4. Limity nieodczytane.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – `CARBS` i `BOLUS` niedostępne do ponownego zapisu limitów, bez użycia wartości domyślnych (LIM-05).
- [x] **P5. Potwierdzenie podniesienia limitu.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – bez dodatkowego okna przy zwiększaniu limitów (zapis jednym przyciskiem). Alternatywa: okno „Zwiększyć limit bolusa do X j.?”.

## Historia zmian

- **2026-10-28 – poprawki po ponownej recenzji PR #221.** LIM-09: jedna migawka stanu limitów na całe sprawdzenie. Design: wczytanie zgodne z LIM-04 (brak części kluczy = `LoadFailed`), `BolusAmountError` i `BolusAmountText` powstają w zadaniu 1 tej spec, test decyzji LIM-11 w mapowaniu.
- **2026-10-28 – poprawki po recenzji PR #221 i zatwierdzenie.** LIM-04: brak części kluczy = `LoadFailed` (spójnie z LIM-05). LIM-09: ponowne liczenie progu „Na pewno” przy starcie (`EXTRA_CONFIRMATION_REQUIRED`). Design: zadanie 4 (limity w panelu i launcherze) przed zadaniem 3 (sekcja w Ustawieniach), zadanie 3 z etykietą `bezpieczenstwo`. Pytania P1–P5 zamknięte, status `zatwierdzona`.
- **2026-10-28 – pierwsza wersja (szkic).** Razem ze Spec 012 i 013. Zmienia CMD-03 – CMD-06 (Spec 003), CMDUI-07 – CMDUI-09 i CMDUI-12 (Spec 009); dodaje sekcję do ekranu Ustawień (Spec 008).
