# Spec 012: Polecenie BOLUS

- **Status:** zatwierdzona
- **Prefiks wymagań:** `BOL`
- **Pakiety:** `commands`, `parser`, `session`, `data` / `data.platform` (historia), `ui.commands`, `ui.history`
- **Powiązane:** [docs/plan.md](../../plan.md#cel-i-zakres); [Spec 002: Parser](../002-parser-odpowiedzi/spec.md) (PARSER-01, PARSER-02, PARSER-08 – PARSER-10); [Spec 003: Katalog](../003-katalog-polecen/spec.md) (CMD-01, CMD-02, CMD-07 – CMD-09); [Spec 004: Sesja](../004-sesja-polecenia/spec.md) (fakty A1 – A7, SESSION-08 – SESSION-15, SESSION-20); [Spec 007: Dane](../007-dane/spec.md) (DATA-07, DATA-09, DATA-14); [Spec 009: UI – polecenia](../009-ui-polecenia/spec.md) (CMDUI-04, CMDUI-09 – CMDUI-12, CMDUI-17, CMDUI-19, CMDUI-23); [Spec 010: UI – historia](../010-ui-historia/spec.md) (HISTUI-03, HISTUI-04); [Spec 011: Limity poleceń](../011-limity-polecen/spec.md) (limit ilości bolusa, odstęp)

## Cel

Użytkownik zleca z aplikacji bolus `BOLUS <j>`. Aplikacja wysyła polecenie tylko po podsumowaniu z PIN-em, pilnuje własnego limitu ilości i odstępu między bolusami, odsyła kod wyłącznie na prośbę AAPS o dokładnie tę ilość i nigdy nie ponawia bolusa sama. Każdy wątpliwy wynik po wysłaniu kodu jest „nieznany – sprawdź w AAPS”.

## Zakres

- Wpis katalogu `BOLUS` (wymaga kodu, podsumowanie z PIN-em), ilość w setnych jednostki, walidacja liczby i tekstu, treść SMS.
- Parser: prośba o kod dla bolusa, potwierdzenie podania bolusa, niezgodna ilość, „Zly kod” w kontekście bolusa.
- Sesja: reguły przed kodem i po kodzie dla `BOLUS` (jak `CARBS`).
- Sprawdzenie w aplikacji: limit ilości (Spec 011), pytanie „Na pewno X j?” powyżej połowy limitu, minimalny odstęp od ostatniego bolusa z aplikacji.
- UI: sekcja „Bolus” w panelu, okno „Na pewno”, podsumowanie z PIN-em, przebieg, wynik, `FLAG_SECURE`.
- Historia: wpis `BOLUS` z ilością, nowy rodzaj wyniku, migracja bazy 1 → 2.

**Poza zakresem:**

- `BOLUS <j> MEAL` i inne warianty (decyzja użytkownika).
- Rozpoznawanie odmów AAPS przed kodem (bolus w kolejce, minimalny odstęp, pętla zawieszona, zdalne polecenia wyłączone), błędu bolusa po kodzie i innych treści, których nie znamy – dają „nierozpoznana” (P3).
- Bolus z kalkulatora, korekta ilości przez aplikację, ponawianie, szybkie wartości bolusa.
- Krok ilości zależny od pompy (aplikacja przyjmuje wielokrotności 0,05 j.; P5).

## Fakty o AAPS (źródło)

Źródło: SMS-y z telefonu użytkownika (AAPS po polsku, bez znaków diakrytycznych; pliki w [dane/](dane/README.md)) i kod `SmsCommunicatorPlugin` (AndroidAPS), przekazane przez użytkownika. Wersja AAPS – jak w Spec 002 (3.4.2.6) (P9).

| Nr | Fakt | Źródło | Skutek |
| --- | --- | --- | --- |
| B1 | Na `BOLUS 0.1` AAPS prosi o kod: `Aby dostarczyc bolus 0,10U wprowadz kod z aplikacji Authenticator dla: <nazwa>, z doklejonym PIN-em` – ilość z przecinkiem i dwoma miejscami, bez spacji przed `U`. | SMS | Wzorzec BOL-07. |
| B2 | Na `BOLUS 99` (ponad max bolus AAPS 5 j.) AAPS obcina ilość i prosi o kod dla `5,00U`. | SMS | Niezgodna ilość → stop bez kodu (BOL-08, BOL-10), jak A3. |
| B3 | Po poprawnym kodzie AAPS odsyła 4 linie: `Bolus 1,20 U podany prawidlowo`, potem stan pompy (`Ost. Pol.: …`, `Tymcz.: …`, `Bat.: …`). W potwierdzeniu jest spacja przed `U`. | SMS | Wzorzec BOL-09; linie 2+ nie są sprawdzane. |
| B4 | Kolejność kontroli AAPS dla `BOLUS`: zdalne polecenia włączone → bolus w kolejce → minimalny odstęp od ostatniego zdalnego bolusa → pętla zawieszona → prośba o kod. Odmowy przychodzą przed prośbą o kod. | kod | Odmowa przed kodem = wiadomość nierozpoznana → `Failed(UNRECOGNIZED_REPLY)`, kod nie jest wysyłany (BOL-10). |
| B5 | Po kodzie AAPS wywołuje `commandQueue.bolus`; przy błędzie odsyła komunikat o niepowodzeniu (treść nieznana). Ilość przechodzi przez ograniczenia AAPS (obcięcie). | kod | Nierozpoznana wiadomość po kodzie → `Unknown` (BOL-11). |
| B6 | `BOLUS` to polecenie pompy: po kodzie AAPS czeka do ok. 3 min na kolejkę pompy (jak A4). | kod | Limit potwierdzenia 6 min (SESSION-15) bez zmian. |
| B7 | W AAPS użytkownika: max bolus 5 j., minimalny odstęp między zdalnymi bolusami 15 min. | użytkownik | Wartości domyślne limitów (Spec 011). |
| B8 | Zły kod: AAPS odsyła „Zly kod. Polecenie anulowano.” wspólnym kodem prośby o kod (`AuthRequest`) – dla bolusa niezweryfikowane na telefonie. | kod (A2) | BOL-11; P4. |

**Niezweryfikowane na telefonie** (zachowanie ostrożne, bez zgadywania treści): odmowa przez minimalny odstęp, odpowiedź przy bolusie w kolejce, błąd bolusa po kodzie, zły kod dla bolusa, czy potwierdzenie bolusa trafia do wszystkich dozwolonych numerów (jak A5 dla `CARBS`).

## Pojęcia

- **Ilość bolusa** – liczba całkowita setnych jednostki (`centiUnits`), np. 0,10 j. = 10, 5,00 j. = 500. W UI zapisywana z przecinkiem i dwoma miejscami i skrótem „j.” (np. „0,10 j.”).
- **Limit bolusa**, **odstęp** – `bolusMaxCentiUnits`, `bolusMinIntervalMinutes` ze Spec 011.
- **Ilość AAPS** (`<U>`) – w wiadomości AAPS: `0` albo cyfra `1`–`9` i 0–2 dalsze cyfry, przecinek, dokładnie dwie cyfry (np. `0,10`, `5,00`, `12,35`); wartość w setnych = część całkowita × 100 + część ułamkowa.

## Wymagania

Wspólne dane kryteriów: jak w Spec 004 (sekret RFC 6238, PIN `4321`, `T0 = 1111111051000`, zegar = `T0` + czas wirtualny, kod `050471` w chwili 60 000), limity domyślne (5,00 j., 15 min), wiadomości z [dane/](dane/README.md): `bolus-prosba-o-kod-0-10.txt` (0,10 j.), `bolus-prosba-o-kod-obcieta-5-00.txt` (5,00 j.), `bolus-sukces-1-20.txt` (1,20 j.), oraz `zly-kod.txt` ze Spec 002.

### Katalog i ilość

### BOL-01: Wpis katalogu BOLUS

Katalog ma wpis `BOLUS`: słowo kluczowe `BOLUS`, parametr „ilość insuliny”, wymaga kodu, sposób zatwierdzenia „podsumowanie z PIN-em” (CMD-02 – bez wyjątku dla małych ilości). Kolejność wpisów: `BG`, `PUMP`, `CARBS`, `BOLUS`, potem wpisy Spec 013.

**Kryteria akceptacji:**

- **Zakładając** katalog, **gdy** pobrano wpis `BOLUS`, **wtedy** słowo kluczowe = `BOLUS`, wymaga kodu = tak, zatwierdzenie = podsumowanie z PIN-em, a wpis jest czwarty na liście.
- **Zakładając** polecenia `BOLUS 0,05 j.` i `BOLUS 5,00 j.`, **gdy** sprawdzono zatwierdzenie ich wpisu, **wtedy** w obu przypadkach to podsumowanie z PIN-em.

### BOL-02: Zakres ilości

Polecenie `BOLUS` można utworzyć tylko z ilością w setnych jednostki, która jest wielokrotnością 5 (0,05 j.) i mieści się w przedziale 5 – `bolusMaxCentiUnits` włącznie (Spec 011). Utworzenie z liczby daje wynik walidacji: „poprawna” z poleceniem albo „błędna” z rodzajem: „poniżej minimum” (< 5), „powyżej maksimum” (> limitu), „niezgodna z krokiem”. Kolejność: poniżej minimum, powyżej maksimum, krok. Brak wyjątku dla żadnej wartości `Int`; nie ma innej drogi utworzenia polecenia.

**Kryteria akceptacji:**

- **Zakładając** limit 500, **gdy** utworzono `BOLUS` z 5, 10, 500, **wtedy** każdy wynik = poprawna.
- **Zakładając** limit 500, **gdy** utworzono z 0, 4, −5, 505, 501, 12, **wtedy** wyniki: poniżej minimum, poniżej minimum, poniżej minimum, powyżej maksimum, powyżej maksimum, niezgodna z krokiem.
- **Zakładając** limit 200 (2,00 j.), **gdy** utworzono z 205, **wtedy** wynik = powyżej maksimum.
- **Zakładając** `Int.MIN_VALUE` i `Int.MAX_VALUE`, **gdy** utworzono, **wtedy** poniżej minimum i powyżej maksimum, bez wyjątku.

### BOL-03: Ilość bolusa z tekstu

Tekst pola ilości jest walidowany bez przycinania; reguły w kolejności (pierwsza pasująca decyduje):

1. Pusty → „pusta”.
2. Tekst nie ma postaci: co najmniej jedna cyfra ASCII, opcjonalnie jeden separator (`,` albo `.`) i co najmniej jedna cyfra ASCII → „niedozwolone znaki” (także spacje, znaki `+`/`-`, litery, cyfry spoza ASCII, separator na początku lub końcu, dwa separatory).
3. Więcej niż 2 cyfry po separatorze → „za dużo miejsc po przecinku”.
4. Część całkowita po usunięciu zer wiodących ma więcej niż 3 cyfry → „powyżej maksimum” (bez przepełnienia).
5. Wartość w setnych oceniana jak w BOL-02.

**Kryteria akceptacji** (limit 500):

| Tekst | Wynik |
| --- | --- |
| `0,1` | poprawna, 10 |
| `0.1` | poprawna, 10 |
| `0,05` | poprawna, 5 |
| `2` | poprawna, 200 |
| `2,50` | poprawna, 250 |
| `05` | poprawna, 500 |
| `5,00` | poprawna, 500 |
| (pusty) | pusta |
| `0` | poniżej minimum |
| `0,04` | poniżej minimum |
| `5,05` | powyżej maksimum |
| `99` | powyżej maksimum |
| `99999999999999999999` | powyżej maksimum, bez wyjątku |
| `1,23` | niezgodna z krokiem |
| `1,234` | za dużo miejsc po przecinku |
| `1,` | niedozwolone znaki |
| `,5` | niedozwolone znaki |
| `1,2,3` | niedozwolone znaki |
| `1 ,5` | niedozwolone znaki |
| ` 1` | niedozwolone znaki |
| `-1` | niedozwolone znaki |
| `1,5j` | niedozwolone znaki |
| `１` (U+FF11) | niedozwolone znaki |

- **Zakładając** każdy wiersz tabeli, **gdy** zwalidowano tekst, **wtedy** wynik jest dokładnie taki, bez wyjątku.

### BOL-04: Dodatkowe potwierdzenie powyżej połowy limitu

Polecenie `BOLUS` wymaga dodatkowego pytania przed PIN-em, gdy ilość > połowy limitu (2 × ilość > `bolusMaxCentiUnits`). Decyzja użytkownika.

**Kryteria akceptacji:**

- **Zakładając** limit 500, **gdy** sprawdzono ilości 250 i 255, **wtedy** oznaczenie = fałsz, prawda.
- **Zakładając** limit 5 (0,05 j.), **gdy** sprawdzono ilość 5, **wtedy** oznaczenie = prawda.
- **Zakładając** limit 1000, **gdy** sprawdzono ilości 500 i 505, **wtedy** oznaczenie = fałsz, prawda.

### BOL-05: Treść SMS

Treść: `BOLUS`, jedna spacja, ilość w jednostkach z kropką dziesiętną: część całkowita bez zer wiodących; gdy część ułamkowa ≠ 0 – kropka i 1 albo 2 cyfry bez końcowego zera. Bez innych znaków. (AAPS przyjął `BOLUS 0.1` i `BOLUS 99` – fakty B1, B2.)

**Kryteria akceptacji:**

| Ilość (setne) | Treść |
| --- | --- |
| 10 | `BOLUS 0.1` |
| 5 | `BOLUS 0.05` |
| 125 | `BOLUS 1.25` |
| 250 | `BOLUS 2.5` |
| 200 | `BOLUS 2` |
| 1000 | `BOLUS 10` |

- **Zakładając** każdy wiersz, **gdy** zbudowano treść, **wtedy** jest dokładnie taka.
- **Zakładając** każdą poprawną ilość 5…1000 co 5, **gdy** zbudowano treść, **wtedy** pasuje w całości do `BOLUS (0|[1-9][0-9]?)(\.[0-9]?[1-9])?`, a jej wartość × 100 = ilość.

### BOL-06: Kontekst parsera i brak ujawniania ilości

Polecenie `BOLUS x` odwzorowuje się na kontekst parsera „bolus x” (`ExpectedCommand.Bolus(x)`, x ≥ 1; < 1 → `IllegalArgumentException` bez danych SMS). `toString()` polecenia, wyniku walidacji i kontekstu nie zawiera ilości ani tekstu wejściowego (jak CMD-09, PARSER-10).

**Kryteria akceptacji:**

- **Zakładając** każde poprawne `BOLUS x`, **gdy** odwzorowano na kontekst, **wtedy** wynik = `ExpectedCommand.Bolus(x)`.
- **Zakładając** `BOLUS` 235 (2,35 j. – syntetycznie, limit 1000) i tekst `"2,35"`, **gdy** wywołano `toString()` na poleceniu, wyniku walidacji i kontekście, **wtedy** żaden tekst nie zawiera `235`, `2,35` ani `2.35`.

### Parser

### BOL-07: Prośba o kod dla bolusa

W kontekście `Bolus x` wiadomość jest prośbą o kod, gdy cała treść po normalizacji (PARSER-02) to jedna linia:

```text
Aby dostarczyc bolus <U>U wprowadz kod z aplikacji Authenticator dla: <nazwa>, z doklejonym PIN-em
```

`<nazwa>` – jak w PARSER-05 (≥ 1 znak inny niż LF). Gdy `<U>` = x, wynik „prośba o kod (bolus)” z ilością i treścią; gdy ≠ – BOL-08.

**Kryteria akceptacji:**

- **Zakładając** `bolus-prosba-o-kod-0-10.txt`, **gdy** kontekst = `Bolus(10)`, **wtedy** wynik = prośba o kod z ilością 10 i treścią równą wejściu.
- **Zakładając** wariant syntetyczny z `2,50U`, **gdy** kontekst = `Bolus(250)`, **wtedy** prośba o kod z 250.
- **Zakładając** tę treść z `0.10U` (kropka), `0,1U`, `0,10 U` (spacja), `00,10U`, `1000,00U`, z dopisaną drugą linią albo z `Aby dostarczyć`, **gdy** kontekst = `Bolus(10)`, **wtedy** wynik = nierozpoznana.
- **Zakładając** `bolus-prosba-o-kod-0-10.txt`, **gdy** kontekst = `BG`, `PUMP` albo `Carbs(1)`, **wtedy** wynik = nierozpoznana; **zakładając** `carbs-prosba-o-kod.txt`, **gdy** kontekst = `Bolus(10)`, **wtedy** nierozpoznana.

### BOL-08: Niezgodna ilość bolusa

Gdy wiadomość ma postać prośby o kod (BOL-07) albo potwierdzenia (BOL-09), ale `<U>` ≠ x, wynik to „niezgodna ilość (bolus)” z ilością oczekiwaną, otrzymaną, etapem (prośba o kod / potwierdzenie) i treścią. Prośba o kod z inną ilością nigdy nie jest „prośbą o kod”.

**Kryteria akceptacji:**

- **Zakładając** `bolus-prosba-o-kod-obcieta-5-00.txt`, **gdy** kontekst = `Bolus(600)` (6 j. przy limicie aplikacji 10 j.), **wtedy** wynik = niezgodna ilość, oczekiwana 600, otrzymana 500, etap prośba o kod.
- **Zakładając** `bolus-prosba-o-kod-0-10.txt`, **gdy** kontekst = `Bolus(100)` (1,00 j. – różni się tylko przesunięciem przecinka), **wtedy** niezgodna ilość z otrzymaną 10.
- **Zakładając** `bolus-sukces-1-20.txt`, **gdy** kontekst = `Bolus(125)`, **wtedy** niezgodna ilość, otrzymana 120, etap potwierdzenie.

### BOL-09: Potwierdzenie bolusa i zły kod

W kontekście `Bolus x`:

- Potwierdzenie: pierwsza linia po normalizacji to dokładnie `Bolus <U> U podany prawidlowo` (spacja przed `U`, fakt B3); kolejne linie nie są sprawdzane. `<U>` = x → wynik „wykonano bolus” z ilością i treścią; ≠ → BOL-08.
- Zły kod: cała treść = `Zly kod. Polecenie anulowano.` → „zły kod” (jak PARSER-08).
- Każda inna treść → „nierozpoznana” z treścią (PARSER-09), w tym nieznane odmowy i komunikaty (B4, B5).

**Kryteria akceptacji:**

- **Zakładając** `bolus-sukces-1-20.txt`, **gdy** kontekst = `Bolus(120)`, **wtedy** wynik = wykonano bolus, ilość 120, treść równa wejściu.
- **Zakładając** wariant syntetyczny z samą pierwszą linią `Bolus 0,10 U podany prawidlowo`, **gdy** kontekst = `Bolus(10)`, **wtedy** wykonano bolus z 10.
- **Zakładając** `bolus-sukces-1-20.txt` z pierwszą linią `Bolus 1,20U podany prawidlowo` (bez spacji) albo poprzedzoną linią `Powiadomienie`, **gdy** kontekst = `Bolus(120)`, **wtedy** nierozpoznana.
- **Zakładając** `zly-kod.txt`, **gdy** kontekst = `Bolus(10)`, **wtedy** zły kod.
- **Zakładając** `bolus-sukces-1-20.txt`, **gdy** kontekst = `Carbs(1)`, `BG` albo `PUMP`, **wtedy** nierozpoznana.
- **Zakładając** wynik każdego rozpoznanego pliku z `dane/`, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `0,10`, `5,00`, `1,20`, `Jan`, `94%` ani żadnej linii treści.

### Sesja

### BOL-10: Wiadomości przed wysłaniem kodu

Sesja `BOLUS` przechodzi te same fazy co `CARBS` (SESSION-03, SESSION-07 – SESSION-10, limity 3 min / 10 s / 240 s). W `AWAITING_CODE_REQUEST` i `PREPARING_CODE` (tabela jak SESSION-11):

| Wynik parsera | `AWAITING_CODE_REQUEST` | `PREPARING_CODE` |
| --- | --- | --- |
| prośba o kod (bolus) | SESSION-08 | `Failed(UNEXPECTED_REPLY)`, kod nie jest wysyłany |
| niezgodna ilość (bolus, dowolny etap) | `Failed(AMOUNT_MISMATCH)` | `Failed(AMOUNT_MISMATCH)`, kod nie jest wysyłany |
| wykonano bolus | wiadomość nieoczekiwana (SESSION-18) | wiadomość nieoczekiwana |
| zły kod | `Failed(UNEXPECTED_REPLY)` | `Failed(UNEXPECTED_REPLY)` |
| nierozpoznana (np. odmowa AAPS przed kodem, B4) | `Failed(UNRECOGNIZED_REPLY)` | `Failed(UNRECOGNIZED_REPLY)` |

**Kryteria akceptacji:**

- **Zakładając** sesję `BOLUS 0,10 j.`, **gdy** w chwili 60 000 przychodzi `bolus-prosba-o-kod-0-10.txt`, **wtedy** w chwili 60 000 wysyłany jest SMS `0504714321`, a faza to `AWAITING_CONFIRMATION`.
- **Zakładając** sesję `BOLUS 6,00 j.` (limit 10 j.), **gdy** przychodzi `bolus-prosba-o-kod-obcieta-5-00.txt`, **wtedy** wynik = `Failed(AMOUNT_MISMATCH)` z treścią, a port wysyłania otrzymał tylko `BOLUS 6`.
- **Zakładając** sesję `BOLUS 0,10 j.` w `AWAITING_CODE_REQUEST`, **gdy** przychodzi syntetyczna odmowa `Inny bolus w kolejce`, **wtedy** wynik = `Failed(UNRECOGNIZED_REPLY)` z tą treścią i bez kodu.
- **Zakładając** sesję `BOLUS 1,20 j.` w `AWAITING_CODE_REQUEST`, **gdy** przychodzi `bolus-sukces-1-20.txt`, **wtedy** wiadomość trafia do nieoczekiwanych, faza bez zmian.
- **Zakładając** sesję `BOLUS 0,10 j.` bez odpowiedzi, **gdy** czas = 180 000 ms, **wtedy** wynik = `TimedOut(AWAITING_CODE_REQUEST)` i bez kodu.

### BOL-11: Wiadomości po wysłaniu kodu

W `AWAITING_CONFIRMATION` (i dla wiadomości odebranych w `SENDING_CODE`, przetwarzanych po „wysłano”):

| Wynik parsera | Wynik sesji |
| --- | --- |
| wykonano bolus, `receivedAt` ≥ chwili wysłania kodu | `BolusConfirmed(ilość, treść)` – „AAPS potwierdził podanie X j.” |
| wykonano bolus, `receivedAt` < chwili wysłania kodu | wiadomość nieoczekiwana |
| zły kod | `Failed(WRONG_CODE)` z treścią (P4) |
| niezgodna ilość (bolus) | `Unknown(AMOUNT_MISMATCH)` |
| prośba o kod (bolus) | `Unknown(UNEXPECTED_REPLY)`; kod nie jest wysyłany ponownie |
| nierozpoznana (np. błąd bolusa, B5) | `Unknown(UNRECOGNIZED_REPLY)` z treścią |

Brak potwierdzenia przez 6 min → `Unknown(CONFIRMATION_TIMEOUT)` (SESSION-15). `BolusConfirmed` – jak `CarbsConfirmed` (SESSION-12) – oznacza potwierdzenie AAPS, nie dowód wykonania polecenia z tej aplikacji. Sesja nigdy nie wysyła drugiego `BOLUS` ani drugiego kodu (SESSION-20).

**Kryteria akceptacji:**

- **Zakładając** sesję `BOLUS 1,20 j.` z kodem wysłanym w chwili 60 000, **gdy** w chwili 120 000 przychodzi `bolus-sukces-1-20.txt`, **wtedy** wynik = `BolusConfirmed` z 120 i treścią pliku.
- **Zakładając** tę sesję, **gdy** przychodzi `zly-kod.txt`, **wtedy** wynik = `Failed(WRONG_CODE)`.
- **Zakładając** tę sesję, **gdy** przychodzi syntetyczny `Bolus nie zostal podany`, **wtedy** wynik = `Unknown(UNRECOGNIZED_REPLY)` z tą treścią.
- **Zakładając** sesję `BOLUS 1,25 j.` po kodzie, **gdy** przychodzi `bolus-sukces-1-20.txt`, **wtedy** wynik = `Unknown(AMOUNT_MISMATCH)`.
- **Zakładając** każdy scenariusz z kryteriów BOL-10 i BOL-11, **gdy** sesja się zakończyła, **wtedy** port wysyłania otrzymał najwyżej 1 SMS `BOLUS …` i najwyżej 1 SMS z kodem.
- **Zakładając** wynik `BolusConfirmed(120)`, **gdy** wywołano `toString()` na wyniku i stanie, **wtedy** tekst nie zawiera `120` ani `1,20`.

### Sprawdzenia w aplikacji

### BOL-12: Minimalny odstęp między bolusami z aplikacji

Dodatkowy bezpiecznik aplikacji (AAPS i tak stosuje własny odstęp, B4). `CommandLauncher.launch` dla `BOLUS`, po sprawdzeniu limitu (LIM-09), a przed odczytem sekretu: gdy odstęp z limitów = 0 – brak sprawdzenia. W przeciwnym razie czyta jeden stan historii (`CommandHistory.entries`, pierwsza emisja):

- `Unavailable` → odrzucenie `BOLUS_HISTORY_UNAVAILABLE`.
- Wpis **liczy się**, gdy polecenie = `BOLUS` i (chwila wysłania kodu ≠ `null` albo rodzaj wyniku = `BolusConfirmed` albo `Unknown`). Chwila odniesienia wpisu = wysłanie kodu, a gdy brak – zakończenie, a gdy brak – rozpoczęcie; wpis bez żadnej z nich jest pomijany.
- Gdy istnieje liczący się wpis z chwilą odniesienia `t`, dla której `teraz − t < odstęp × 60 000 ms` albo `t > teraz` (zegar cofnięty) → odrzucenie `BOLUS_INTERVAL` z liczbą minut do końca odstępu (zaokrągloną w górę, najmniej 1; dla `t > teraz` – pełny odstęp).
- „teraz” = `EpochClock` aplikacji.

Przy odrzuceniu PIN jest czyszczony, brak startu.

Decyzja: wpis `Failed(WRONG_CODE)` ma wysłany kod, więc liczy się do odstępu – literówka w PIN-ie blokuje ponowną próbę z aplikacji do końca odstępu. To strona bezpieczna (aplikacja nie wie na pewno, czy AAPS odrzucił kod); w razie potrzeby użytkownik ustawia odstęp 0 albo podaje bolus w AAPS.

Ryzyko szczątkowe (świadomie przyjęte, AAPS ma własną kontrolę odstępu – B4): sprawdzenie nie widzi bolusa, którego wpis nie zapisał się w historii (`RecordResult.Failed`, DATA-13), bolusów sprzed „Wyczyść historię” (HISTUI-10) ani bolusów podanych w AAPS ręcznie albo z innych numerów; zegar telefonu przestawiony do przodu skraca odstęp (cofnięty – blokuje, kryterium niżej).

Komunikaty:

| Powód | Komunikat | Akcja |
| --- | --- | --- |
| `BOLUS_INTERVAL` | „Od ostatniego bolusa z aplikacji minęło mniej niż N min (limit w Ustawieniach). Spróbuj za M min.” | – |
| `BOLUS_HISTORY_UNAVAILABLE` | „Nie udało się odczytać historii, więc aplikacja nie sprawdzi odstępu między bolusami. Spróbuj ponownie.” | – |

**Kryteria akceptacji** (odstęp 15 min, teraz = `T0`):

- **Zakładając** wpis `BOLUS` `BolusConfirmed` z wysłaniem kodu `T0 − 600 000` (10 min), **gdy** wywołano `launch(BOLUS 0,10 j., PIN)`, **wtedy** wynik = `BOLUS_INTERVAL` z N = 15 i M = 5, PIN wyczyszczony, `load()` nie był wywołany, brak startu.
- **Zakładając** ten wpis z wysłaniem kodu `T0 − 900 000` (dokładnie 15 min), **gdy** wywołano `launch`, **wtedy** wynik = `Started`.
- **Zakładając** wpis `BOLUS` `Unknown(CODE_SEND_FAILED)` bez wysłania kodu, z zakończeniem `T0 − 60 000`, **gdy** wywołano `launch`, **wtedy** `BOLUS_INTERVAL` z M = 14.
- **Zakładając** wpis `BOLUS` `Failed(AMOUNT_MISMATCH)` bez wysłania kodu z zakończeniem `T0 − 60 000`, **gdy** wywołano `launch`, **wtedy** `Started` (bolus nie mógł zostać podany).
- **Zakładając** wpis `BOLUS` `BolusConfirmed` z wysłaniem kodu `T0 + 60 000` (zegar cofnięty), **gdy** wywołano `launch`, **wtedy** `BOLUS_INTERVAL` z M = 15.
- **Zakładając** wpis `CARBS` `CarbsConfirmed` z wysłaniem kodu `T0 − 60 000`, **gdy** wywołano `launch(BOLUS …)`, **wtedy** `Started`.
- **Zakładając** historię `Unavailable`, **gdy** wywołano `launch(BOLUS …)`, **wtedy** `BOLUS_HISTORY_UNAVAILABLE`; przy odstępie 0 – `Started` bez czytania historii.

### UI

### BOL-13: Sekcja „Bolus” w panelu

Panel poleceń ma pod sekcją `CARBS` sekcję „Bolus” z polem „Insulina (j.)” (`KeyboardType.Decimal`) i przyciskiem „Dalej” – bez szybkich wartości. Dostępność jak `CARBS`: numer, uprawnienia SMS, sekret (CMDUI-04 obejmuje każde polecenie z kodem), limity `Loaded` (LIM-05), brak polecenia w toku i uruchamiania (CMDUI-05). „Dalej” waliduje tekst (BOL-03) z bieżącymi limitami; błąd pod polem według tabeli; zmiana tekstu usuwa błąd.

| Błąd | Tekst |
| --- | --- |
| pusta | „Podaj ilość insuliny (0,05–X j.).” |
| niedozwolone znaki | „Podaj liczbę, np. 0,5.” |
| za dużo miejsc po przecinku | „Najwyżej 2 miejsca po przecinku.” |
| poniżej minimum | „Najmniejsza ilość to 0,05 j.” |
| powyżej maksimum | „Największa ilość to X j. (limit w Ustawieniach).” |
| niezgodna z krokiem | „Podaj wielokrotność 0,05 j.” |

X = limit bolusa z przecinkiem i dwoma miejscami.

**Kryteria akceptacji:**

- **Zakładając** teksty pola kolejno `""`, `"1,5j"`, `"1,234"`, `"0"`, `"6"`, `"1,23"`, **gdy** za każdym razem wywołano „Dalej”, **wtedy** błędy odpowiadają tabeli, podsumowanie zamknięte, `FakeSmsSender` bez SMS-a.
- **Zakładając** tekst `"0,1"`, **gdy** wywołano „Dalej”, **wtedy** podsumowanie jest otwarte dla 10 (bez okna „Na pewno”).
- **Zakładając** stan sekretu `ABSENT` (osobno limity `LoadFailed`), **gdy** odczytano stan, **wtedy** „Dalej” bolusa jest niedostępne, a `BG` dostępne.

### BOL-14: Okno „Na pewno X j?”

Gdy poprawne polecenie wymaga dodatkowego potwierdzenia (BOL-04), „Dalej” otwiera okno „Na pewno X j.?” z tekstem „To więcej niż połowa limitu bolusa (Y j.). Sprawdź ilość, zanim wpiszesz PIN.” i przyciskami „Tak, X j.” i „Nie” – zachowanie jak CMDUI-09 (w tym `FLAG_SECURE` okna `Activity` i `DialogProperties(securePolicy = SecureOn)` dialogu, zamknięcie przez `onStop()`). Y = połowa limitu.

**Kryteria akceptacji:**

- **Zakładając** limit 500 i tekst `"3"`, **gdy** wywołano „Dalej”, **wtedy** okno jest otwarte dla 300, znacznik „ekran chroniony” = `true`; „Tak” → podsumowanie dla 300; „Nie” → brak podsumowania, tekst pola bez zmian, brak SMS-a.
- **Zakładając** tekst `"2,5"`, **gdy** wywołano „Dalej”, **wtedy** okno nie jest otwierane, podsumowanie otwarte dla 250.
- **Zakładając** źródło okna, **gdy** test JVM je przeczytał, **wtedy** `AlertDialog` okna bolusa ma `DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)`.

### BOL-15: Podsumowanie z PIN-em

Podsumowanie bolusa działa jak CMDUI-10 i CMDUI-11 (pole PIN-u, tylko cyfry, 1–12 znaków, maskowanie, czyszczenie PIN-u przy „Wyślij”, „Anuluj”, Wstecz, `onStop()`, `FLAG_SECURE`), z tekstami: „Podać bolus X j.?” i „Aplikacja wyśle BOLUS X j., a kod z PIN-em odeśle dopiero, gdy AAPS poprosi o kod dla X j. Bolusa nie da się cofnąć.”. „Wyślij” wywołuje `CommandLauncher.launch(BOLUS x, AapsPin)`; odrzucenia – komunikaty CMDUI-12, LIM-09, BOL-12. Bolus nigdy nie jest wysyłany bez podsumowania z PIN-em.

**Kryteria akceptacji:**

- **Zakładając** podsumowanie dla 10 z PIN-em `4321`, **gdy** wywołano „Wyślij” i przyszły kolejno `bolus-prosba-o-kod-0-10.txt` (60 000) i syntetyczny `Bolus 0,10 U podany prawidlowo` (120 000), **wtedy** `FakeSmsSender` dostał `BOLUS 0.1` i `0504714321`, a wynik = `BolusConfirmed(10)`.
- **Zakładając** podsumowanie z PIN-em `4321`, **gdy** wywołano `onStop()`, **wtedy** PIN = `""`, podsumowanie zamknięte, tekst pola ilości bez zmian.
- **Zakładając** `CommandsViewModel`, **gdy** sprawdzono publiczne metody, **wtedy** jedyną drogą do `launch` z poleceniem `BOLUS` jest „Wyślij” podsumowania.

### BOL-16: Przebieg i wynik

Przebieg `BOLUS` ma 5 kroków jak `CARBS` (CMDUI-17; tabela kroków obejmuje każde polecenie z kodem), nagłówek „Polecenie BOLUS X j.”. Wynik – jak CMDUI-19 z dodatkami:

| Wynik | Rodzaj | Komunikat |
| --- | --- | --- |
| `BolusConfirmed(X)` | sukces | „AAPS potwierdził podanie X j.” (X z wyniku) |
| `Failed(AMOUNT_MISMATCH)` | błąd | „AAPS poprosił o kod dla innej ilości niż wysłana (np. obciętej przez limit w AAPS). Kod nie został wysłany.” – osobny tekst, żeby nie sugerować podania insuliny |
| `Unknown(UNRECOGNIZED_REPLY)` | nieznany | „Po wysłaniu kodu przyszła nierozpoznana odpowiedź AAPS. Przeczytaj treść SMS i sprawdź w AAPS.” |

Pozostałe wyniki – jak CMDUI-19. Dla każdego `Unknown` bolusa ostrzeżenie brzmi „Nie ponawiaj bolusa, zanim nie sprawdzisz w AAPS, czy został podany.” (zamiast ogólnego). „Kod nie został wysłany.” – jak dla `CARBS`.

**Kryteria akceptacji:**

- **Zakładając** wynik `BolusConfirmed(120)` dla polecenia `BOLUS 1,20 j.`, **gdy** odwzorowano, **wtedy** sukces, argument ilości = 120, treść = `bolus-sukces-1-20.txt`.
- **Zakładając** `BOLUS` i każdy `UnknownReason`, **gdy** odwzorowano, **wtedy** ostrzeżenie = bolusowe; dla `CARBS` – ogólne.
- **Zakładając** `BOLUS` i każdą fazę, **gdy** wyliczono kroki, **wtedy** są takie jak dla `CARBS`.
- **Zakładając** `BOLUS` z `Failed(AMOUNT_MISMATCH)`, **gdy** odwzorowano, **wtedy** komunikat to tekst bolusowy (inny klucz niż dla `CARBS`).

### BOL-17: Historia

Zmiana DATA-07, DATA-09, DATA-14 i HISTUI-03/04: wpis ma pole „ilość bolusa (setne)” – `Command.Bolus.centiUnits` dla `BOLUS`, inaczej brak. Nowe stałe teksty: polecenie `BOLUS`, rodzaj wyniku `BOLUS_CONFIRMED`. Baza w wersji 2: migracja 1 → 2 dodaje kolumnę `bolus_centi_units INTEGER` (dopuszcza `NULL`), istniejące wiersze zostają bez zmian; brak migracji destrukcyjnych; plik schematu `2.json` w repo. Wszystkie migracje są rejestrowane jednym wywołaniem `addMigrations(...)` (po Spec 013: `MIGRATION_1_2, MIGRATION_2_3`). Zadanie z migracją można scalić dopiero po ręcznym sprawdzeniu W3 na prawdziwej bazie (wynik w opisie PR). Odczyt: `bolus_centi_units` przy poleceniu innym niż `BOLUS` → brak. Etykieta wpisu: „BOLUS X j.” (X z przecinkiem, 2 miejsca), przy braku ilości „BOLUS (ilość nieznana)”. Wynik wpisu `BolusConfirmed` z ilością → „AAPS potwierdził podanie X j.”; bez ilości → „Brak szczegółów wyniku …”.

**Kryteria akceptacji:**

- **Zakładając** wynik `BOLUS 1,20 j.` `BolusConfirmed(120)`, **gdy** utworzono wpis i wiersz, **wtedy** polecenie = `BOLUS`, ilość bolusa = 120, ilość węglowodanów = brak, rodzaj = `BOLUS_CONFIRMED`.
- **Zakładając** wiersz `CARBS` z `bolus_centi_units` = 50, **gdy** go odczytano, **wtedy** ilość bolusa = brak.
- **Zakładając** plik `app/schemas/…/2.json`, **gdy** test JVM go przeczytał, **wtedy** wersja = 2, tabela ma kolumny wersji 1 i `bolus_centi_units` (`INTEGER`, bez `NOT NULL`); migracja 1 → 2 zawiera dokładnie `ALTER TABLE command_history ADD COLUMN bolus_centi_units INTEGER`.
- **Zakładając** telefon z historią w wersji 1 (W3), **gdy** zainstalowano nową wersję, **wtedy** wcześniejsze wpisy są widoczne bez zmian, a nowy bolus ma wpis „BOLUS X j.”.
- **Zakładając** wpisy `BOLUS` 120 i `BOLUS` bez ilości, **gdy** odwzorowano etykiety, **wtedy** „BOLUS” z argumentem 120 i „BOLUS (ilość nieznana)”.

### BOL-18: Brak ujawniania danych

Ilość bolusa, PIN, kod i treść SMS nie trafiają do logów, wyjątków, `toString()` ani komunikatów poza polami ilości, podsumowaniem, przebiegiem, wynikiem i historią (zasady CMD-09, PARSER-10, SESSION-23, CMDUI-26, DATA-17, HISTUI-15 obejmują nowe typy).

**Kryteria akceptacji:**

- **Zakładając** stan `CommandsViewModel` z tekstem pola bolusa `"2,35"`, podsumowaniem dla 235 i PIN-em `4321`, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `2,35`, `235` ani `4321`.
- **Zakładając** źródła zmienionych pakietów, **gdy** test JVM je przeczytał, **wtedy** nie zawierają wywołań API logowania.

### BOL-19: Rodzaj prośby o kod zgodny z poleceniem

Sesja odsyła kod tylko wtedy, gdy rodzaj prośby o kod odpowiada poleceniu sesji: `CodeRequest` – `CARBS`, `BolusCodeRequest` – `BOLUS`, `ProfileCodeRequest` – zmiana profilu ([Spec 013](../013-profil/spec.md)). To samo dotyczy potwierdzenia (`CarbsDone` – `CARBS`, `BolusDone` – `BOLUS`). Wynik parsera innego rodzaju (możliwy tylko przy błędzie parsera, bo kontekst ogranicza wyniki – PARSER-01) jest traktowany jak `Unrecognized`: przed kodem `Failed(UNRECOGNIZED_REPLY)` bez kodu, po kodzie `Unknown(UNRECOGNIZED_REPLY)`. Sprawdzenie robi jedna czysta funkcja w `session` z wyczerpującym `when` po `CommandId`.

**Kryteria akceptacji:**

- **Zakładając** każdą parę (polecenie, rodzaj prośby o kod) i (polecenie, rodzaj potwierdzenia), **gdy** wywołano funkcję zgodności, **wtedy** zwraca prawdę tylko dla par z opisu.
- **Zakładając** sesję `CARBS 1`, **gdy** w `AWAITING_CODE_REQUEST` przychodzi `bolus-prosba-o-kod-0-10.txt`, **wtedy** wynik = `Failed(UNRECOGNIZED_REPLY)` i żaden SMS z kodem nie jest wysyłany.
- **Zakładając** sesję `BOLUS 0,10 j.`, **gdy** przychodzi `carbs-prosba-o-kod.txt` (osobno: `profile-prosba-o-kod.txt` ze Spec 013), **wtedy** `Failed(UNRECOGNIZED_REPLY)`, bez kodu.
- **Zakładając** sesję `BOLUS 0,10 j.` z podstawionym parserem (port parsera w sesji, design) zwracającym `CodeRequest(1, …)`, **gdy** przyszła wiadomość, **wtedy** `Failed(UNRECOGNIZED_REPLY)`, bez kodu; zwracającym `CarbsDone(1, …)` po kodzie – `Unknown(UNRECOGNIZED_REPLY)`.

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Tekst ilości pusty / zły / za dużo miejsc / poza krokiem / poza zakresem | błąd pod polem, brak podsumowania | BOL-03, BOL-13 |
| Ilość > połowy limitu | okno „Na pewno X j.?” przed PIN-em | BOL-04, BOL-14 |
| Limit zmieniony między „Dalej” a „Wyślij” | `AMOUNT_ABOVE_LIMIT`, PIN wyczyszczony | LIM-09 |
| Ostatni bolus z aplikacji w odstępie / zegar cofnięty | `BOLUS_INTERVAL`, PIN wyczyszczony, brak SMS-a | BOL-12 |
| Historia niedostępna przy odstępie > 0 | `BOLUS_HISTORY_UNAVAILABLE` | BOL-12 |
| AAPS obciął ilość (prośba o kod z inną ilością) | `Failed(AMOUNT_MISMATCH)`, kod nie jest wysyłany | BOL-08, BOL-10 |
| Odmowa AAPS przed kodem (kolejka, odstęp, pętla, zdalne wyłączone) | `Failed(UNRECOGNIZED_REPLY)` z treścią, kod nie jest wysyłany | BOL-10 |
| Brak prośby o kod w 3 min | `TimedOut`, bez kodu | BOL-10 |
| Zły kod | `Failed(WRONG_CODE)`; kolejny bolus z aplikacji dopiero po odstępie | BOL-11, BOL-12 |
| Prośba o kod innego polecenia (np. `CARBS`) w sesji `BOLUS` | `Failed(UNRECOGNIZED_REPLY)`, bez kodu | BOL-19 |
| Potwierdzenie z inną ilością po kodzie | `Unknown(AMOUNT_MISMATCH)` | BOL-11 |
| Nierozpoznana wiadomość po kodzie (np. błąd bolusa) | `Unknown(UNRECOGNIZED_REPLY)` z treścią i ostrzeżeniem | BOL-11, BOL-16 |
| Brak potwierdzenia w 6 min / anulowanie po kodzie / błąd wysyłania kodu | `Unknown(...)`, nigdy ponowienie | BOL-11, SESSION-15 – SESSION-17 |

## Bezpieczeństwo

- **Kod tylko na zgodną prośbę.** Jedyną drogą do kodu jest „prośba o kod (bolus)” z ilością równą wysłanej, w fazie `AWAITING_CODE_REQUEST` (BOL-07, BOL-10, SESSION-08). Obcięta ilość, nierozpoznana treść, inny nadawca (filtr `sms`), wiadomość sprzed wysłania (SESSION-05) – stop bez kodu.
- **Bez ponawiania.** Najwyżej 1 SMS `BOLUS` i 1 kod na sesję; każdy wątpliwy wynik po kodzie to „nieznany – sprawdź w AAPS” z ostrzeżeniem przed powtórzeniem bolusa (BOL-11, BOL-16). Aplikacja nie koryguje ilości.
- **Bez omijania limitów AAPS.** Limit i odstęp w aplikacji tylko zawężają (Spec 011, BOL-12); AAPS decyduje ostatecznie.
- **Potwierdzenie bolusa nie dowodzi wykonania polecenia z aplikacji** (jak A5): przyjmowane tylko po wysłaniu kodu, z ilością równą wysłanej, prezentowane jako „AAPS potwierdził podanie X j.”. Ryzyko szczątkowe jak w SESSION-12 (bolus z tą samą ilością z innego numeru w oknie oczekiwania).
- **Odstęp liczony z historii aplikacji** nie widzi bolusów podanych w AAPS ręcznie ani z innych numerów; wyczyszczenie historii usuwa dane do sprawdzenia (P6). Ryzyko szczątkowe: wpis historii zapisuje się asynchronicznie po zakończeniu sesji (DATA-13); start kolejnego bolusa w ułamku sekundy po zakończeniu poprzedniego mógłby go nie zobaczyć – w praktyce zamknięcie wyniku, wpisanie ilości i PIN-u trwa dłużej.
- **PIN i kod** – zasady Spec 004 i 009 bez zmian; podsumowanie, okno „Na pewno”, przebieg i wynik mają `FLAG_SECURE` (BOL-14, BOL-15, CMDUI-23).
- **Dane w repo:** imię w SMS-ach zastąpione `Jan`; brak numerów, PIN-ów i kodów.

## Dane testowe

- [dane/](dane/README.md): prawdziwe SMS-y AAPS z telefonu użytkownika (imię → `Jan`): prośba o kod dla 0,10 j., prośba o kod obcięta do 5,00 j., potwierdzenie podania 1,20 j. z liniami stanu pompy.
- Warianty syntetyczne (np. `Inny bolus w kolejce`, `Bolus nie zostal podany`) – tylko w kryteriach, nie są komunikatami AAPS i nie trafiają do `dane/`.
- Sekret RFC 6238, PIN fikcyjny `4321`, `T0` i kod `050471` jak w Spec 004.
- Weryfikacja na telefonie **wyłącznie z wirtualną pompą w AAPS** (nigdy z prawdziwą pompą).

## Weryfikacja na urządzeniu

Tylko AAPS z wirtualną pompą. Wyniki opisuje PR zadania, które wprowadza ekran.

| Nr | Co sprawdzić | Wymagania |
| --- | --- | --- |
| W1 | `BOLUS 0,1` z poprawnym PIN-em: podsumowanie, kroki, prośba o kod, „AAPS potwierdził podanie 0,10 j.”; zanotować treść potwierdzenia | BOL-13, BOL-15, BOL-16 |
| W2 | Limit aplikacji 10 j., `BOLUS 6` przy max bolus AAPS 5 j. → „AAPS podał inną ilość…”, kod nie wysłany (skrzynka wysłanych: tylko `BOLUS 6`) | BOL-10 |
| W3 | Aktualizacja z wersji z historią w bazie 1: wpisy zachowane; nowy wpis „BOLUS 0,10 j.” | BOL-17 |
| W4 | Drugi `BOLUS` przed upływem 15 min → komunikat o odstępie bez SMS-a; odstęp 0 w Ustawieniach → AAPS odpowiada sam (zanotować treść odmowy AAPS – dane do P3) | BOL-12 |
| W5 | Zły PIN → zanotować treść i wynik; `FLAG_SECURE` okna „Na pewno 3 j.?” i podsumowania | BOL-11, BOL-14 |

## Otwarte pytania

Brak. Decyzje użytkownika przyjęte przy tej specyfikacji (2026-10-28):

- [x] **P1. Format SMS.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – kropka dziesiętna bez końcowych zer (`BOLUS 0.1`, `BOLUS 2`, `BOLUS 1.25`), BOL-05. Potwierdzone tylko `0.1` i `99`; `1.25` i `0.05` – do sprawdzenia na wirtualnej pompie.
- [x] **P2. Format ilości w odpowiedziach AAPS.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – tylko przecinek i dokładnie 2 miejsca (`0,10U` w prośbie, `1,20 U` w potwierdzeniu); inny zapis → nierozpoznana (stop/nieznany). Pompy z krokiem 0,01/0,025 j. mogłyby dać 3 miejsca – poza zakresem.
- [x] **P3. Odmowy przed kodem** (bolus w kolejce, minimalny odstęp, pętla zawieszona) i błąd bolusa po kodzie – treści nieznane. Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – „nierozpoznana” → przed kodem `Failed(UNRECOGNIZED_REPLY)`, po kodzie `Unknown`; treść zawsze pokazana. Osobne wzorce po zebraniu SMS-ów (W4).
- [x] **P4. „Zly kod” dla bolusa.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – jak A2 → `Failed(WRONG_CODE)` (wspólny kod AAPS `AuthRequest`); do potwierdzenia na wirtualnej pompie (W5). Alternatywa ostrożniejsza: `Unknown(UNRECOGNIZED_REPLY)`.
- [x] **P5. Krok ilości.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – wielokrotności 0,05 j. (BOL-02). Gdy pompa ma większy krok, AAPS zaokrągli i poprosi o kod z inną ilością → stop (bezpiecznie, ale polecenie się nie uda).
- [x] **P6. Odstęp w aplikacji.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – (BOL-12) aplikacja sama pilnuje odstępu na podstawie własnej historii (bolusy z wysłanym kodem, potwierdzone albo nieznane), mierzonego od wysłania kodu; historia niedostępna → blokada; zegar cofnięty → blokada; 0 = wyłączone. Alternatywa: tylko zapis ustawienia bez sprawdzania (AAPS i tak odmówi). Doprecyzowanie: `Failed(WRONG_CODE)` liczy się do odstępu (decyzja w BOL-12); nieudany zapis historii, „Wyczyść historię” i przestawiony zegar to ryzyko szczątkowe opisane w BOL-12.
- [x] **P7. Potwierdzenie do wszystkich numerów.** Nie wiemy, czy AAPS wysyła potwierdzenie bolusa także na inne dozwolone numery. Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – przyjmować je jak `CARBS` (tylko po kodzie, prezentacja „AAPS potwierdził”).
- [x] **P8. Ostrzeżenie dla `Unknown` bolusa.** Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – osobny tekst „Nie ponawiaj bolusa…” (BOL-16).
- [x] **P9. Wersja AAPS** dla nowych SMS-ów. Rozstrzygnięte (użytkownik, 2026-10-28): przyjęto propozycję – 3.4.2.6 jak w Spec 002.

## Historia zmian

- **2026-10-28 – poprawki po recenzji PR #221 i zatwierdzenie.** BOL-12: ryzyko szczątkowe (nieudany zapis historii, wyczyszczona historia, zegar) i decyzja o `Failed(WRONG_CODE)`; BOL-16: osobny tekst niezgodnej ilości bolusa przed kodem; BOL-17: jedno `addMigrations`, W3 warunkiem scalenia; nowe BOL-19 (rodzaj prośby o kod zgodny z poleceniem). Design: zadanie z kontrolą odstępu i tekstami wyniku przed zadaniem z „Wyślij”, pełna lista plików zadania 1. Pytania P1–P9 zamknięte, status `zatwierdzona`.
- **2026-10-28 – pierwsza wersja (szkic).** Uwzględnia potwierdzenie bolusa przesłane przez użytkownika (fakt B3). Zmienia CMD-01 (Spec 003), PARSER-01 (Spec 002, nowy kontekst), SESSION-08, SESSION-11 – SESSION-13 (Spec 004, rozszerzenie tabel na bolus), DATA-07, DATA-09, DATA-14 (Spec 007, kolumna i migracja), CMDUI-04, CMDUI-12, CMDUI-17, CMDUI-19 (Spec 009), HISTUI-03, HISTUI-04 (Spec 010).
