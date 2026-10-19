# Design 003: Katalog poleceń

Dotyczy: [spec.md](spec.md)

## Przegląd

Cała funkcja leży w pakiecie `pl.twojanazwa.aapsremote.commands` i jest czystym Kotlinem/JVM (bez `android.*`), zgodnie z [AGENTS.md](../../../AGENTS.md#architektura). Jedyna zależność spoza biblioteki standardowej to `pl.twojanazwa.aapsremote.parser.ExpectedCommand` (CMD-08). Kierunek zależności: `commands` → `parser`; `parser` nie importuje `commands` (zgodnie z [design 002](../002-parser-odpowiedzi/design.md#zależność-od-commands)).

Składniki:

- `CommandId` – identyfikatory rodzajów poleceń (CMD-01).
- `CommandDefinition`, `CommandParameter`, `Approval` – wpis katalogu jako dane (CMD-01, CMD-02, CMD-06).
- `CommandCatalog` – lista wpisów MVP i wyszukiwanie po identyfikatorze (CMD-01).
- `CarbsLimits` – stałe: 1, 50, 40, szybkie wartości (CMD-03, CMD-05, CMD-06).
- `Command` – zamknięty typ poprawnego polecenia; `Command.Carbs` powstaje tylko przez walidację (CMD-03, CMD-04, CMD-05, CMD-09).
- `CarbsValidation`, `CarbsAmountError` – wynik walidacji (CMD-03, CMD-04, CMD-09).
- `smsText()`, `toExpectedCommand()`, `definition` – rozszerzenia na `Command` (CMD-07, CMD-08, CMD-01).

Przepływ danych: `ui` pokazuje wpisy z `CommandCatalog` → dla `CARBS` waliduje pole przez `Command.Carbs.parse(text)` albo szybką wartość przez `Command.Carbs.of(g)` → sprawdza `approval` i `requiresExtraConfirmation` → `session` dostaje `Command`, wysyła `command.smsText()` przez `sms` i parsuje odpowiedzi z `command.toExpectedCommand()`.

## Interfejsy

```kotlin
package pl.twojanazwa.aapsremote.commands

import pl.twojanazwa.aapsremote.parser.ExpectedCommand

/** Rodzaj polecenia. Nowe polecenie = nowa wartość + wpis w CommandCatalog (CMD-01). */
enum class CommandId { BG, PUMP, CARBS }

/** Sposób zatwierdzenia przez użytkownika (CMD-02). */
enum class Approval {
    /** Wysłanie od razu po wybraniu, bez ekranu potwierdzenia. */
    ONE_TAP,

    /** Ekran podsumowania i PIN AAPS przed wysłaniem. */
    SUMMARY_WITH_PIN,
}

/** Parametr polecenia jako dane (CMD-01, CMD-06). */
sealed interface CommandParameter {
    data object None : CommandParameter

    data class Grams(
        val min: Int,
        val max: Int,
        val extraConfirmationAboveGrams: Int,
        val quickValues: List<Int>,
    ) : CommandParameter
}

/** Wpis katalogu. keyword: tylko A–Z. */
data class CommandDefinition(
    val id: CommandId,
    val keyword: String,
    val parameter: CommandParameter,
    val requiresCode: Boolean,
    val approval: Approval,
)

object CarbsLimits {
    const val MIN_GRAMS: Int = 1
    const val MAX_GRAMS: Int = 50
    /** Ilość > tej wartości wymaga dodatkowego potwierdzenia (CMD-05). */
    const val EXTRA_CONFIRMATION_ABOVE_GRAMS: Int = 40
    val QUICK_VALUES: List<Int> = listOf(10, 20, 30)
}

object CommandCatalog {
    /** BG, PUMP, CARBS – w tej kolejności (CMD-01). */
    val entries: List<CommandDefinition>

    /** Zawsze zwraca wpis; każdy CommandId ma dokładnie jeden. */
    fun definitionOf(id: CommandId): CommandDefinition
}

/** Poprawne polecenie gotowe do wysłania. */
sealed interface Command {
    val id: CommandId

    /** CMD-05; false dla BG i PUMP. */
    val requiresExtraConfirmation: Boolean

    data object Bg : Command

    data object Pump : Command

    /** Tylko przez of()/parse() – konstruktor prywatny (CMD-03). toString() bez ilości (CMD-09). */
    class Carbs private constructor(val grams: Int) : Command {
        override fun equals(other: Any?): Boolean = other is Carbs && other.grams == grams
        override fun hashCode(): Int = grams
        override fun toString(): String = "Command.Carbs(***)"

        companion object {
            /** CMD-03. Nigdy nie rzuca. */
            fun of(grams: Int): CarbsValidation

            /** CMD-04. Nigdy nie rzuca. Bez trim(). */
            fun parse(input: String): CarbsValidation
        }
    }
}

sealed interface CarbsValidation {
    /** toString() zawiera tylko zredagowane polecenie. */
    data class Valid(val command: Command.Carbs) : CarbsValidation

    /** Bez tekstu wejściowego (CMD-09). */
    data class Invalid(val error: CarbsAmountError) : CarbsValidation
}

enum class CarbsAmountError { EMPTY, INVALID_CHARACTERS, BELOW_MIN, ABOVE_MAX }

/** CMD-07: "BG", "PUMP", "CARBS <g>". */
fun Command.smsText(): String

/** CMD-08 */
fun Command.toExpectedCommand(): ExpectedCommand

/** CMD-01: CommandCatalog.definitionOf(id). */
val Command.definition: CommandDefinition
```

Sygnatury są wiążące dla nazw i typów publicznych; ciała i prywatne pomocniki zależą od wykonawcy. `data object` `Bg`/`Pump` mają domyślny `toString()` (`Bg`, `Pump`) – nie zawierają danych medycznych.

## Model danych i stany

Logika jest bezstanowa. Wpisy katalogu:

| `id` | `keyword` | `parameter` | `requiresCode` | `approval` |
| --- | --- | --- | --- | --- |
| `BG` | `BG` | `None` | `false` | `ONE_TAP` |
| `PUMP` | `PUMP` | `None` | `false` | `ONE_TAP` |
| `CARBS` | `CARBS` | `Grams(1, 50, 40, [10, 20, 30])` (z `CarbsLimits`) | `true` | `SUMMARY_WITH_PIN` |

Algorytm `Command.Carbs.parse(input)` (CMD-04):

1. `input.isEmpty()` → `Invalid(EMPTY)`.
2. `input.any { it !in '0'..'9' }` → `Invalid(INVALID_CHARACTERS)`. (Nie `Char.isDigit()` – akceptuje cyfry spoza ASCII.)
3. `s = input.trimStart('0')`; `s.isEmpty()` → `of(0)` → `Invalid(BELOW_MIN)`.
4. `s.length > 2` → `Invalid(ABOVE_MAX)` (bez `toInt()`, więc bez przepełnienia).
5. Inaczej `of(s.toInt())` – co najwyżej 2 cyfry, `toInt()` nie rzuca.

Algorytm `Command.Carbs.of(grams)` (CMD-03): `grams < MIN_GRAMS` → `Invalid(BELOW_MIN)`; `grams > MAX_GRAMS` → `Invalid(ABOVE_MAX)`; inaczej `Valid(Carbs(grams))`.

`requiresExtraConfirmation` dla `Carbs`: `grams > EXTRA_CONFIRMATION_ABOVE_GRAMS`.

`smsText()`: `Bg` → `definition.keyword`; `Pump` → `definition.keyword`; `Carbs` → `"${definition.keyword} $grams"` (`Int.toString()` daje cyfry ASCII bez zer wiodących).

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| **Białe znaki na brzegach nie są obcinane** – ` 20`, `20 `, `20\n` → „niedozwolone znaki” | `trim()` przed walidacją | Bezpieczniej: walidator przyjmuje tylko to, co jednoznacznie jest liczbą, i niczego nie poprawia po cichu. `trim()` w Kotlinie obcina znaki wg `Char.isWhitespace()` (m.in. U+00A0, U+2007, U+3000), czyli zakres zależny od Unicode, którego nie chcemy rozstrzygać w logice medycznej. Spacja lub koniec linii w polu ilości może pochodzić z wklejonego tekstu; wtedy lepiej, żeby użytkownik zobaczył błąd, niż żeby aplikacja zgadywała, co miał na myśli. Pole w `ui` z klawiaturą numeryczną i tak nie wprowadzi spacji przy wpisywaniu z klawiatury, więc reguła nie utrudnia wpisania poprawnej ilości. |
| Zera wiodące dopuszczalne bez limitu liczby | limit długości tekstu; odrzucanie zer wiodących | Ustalenie z użytkownikiem: `020` → 20. Zera wiodące nie zmieniają wartości, a ocena po liczbie cyfr znaczących (krok 4) wyklucza przepełnienie bez osobnego limitu długości. Ograniczenie długości pola to sprawa `ui`. |
| `-5` → „niedozwolone znaki”, a nie „poniżej minimum” | parsowanie znaku liczby | Reguła „tylko cyfry ASCII” jest prostsza i nie wymaga obsługi `-0`, `+5`, `--5`. Rodzaj błędu i tak prowadzi do odrzucenia. |
| Wynik walidacji jako `sealed` typ, nie wyjątek | `require()` / `IllegalArgumentException` | Ustalenie z użytkownikiem; błędne dane od użytkownika to normalna ścieżka, a `ui` potrzebuje rodzaju błędu do komunikatu. |
| `Command.Carbs` z prywatnym konstruktorem | publiczny konstruktor z `require` (jak `ExpectedCommand.Carbs`) | Typ gwarantuje, że każde istniejące polecenie `CARBS` ma ilość 1–50; `session` i `sms` nie muszą walidować ponownie, a `toExpectedCommand()` nigdy nie trafi na `require(grams >= 1)` w parserze. |
| Treść SMS i kontekst parsera wyliczane z tego samego egzemplarza `Command` | `session` buduje treść i kontekst osobno | Ilość w SMS-ie i ilość oczekiwana przez parser nie mogą się rozjechać. |
| `commands` zależy od `parser` (odwzorowanie w `commands`) | odwzorowanie w `session`; wspólny typ w trzecim pakiecie | Spec 002 dopuszcza obie strony; w `commands` odwzorowanie jest blisko danych polecenia i nowy wariant polecenia wymusza (przez `when` wyczerpujący po `sealed`) uzupełnienie odwzorowania przy kompilacji. Parser zostaje bez zmian. |
| Osobne pola `requiresCode` i `approval` | jedno pole | Dziś pokrywają się, ale znaczą co innego: czy AAPS poprosi o kod (dla `session`) i jak użytkownik zatwierdza (dla `ui`). Spójność pilnuje CMD-02 (`requiresCode` ⇒ `SUMMARY_WITH_PIN`). |
| Katalog bez etykiet UI („Glikemia”, „Stan pompy”, „Węglowodany”) | nazwy po polsku w `CommandDefinition` | Teksty UI należą do zasobów Androida (`strings.xml`), a `commands` ma nie zależeć od Androida. `ui` mapuje `CommandId` na zasób. |
| Zakres, próg i szybkie wartości jako stałe w `CarbsLimits` | ustawienia użytkownika | Ustalenie z użytkownikiem dla MVP; zmiana wartości = zmiana spec. |
| `requiresExtraConfirmation` na `Command`, a nie tylko próg w danych | `ui` sam porównuje z progiem | Jedno miejsce reguły (> 40), testowane jednostkowo; `ui` tylko czyta flagę. |
| Redakcja ilości w `toString()` | pokazywanie ilości | Spójność z PARSER-10. Otwarte pytanie w spec – do potwierdzenia przez użytkownika. |

## Mapowanie wymagań na testy

Testy jednostkowe JUnit 5 w `app/src/test/java/pl/twojanazwa/aapsremote/commands/`, nazwy zawierają identyfikator wymagania.

| Wymaganie | Test (klasa / rodzaj) |
| --- | --- |
| CMD-01 | `CommandCatalogTest` – jednostkowy: liczba i kolejność wpisów, `definitionOf` dla każdego `CommandId.entries`, unikalność, słowa kluczowe `[A-Z]+`, `requiresCode`, parametry, `Command.definition` |
| CMD-02 | `CommandCatalogTest` – jednostkowy: `approval` wpisów, `requiresCode` ⇒ `SUMMARY_WITH_PIN` |
| CMD-03 | `CarbsAmountTest` – jednostkowy, parametryzowany: 1, 50, 0, −5, 51, `Int.MIN_VALUE`, `Int.MAX_VALUE`; równość |
| CMD-04 | `CarbsInputParseTest` – jednostkowy, parametryzowany tabelą z spec; 10 000 cyfr `9`; 10 000 zer + `20` |
| CMD-05 | `CarbsExtraConfirmationTest` – jednostkowy: 1, 40, 41, 50, `041`, `Bg`, `Pump` |
| CMD-06 | `CommandCatalogTest` – jednostkowy: szybkie wartości i ich walidacja |
| CMD-07 | `CommandSmsTextTest` – jednostkowy: `BG`, `PUMP`, `CARBS 1/20/50`, `007`; pętla 1–50 z wzorcem |
| CMD-08 | `CommandToExpectedTest` – jednostkowy: `Bg`, `Pump`, pętla 1–50 |
| CMD-09 | `CommandRedactionTest` – jednostkowy: `toString()` dla `CARBS 37`, `Valid`, `Invalid` z `47`, `73`, `12abc` |

Brak wywołań API logowania i brak importów `android.*` w `commands` (CMD-09, Przegląd) sprawdza recenzja kodu; test nie jest wymagany.

## Podział na zadania

Kolejność wynika z zależności. Każde zadanie to jeden PR, ok. 200–400 linii z testami.

1. **Polecenie i walidacja ilości CARBS** – `CommandId`, `CarbsLimits`, `Command` (`Bg`, `Pump`, `Carbs` z `of`/`parse`, `requiresExtraConfirmation`, redagowany `toString()`), `CarbsValidation`, `CarbsAmountError` + `CarbsAmountTest`, `CarbsInputParseTest`, `CarbsExtraConfirmationTest`, część `CommandRedactionTest`. Wymagania: CMD-03, CMD-04, CMD-05, CMD-09 (część).
2. **Katalog, treść SMS i kontekst parsera** – `Approval`, `CommandParameter`, `CommandDefinition`, `CommandCatalog`, `Command.definition`, `smsText()`, `toExpectedCommand()` + `CommandCatalogTest`, `CommandSmsTextTest`, `CommandToExpectedTest`, reszta `CommandRedactionTest`. Wymagania: CMD-01, CMD-02, CMD-06, CMD-07, CMD-08, CMD-09 (reszta). Zależy od zadania 1.
