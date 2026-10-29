# Spec 002: Parser odpowiedzi SMS z AAPS

- **Status:** zatwierdzona
- **Prefiks wymagań:** `PARSER`
- **Pakiety:** `parser`
- **Powiązane:** [docs/plan.md](../../plan.md#parsowanie-odpowiedzi-i-obsługa-błędów), [docs/plan.md](../../plan.md#przepływ-działania) (krok 5), [docs/plan.md](../../plan.md#bezpieczeństwo); [Spec 001: Generator TOTP](../001-totp/spec.md) (kod odsyłany po prośbie o kod); przyszłe specyfikacje `commands` (katalog poleceń), `sms` (odbiór i filtr numeru), `session` (maszyna stanów, timeouty, składanie odpowiedzi z kodem)

## Cel

Aplikacja musi rozpoznać, co AAPS odpisał na wysłane polecenie, i odesłać kod tylko wtedy, gdy wiadomość jest dokładnie prośbą o kod dla tego polecenia i tej ilości. Ta specyfikacja opisuje czystą logikę w Kotlinie: treść SMS + oczekiwane polecenie → zamknięty typ wyniku. Wzorce pochodzą z prawdziwych SMS-ów AAPS 3.4.2.6 w języku polskim ([dane/](dane/README.md)).

## Zakres

- Rozpoznawanie wiadomości AAPS 3.4.2.6 (język polski, bez znaków diakrytycznych) dla poleceń MVP: odpowiedź na `BG`, odpowiedź na `PUMP`, prośba o kod dla `CARBS <g>`, potwierdzenie wykonania `CARBS`, odrzucenie kodu („Zly kod”).
- Minimalny kontekst wejściowy: które polecenie wysłano i (dla `CARBS`) z jaką ilością gramów.
- Porównanie ilości gramów z wiadomości z ilością wysłaną (wynik „niezgodna ilość”).
- Normalizacja białych znaków (końce linii, końcowe spacje) przed dopasowaniem.
- Wyciągnięcie pól potrzebnych w MVP: glikemia z `BG`, ilość gramów z prośby o kod i z potwierdzenia `CARBS`.
- Wynik „nierozpoznana” dla każdej innej treści, w tym nieznanych powiadomień AAPS.
- Brak ujawniania treści SMS w `toString()`, wyjątkach i logach.

**Poza zakresem:**

- Wysyłanie i odbiór SMS, składanie wiadomości wieloczęściowych, filtr po numerze nadawcy – pakiet `sms`. Parser dostaje gotową, kompletną treść jednej wiadomości od właściwego nadawcy.
- Maszyna stanów polecenia, limity czasu (brak odpowiedzi, czas na odesłanie kodu), odrzucanie wiadomości sprzed wysłania polecenia, decyzja, który wynik jest dopuszczalny w danym stanie – pakiet `session`.
- Składanie odpowiedzi `<6 cyfr TOTP><PIN>` i jej wysyłanie – `session` (z użyciem [Spec 001](../001-totp/spec.md)).
- Katalog poleceń (nazwy, szablony SMS, zakres ilości `CARBS`) – specyfikacja `commands`, jeszcze nie istnieje. Parser definiuje własny minimalny typ kontekstu (patrz PARSER-01 i [design.md](design.md#zależność-od-commands)).
- Inne wersje i języki AAPS, komunikaty ze znakami diakrytycznymi, glikemia w mmol/L, format czasu 12-godzinny (AM/PM). Takie wiadomości dają wynik „nierozpoznana”.
- Polecenia spoza MVP i spoza Spec 012 – 013 (cele, LOOP itd.) i ich komunikaty. Bolus i profile opisują [Spec 012](../012-bolus/spec.md) i [Spec 013](../013-profil/spec.md).
- Zamiana pól IOB, COB, delta, baza tymczasowa, bateria na liczby – w MVP są pokazywane użytkownikowi jako tekst wiadomości.
- Teksty komunikatów UI.

## Pojęcia

- **Treść** – tekst jednej wiadomości SMS przekazany parserowi (`String`).
- **Linia** – fragment treści po normalizacji (PARSER-02) rozdzielony znakiem LF.
- **Pierwsza linia** – linia przed pierwszym LF (cała treść, jeśli LF brak).
- **Liczba gramów** (`G`) – zapis `0` albo cyfra `1`–`9`, po której są 0–3 cyfry `0`–`9` (zakres 0–9999, bez zer wiodących).
- **Liczba dziesiętna** (`D`) – opcjonalny znak `-`, co najmniej jedna cyfra, opcjonalnie separator `,` lub `.` i co najmniej jedna cyfra (np. `2`, `10.0`, `0,99`, `-0,34`).
- **Godzina** (`H`) – `0`–`23` zapisane jedną lub dwiema cyframi, `:`, dwie cyfry `00`–`59` (np. `14:50`, `9:05`, `09:05`).

## Wymagania

### PARSER-01: Wejście, kontekst i zamknięty typ wyniku

Parser to czysta funkcja: treść + oczekiwane polecenie → wynik. Oczekiwane polecenie to jedno z: `BG`, `PUMP`, `CARBS` z ilością gramów ≥ 1, `BOLUS` z ilością ≥ 1 setnej jednostki ([Spec 012](../012-bolus/spec.md), BOL-06), „stan profilu”, „lista profili” i „zmiana profilu” z nazwą (niepustą, bez LF) i procentem ≥ 1 ([Spec 013](../013-profil/spec.md), PROF-05). Wynik jest jednym z rodzajów: „odpowiedź BG”, „stan pompy”, „prośba o kod”, „niezgodna ilość”, „wykonano CARBS”, „zły kod”, „nierozpoznana” oraz rodzajów opisanych w Spec 012 i 013 (prośba o kod i niezgodna ilość dla bolusa, wykonano bolus, stan profilu, lista profili, prośba o kod i niezgodny profil dla zmiany profilu). Rodzaje „prośba o kod” (dla `CARBS`, `BOLUS` i zmiany profilu) mają wspólny nadtyp „prośba o kod zgodna z poleceniem”, „niezgodna ilość/profil” – nadtyp „niezgodność z poleceniem”, a „wykonano CARBS” i „wykonano bolus” – nadtyp „potwierdzenie zgodne z poleceniem” (interfejsy znacznikowe; `session` rozpoznaje prośbę o kod i niezgodność po nadtypie, BOL-19). Nadtypy nie zmieniają pól ani `toString()` istniejących rodzajów. Każdy wynik poza „zły kod” zawiera treść wejściową bez zmian (do pokazania użytkownikowi). Parser nie ma stanu: te same argumenty dają zawsze ten sam wynik. Dla dowolnej treści (pustej, z dowolnymi znakami Unicode, dowolnej długości) parser zwraca wynik i nie rzuca wyjątku. Wyjątek (`IllegalArgumentException`) jest dopuszczalny tylko przy tworzeniu kontekstu z wartością niedozwoloną: `CARBS` z ilością < 1, `BOLUS` z ilością < 1, zmiana profilu z pustą nazwą, nazwą z LF albo procentem < 1 (komunikat bez danych SMS).

Kontekst ogranicza rozpoznawane rodzaje:

| Oczekiwane polecenie | Możliwe wyniki |
| --- | --- |
| `BG` | „odpowiedź BG”, „nierozpoznana” |
| `PUMP` | „stan pompy”, „nierozpoznana” |
| `CARBS g` | „prośba o kod”, „niezgodna ilość”, „wykonano CARBS”, „zły kod”, „nierozpoznana” |
| `BOLUS x` | prośba o kod (bolus), niezgodna ilość (bolus), wykonano bolus, „zły kod”, „nierozpoznana” (BOL-07 – BOL-09) |
| stan profilu | stan profilu, „nierozpoznana” (PROF-06) |
| lista profili | lista profili, „nierozpoznana” (PROF-07) |
| zmiana profilu | prośba o kod (profil), niezgodny profil, „zły kod”, „nierozpoznana” (PROF-08 – PROF-10) |

**Kryteria akceptacji:**

- **Zakładając** treść z `dane/bg.txt`, **gdy** oczekiwane polecenie to `PUMP` albo `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść z `dane/pump.txt`, **gdy** oczekiwane polecenie to `BG` albo `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść z `dane/carbs-prosba-o-kod.txt`, `dane/carbs-sukces.txt` albo `dane/zly-kod.txt`, **gdy** oczekiwane polecenie to `BG` albo `PUMP`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść pustą (`""`), **gdy** oczekiwane polecenie to `BG`, `PUMP` albo `CARBS 1`, **wtedy** wynik to „nierozpoznana” z treścią `""`, bez wyjątku.
- **Zakładając** ilość 0 albo −5, **gdy** tworzony jest kontekst `CARBS`, **wtedy** rzucany jest `IllegalArgumentException`.
- **Zakładając** ilość 0 dla kontekstu `BOLUS` albo pustą nazwę, nazwę z LF lub procent 0 dla kontekstu zmiany profilu, **gdy** tworzony jest kontekst, **wtedy** rzucany jest `IllegalArgumentException` bez danych SMS w komunikacie (BOL-06, PROF-05).
- **Zakładając** wynik każdego rodzaju „prośba o kod” (`CARBS`, `BOLUS`, zmiana profilu) i każdego rodzaju niezgodności, **gdy** sprawdzono nadtypy, **wtedy** pierwsze są „prośbą o kod zgodną z poleceniem”, drugie – „niezgodnością z poleceniem” (BOL-19).
- **Zakładając** treść z `dane/bg.txt` i kontekst `BG`, **gdy** parser jest wywołany dwa razy, **wtedy** oba wyniki są równe (`==`).

### PARSER-02: Normalizacja białych znaków

Przed dopasowaniem treść jest normalizowana, a wzorce są sprawdzane na wyniku normalizacji:

1. Treść dłuższa niż 2000 znaków (UTF-16, przed normalizacją) → od razu „nierozpoznana”.
2. Każde `CRLF` (`\r\n`) i każde samotne `CR` (`\r`) zamieniane jest na `LF` (`\n`).
3. Na końcu każdej linii usuwane są spacje (U+0020) i tabulatory (U+0009).
4. Na początku i końcu całej treści usuwane są spacje, tabulatory i znaki LF.

Nic więcej nie jest zmieniane: wielkość liter, spacje wewnątrz linii (także podwójne), spacje na początku linii 2 i dalszych, puste linie wewnątrz treści, inne białe znaki Unicode (np. U+00A0) pozostają i mogą sprawić, że wzorzec się nie dopasuje.

**Kryteria akceptacji:**

- **Zakładając** treść z `dane/pump.txt` z końcami linii `\r\n`, **gdy** kontekst to `PUMP`, **wtedy** wynik to „stan pompy”.
- **Zakładając** treść z `dane/carbs-sukces.txt` z końcami linii `\r`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „wykonano CARBS” z ilością 1.
- **Zakładając** treść z `dane/zly-kod.txt` z dopisanym `"  \n"` na końcu i `" "` na początku, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „zły kod”.
- **Zakładając** treść z `dane/carbs-prosba-o-kod.txt`, w której `odeslij w SMS` zastąpiono `odeslij  w SMS` (dwie spacje), **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść z `dane/zly-kod.txt` zapisaną wielkimi literami (`ZLY KOD. POLECENIE ANULOWANO.`), **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść z `dane/zly-kod.txt` ze znakiem U+00A0 zamiast spacji po `Zly`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść z `dane/bg.txt` dopełnioną spacjami na końcu do długości 2000 znaków, **gdy** kontekst to `BG`, **wtedy** wynik to „odpowiedź BG”; **zakładając** tę samą treść dopełnioną do 2001 znaków, **wtedy** wynik to „nierozpoznana”.

### PARSER-03: Odpowiedź na BG

W kontekście `BG` wynik „odpowiedź BG” powstaje, gdy cała treść po normalizacji to jedna linia o postaci (spacje i znaki interpunkcyjne dokładnie jak niżej):

```text
BG: <glikemia>, Delta: <D> MGDL, IOB: <D>U (Bolus: <D>U Baza: <D>U), COB: <D>g
```

gdzie `<glikemia>` to 1–3 cyfry `0`–`9`. Wynik zawiera glikemię jako liczbę całkowitą w mg/dL i treść wejściową. Pozostałe pola nie są zamieniane na liczby.

**Kryteria akceptacji:**

- **Zakładając** treść z `dane/bg.txt`, **gdy** kontekst to `BG`, **wtedy** wynik to „odpowiedź BG” z glikemią 210 i treścią równą wejściu.
- **Zakładając** wariant syntetyczny `BG: 95, Delta: -4.5 MGDL, IOB: -0,12U (Bolus: 0,00U Baza: -0,12U), COB: 0g`, **gdy** kontekst to `BG`, **wtedy** wynik to „odpowiedź BG” z glikemią 95.
- **Zakładając** wariant syntetyczny `BG: 210, Delta: 10,0 MGDL, IOB: 0.99U (Bolus: 1.33U Baza: -0.34U), COB: 2g` (zamienione separatory), **gdy** kontekst to `BG`, **wtedy** wynik to „odpowiedź BG” z glikemią 210.
- **Zakładając** treść z `dane/bg.txt` z `MGDL` zamienionym na `MMOL` albo z glikemią `6,5` zamiast `210`, **gdy** kontekst to `BG`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść z `dane/bg.txt` z glikemią `1000` (4 cyfry), **gdy** kontekst to `BG`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść z `dane/bg.txt` z dopisaną drugą linią `Uwaga`, **gdy** kontekst to `BG`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść `Odpowiedz: BG: 210, Delta: 10.0 MGDL, IOB: 0,99U (Bolus: 1,33U Baza: -0,34U), COB: 2g` (poprawna linia poprzedzona innym tekstem), **gdy** kontekst to `BG`, **wtedy** wynik to „nierozpoznana”.

### PARSER-04: Odpowiedź na PUMP

W kontekście `PUMP` wynik „stan pompy” powstaje, gdy pierwsza linia po normalizacji to dokładnie:

```text
Ost. Pol.: <minuty> m. temu
```

gdzie `<minuty>` to 1–5 cyfr `0`–`9`. Kolejne linie (zero lub więcej) nie są sprawdzane, bo ich treść zależy od stanu pompy (np. aktywna baza tymczasowa). Wynik zawiera tylko treść wejściową.

**Kryteria akceptacji:**

- **Zakładając** treść z `dane/pump.txt`, **gdy** kontekst to `PUMP`, **wtedy** wynik to „stan pompy” z treścią równą wejściu.
- **Zakładając** wariant syntetyczny z samą pierwszą linią `Ost. Pol.: 12 m. temu`, **gdy** kontekst to `PUMP`, **wtedy** wynik to „stan pompy”.
- **Zakładając** wariant syntetyczny, w którym linie 2–3 z `dane/pump.txt` zastąpiono linią `Bat.: 15%`, **gdy** kontekst to `PUMP`, **wtedy** wynik to „stan pompy”.
- **Zakładając** treść z `dane/pump.txt` z pierwszą linią `Ost. Pol: 0 m. temu` (bez kropki po `Pol`) albo `Ost. Pol.: m. temu` (bez liczby), **gdy** kontekst to `PUMP`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść z `dane/carbs-sukces.txt` (linia `Ost. Pol.: …` jest dopiero drugą linią), **gdy** kontekst to `PUMP`, **wtedy** wynik to „nierozpoznana”.

### PARSER-05: Prośba o kod dla CARBS

W kontekście `CARBS g` wiadomość jest prośbą o kod, gdy cała treść po normalizacji to jedna linia o postaci:

```text
Aby wprowadzic <G>g o <H> odeslij w SMS kod z aplikacji Authenticator dla: <nazwa>, z doklejonym PIN-em
```

gdzie `<nazwa>` to co najmniej jeden dowolny znak inny niż LF (nazwa jest ustawieniem AAPS; parser jej nie wyciąga i od niej nie zależy). Gdy `<G>` = `g`, wynik to „prośba o kod” z ilością `<G>` i treścią wejściową. Gdy `<G>` ≠ `g`, obowiązuje PARSER-06. Godzina `<H>` jest tylko sprawdzana co do formatu i nie jest porównywana z zegarem telefonu.

**Kryteria akceptacji:**

- **Zakładając** treść z `dane/carbs-prosba-o-kod.txt`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „prośba o kod” z ilością 1 i treścią równą wejściu.
- **Zakładając** tę treść z nazwą `Anna Nowak` albo `X` zamiast `Jan`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „prośba o kod” z ilością 1.
- **Zakładając** tę treść z pustą nazwą (`dla: , z doklejonym PIN-em`), **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** wariant syntetyczny `Aby wprowadzic 20g o 9:05 odeslij w SMS kod z aplikacji Authenticator dla: Jan, z doklejonym PIN-em`, **gdy** kontekst to `CARBS 20`, **wtedy** wynik to „prośba o kod” z ilością 20; to samo dla godziny `09:05` i `0:00`.
- **Zakładając** tę treść z godziną `24:00`, `14:5`, `14:60` albo `2:50 PM`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** tę treść z ilością `01g`, `1 g` (spacja przed `g`), `1,5g` albo `10000g`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** tę treść z dopisanym na końcu ` Dziekujemy` albo drugą linią, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** tę treść z `Aby wprowadzić` (ze znakiem `ć`), **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.

### PARSER-06: Niezgodna ilość gramów

Gdy wiadomość ma postać prośby o kod (PARSER-05) albo potwierdzenia wykonania (PARSER-07), ale wyciągnięta ilość `<G>` różni się od ilości `g` z kontekstu, wynik to „niezgodna ilość” z ilością oczekiwaną, ilością z wiadomości, etapem („prośba o kod” albo „potwierdzenie wykonania”) i treścią wejściową. Wynik „prośba o kod” powstaje wyłącznie przy równych ilościach, więc nie istnieje „prośba o kod” z ilością różną od wysłanej.

**Kryteria akceptacji:**

- **Zakładając** treść z `dane/carbs-prosba-o-kod.txt` (1 g), **gdy** kontekst to `CARBS 10`, **wtedy** wynik to „niezgodna ilość” z oczekiwaną 10, otrzymaną 1, etapem „prośba o kod”.
- **Zakładając** wariant syntetyczny prośby o kod z `100g`, **gdy** kontekst to `CARBS 150`, **wtedy** wynik to „niezgodna ilość” z oczekiwaną 150, otrzymaną 100, etapem „prośba o kod”.
- **Zakładając** wariant syntetyczny prośby o kod z `0g`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „niezgodna ilość” z oczekiwaną 1, otrzymaną 0.
- **Zakładając** treść z `dane/carbs-sukces.txt` (1 g), **gdy** kontekst to `CARBS 2`, **wtedy** wynik to „niezgodna ilość” z oczekiwaną 2, otrzymaną 1, etapem „potwierdzenie wykonania”.
- **Zakładając** wariant syntetyczny prośby o kod z `12g`, **gdy** kontekst to `CARBS 1` albo `CARBS 2` (zgodny prefiks lub sufiks cyfr), **wtedy** wynik to „niezgodna ilość” z otrzymaną 12.

### PARSER-07: Wykonano CARBS

W kontekście `CARBS g` wiadomość jest potwierdzeniem wykonania, gdy pierwsza linia po normalizacji to dokładnie:

```text
Pomyslnie wprowadzono <G> g weglowodanow
```

Kolejne linie (zero lub więcej) nie są sprawdzane (stan pompy, zmienny). Gdy `<G>` = `g`, wynik to „wykonano CARBS” z ilością `<G>` i treścią wejściową. Gdy `<G>` ≠ `g`, obowiązuje PARSER-06.

**Kryteria akceptacji:**

- **Zakładając** treść z `dane/carbs-sukces.txt`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „wykonano CARBS” z ilością 1 i treścią równą wejściu.
- **Zakładając** wariant syntetyczny z samą pierwszą linią `Pomyslnie wprowadzono 25 g weglowodanow`, **gdy** kontekst to `CARBS 25`, **wtedy** wynik to „wykonano CARBS” z ilością 25.
- **Zakładając** treść z `dane/carbs-sukces.txt` z pierwszą linią `Pomyslnie wprowadzono 1g weglowodanow` (bez spacji przed `g`) albo `Pomyślnie wprowadzono 1 g węglowodanów`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść z `dane/carbs-sukces.txt` poprzedzoną linią `Powiadomienie`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.

### PARSER-08: Zły kod

W kontekście `CARBS g` wynik „zły kod” powstaje, gdy cała treść po normalizacji to dokładnie `Zly kod. Polecenie anulowano.`. Wynik nie zawiera treści ani innych pól.

**Kryteria akceptacji:**

- **Zakładając** treść z `dane/zly-kod.txt`, **gdy** kontekst to `CARBS 1` albo `CARBS 50`, **wtedy** wynik to „zły kod”.
- **Zakładając** treść `Zly kod. Polecenie anulowano` (bez końcowej kropki) albo `Zly kod.`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść z `dane/zly-kod.txt` z drugą linią `Ost. Pol.: 1 m. temu`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.

### PARSER-09: Nierozpoznana treść

Każda treść, która w danym kontekście nie spełnia dokładnie jednego z wzorców PARSER-03 – PARSER-08, daje wynik „nierozpoznana” z treścią wejściową. Dotyczy to w szczególności: nieznanych powiadomień AAPS (np. o poleceniu wykonanym z innego dozwolonego numeru), komunikatów AAPS, których treści nie znamy (przekroczony czas na kod, przekroczony limit), wiadomości, które jedynie zawierają rozpoznawany fragment, oraz wiadomości od ludzi. Parser nie szuka wzorców wewnątrz treści („zawiera”), tylko dopasowuje całą treść albo całą pierwszą linię od jej początku do końca.

**Kryteria akceptacji:**

- **Zakładając** wariant syntetyczny `Uzytkownik wykonal polecenie. Aby wprowadzic 1g o 14:50 odeslij w SMS kod z aplikacji Authenticator dla: Jan, z doklejonym PIN-em`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** wariant syntetyczny z dwiema liniami: pierwsza `Bolus 2,00U podany`, druga – treść z `dane/carbs-prosba-o-kod.txt`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.
- **Zakładając** treść `Czesc, kupisz chleb?`, **gdy** kontekst to `BG`, `PUMP` albo `CARBS 1`, **wtedy** wynik to „nierozpoznana” z treścią `Czesc, kupisz chleb?`.
- **Zakładając** treść `123456`, **gdy** kontekst to `CARBS 1`, **wtedy** wynik to „nierozpoznana”.

### PARSER-10: Brak ujawniania treści

Treść SMS z AAPS to dane medyczne (glikemia, insulina, węglowodany), a nierozpoznana wiadomość może zawierać cokolwiek. Dlatego:

- `toString()` każdego wyniku i kontekstu zawiera tylko nazwę rodzaju wyniku (dla „niezgodna ilość” także etap: prośba o kod albo potwierdzenie – bez wartości) i nie zawiera treści wejściowej ani żadnej wartości z niej wyciągniętej (glikemii, ilości, nazwy, godziny). Ilość z kontekstu też nie jest wypisywana.
- Pakiet `parser` nie wywołuje żadnego API logowania (`android.util.Log`, `println`, `System.out`/`err`, loggerów).
- Parser nie rzuca wyjątków zawierających treść wejściową (PARSER-01: dla treści nie rzuca żadnych).

**Kryteria akceptacji:**

- **Zakładając** wynik parsowania każdego pliku z `dane/` w kontekście, w którym jest rozpoznawany, **gdy** wywołano `toString()` na wyniku, **wtedy** tekst nie zawiera `210`, `89%`, `Jan`, `14:50`, `Pomyslnie`, `Zly` ani żadnej linii treści.
- **Zakładając** wynik „niezgodna ilość” z oczekiwaną 150 i otrzymaną 100, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `150` ani `100`.
- **Zakładając** treść `Czesc, kupisz chleb?` w kontekście `BG`, **gdy** wywołano `toString()` na wyniku „nierozpoznana”, **wtedy** tekst nie zawiera `chleb`.
- **Zakładając** kontekst `CARBS 37`, **gdy** wywołano na nim `toString()`, **wtedy** tekst nie zawiera `37`.

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Kontekst `CARBS` z ilością < 1 | `IllegalArgumentException` (bez treści SMS) | PARSER-01 |
| Treść pusta, z dziwnymi znakami | „nierozpoznana”, bez wyjątku | PARSER-01, PARSER-09 |
| Treść dłuższa niż 2000 znaków | „nierozpoznana” | PARSER-02 |
| Rozpoznawalna treść w innym kontekście (np. odpowiedź BG przy `PUMP`) | „nierozpoznana” | PARSER-01 |
| Prośba o kod z inną ilością niż wysłana (np. obcięta przez limit AAPS) | „niezgodna ilość”, etap „prośba o kod” | PARSER-06 |
| Potwierdzenie wykonania z inną ilością niż wysłana | „niezgodna ilość”, etap „potwierdzenie wykonania” | PARSER-06 |
| Ilość w wiadomości z zerem wiodącym, ułamkowa lub > 9999 | „nierozpoznana” | PARSER-05, PARSER-07 |
| Godzina w prośbie o kod poza 0:00–23:59 lub w formacie 12-godzinnym | „nierozpoznana” | PARSER-05 |
| Poprawny wzorzec otoczony innym tekstem lub inną linią przed nim | „nierozpoznana” | PARSER-09 |
| Nieznane powiadomienie AAPS, komunikat o timeoucie / limicie | „nierozpoznana” | PARSER-09 |
| Inna wersja / język AAPS, znaki diakrytyczne, mmol/L | „nierozpoznana” | PARSER-02, PARSER-03, PARSER-09 |

Teksty w cudzysłowach to nazwy rodzajów wyniku, nie komunikaty UI. Reakcję na każdy wynik (stop, komunikat, pokazanie treści) określi specyfikacja `session`.

## Bezpieczeństwo

Zasady z [AGENTS.md](../../../AGENTS.md#bezpieczeństwo), które dotyczą tej funkcji:

- **Nie odsyłamy kodu, jeśli odpowiedź nie zgadza się z poleceniem.** Parser dostarcza `session` jednoznaczną podstawę: jedynym wynikiem, po którym wolno odesłać kod, jest „prośba o kod”, a powstaje on tylko przy dokładnym dopasowaniu całej treści (PARSER-05, PARSER-09), w kontekście `CARBS` (PARSER-01) i przy ilości równej wysłanej (PARSER-06). Inna ilość → „niezgodna ilość”; nierozpoznana treść → „nierozpoznana”. Pozostałe warunki z AGENTS.md należą do innych pakietów: inny numer nadawcy – `sms`; odpowiedź sprzed wysłania i wynik niepasujący do stanu sesji – `session`.
- **Powiadomienia AAPS bez pytania.** AAPS wysyła SMS-y o poleceniach wykonanych z innych dozwolonych numerów. Użytkownik potwierdził (pytanie 4), że po `CARBS` z innego numeru dostaje tekst **identyczny** z własnym potwierdzeniem (`dane/carbs-sukces.txt`). Parser tego nie odróżni i nie próbuje: „wykonano CARBS” (PARSER-07) mówi tylko, że treść pasuje do wzorca, a nie, czyje polecenie wykonano. Wymóg dla `session` (do jej specyfikacji): wynik „wykonano CARBS” przyjmować wyłącznie w stanie „kod wysłany”; w każdym innym stanie (np. czekanie na prośbę o kod) traktować go jako nieoczekiwaną wiadomość – stop i komunikat, bez odsyłania kodu. Ryzyko szczątkowe: `CARBS` z tą samą ilością z innego numeru w oknie po odesłaniu naszego kodu zostanie wzięte za nasze potwierdzenie – do opisania w spec `session`. Prośba o kod trafia tylko do numeru, który wysłał polecenie, a ścisłe wzorce (PARSER-09) sprawiają, że powiadomienie nie zostanie uznane za prośbę o kod, chyba że byłoby z nią identyczne znak w znak.
- **Nie logujemy PIN-u ani kodów.** Parser nie przyjmuje PIN-u ani kodu TOTP; wiadomości AAPS z danych nie zawierają kodu. Treść SMS nie jest logowana ani wypisywana w `toString()` (PARSER-10).
- **Żadnych prawdziwych danych w repo.** Imię w prośbie o kod zastąpione fikcyjnym `Jan`; pliki w `dane/` nie zawierają numerów, PIN-ów ani kodów. Wartości medyczne w danych (glikemia 210, IOB) to jednorazowy odczyt potrzebny jako wzorzec, bez danych identyfikujących.
- **Bez automatycznego ponawiania i omijania limitów.** Parser niczego nie wysyła; „niezgodna ilość” (np. po obcięciu przez limit AAPS) nie prowadzi do korekty ani ponownej próby.

## Dane testowe

Źródło: prawdziwe SMS-y z AAPS 3.4.2.6 w języku polskim (bez znaków diakrytycznych), zebrane przez użytkownika; imię zastąpione fikcyjnym `Jan`. Pliki leżą w [dane/](dane/README.md), jeden plik na wiadomość, z zachowanym podziałem linii:

| Plik | Kontekst | Oczekiwany wynik |
| --- | --- | --- |
| `bg.txt` | `BG` | „odpowiedź BG”, glikemia 210 |
| `pump.txt` | `PUMP` | „stan pompy” |
| `carbs-prosba-o-kod.txt` | `CARBS 1` | „prośba o kod”, 1 g |
| `carbs-sukces.txt` | `CARBS 1` | „wykonano CARBS”, 1 g |
| `zly-kod.txt` | `CARBS 1` | „zły kod” |

Warianty oznaczone w kryteriach jako „syntetyczne” to modyfikacje tych wiadomości wymyślone do testów granic; nie są to potwierdzone komunikaty AAPS i nie trafiają do `dane/`.

## Otwarte pytania

- [x] **1. Przekroczenie czasu na kod.** Rozstrzygnięte: AAPS nic nie wysyła. Upływ czasu obsługuje `session` (timeout), parser nie ma tu nic do rozpoznania.
- [x] **2. CARBS powyżej limitu AAPS.** Rozstrzygnięte bez znajomości treści (użytkownik nie może tego sprawdzić): oba możliwe warianty kończą się bezpiecznie – prośba o kod z obciętą ilością daje „niezgodna ilość” (PARSER-06), osobny komunikat odmowy daje „nierozpoznana” (PARSER-09); w obu przypadkach kod nie jest odsyłany, a użytkownik widzi treść SMS. Osobny wynik „odrzucono przez limit” poza MVP; do dodania, gdy poznamy treść.
- [x] **3. Czas na odesłanie kodu.** Nie dotyczy parsera. W SMS Communicator nie ma takiego ustawienia (wg użytkownika); wartość trzeba ustalić w spec `session` (np. z dokumentacji lub kodu AAPS).
- [x] **4. Powiadomienie o poleceniu z innego numeru.** Rozstrzygnięte: po `CARBS` z innego numeru treść jest identyczna z własnym potwierdzeniem. Skutki i wymóg dla `session` – sekcja Bezpieczeństwo. Treści powiadomień o innych poleceniach (np. bolus) nie znamy; dają „nierozpoznana”.

## Historia zmian

- **2026-10-29 – scalenie zmian ze Spec 011 – 013 (po wdrożeniu).** PARSER-01: konteksty `BOLUS`, stan profilu, lista profili i zmiana profilu z nowymi rodzajami wyników (opisanymi w BOL-06 – BOL-09 i PROF-05 – PROF-10); wspólne nadtypy znacznikowe wyników (prośba o kod, niezgodność, potwierdzenie) zgodnie z kodem; wyjątek przy tworzeniu kontekstu dla nowych poleceń. Wyniki dla `BG`, `PUMP` i `CARBS` bez zmian. Zakres i „Poza zakresem” doprecyzowane (bolus i profile opisują Spec 012 i 013). Design 002 bez zmian (interfejsy opisuje [design 012](../012-bolus/design.md#interfejsy)).
