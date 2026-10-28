# Design 011: Limity poleceń w ustawieniach

Dotyczy: [spec.md](spec.md)

## Przegląd

- `commands`: typ `CommandLimits` (dane + walidacja zakresów, LIM-01 – LIM-03), wyliczane progi i szybkie wartości `CARBS` (LIM-07, LIM-08); `Command.Carbs.of/parse` przyjmują limity (LIM-06). Czysty Kotlin.
- `data`: `CommandLimitsSettings` (singleton, `StateFlow<CommandLimitsState>`) według wzorca `AapsNumberSettings` (Spec 007); port `CommandLimitsStorage` i implementacja `data.platform.SharedPreferencesCommandLimitsStorage` (plik `command_limits`).
- `ui.settings`: `CommandLimitsViewModel` + `CommandLimitsSection` na ekranie Ustawienia (LIM-10, LIM-11).
- `ui.commands`: `CommandsViewModel` czyta stan limitów (LIM-05 – LIM-08), `CommandLauncher` sprawdza limit przed odczytem sekretu (LIM-09).

## Interfejsy

```kotlin
package pl.twojanazwa.aapsremote.commands

/** LIM-01, LIM-02. Tylko przez of(); toString() bez wartości (LIM-12). */
class CommandLimits private constructor(
    val carbsMaxGrams: Int,
    val bolusMaxCentiUnits: Int,
    val bolusMinIntervalMinutes: Int,
) {
    /** LIM-07: ⌊carbsMaxGrams × 4 / 5⌋. */
    val carbsExtraConfirmationAboveGrams: Int get() = carbsMaxGrams * 4 / 5

    /** LIM-08: CarbsLimits.QUICK_VALUES ≤ progu. */
    val carbsQuickValues: List<Int>

    /** BOL-04 (Spec 012): ilość > połowy limitu, czyli 2 × ilość > bolusMaxCentiUnits. */
    fun bolusRequiresExtraConfirmation(centiUnits: Int): Boolean

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
    override fun toString(): String = "CommandLimits(***)"

    companion object {
        val DEFAULT: CommandLimits          // 50, 500, 15
        fun of(carbsMaxGrams: Int, bolusMaxCentiUnits: Int, bolusMinIntervalMinutes: Int): LimitsValidation
    }
}

object LimitRanges {
    const val CARBS_MAX_MIN = 10;  const val CARBS_MAX_MAX = 100
    const val BOLUS_MAX_MIN = 5;   const val BOLUS_MAX_MAX = 1000; const val BOLUS_STEP = 5
    const val INTERVAL_MIN = 0;    const val INTERVAL_MAX = 60
}

enum class LimitField { CARBS_MAX, BOLUS_MAX, BOLUS_INTERVAL }
enum class LimitValueError { EMPTY, INVALID_CHARACTERS, TOO_MANY_DECIMALS, BELOW_MIN, ABOVE_MAX, NOT_STEP }

sealed interface LimitsValidation {
    data class Valid(val limits: CommandLimits) : LimitsValidation
    /** Błąd dla każdego błędnego pola; bez tekstu wejściowego. */
    data class Invalid(val errors: Map<LimitField, LimitValueError>) : LimitsValidation
}

/** LIM-03: tekst pól → LimitsValidation. Nigdy nie rzuca. Bolus – reguły BolusAmountText (Spec 012). */
object LimitsInput {
    fun parse(carbsMax: String, bolusMax: String, interval: String): LimitsValidation
}

// Zmiana Spec 003 (LIM-06, LIM-07):
//   Command.Carbs.of(grams: Int, limits: CommandLimits): CarbsValidation
//   Command.Carbs.parse(input: String, limits: CommandLimits): CarbsValidation
//   Command.requiresExtraConfirmation(limits: CommandLimits): Boolean   // zamiast właściwości
// CarbsLimits.MAX_GRAMS i EXTRA_CONFIRMATION_ABOVE_GRAMS są usuwane; MIN_GRAMS i QUICK_VALUES zostają.
// CommandParameter.Grams traci pola max/extraConfirmationAboveGrams (wartości są w CommandLimits).
```

