# Spec 003: Katalog poleceń

- **Status:** szkic
- **Prefiks wymagań:** `CMD`
- **Pakiety:** `commands`
- **Powiązane:** [docs/plan.md](../../plan.md#cel-i-zakres) (tabela poleceń MVP), [docs/plan.md](../../plan.md#przepływ-działania), [docs/plan.md](../../plan.md#ekrany-aplikacji) (ekran CARBS, szybkie wartości), [docs/plan.md](../../plan.md#otwarte-kwestie) (maksymalna ilość CARBS); [Spec 002: Parser odpowiedzi SMS z AAPS](../002-parser-odpowiedzi/spec.md) (kontekst `ExpectedCommand`, PARSER-01, PARSER-10); przyszłe specyfikacje `session` (maszyna stanów, PIN, odesłanie kodu), `sms` (wysyłanie), `ui` (ekrany i komunikaty)

## Cel

Polecenia, które aplikacja wysyła do AAPS, mają być opisane w jednym miejscu jako dane, a nie rozsiane po ekranach. Ta specyfikacja opisuje czystą logikę w Kotlinie: katalog poleceń MVP (`BG`, `PUMP`, `CARBS <g>`), walidację ilości węglowodanów, budowę treści SMS i odwzorowanie polecenia na kontekst parsera odpowiedzi.

## Zakres

- Katalog poleceń MVP: `BG`, `PUMP`, `CARBS` – identyfikator, słowo kluczowe SMS, parametr, czy AAPS wymaga kodu, sposób zatwierdzenia przez użytkownika.
- Parametr `CARBS`: liczba całkowita gramów w zakresie 1–50 włącznie, szybkie wartości 10, 20, 30 g, próg dodatkowego potwierdzenia (powyżej 40 g).
- Walidacja ilości podanej jako liczba (`Int`) i jako tekst z pola formularza; wynik walidacji z rodzajem błędu zamiast wyjątku.
- Budowa treści SMS z poprawnego polecenia (`BG`, `PUMP`, `CARBS 20`).
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
- Wariant AAPS z godziną (`CARBS 20 12:30`), polecenia spoza MVP (bolus, cel, `LOOP`, profile). Wrócą jako zmiana tej specyfikacji.
- Zmiana parsera i `ExpectedCommand` – Spec 002 zostaje bez zmian.
- Ustawianie zakresu, progu i szybkich wartości przez użytkownika – w MVP to stałe.

## Pojęcia

- **Polecenie** – poprawny, gotowy do wysłania egzemplarz: `BG`, `PUMP` albo `CARBS` z ilością. Niepoprawnego polecenia nie da się utworzyć.
- **Wpis katalogu** – opis rodzaju polecenia (dane), wspólny dla wszystkich egzemplarzy.
- **Ilość** – liczba całkowita gramów węglowodanów dla `CARBS`.
- **Cyfra ASCII** – znak `0`–`9` (U+0030–U+0039). Inne znaki cyfr Unicode (np. `２` U+FF12, `٢` U+0662) nie są cyframi ASCII.

## Wymagania

### CMD-01: Katalog poleceń MVP

Katalog zawiera dokładnie trzy wpisy, w kolejności: `BG`, `PUMP`, `CARBS`. Każdy wpis ma identyfikator, słowo kluczowe SMS (tylko wielkie litery ASCII `A`–`Z`), parametr i informację, czy AAPS wymaga odesłania kodu. Każdy identyfikator rodzaju polecenia ma dokładnie jeden wpis; identyfikatory i słowa kluczowe są unikalne.

| Identyfikator | Słowo kluczowe | Parametr | Wymaga kodu |
| --- | --- | --- | --- |
| `BG` | `BG` | brak | nie |
| `PUMP` | `PUMP` | brak | nie |
| `CARBS` | `CARBS` | ilość w gramach (CMD-03) | tak |

**Kryteria akceptacji:**

- **Zakładając** katalog, **gdy** pobrano listę wpisów, **wtedy** ma 3 elementy o identyfikatorach `BG`, `PUMP`, `CARBS` w tej kolejności.
- **Zakładając** katalog, **gdy** dla każdego identyfikatora rodzaju polecenia pobrano wpis, **wtedy** każdy zwraca wpis o tym samym identyfikatorze, a słowo kluczowe wpisu `BG` to `BG`, wpisu `PUMP` to `PUMP`, wpisu `CARBS` to `CARBS`.
- **Zakładając** katalog, **gdy** sprawdzono pole „wymaga kodu”, **wtedy** `BG` – nie, `PUMP` – nie, `CARBS` – tak.
- **Zakładając** katalog, **gdy** sprawdzono parametr, **wtedy** `BG` i `PUMP` nie mają parametru, a `CARBS` ma parametr ilości z min. 1, maks. 50, progiem dodatkowego potwierdzenia 40 i szybkimi wartościami 10, 20, 30.
- **Zakładając** polecenie `BG`, `PUMP` i `CARBS 20`, **gdy** pobrano ich wpis katalogu, **wtedy** `BG` daje wpis `BG`, `PUMP` – wpis `PUMP`, `CARBS 20` – wpis `CARBS`.

### CMD-02: Sposób zatwierdzenia

Każdy wpis określa sposób zatwierdzenia przez użytkownika: „jedno dotknięcie” (wysłanie od razu po wybraniu, bez ekranu potwierdzenia) albo „podsumowanie z PIN-em” (ekran podsumowania i wpisanie PIN-u AAPS przed wysłaniem). `BG` i `PUMP` – jedno dotknięcie; `CARBS` – zawsze podsumowanie z PIN-em, niezależnie od ilości. Każdy wpis, który wymaga kodu, ma sposób „podsumowanie z PIN-em” (bez PIN-u nie da się złożyć odpowiedzi z kodem).

**Kryteria akceptacji:**

- **Zakładając** katalog, **gdy** sprawdzono sposób zatwierdzenia, **wtedy** `BG` – jedno dotknięcie, `PUMP` – jedno dotknięcie, `CARBS` – podsumowanie z PIN-em.
- **Zakładając** katalog, **gdy** sprawdzono wszystkie wpisy z „wymaga kodu” = tak, **wtedy** każdy ma sposób „podsumowanie z PIN-em”.
- **Zakładając** polecenia `CARBS 1`, `CARBS 10` i `CARBS 50`, **gdy** sprawdzono sposób zatwierdzenia ich wpisu, **wtedy** dla każdego jest to „podsumowanie z PIN-em”.

### CMD-03: Zakres ilości CARBS

Polecenie `CARBS` można utworzyć tylko z ilością całkowitą od 1 do 50 g włącznie. Utworzenie z liczby (`Int`) zwraca wynik walidacji: „poprawna” z poleceniem albo „błędna” z rodzajem błędu. Dla ilości < 1 rodzaj to „poniżej minimum”, dla > 50 – „powyżej maksimum”. Dla żadnej wartości `Int` nie jest rzucany wyjątek. Nie istnieje inna droga utworzenia polecenia `CARBS` niż walidacja z CMD-03 i CMD-04.

**Kryteria akceptacji:**

- **Zakładając** ilość 1, **gdy** utworzono `CARBS`, **wtedy** wynik to „poprawna” z ilością 1.
- **Zakładając** ilość 50, **gdy** utworzono `CARBS`, **wtedy** wynik to „poprawna” z ilością 50.
- **Zakładając** ilość 0, **gdy** utworzono `CARBS`, **wtedy** wynik to „błędna”, „poniżej minimum”.
- **Zakładając** ilość −5, **gdy** utworzono `CARBS`, **wtedy** wynik to „błędna”, „poniżej minimum”.
- **Zakładając** ilość 51, **gdy** utworzono `CARBS`, **wtedy** wynik to „błędna”, „powyżej maksimum”.
- **Zakładając** ilość `Int.MIN_VALUE` i `Int.MAX_VALUE`, **gdy** utworzono `CARBS`, **wtedy** `Int.MIN_VALUE` daje „poniżej minimum”, a `Int.MAX_VALUE` – „powyżej maksimum”, bez wyjątku.
- **Zakładając** dwa poprawne polecenia `CARBS 20`, **gdy** porównano je, **wtedy** są równe; `CARBS 20` i `CARBS 21` są różne.

### CMD-04: Ilość CARBS z tekstu

Tekst z pola ilości jest walidowany bez przycinania i bez innej normalizacji niż usunięcie zer wiodących. Reguły w kolejności (pierwsza pasująca decyduje):

1. Tekst pusty (długość 0) → „błędna”, „pusta”.
2. Tekst zawiera dowolny znak, który nie jest cyfrą ASCII (w tym spację lub inny biały znak na początku, w środku lub na końcu, `-`, `+`, `,`, `.`, litery, cyfry spoza ASCII) → „błędna”, „niedozwolone znaki”.
3. Wartość po usunięciu zer wiodących (same zera → 0) jest oceniana jak w CMD-03: 0 → „poniżej minimum”, > 50 → „powyżej maksimum”, 1–50 → „poprawna”.

Tekst dowolnej długości złożony z cyfr ASCII nie powoduje przepełnienia ani wyjątku: wartość z więcej niż dwiema cyframi znaczącymi to „powyżej maksimum”. Liczba zer wiodących nie jest ograniczona.

**Kryteria akceptacji:**

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

### CMD-05: Dodatkowe potwierdzenie dużej ilości

Polecenie `CARBS` z ilością większą niż 40 g (41–50) jest oznaczone jako wymagające dodatkowego potwierdzenia ilości przed wpisaniem PIN-u. Dla ilości 1–40 oznaczenie jest fałszywe. Próg 40 jest stałą w katalogu. `BG` i `PUMP` nigdy nie wymagają dodatkowego potwierdzenia. Samo pytanie pokazuje `ui`.

**Kryteria akceptacji:**

- **Zakładając** polecenie `CARBS 40`, **gdy** sprawdzono oznaczenie, **wtedy** jest fałszywe.
- **Zakładając** polecenie `CARBS 41`, **gdy** sprawdzono oznaczenie, **wtedy** jest prawdziwe.
- **Zakładając** polecenie `CARBS 50`, **gdy** sprawdzono oznaczenie, **wtedy** jest prawdziwe.
- **Zakładając** polecenie `CARBS 1`, **gdy** sprawdzono oznaczenie, **wtedy** jest fałszywe.
- **Zakładając** polecenie `CARBS` utworzone z tekstu `041`, **gdy** sprawdzono oznaczenie, **wtedy** jest prawdziwe.
- **Zakładając** polecenia `BG` i `PUMP`, **gdy** sprawdzono oznaczenie, **wtedy** jest fałszywe.

### CMD-06: Szybkie wartości CARBS

Wpis `CARBS` udostępnia szybkie wartości 10, 20, 30 g w tej kolejności. Każda szybka wartość daje poprawne polecenie (CMD-03) i żadna nie wymaga dodatkowego potwierdzenia (CMD-05).

**Kryteria akceptacji:**

- **Zakładając** wpis `CARBS`, **gdy** pobrano szybkie wartości, **wtedy** lista to dokładnie `[10, 20, 30]`.
- **Zakładając** każdą szybką wartość, **gdy** utworzono z niej `CARBS`, **wtedy** wynik to „poprawna”, a polecenie nie wymaga dodatkowego potwierdzenia.

### CMD-07: Treść SMS

Treść SMS jest budowana wyłącznie z poprawnego polecenia: słowo kluczowe z katalogu, a dla `CARBS` jedna spacja (U+0020) i ilość zapisana cyframi ASCII w systemie dziesiętnym, bez zer wiodących, bez znaku. Treść nie zawiera godziny, innych spacji, końca linii ani innych znaków.

**Kryteria akceptacji:**

- **Zakładając** polecenie `BG`, **gdy** zbudowano treść SMS, **wtedy** jest to dokładnie `BG` (2 znaki).
- **Zakładając** polecenie `PUMP`, **gdy** zbudowano treść SMS, **wtedy** jest to dokładnie `PUMP` (4 znaki).
- **Zakładając** polecenie `CARBS 20`, **gdy** zbudowano treść SMS, **wtedy** jest to dokładnie `CARBS 20` (8 znaków).
- **Zakładając** polecenia `CARBS 1` i `CARBS 50`, **gdy** zbudowano treść SMS, **wtedy** są to `CARBS 1` i `CARBS 50`.
- **Zakładając** polecenie utworzone z tekstu `007`, **gdy** zbudowano treść SMS, **wtedy** jest to `CARBS 7`.
- **Zakładając** każde poprawne polecenie `CARBS` z ilością 1–50, **gdy** zbudowano treść SMS, **wtedy** pasuje ona w całości do wzorca `CARBS [1-9][0-9]?` i liczba w niej równa się ilości polecenia.

### CMD-08: Kontekst dla parsera

Każde polecenie odwzorowuje się na `ExpectedCommand` z [Spec 002](../002-parser-odpowiedzi/spec.md) (PARSER-01): `BG` → `ExpectedCommand.Bg`, `PUMP` → `ExpectedCommand.Pump`, `CARBS g` → `ExpectedCommand.Carbs(g)` z tą samą ilością. Odwzorowanie nie rzuca wyjątku dla żadnego poprawnego polecenia. Parser nie zależy od pakietu `commands`.

**Kryteria akceptacji:**

- **Zakładając** polecenie `BG`, **gdy** odwzorowano je na kontekst parsera, **wtedy** wynik to `ExpectedCommand.Bg`.
- **Zakładając** polecenie `PUMP`, **gdy** odwzorowano je, **wtedy** wynik to `ExpectedCommand.Pump`.
- **Zakładając** każde poprawne polecenie `CARBS g` dla g = 1…50, **gdy** odwzorowano je, **wtedy** wynik jest równy `ExpectedCommand.Carbs(g)` i nie jest rzucany wyjątek.

### CMD-09: Brak ujawniania ilości i danych wejściowych

Ilość węglowodanów to dana medyczna, więc – spójnie z PARSER-10 – pakiet `commands` jej nie ujawnia poza treścią SMS i polem ilości:

- `toString()` polecenia `CARBS` i wyniku walidacji nie zawiera ilości; wynik „błędna” nie przechowuje ani nie wypisuje tekstu wejściowego, tylko rodzaj błędu.
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
| Ilość > 50 (liczba) | „błędna”, „powyżej maksimum”, polecenia nie ma | CMD-03 |
| Pusty tekst w polu ilości | „błędna”, „pusta” | CMD-04 |
| `0` lub same zera | „błędna”, „poniżej minimum” | CMD-04 |
| Ułamek, znak `-`/`+`, litery, spacja (także na brzegach), biały znak, cyfry spoza ASCII | „błędna”, „niedozwolone znaki” | CMD-04 |
| Bardzo długi ciąg cyfr | „błędna”, „powyżej maksimum”, bez przepełnienia | CMD-04 |
| Ilość 41–50 | polecenie poprawne, oznaczone do dodatkowego potwierdzenia | CMD-05 |
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

- [ ] Czy `toString()` polecenia `CARBS` i wyników walidacji ma ukrywać ilość (jak `ExpectedCommand.Carbs` w PARSER-10), czy można ją pokazywać (ilość nie jest sekretem, a ułatwia diagnostykę)? Specyfikacja przyjmuje na razie redakcję – spójnie z parserem (CMD-09).
