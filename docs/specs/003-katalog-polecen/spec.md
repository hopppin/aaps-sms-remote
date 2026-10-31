# Spec 003: Katalog poleceń

- **Status:** zatwierdzona
- **Prefiks wymagań:** `CMD`
- **Pakiety:** `commands`
- **Powiązane:** [docs/plan.md](../../plan.md#cel-i-zakres) (tabela poleceń MVP), [docs/plan.md](../../plan.md#przepływ-działania), [docs/plan.md](../../plan.md#ekrany-aplikacji) (ekran CARBS, szybkie wartości), [docs/plan.md](../../plan.md#otwarte-kwestie) (maksymalna ilość CARBS); [Spec 002: Parser odpowiedzi SMS z AAPS](../002-parser-odpowiedzi/spec.md) (kontekst `ExpectedCommand`, PARSER-01, PARSER-10); przyszłe specyfikacje `session` (maszyna stanów, PIN, odesłanie kodu), `sms` (wysyłanie), `ui` (ekrany i komunikaty)

## Cel

Polecenia, które aplikacja wysyła do AAPS, mają być opisane w jednym miejscu jako dane, a nie rozsiane po ekranach. Ta specyfikacja opisuje czystą logikę w Kotlinie: katalog poleceń (`BG`, `PUMP`, `CARBS <g>` oraz polecenia ze [Spec 012](../012-bolus/spec.md) i [Spec 013](../013-profil/spec.md): `BOLUS`, `PROFILE STATUS`, `PROFILE LIST`, zmiana profilu), walidację ilości węglowodanów, budowę treści SMS i odwzorowanie polecenia na kontekst parsera odpowiedzi.

## Zakres

- Katalog poleceń: `BG`, `PUMP`, `CARBS`, `BOLUS`, `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH` – identyfikator, słowo kluczowe SMS, parametr, czy AAPS wymaga kodu, sposób zatwierdzenia przez użytkownika. Reguły ilości `BOLUS` i zmiany profilu opisują Spec 012 i 013 (BOL-02 – BOL-05, PROF-02 – PROF-04).
- Parametr `CARBS`: liczba całkowita gramów od 1 do `carbsMaxGrams` z ustawień ([Spec 011](../011-limity-polecen/spec.md), domyślnie 50 g), szybkie wartości 10, 20, 30 g ograniczone do progu, próg dodatkowego potwierdzenia = 80% limitu zaokrąglone w dół (domyślnie 40 g).
- Walidacja ilości podanej jako liczba (`Int`) i jako tekst z pola formularza; wynik walidacji z rodzajem błędu zamiast wyjątku.
- Budowa treści SMS z poprawnego polecenia (`BG`, `PUMP`, `CARBS 20`, `BOLUS 0.1`, `PROFILE LIST`, `PROFILE 1 115`).
- Odwzorowanie polecenia na `ExpectedCommand` z pakietu `parser` (Spec 002).
- Brak ujawniania ilości w `toString()`, brak logowania.
- Struktura pozwalająca dodać kolejne polecenie jako nowy wpis katalogu.

**Poza zakresem:**

- Wysyłanie SMS, wybór karty SIM, numer AAPS – pakiet `sms`.
- Maszyna stanów polecenia, limity czasu, blokada drugiego polecenia, składanie odpowiedzi `<kod><PIN>` – pakiet `session`.
- PIN AAPS (wpisywanie, przechowywanie w pamięci, czyszczenie) – `security` / `session`. Katalog tylko mówi, że polecenie wymaga zatwierdzenia PIN-em; PIN-u nie dotyka.
- TOTP – [Spec 001](../001-totp/spec.md).
- Ekrany, teksty komunikatów (w tym pytanie „Na pewno X g?” i komunikaty błędów walidacji), etykiety przycisków, klawiatura numeryczna – `ui`. Katalog udostępnia tylko dane i rodzaje błędów.
- Historia poleceń – `data`.
- Wariant AAPS z godziną (`CARBS 20 12:30`), polecenia spoza katalogu (cel, `LOOP` itd.). Wrócą jako zmiana tej specyfikacji.
- Zmiana parsera i `ExpectedCommand` – [Spec 002](../002-parser-odpowiedzi/spec.md) i Spec 012 – 013.
- Ustawianie górnej granicy `CARBS` w aplikacji (ekran, zapis limitów) – [Spec 011](../011-limity-polecen/spec.md) i `ui`; katalog tylko przyjmuje limity jako argument. Szybkie wartości 10, 20, 30 g nadal nie są ustawieniem.
- Sprawdzanie odstępu między bolusami i pytanie „Na pewno X j?” – Spec 012, `ui`.

## Pojęcia

- **Polecenie** – poprawny, gotowy do wysłania egzemplarz: `BG`, `PUMP`, `CARBS` z ilością, `BOLUS` z ilością, `PROFILE STATUS`, `PROFILE LIST` albo zmiana profilu (procent; numer profilu zawsze 1). Niepoprawnego polecenia nie da się utworzyć.
- **Wpis katalogu** – opis rodzaju polecenia (dane), wspólny dla wszystkich egzemplarzy.
- **Ilość** – liczba całkowita gramów węglowodanów dla `CARBS` (dla `BOLUS` – setne części jednostki, Spec 012).
- **Limity** – `CommandLimits` ze [Spec 011](../011-limity-polecen/spec.md): `carbsMaxGrams`, `bolusMaxCentiUnits`, `bolusMinIntervalMinutes`; walidacja `CARBS` i `BOLUS` przyjmuje je jako argument.
- **Cyfra ASCII** – znak `0`–`9` (U+0030–U+0039). Inne znaki cyfr Unicode (np. `２` U+FF12, `٢` U+0662) nie są cyframi ASCII.

## Wymagania

### CMD-01: Katalog poleceń

Katalog zawiera dokładnie siedem wpisów, w kolejności: `BG`, `PUMP`, `CARBS`, `BOLUS`, `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH`. Każdy wpis ma identyfikator, słowo kluczowe SMS (wielkie litery ASCII `A`–`Z`, a między słowami pojedyncze spacje, np. `PROFILE STATUS`), parametr i informację, czy AAPS wymaga odesłania kodu. Każdy identyfikator rodzaju polecenia ma dokładnie jeden wpis; identyfikatory i słowa kluczowe są unikalne.

| Identyfikator | Słowo kluczowe | Parametr | Wymaga kodu |
| --- | --- | --- | --- |
| `BG` | `BG` | brak | nie |
| `PUMP` | `PUMP` | brak | nie |
| `CARBS` | `CARBS` | ilość w gramach (CMD-03) | tak |
| `BOLUS` | `BOLUS` | ilość insuliny (BOL-01, BOL-02) | tak |
| `PROFILE_STATUS` | `PROFILE STATUS` | brak | nie |
| `PROFILE_LIST` | `PROFILE LIST` | brak | nie |
| `PROFILE_SWITCH` | `PROFILE` | procent (numer zawsze 1; PROF-01, PROF-02) | tak |

**Kryteria akceptacji:**

- **Zakładając** katalog, **gdy** pobrano listę wpisów, **wtedy** ma 7 elementów o identyfikatorach `BG`, `PUMP`, `CARBS`, `BOLUS`, `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH` w tej kolejności (BOL-01, PROF-01).
- **Zakładając** katalog, **gdy** dla każdego identyfikatora rodzaju polecenia pobrano wpis, **wtedy** każdy zwraca wpis o tym samym identyfikatorze, a słowo kluczowe wpisu `BG` to `BG`, wpisu `PUMP` to `PUMP`, wpisu `CARBS` to `CARBS`, wpisu `BOLUS` to `BOLUS`, a wpisy profilu mają słowa `PROFILE STATUS`, `PROFILE LIST`, `PROFILE`.
- **Zakładając** katalog, **gdy** sprawdzono słowa kluczowe, **wtedy** są unikalne i pasują do `[A-Z]+( [A-Z]+)*`.
- **Zakładając** katalog, **gdy** sprawdzono pole „wymaga kodu”, **wtedy** `BG`, `PUMP`, `PROFILE_STATUS`, `PROFILE_LIST` – nie; `CARBS`, `BOLUS`, `PROFILE_SWITCH` – tak.
- **Zakładając** katalog, **gdy** sprawdzono parametr, **wtedy** `BG` i `PUMP` nie mają parametru, a `CARBS` ma parametr ilości z min. 1 i szybkimi wartościami 10, 20, 30 (górna granica i próg pochodzą z limitów, LIM-06, LIM-07); `BOLUS` ma parametr ilości insuliny (min. 0,05 j., krok 0,05 j.), a zmiana profilu – parametr procentu 50–200 (domyślnie 100; numer profilu jest stały i równy 1, bez wyboru nazwy).
- **Zakładając** polecenie `BG`, `PUMP` i `CARBS 20`, **gdy** pobrano ich wpis katalogu, **wtedy** `BG` daje wpis `BG`, `PUMP` – wpis `PUMP`, `CARBS 20` – wpis `CARBS`.

### CMD-02: Sposób zatwierdzenia

Każdy wpis określa sposób zatwierdzenia przez użytkownika: „jedno dotknięcie” (wysłanie od razu po wybraniu, bez ekranu potwierdzenia) albo „podsumowanie z PIN-em” (ekran podsumowania i wpisanie PIN-u AAPS przed wysłaniem). `BG`, `PUMP`, `PROFILE_STATUS` i `PROFILE_LIST` – jedno dotknięcie; `CARBS`, `BOLUS` i `PROFILE_SWITCH` – zawsze podsumowanie z PIN-em, niezależnie od ilości. Każdy wpis, który wymaga kodu, ma sposób „podsumowanie z PIN-em” (bez PIN-u nie da się złożyć odpowiedzi z kodem).

**Kryteria akceptacji:**

- **Zakładając** katalog, **gdy** sprawdzono sposób zatwierdzenia, **wtedy** `BG`, `PUMP`, `PROFILE_STATUS`, `PROFILE_LIST` – jedno dotknięcie; `CARBS`, `BOLUS`, `PROFILE_SWITCH` – podsumowanie z PIN-em.
- **Zakładając** katalog, **gdy** sprawdzono wszystkie wpisy z „wymaga kodu” = tak, **wtedy** każdy ma sposób „podsumowanie z PIN-em”.
- **Zakładając** polecenia `CARBS 1`, `CARBS 10` i `CARBS 50`, **gdy** sprawdzono sposób zatwierdzenia ich wpisu, **wtedy** dla każdego jest to „podsumowanie z PIN-em”.

### CMD-03: Zakres ilości CARBS

Polecenie `CARBS` można utworzyć tylko z ilością całkowitą od 1 do `carbsMaxGrams` włącznie ([LIM-06](../011-limity-polecen/spec.md); domyślnie 50 g). Utworzenie z liczby (`Int`) i limitów (`CommandLimits`) zwraca wynik walidacji: „poprawna” z poleceniem albo „błędna” z rodzajem błędu. Dla ilości < 1 rodzaj to „poniżej minimum”, dla > `carbsMaxGrams` – „powyżej maksimum”. Dla żadnej wartości `Int` nie jest rzucany wyjątek. Nie istnieje inna droga utworzenia polecenia `CARBS` niż walidacja z CMD-03 i CMD-04.

**Kryteria akceptacji** (limity domyślne, `carbsMaxGrams` = 50, o ile nie podano inaczej):

- **Zakładając** ilość 1, **gdy** utworzono `CARBS`, **wtedy** wynik to „poprawna” z ilością 1.
- **Zakładając** ilość 50, **gdy** utworzono `CARBS`, **wtedy** wynik to „poprawna” z ilością 50.
- **Zakładając** ilość 0, **gdy** utworzono `CARBS`, **wtedy** wynik to „błędna”, „poniżej minimum”.
- **Zakładając** ilość −5, **gdy** utworzono `CARBS`, **wtedy** wynik to „błędna”, „poniżej minimum”.
- **Zakładając** ilość 51, **gdy** utworzono `CARBS`, **wtedy** wynik to „błędna”, „powyżej maksimum”.
- **Zakładając** ilość `Int.MIN_VALUE` i `Int.MAX_VALUE`, **gdy** utworzono `CARBS`, **wtedy** `Int.MIN_VALUE` daje „poniżej minimum”, a `Int.MAX_VALUE` – „powyżej maksimum”, bez wyjątku.
- **Zakładając** dwa poprawne polecenia `CARBS 20`, **gdy** porównano je, **wtedy** są równe; `CARBS 20` i `CARBS 21` są różne.
- **Zakładając** `carbsMaxGrams` = 30, **gdy** utworzono `CARBS` z 30 i z 31, **wtedy** wyniki: poprawna, powyżej maksimum (LIM-06).

### CMD-04: Ilość CARBS z tekstu

Tekst z pola ilości jest walidowany bez przycinania i bez innej normalizacji niż usunięcie zer wiodących. Reguły w kolejności (pierwsza pasująca decyduje):

1. Tekst pusty (długość 0) → „błędna”, „pusta”.
2. Tekst zawiera dowolny znak, który nie jest cyfrą ASCII (w tym spację lub inny biały znak na początku, w środku lub na końcu, `-`, `+`, `,`, `.`, litery, cyfry spoza ASCII) → „błędna”, „niedozwolone znaki”.
3. Wartość po usunięciu zer wiodących (same zera → 0) jest oceniana jak w CMD-03: 0 → „poniżej minimum”, > `carbsMaxGrams` → „powyżej maksimum”, 1–`carbsMaxGrams` → „poprawna”.

Walidacja przyjmuje `CommandLimits` (LIM-06). Tekst dowolnej długości złożony z cyfr ASCII nie powoduje przepełnienia ani wyjątku: wartość z więcej niż trzema cyframi znaczącymi (limit może wynosić do 100 g) to „powyżej maksimum”. Liczba zer wiodących nie jest ograniczona.

**Kryteria akceptacji** (tabela przy limicie 50 g):

| Tekst | Wynik |
| --- | --- |
| `20` | „poprawna”, 20 |
| `1` | „poprawna”, 1 |
| `50` | „poprawna”, 50 |
| `020` | „poprawna”, 20 |
| `0050` | „poprawna”, 50 |
| `001` | „poprawna”, 1 |
| (pusty) | „pusta” |
| `0` | „poniżej minimum” |
| `000` | „poniżej minimum” |
| `51` | „powyżej maksimum” |
| `100` | „powyżej maksimum” |
| `0051` | „powyżej maksimum” |
| `99999999999999999999` (20 cyfr) | „powyżej maksimum”, bez wyjątku |
| `2147483648` | „powyżej maksimum”, bez wyjątku |
| `1,5` | „niedozwolone znaki” |
| `1.5` | „niedozwolone znaki” |
| `20.0` | „niedozwolone znaki” |
| `-5` | „niedozwolone znaki” |
| `+5` | „niedozwolone znaki” |
| `2 0` | „niedozwolone znaki” |
| ` 20` (spacja na początku) | „niedozwolone znaki” |
| `20 ` (spacja na końcu) | „niedozwolone znaki” |
| `20\n` (LF na końcu) | „niedozwolone znaki” |
| ` ` (jedna spacja) | „niedozwolone znaki” |
| `20g` | „niedozwolone znaki” |
| `abc` | „niedozwolone znaki” |
| `２０` (U+FF12 U+FF10) | „niedozwolone znaki” |
| `٢٠` (U+0662 U+0660) | „niedozwolone znaki” |

- **Zakładając** każdy tekst z tabeli, **gdy** zwalidowano go jako ilość `CARBS`, **wtedy** wynik jest dokładnie taki jak w tabeli i nie jest rzucany wyjątek.
- **Zakładając** tekst `-99999999999999999999`, **gdy** zwalidowano go, **wtedy** wynik to „niedozwolone znaki” (reguła 2 przed regułą 3).
- **Zakładając** tekst złożony z 10 000 cyfr `9`, **gdy** zwalidowano go, **wtedy** wynik to „powyżej maksimum”, bez wyjątku.
- **Zakładając** tekst złożony z 10 000 zer i cyfr `20` na końcu, **gdy** zwalidowano go, **wtedy** wynik to „poprawna”, 20.
- **Zakładając** `carbsMaxGrams` = 100, **gdy** zwalidowano `100` i `0100`, **wtedy** oba dają „poprawna”, 100; tekst `101` – „powyżej maksimum” (LIM-06).

### CMD-05: Dodatkowe potwierdzenie dużej ilości

Polecenie `CARBS` z ilością większą niż próg `⌊carbsMaxGrams × 4 / 5⌋` (80% limitu zaokrąglone w dół; [LIM-07](../011-limity-polecen/spec.md)) jest oznaczone jako wymagające dodatkowego potwierdzenia ilości przed wpisaniem PIN-u; oznaczenie liczy się z `CommandLimits` przekazanych w wywołaniu. Przy limicie domyślnym 50 g próg to 40 g: ilości 41–50 wymagają potwierdzenia, 1–40 nie. `BOLUS` wymaga dodatkowego potwierdzenia powyżej połowy limitu bolusa (BOL-04). `BG`, `PUMP` i polecenia profilu nigdy nie wymagają dodatkowego potwierdzenia. Samo pytanie pokazuje `ui`.

**Kryteria akceptacji** (limity domyślne, o ile nie podano inaczej):

- **Zakładając** polecenie `CARBS 40`, **gdy** sprawdzono oznaczenie, **wtedy** jest fałszywe.
- **Zakładając** polecenie `CARBS 41`, **gdy** sprawdzono oznaczenie, **wtedy** jest prawdziwe.
- **Zakładając** polecenie `CARBS 50`, **gdy** sprawdzono oznaczenie, **wtedy** jest prawdziwe.
- **Zakładając** polecenie `CARBS 1`, **gdy** sprawdzono oznaczenie, **wtedy** jest fałszywe.
- **Zakładając** polecenie `CARBS` utworzone z tekstu `041`, **gdy** sprawdzono oznaczenie, **wtedy** jest prawdziwe.
- **Zakładając** polecenia `BG`, `PUMP`, `PROFILE STATUS`, `PROFILE LIST` i zmianę profilu, **gdy** sprawdzono oznaczenie, **wtedy** jest fałszywe.
- **Zakładając** `carbsMaxGrams` równe 50, 100, 10 i 33, **gdy** sprawdzono ilości równą progowi i o 1 większą, **wtedy** próg to kolejno 40, 80, 8, 26: oznaczenie fałszywe dla ilości równej progowi, prawdziwe dla progu + 1 (LIM-07).

### CMD-06: Szybkie wartości CARBS

Wpis `CARBS` udostępnia szybkie wartości 10, 20, 30 g w tej kolejności; limity (`CommandLimits`) zwracają z nich tylko te, które są ≤ progowi dodatkowego potwierdzenia (CMD-05, [LIM-08](../011-limity-polecen/spec.md)). Każda zwrócona szybka wartość daje poprawne polecenie (CMD-03) i żadna nie wymaga dodatkowego potwierdzenia (CMD-05).

**Kryteria akceptacji:**

- **Zakładając** wpis `CARBS`, **gdy** pobrano szybkie wartości wpisu, **wtedy** lista to dokładnie `[10, 20, 30]`.
- **Zakładając** `carbsMaxGrams` równe 50, 30 i 10, **gdy** pobrano szybkie wartości z limitów, **wtedy** listy to kolejno `[10, 20, 30]`, `[10, 20]` i pusta (LIM-08).
- **Zakładając** każdą szybką wartość, **gdy** utworzono z niej `CARBS`, **wtedy** wynik to „poprawna”, a polecenie nie wymaga dodatkowego potwierdzenia.

### CMD-07: Treść SMS

Treść SMS jest budowana wyłącznie z poprawnego polecenia: słowo kluczowe z katalogu, a dla `CARBS` jedna spacja (U+0020) i ilość zapisana cyframi ASCII w systemie dziesiętnym, bez zer wiodących, bez znaku. Dla `BOLUS`: jedna spacja i ilość z kropką dziesiętną (BOL-05, np. `BOLUS 0.1`); dla `PROFILE STATUS` i `PROFILE LIST`: samo słowo kluczowe; dla zmiany profilu: zawsze `PROFILE 1 <p>`, także dla procentu 100 (PROF-04; `PROFILE 1 100` jest dla AAPS równoważne `PROFILE 1`, R7 w Spec 013; nazwa profilu nie trafia do SMS-a). Treść nie zawiera godziny, innych spacji niż opisane, końca linii ani innych znaków.

**Kryteria akceptacji:**

- **Zakładając** polecenie `BG`, **gdy** zbudowano treść SMS, **wtedy** jest to dokładnie `BG` (2 znaki).
- **Zakładając** polecenie `PUMP`, **gdy** zbudowano treść SMS, **wtedy** jest to dokładnie `PUMP` (4 znaki).
- **Zakładając** polecenie `CARBS 20`, **gdy** zbudowano treść SMS, **wtedy** jest to dokładnie `CARBS 20` (8 znaków).
- **Zakładając** polecenia `CARBS 1` i `CARBS 50`, **gdy** zbudowano treść SMS, **wtedy** są to `CARBS 1` i `CARBS 50`.
- **Zakładając** polecenie utworzone z tekstu `007`, **gdy** zbudowano treść SMS, **wtedy** jest to `CARBS 7`.
- **Zakładając** polecenia `BOLUS` 10, 125 i 200 (setne), `PROFILE STATUS`, `PROFILE LIST` oraz zmianę profilu z procentem 100 i 115, **gdy** zbudowano treści SMS, **wtedy** są to kolejno `BOLUS 0.1`, `BOLUS 1.25`, `BOLUS 2`, `PROFILE STATUS`, `PROFILE LIST`, `PROFILE 1 100`, `PROFILE 1 115` (BOL-05, PROF-04).
- **Zakładając** każde poprawne polecenie `CARBS` z ilością 1–50, **gdy** zbudowano treść SMS, **wtedy** pasuje ona w całości do wzorca `CARBS [1-9][0-9]?` i liczba w niej równa się ilości polecenia.
- **Zakładając** `carbsMaxGrams` = 100 (LIM-02, LIM-06) i polecenie `CARBS` z ilością 100 (górna granica tego limitu), **gdy** zbudowano treść SMS, **wtedy** jest to dokładnie `CARBS 100` (9 znaków; przy domyślnym 50 g `CARBS 100` nie jest poprawnym poleceniem); ogólnie dla ilości 1…`carbsMaxGrams` treść pasuje do wzorca `CARBS [1-9][0-9]{0,2}` i liczba w niej równa się ilości.

### CMD-08: Kontekst dla parsera

Każde polecenie odwzorowuje się na `ExpectedCommand` z [Spec 002](../002-parser-odpowiedzi/spec.md) (PARSER-01): `BG` → `ExpectedCommand.Bg`, `PUMP` → `ExpectedCommand.Pump`, `CARBS g` → `ExpectedCommand.Carbs(g)` z tą samą ilością, `BOLUS x` → `ExpectedCommand.Bolus(x)` (BOL-06), `PROFILE STATUS` → `ExpectedCommand.ProfileStatus`, `PROFILE LIST` → `ExpectedCommand.ProfileList`, zmiana profilu → `ExpectedCommand.ProfileSwitch(procent)` (tylko procent, bez numeru i nazwy; PROF-05). Odwzorowanie nie rzuca wyjątku dla żadnego poprawnego polecenia. Parser nie zależy od pakietu `commands`.

**Kryteria akceptacji:**

- **Zakładając** polecenie `BG`, **gdy** odwzorowano je na kontekst parsera, **wtedy** wynik to `ExpectedCommand.Bg`.
- **Zakładając** polecenie `PUMP`, **gdy** odwzorowano je, **wtedy** wynik to `ExpectedCommand.Pump`.
- **Zakładając** każde poprawne polecenie `CARBS g` dla g = 1…50, **gdy** odwzorowano je, **wtedy** wynik jest równy `ExpectedCommand.Carbs(g)` i nie jest rzucany wyjątek.
- **Zakładając** `BOLUS` 10, `PROFILE STATUS`, `PROFILE LIST` i zmianę profilu z procentem 115, **gdy** odwzorowano je, **wtedy** wyniki to `ExpectedCommand.Bolus(10)`, `ProfileStatus`, `ProfileList` i `ProfileSwitch(115)`.

### CMD-09: Brak ujawniania ilości i danych wejściowych

Ilość węglowodanów to dana medyczna, więc – spójnie z PARSER-10 – pakiet `commands` jej nie ujawnia poza treścią SMS i polem ilości:

- `toString()` poleceń z ilością (`CARBS`, `BOLUS`) i zmiany profilu (procent) oraz wyniku walidacji nie zawiera ilości ani procentu (BOL-06, PROF-05); wynik „błędna” nie przechowuje ani nie wypisuje tekstu wejściowego, tylko rodzaj błędu.
- Pakiet `commands` nie wywołuje żadnego API logowania (`android.util.Log`, `println`, `System.out`/`err`, loggerów).
- Dla danych od użytkownika (liczba, tekst) pakiet nie rzuca wyjątków (CMD-03, CMD-04).

**Kryteria akceptacji:**

- **Zakładając** polecenie `CARBS 37`, **gdy** wywołano `toString()` na poleceniu i na wyniku walidacji „poprawna”, **wtedy** żaden tekst nie zawiera `37`.
- **Zakładając** tekst `47`, **gdy** zwalidowano go i wywołano `toString()` na wyniku, **wtedy** tekst nie zawiera `47`.
- **Zakładając** tekst `73` (powyżej maksimum), **gdy** zwalidowano go i wywołano `toString()` na wyniku, **wtedy** tekst nie zawiera `73`, a zawiera nazwę rodzaju błędu.
- **Zakładając** tekst `12abc`, **gdy** zwalidowano go i wywołano `toString()` na wyniku, **wtedy** tekst nie zawiera `12` ani `abc`.

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Ilość < 1 (liczba) | „błędna”, „poniżej minimum”, polecenia nie ma | CMD-03 |
| Ilość > `carbsMaxGrams` (liczba; domyślnie 50) | „błędna”, „powyżej maksimum”, polecenia nie ma | CMD-03 |
| Pusty tekst w polu ilości | „błędna”, „pusta” | CMD-04 |
| `0` lub same zera | „błędna”, „poniżej minimum” | CMD-04 |
| Ułamek, znak `-`/`+`, litery, spacja (także na brzegach), biały znak, cyfry spoza ASCII | „błędna”, „niedozwolone znaki” | CMD-04 |
| Bardzo długi ciąg cyfr | „błędna”, „powyżej maksimum”, bez przepełnienia | CMD-04 |
| Ilość powyżej progu 80% limitu (domyślnie 41–50) | polecenie poprawne, oznaczone do dodatkowego potwierdzenia | CMD-05 |
| Próba utworzenia `CARBS` z pominięciem walidacji | niemożliwa (brak publicznego konstruktora) | CMD-03 |

## Bezpieczeństwo

- **Nie odsyłamy kodu, gdy odpowiedź nie zgadza się z poleceniem.** Katalog dostarcza `session` dokładną ilość dla parsera (CMD-08) – tę samą, która trafiła do treści SMS (CMD-07), bo obie są wyliczane z jednego egzemplarza polecenia. Porównanie robi parser (PARSER-06), decyzję – `session`.
- **Bez omijania limitów AAPS.** Zakres 1–50 g to dodatkowy limit aplikacji, a nie zastępstwo limitu AAPS; katalog nie zmienia ilości (nie przycina 60 do 50) – wartość spoza zakresu daje błąd i polecenia nie ma.
- **Bez automatycznego ponawiania.** Katalog niczego nie wysyła.
- **PIN i TOTP.** Katalog nie przyjmuje PIN-u ani kodu; informacja „podsumowanie z PIN-em” (CMD-02) to tylko oznaczenie dla `ui`/`session`.
- **Nie logujemy danych.** CMD-09: ilość nie trafia do `toString()`, tekst wejściowy nie jest przechowywany w wyniku błędu, brak logowania.
- **Żadnych prawdziwych danych w repo.** Specyfikacja i testy używają tylko fikcyjnych ilości; brak numerów telefonu, PIN-ów, sekretów.

## Dane testowe

Fikcyjne ilości i teksty z kryteriów akceptacji (CMD-03 – CMD-09). Brak plików w `dane/` – katalog nie przetwarza wiadomości z AAPS. Wartości `ExpectedCommand` pochodzą z kodu parsera (Spec 002).

## Otwarte pytania

- [x] Czy `toString()` polecenia `CARBS` i wyników walidacji ma ukrywać ilość (jak `ExpectedCommand.Carbs` w PARSER-10), czy można ją pokazywać (ilość nie jest sekretem, a ułatwia diagnostykę)? Rozstrzygnięte: ukrywa ilość, spójnie z parserem (CMD-09).

## Historia zmian

- **2026-10-31 – scalenie zmian ze Spec 013 z 2026-10-30 (po wdrożeniu).**
  - CMD-01: parametr `PROFILE_SWITCH` = procent, numer zawsze 1 (bez nazwy z listy); 7 wpisów bez zmian (`PROFILE_LIST` zostaje).
  - CMD-07: zmiana profilu zawsze `PROFILE 1 <p>` (np. `PROFILE 1 100`, `PROFILE 1 115`); `PROFILE 1 100` jest dla AAPS równoważne `PROFILE 1` (R7 w Spec 013).
  - CMD-08: kontekst zmiany profilu tylko z procentem (`ExpectedCommand.ProfileSwitch(procent)`).
  - CMD-09: `toString()` zmiany profilu bez procentu (nazwy ani numeru polecenie już nie ma).
  - Zakres, „Pojęcia”: ujednolicone z powyższym. Źródło: [PROF-02](../013-profil/spec.md#prof-02-polecenie-zmiany-profilu) – PROF-05. Identyfikatory wymagań bez zmian.

- **2026-10-29 – scalenie zmian ze Spec 011 – 013 (po wdrożeniu).**
  - CMD-01, CMD-02: katalog ma 7 wpisów (`BG`, `PUMP`, `CARBS`, `BOLUS`, `PROFILE_STATUS`, `PROFILE_LIST`, `PROFILE_SWITCH`); słowo kluczowe może mieć pojedyncze spacje między słowami (BOL-01, PROF-01); polecenia z kodem mają zatwierdzenie „podsumowanie z PIN-em”.
  - CMD-03, CMD-04: górna granica `CARBS` = `carbsMaxGrams` z `CommandLimits`, domyślnie 50 g (LIM-06); walidacja przyjmuje limity jako argument; więcej niż 3 cyfry znaczące = „powyżej maksimum”. CMD-05: próg = 80% limitu zaokrąglone w dół, przy 50 g nadal 40 g (LIM-07). CMD-06: szybkie wartości tylko ≤ progowi (LIM-08). Przy limitach domyślnych zachowanie bez zmian.
  - CMD-07, CMD-08, CMD-09: treść SMS, kontekst parsera i brak ujawniania danych dla `BOLUS` i poleceń profilu (BOL-05, BOL-06, PROF-04, PROF-05). „Poza zakresem” (bolus, profile, ustawianie zakresu) przeniesione do Spec 011 – 013.