```kotlin
package pl.twojanazwa.aapsremote.data

sealed interface CommandLimitsState {
    data object Loading : CommandLimitsState
    data class Loaded(val limits: CommandLimits) : CommandLimitsState
    data object LoadFailed : CommandLimitsState
}

enum class SaveLimitsResult { Saved, Failed }

interface CommandLimitsStore {
    val state: StateFlow<CommandLimitsState>
    suspend fun save(limits: CommandLimits): SaveLimitsResult
}

/** Blokujący; tylko na @DataDispatcher, pod blokadą. Może rzucać. */
interface CommandLimitsStorage {
    /** null = brak klucza. ClassCastException przy innym typie. */
    fun readInt(key: String): Int?
    /** Wszystkie trzy wartości jednym edit()…commit(). */
    fun writeAll(carbsMaxGrams: Int, bolusMaxCentiUnits: Int, bolusMinIntervalMinutes: Int): Boolean
}
```

```kotlin
package pl.twojanazwa.aapsremote.ui.commands
// LaunchRejection: nowe wartości LIMITS_UNAVAILABLE, AMOUNT_ABOVE_LIMIT, EXTRA_CONFIRMATION_REQUIRED (LIM-09).
// CommandLauncher.launch(command, pin, extraConfirmed: Boolean = false) – ViewModel przekazuje true tylko po „Tak” w oknie „Na pewno”.
// CommandLauncher: nowa zależność CommandLimitsStore.
```

## Model danych i stany

Wczytanie (`CommandLimitsSettings.load`): dla każdego klucza `readInt`; `null` → wartość domyślna; potem `CommandLimits.of(...)`: `Valid` → `Loaded`, `Invalid` → `LoadFailed`; wyjątek → `LoadFailed`. Wczytanie nie nadpisuje stanu, gdy wcześniej zakończył się zapis (jak DATA-02).

`CommandLimitsViewModel` (sekcja):

| Stan | Zdarzenie | Nowy stan | Akcja | Wymaganie |
| --- | --- | --- | --- | --- |
| `Loading` | stan → `Loaded(l)` | pola = wartości `l`, zapis dostępny | – | LIM-10 |
| `Loading` | stan → `LoadFailed` | pola = domyślne, tekst błędu wczytania | – | LIM-10 |
| dowolny, bez `busy` | „Zapisz limity”, `activeSession != null` | bez zmian | komunikat `msg_command_active` | LIM-11 |
| dowolny, bez `busy` | „Zapisz limity”, `Invalid` | błędy pól | – | LIM-10 |
| dowolny, bez `busy` | „Zapisz limity”, `Valid` | `busy` | `store.save` | LIM-10, LIM-11 |
| `busy` | wynik zapisu | bez `busy`, komunikat | – | LIM-10 |
| `busy` | „Zapisz limity” | bez zmian | – | LIM-11 |

Pola nie są nadpisywane wartościami ze stanu po pierwszym wypełnieniu (edycja użytkownika nie ginie przy kolejnej emisji); po `Saved` pola = zapisane wartości.

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| Osobny plik `command_limits` | klucze w pliku `settings` | DATA-01 („tylko klucz `aaps_number`”) zostaje bez zmian; domena `sharedpref` i tak jest wykluczona z kopii. |
| Bolus w setnych jednostki (`Int`) | `Double`, `BigDecimal` | Brak błędów zaokrągleń, proste porównania z AAPS (2 miejsca po przecinku). |
| Zapis trzech wartości jednym `commit()` | zapis pola osobno | Brak stanu pośredniego (np. nowy limit bolusa ze starym odstępem). |
| `LoadFailed` blokuje `CARBS`/`BOLUS` | użycie wartości domyślnych | Domyślne mogą być wyższe niż ustawione przez użytkownika. |
| Próg `CARBS` = 80% limitu | połowa limitu, stała 40 | Bez zmiany zachowania przy limicie domyślnym (P1). |
| Szybkie wartości ≤ progu | wszystkie ≤ limitu | Szybka wartość ma od razu otwierać podsumowanie (CMDUI-08) – bez okna „Na pewno”. |
| Sprawdzenie w `CommandLauncher` przed odczytem sekretu | tylko przy „Dalej” | Limit mógł się zmienić między „Dalej” a „Wyślij” (P3); odczyt sekretu jest dopiero po wszystkich sprawdzeniach (CMDUI-12). |
| Sekcja bez `FLAG_SECURE` | z `FLAG_SECURE` | Limity to konfiguracja, nie dane z AAPS ani PIN; spójnie z resztą Ustawień (UI-23 chroni tylko sekret). |

## Mapowanie wymagań na testy

