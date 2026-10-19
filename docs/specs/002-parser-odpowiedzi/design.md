# Design 002: Parser odpowiedzi SMS z AAPS

Dotyczy: [spec.md](spec.md)

## Przegląd

Cała funkcja leży w pakiecie `pl.twojanazwa.aapsremote.parser` i jest czystym Kotlinem/JVM (bez `android.*`), zgodnie z [AGENTS.md](../../../AGENTS.md#architektura). Używa tylko `kotlin.text.Regex` (pod spodem `java.util.regex`) i operacji na `String`.

Składniki:

- `ExpectedCommand` – minimalny kontekst: jakie polecenie wysłano i z jaką ilością (PARSER-01).
- `AapsReply` – zamknięty (`sealed`) typ wyniku z redagowanym `toString()` (PARSER-01, PARSER-10).
- `ReplyNormalizer` – limit długości i normalizacja białych znaków (PARSER-02).
- `AapsReplyParser` – wybór wzorców wg kontekstu, dopasowanie, porównanie ilości (PARSER-03 – PARSER-09).
- `AapsPl3426Patterns` – wzorce dla AAPS 3.4.2.6, język polski (stałe `Regex`, `internal`).

Przepływ danych: `sms` (odbiór, filtr numeru, sklejenie części) → `session` (wie, jakie polecenie jest aktywne) → `AapsReplyParser.parse(body, expected)` → `session` decyduje na podstawie wyniku i własnego stanu.

## Interfejsy

```kotlin
package pl.twojanazwa.aapsremote.parser

/** Polecenie, na które czekamy odpowiedzi. toString() bez ilości (PARSER-10). */
sealed interface ExpectedCommand {
    data object Bg : ExpectedCommand
    data object Pump : ExpectedCommand

    /** Rzuca IllegalArgumentException dla grams < 1 (komunikat bez danych SMS). */
    class Carbs(val grams: Int) : ExpectedCommand {
        init { require(grams >= 1) { "grams must be >= 1" } }
        override fun equals(other: Any?): Boolean = other is Carbs && other.grams == grams
        override fun hashCode(): Int = grams
        override fun toString(): String = "ExpectedCommand.Carbs(***)"
    }
}

/**
 * Wynik parsowania. Każdy wariant (poza WrongCode) ma [text] = treść wejściowa bez zmian.
 * data class z jawnie nadpisanym toString() – equals/hashCode generowane, toString redagowane.
 */
sealed interface AapsReply {
    /** PARSER-03 */
    data class BgReport(val glucoseMgdl: Int, val text: String) : AapsReply {
        override fun toString(): String = "AapsReply.BgReport(***)"
    }

    /** PARSER-04 */
    data class PumpStatus(val text: String) : AapsReply {
        override fun toString(): String = "AapsReply.PumpStatus(***)"
    }

    /** PARSER-05. Istnieje tylko, gdy grams == ExpectedCommand.Carbs.grams. */
    data class CodeRequest(val grams: Int, val text: String) : AapsReply {
        override fun toString(): String = "AapsReply.CodeRequest(***)"
    }

    /** PARSER-06 */
    data class CarbsAmountMismatch(
        val expectedGrams: Int,
        val receivedGrams: Int,
        val stage: CarbsStage,
        val text: String,
    ) : AapsReply {
        override fun toString(): String = "AapsReply.CarbsAmountMismatch(stage=$stage, ***)"
    }

    /** PARSER-07 */
    data class CarbsDone(val grams: Int, val text: String) : AapsReply {
        override fun toString(): String = "AapsReply.CarbsDone(***)"
    }

    /** PARSER-08 */
    data object WrongCode : AapsReply

    /** PARSER-09 */
    data class Unrecognized(val text: String) : AapsReply {
        override fun toString(): String = "AapsReply.Unrecognized(***)"
    }
}

enum class CarbsStage { CODE_REQUEST, CONFIRMATION }

object AapsReplyParser {
    const val MAX_LENGTH: Int = 2000

    /** Nigdy nie rzuca wyjątku (PARSER-01). */
    fun parse(body: String, expected: ExpectedCommand): AapsReply
}
```

Sygnatury są wiążące dla nazw i typów publicznych; ciała i prywatne pomocniki zależą od wykonawcy. `CarbsStage` w `toString()` jest dopuszczalny – to stała, nie dane z SMS.

## Zależność od `commands`

Specyfikacja `commands` jeszcze nie istnieje. `ExpectedCommand` jest celowo minimalny i należy do `parser`, żeby parser nie zależał od przyszłego katalogu. Gdy powstanie `commands`, to `session` (albo sam katalog) mapuje wpis katalogu na `ExpectedCommand`; parser nie importuje `commands`. Zakres ilości `CARBS` (np. 1–100 z planu) waliduje `commands`; parser wymaga tylko `grams >= 1`. Nowe polecenie z katalogu = nowy wariant `ExpectedCommand`, nowe wzorce i nowe wyniki – zmiana tej specyfikacji.

## Model danych i stany

Parser jest bezstanowy. Algorytm `parse(body, expected)`:

1. `body.length > 2000` → `Unrecognized(body)`.
2. Normalizacja (PARSER-02): `replace("\r\n", "\n")`, potem `replace('\r', '\n')`; każda linia `trimEnd(' ', '\t')`; całość `trim(' ', '\t', '\n')`. Wynik `n`.
3. `firstLine = n.substringBefore('\n')`.
4. Wybór wg `expected`:
   - `Bg`: `BG_LINE.matchEntire(n)` → `BgReport(glucose.toInt(), body)`.
   - `Pump`: `PUMP_FIRST_LINE.matchEntire(firstLine)` → `PumpStatus(body)`.
   - `Carbs(g)`, kolejno:
     1. `WRONG_CODE.matchEntire(n)` → `WrongCode`.
     2. `CODE_REQUEST.matchEntire(n)` → ilość `r`; `r == g` → `CodeRequest(r, body)`, inaczej `CarbsAmountMismatch(g, r, CODE_REQUEST, body)`.
     3. `CARBS_DONE_FIRST_LINE.matchEntire(firstLine)` → ilość `r`; `r == g` → `CarbsDone(r, body)`, inaczej `CarbsAmountMismatch(g, r, CONFIRMATION, body)`.
5. Brak dopasowania → `Unrecognized(body)`.

Wzorce rozpoznawane w kontekście `CARBS` wykluczają się wzajemnie (różne pierwsze słowa: `Zly`, `Aby`, `Pomyslnie`), więc kolejność nie zmienia wyniku. Zawsze `matchEntire` – nigdy `find`/`containsMatchIn` (PARSER-09). Liczby gramów mają co najwyżej 4 cyfry, glikemia 3, więc `toInt()` nie przepełnia się; brak `NumberFormatException`.

Wzorce (`AapsPl3426Patterns`, stałe `Regex`; `.` w `Regex` domyślnie nie dopasowuje `\n`):

```kotlin
private const val G = """(0|[1-9][0-9]{0,3})"""          // liczba gramów
private const val D = """-?[0-9]+(?:[.,][0-9]+)?"""       // liczba dziesiętna
private const val H = """(?:[01]?[0-9]|2[0-3]):[0-5][0-9]""" // godzina 24 h

val BG_LINE = Regex(
    """BG: ([0-9]{1,3}), Delta: $D MGDL, IOB: ${D}U \(Bolus: ${D}U Baza: ${D}U\), COB: ${D}g"""
)
val PUMP_FIRST_LINE = Regex("""Ost\. Pol\.: [0-9]{1,5} m\. temu""")
val CODE_REQUEST = Regex(
    """Aby wprowadzic ${G}g o $H odeslij w SMS kod z aplikacji Authenticator dla: [^\n]+, z doklejonym PIN-em"""
)
val CARBS_DONE_FIRST_LINE = Regex("""Pomyslnie wprowadzono $G g weglowodanow""")
val WRONG_CODE = Regex("""Zly kod\. Polecenie anulowano\.""")
```

Wzorce służą jako wskazówka; wiążące są reguły i kryteria w spec. Wykonawca może je zapisać inaczej, jeśli testy przechodzą.

Mapowanie wyników na reakcję `session` (informacyjnie, wiąże dopiero spec `session`):

| Wynik | Dopuszczalny w stanie sesji | Reakcja |
| --- | --- | --- |
| `BgReport`, `PumpStatus` | czeka na odpowiedź `BG`/`PUMP` | pokaż, zakończ |
| `CodeRequest` | czeka na prośbę o kod | jedyny wynik, po którym wolno odesłać kod |
| `CarbsAmountMismatch` | dowolny | stop, pokaż treść, nic nie odsyłaj |
| `CarbsDone` | kod wysłany | pokaż, zapisz w historii |
| `WrongCode` | kod wysłany | komunikat + sugestia sprawdzenia zegara |
| `Unrecognized` | dowolny | stop (dla `CARBS` przed odesłaniem kodu) albo pokaż surową treść |
| dowolny wynik w innym stanie niż wymieniony | – | stop, nic nie odsyłaj |

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| **Porównanie ilości robi parser** (wynik `CarbsAmountMismatch`), a `CodeRequest` istnieje tylko przy zgodnej ilości | parser zwraca `CodeRequest(grams)` dla dowolnej ilości, porównuje `session` | Parser i tak dostaje kontekst z ilością. Dzięki temu w typie wyniku nie istnieje „prośba o kod z inną ilością” – `session` nie może zapomnieć porównania, a jedyny wynik otwierający odesłanie kodu jest już sprawdzony (zasada AGENTS.md o niezgodnej ilości jest wymuszona typem, nie dyscypliną). `CodeRequest.grams` zostaje w wyniku, więc `session` może dodatkowo sprawdzić `grams == wysłane` jako asercję obronną. Porównanie testuje się w `parser` bez maszyny stanów. |
| Parser nie zna stanu sesji (faza „przed kodem”/„po kodzie”) | kontekst z fazą, parser odrzuca np. `CarbsDone` przed odesłaniem kodu | Stan należy do `session`; jego powielanie w parserze grozi rozjazdem. Kontekst polecenia wystarcza, żeby odciąć wiadomości o innych poleceniach. `session` musi traktować wynik niepasujący do stanu jako błąd (tabela wyżej). |
| Ścisłe, kotwiczone dopasowanie (`matchEntire`) całej treści lub całej pierwszej linii | szukanie fragmentu (`contains`) | Powiadomienie AAPS o poleceniu z innego numeru albo dowolny SMS zawierający fragment prośby o kod nie może zostać uznany za prośbę o kod (PARSER-09). Koszt: każda zmiana tekstu AAPS daje „nierozpoznana” – to bezpieczna porażka. |
| Cała treść dla `BG`, prośby o kod, „zły kod”; tylko pierwsza linia dla `PUMP` i potwierdzenia `CARBS` | cała treść wszędzie | Linie 2+ w `PUMP` i potwierdzeniu `CARBS` to stan pompy zależny od sytuacji (baza tymczasowa, łączność); znamy tylko jeden wariant. Pierwsza linia jest charakterystyczna, a ani `PumpStatus`, ani `CarbsDone` nie otwierają odesłania kodu. Prośba o kod – jedyna wiadomość prowadząca do odesłania kodu – jest sprawdzana w całości. |
| Normalizacja: CRLF/CR → LF, końcowe spacje/tabulatory linii, `trim` całości; nic więcej | brak normalizacji; pełna (zwijanie spacji, ignorowanie wielkości liter, usuwanie diakrytyków) | Końce linii zależą od bramki SMS / emulatora / systemu, a końcowe spacje są niewidoczne – ich różnica nie zmienia znaczenia. Zwijanie spacji, ignorowanie wielkości liter i diakrytyków osłabiałoby kotwice i dopuszczało teksty, których AAPS 3.4.2.6 nie wysyła. |
| Limit 2000 znaków | brak limitu | Wiadomość AAPS ma najwyżej kilkaset znaków; limit zamyka przypadek ogromnego wejścia od innego nadawcy, zanim trafi do wyrażeń regularnych. Wartość ze zapasem ponad 10 części SMS (10 × 153 znaki GSM-7 = 1530). |
| Wyciągamy tylko glikemię (`BG`) i gramy (`CARBS`) | wszystkie pola (delta, IOB, COB, bateria, baza tymczasowa) | Plan potrzebuje glikemii na ekranie głównym i gramów do porównania. Reszta jest pokazywana jako tekst. Mniej parsowania = mniej miejsc na pomyłkę w formatach liczb, których znamy po jednym przykładzie. Pola można dodać zmianą spec. |
| Format pól liczbowych `BG` tolerancyjny (`-`, `,` lub `.`) | dokładnie jak w przykładzie (`10.0`, `0,99`) | Znamy jeden przykład; separator zależy od ustawień regionalnych AAPS. `BgReport` nie otwiera odesłania kodu, więc tolerancja nie zmniejsza bezpieczeństwa, a zmniejsza liczbę fałszywych „nierozpoznana”. Struktura linii (etykiety, kolejność, `MGDL`) pozostaje ścisła. |
| Godzina w prośbie o kod: 1–2 cyfry godziny, bez porównania z zegarem | dokładnie 2 cyfry; porównanie z czasem telefonu | Przed 10:00 AAPS może wypisać `9:05` lub `09:05` – nie wiemy którego, oba są bezpieczne. Porównanie z zegarem wymagałoby tolerancji, której spec nie zna (zegary dwóch telefonów); ochronę przed starymi wiadomościami daje `session`. |
| Nazwa w prośbie o kod: dowolny niepusty tekst w jednej linii, nie wyciągana | dopasowanie do skonfigurowanej nazwy | Nazwa jest ustawieniem AAPS, zmienia się; aplikacja jej nie zna. Wyciąganie jej niepotrzebnie zwiększałoby ilość danych osobowych w wyniku. |
| Gramy bez zer wiodących, maks. 4 cyfry | `\d+` | `01g` nie jest formatem AAPS – lepiej „nierozpoznana” niż zgadywanie; 4 cyfry wykluczają przepełnienie `Int`. |
| Wynik niesie `text` = wejście bez zmian | tekst po normalizacji | Plan wymaga pokazania surowej treści; użytkownik widzi dokładnie to, co przyszło. |
| `data class` z ręcznym `toString()` | zwykłe klasy | Równość strukturalna przydaje się w testach i `session`; nadpisany `toString()` usuwa dane medyczne z logów i wyjątków (PARSER-10). |
| Wzorce w osobnym obiekcie z nazwą wersji (`AapsPl3426Patterns`) | wzorce w parserze | Teksty są przypisane do wersji i języka AAPS; nowa wersja = nowy zestaw obok, bez mieszania. |

## Mapowanie wymagań na testy

Testy jednostkowe JUnit 5 w `app/src/test/java/pl/twojanazwa/aapsremote/parser/`, nazwy zawierają identyfikator wymagania. Pliki z [dane/](dane/README.md) kopiowane są bez zmian do `app/src/test/resources/parser/aaps-3.4.2.6-pl/` i czytane przez `ClassLoader.getResource` (pomocnik `AapsSamples`). Testy końców linii (PARSER-02) budują tekst z jawnymi `\n`, `\r\n`, `\r`.

| Wymaganie | Test (klasa / rodzaj) |
| --- | --- |
| PARSER-01 | `AapsReplyParserContextTest` – jednostkowy, parametryzowany: każdy plik × każdy kontekst; pusta treść; `Carbs(0)`, `Carbs(-5)`; determinizm |
| PARSER-02 | `ReplyNormalizerTest` – jednostkowy: CRLF, CR, spacje końcowe, wielkie litery, U+00A0, granica 2000/2001 |
| PARSER-03 | `BgReplyTest` – jednostkowy: `bg.txt` + warianty syntetyczne z spec |
| PARSER-04 | `PumpReplyTest` – jednostkowy |
| PARSER-05 | `CarbsCodeRequestTest` – jednostkowy, parametryzowany godzinami i ilościami z spec |
| PARSER-06 | `CarbsAmountMismatchTest` – jednostkowy |
| PARSER-07 | `CarbsDoneTest` – jednostkowy |
| PARSER-08 | `WrongCodeTest` – jednostkowy |
| PARSER-09 | `UnrecognizedReplyTest` – jednostkowy: wzorce osadzone w innym tekście, druga linia, SMS od człowieka |
| PARSER-10 | `ParserRedactionTest` – jednostkowy: `toString()` wyników dla każdego pliku, `CarbsAmountMismatch`, `Unrecognized`, `ExpectedCommand.Carbs(37)` |

Brak wywołań API logowania w `parser` (PARSER-10) sprawdza recenzja kodu; test nie jest wymagany.

## Podział na zadania

Kolejność wynika z zależności. Każde zadanie to jeden PR, ok. 200–400 linii z testami.

1. **Typy, normalizacja, BG i PUMP** – `ExpectedCommand`, `AapsReply`, `CarbsStage`, `ReplyNormalizer`, `AapsReplyParser` (gałęzie `Bg`, `Pump`, fallback `Unrecognized`; gałąź `Carbs` na razie zwraca `Unrecognized`), wzorce `BG_LINE` i `PUMP_FIRST_LINE`, zasoby testowe (wszystkie 5 plików) i `AapsSamples` + `ReplyNormalizerTest`, `BgReplyTest`, `PumpReplyTest`, część `ParserRedactionTest`. Wymagania: PARSER-02, PARSER-03, PARSER-04, PARSER-01 (część), PARSER-09 (część), PARSER-10 (część).
2. **CARBS: prośba o kod, niezgodna ilość, potwierdzenie, zły kod** – gałąź `Carbs` i wzorce `CODE_REQUEST`, `CARBS_DONE_FIRST_LINE`, `WRONG_CODE` + `CarbsCodeRequestTest`, `CarbsAmountMismatchTest`, `CarbsDoneTest`, `WrongCodeTest`, `UnrecognizedReplyTest`, `AapsReplyParserContextTest`, reszta `ParserRedactionTest`. Wymagania: PARSER-05, PARSER-06, PARSER-07, PARSER-08, PARSER-01, PARSER-09, PARSER-10 (reszta).