| Wymaganie | Test (klasa / rodzaj) |
| --- | --- |
| LIM-01, LIM-02 | `CommandLimitsTest` – jednostkowy |
| LIM-03 | `LimitsInputTest` – jednostkowy, tabela z kryteriów |
| LIM-04 | `CommandLimitsSettingsTest` – jednostkowy (atrapa `CommandLimitsStorage`, Turbine); W1 |
| LIM-05 | `CommandsViewModelLimitsTest` – jednostkowy |
| LIM-06, LIM-07, LIM-08 | `CarbsLimitsTest` (commands, w tym wszystkie kryteria CMD-03 – CMD-06 z limitami domyślnymi), `CommandsViewModelLimitsTest` |
| LIM-09 | `CommandLauncherLimitsTest` – jednostkowy |
| LIM-10, LIM-11 | `CommandLimitsViewModelTest` – jednostkowy; W2 |
| LIM-12 | `LimitsRedactionTest`, rozszerzenie `UiNoLoggingTest` i testów braku logowania `commands`/`data` |

## Podział na zadania

Każde zadanie to jeden PR, ok. 200–400 linii z testami.

1. **Limity w `commands` i `CARBS` z limitami** – `commands/CommandLimits.kt`, `LimitRanges.kt`, `LimitsInput.kt`, `BolusAmountText.kt` (reguły tekstu ilości insuliny z BOL-03, wspólne z polem bolusa w Spec 012), zmiany `Command.kt`, `CarbsLimits.kt`, `CommandParameter.kt`, `CommandCatalog.kt`; dostosowanie wywołań w `ui/commands/CommandsViewModel.kt` i `CommandsUiState.kt` do `CommandLimits.DEFAULT` (bez zmiany zachowania); testy `CommandLimitsTest`, `LimitsInputTest`, `BolusAmountTextTest` (tabela BOL-03), `CarbsLimitsTest`, aktualizacja testów Spec 003. Wymagania: LIM-01 – LIM-03, LIM-06 – LIM-08 (logika), BOL-03 (reguły tekstu). Etykiety `bezpieczenstwo`, `trudnosc:trudne`.
2. **Zapis limitów** – `data/CommandLimitsState.kt`, `CommandLimitsStore.kt`, `CommandLimitsStorage.kt`, `CommandLimitsSettings.kt`, `data/platform/SharedPreferencesCommandLimitsStorage.kt`, wiązanie w `data/platform/DataModule.kt`; testy `CommandLimitsSettingsTest`. Wymagania: LIM-04, LIM-12 (część). Zależy od 1. Etykiety `bezpieczenstwo`, `trudnosc:latwe`.
3. **Sekcja „Limity poleceń”** – `ui/settings/limits/CommandLimitsViewModel.kt`, `CommandLimitsUiState.kt`, `CommandLimitsSection.kt`, `LimitsTexts.kt`, wpięcie w `SettingsScreen.kt`, blok `<!-- 011: limity -->` w `strings.xml`; testy `CommandLimitsViewModelTest`, `LimitsTextsTest`. Wymagania: LIM-10, LIM-11, LIM-12 (część). PR opisuje W1, W2. Zależy od 2 i 4 – sekcja pozwala zmienić limit dopiero wtedy, gdy panel i launcher go stosują (inaczej obniżony limit byłby ignorowany). Etykiety `bezpieczenstwo` (jedyna droga zmiany limitów dawek), `trudnosc:latwe`.
4. **Limity w panelu i przy starcie** – `ui/commands/CommandsViewModel.kt`, `CommandsUiState.kt`, `Readiness.kt` (baner limitów), `CommandsPanel.kt`, `CommandLauncher.kt`, `LaunchResult.kt`, `CommandMessage.kt`, `CommandTexts.kt`, teksty w `strings.xml`; testy `CommandsViewModelLimitsTest`, `CommandLauncherLimitsTest`. Wymagania: LIM-05 – LIM-09 (UI). PR opisuje W3. Zależy od 2. Etykiety `bezpieczenstwo`, `trudnosc:trudne`.

Zależności: 1 → 2 → 4 → 3. Do scalenia zadania 4 panel używa `CommandLimits.DEFAULT` (zachowanie jak dziś), a sekcji zmiany limitów jeszcze nie ma. Spec 012 i 013 zależą od zadania 1 (typ `CommandLimits`, `BolusAmountText`).
