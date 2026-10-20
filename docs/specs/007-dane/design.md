# Design 007: Dane – ustawienia i historia poleceń

Dotyczy: [spec.md](spec.md)

## Przegląd

Pakiet `pl.twojanazwa.aapsremote.data` dostaje dwie niezależne części: ustawienia (numer AAPS) i historię poleceń. Podział jak w Spec 005 i Spec 006, zgodnie z [AGENTS.md](../../../AGENTS.md#architektura):

- **`data` – logika czysta** (Kotlin/JVM, `kotlinx.coroutines`; bez importów `android.*` i `androidx.*`; dozwolone adnotacje `javax.inject`): stan i operacje numeru AAPS nad portem `SettingsStorage`, implementacja `sms.AapsNumberProvider`, model wpisu historii, kodek `SessionResult` → wiersz → wpis, repozytorium historii z retencją nad portem `HistoryStorage`, rejestrator wyników sesji. Testy JUnit 5 w JVM z atrapami portów.
- **`data.platform` – cienka warstwa Androida:** `SharedPreferences` (port `SettingsStorage`), encja, DAO i baza Room (port `HistoryStorage`), moduł Hilt. Bez decyzji – walidacja, stany, retencja (liczba do usunięcia), kolejność i kodowanie są w logice czystej. Sprawdzana testem wyeksportowanego schematu (JVM) i ręcznie (W1–W7), bez Robolectric i `androidTest`.

Zależności (kierunek): `data` → `sms` (`AapsNumber`, `AapsNumberProvider`), `data` → `session` (`SessionResult`, `SessionOutcome`, `SessionState`, enumy), `data` → `commands` (`Command`, `CommandId`). `sms`, `session` i `commands` nie importują `data`. `ui` (przyszła spec): `AapsNumberSettingsStore` (ekran ustawień, blokada przycisków w `Loading`), `CommandHistory` (lista, czyszczenie), `SessionHistoryRecorder.track` (po `StartResult.Started`).

Przepływ numeru: start procesu → pierwsze wstrzyknięcie `AapsNumberSettings` (np. przez `IncomingSmsHub`, `AapsSmsSender` albo ekran `ui`) → zadanie wczytania w `@DataScope` → `SettingsStorage.readAapsNumber()` na `@DataDispatcher` → `StateFlow<AapsNumberState>`; `sms` czyta `currentNumber()` = `state.value`.

Przepływ historii: `ui` → `SessionCoordinator.start` → `Started(session)` → `SessionHistoryRecorder.track(session.state)` → (w `@DataScope`) `state.first { FINISHED }` → `CommandHistory.record(result)` → `HistoryCodec.toRow` → `HistoryStorage.inTransaction { insert; count; deleteOldest }`. `ui` → `CommandHistory.entries` → `HistoryStorage.observeNewestFirst()` → `HistoryCodec.toEntry`.

## Interfejsy

```kotlin
package pl.twojanazwa.aapsremote.data

// ---- Ustawienia: publiczne API dla ui ----

/** DATA-02. */
sealed interface AapsNumberState {
    data object Loading : AapsNumberState
    data object NotConfigured : AapsNumberState
    class Configured(val number: AapsNumber) : AapsNumberState {
        override fun toString(): String = "Configured(***)"
    }
    data object LoadFailed : AapsNumberState
}

/** DATA-03. */
sealed interface SaveNumberResult {
    data object Saved : SaveNumberResult
    data object Invalid : SaveNumberResult
    data object Failed : SaveNumberResult
}

/** DATA-04. */
sealed interface ClearNumberResult {
    data object Cleared : ClearNumberResult
    data object Failed : ClearNumberResult
}

interface AapsNumberSettingsStore {
    val state: StateFlow<AapsNumberState>

    /** DATA-03. Nie rzuca poza CancellationException (DATA-15). */
    suspend fun save(raw: String): SaveNumberResult

    /** DATA-04. Nie rzuca poza CancellationException (DATA-15). */
    suspend fun clear(): ClearNumberResult
}

// ---- Ustawienia: port platformy ----

/** Blokujący; wołany wyłącznie na @DataDispatcher, pod blokadą (DATA-06). Może rzucać. */
interface SettingsStorage {
    fun readAapsNumber(): String?
    /** Wynik commit(). */
    fun writeAapsNumber(value: String): Boolean
    /** Wynik commit(). */
    fun removeAapsNumber(): Boolean
}

/** Dyspozytor I/O ustawień; w aplikacji Dispatchers.IO (DATA-06). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DataDispatcher

/** Zakres korutyn aplikacji (SupervisorJob + Dispatchers.IO): wczytanie ustawień, rejestrator. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DataScope

@Singleton
class AapsNumberSettings @Inject constructor(
    private val storage: SettingsStorage,
    @DataDispatcher private val dispatcher: CoroutineDispatcher,
    @DataScope scope: CoroutineScope,
) : AapsNumberSettingsStore, AapsNumberProvider {
    /** DATA-05: tylko odczyt state.value; bez I/O, bez blokady. */
    override fun currentNumber(): String?
    override fun toString(): String = "AapsNumberSettings(***)"
}

// ---- Historia: model ----

enum class HistoryCommand { BG, PUMP, CARBS, UNRECOGNIZED }

sealed interface HistoryOutcome {
    data object Completed : HistoryOutcome
    data object CarbsConfirmed : HistoryOutcome
    /** reason == null: nieznany albo brakujący powód w wierszu (DATA-09). */
    data class Failed(val reason: FailureReason?) : HistoryOutcome
    data class TimedOut(val phase: SessionPhase?) : HistoryOutcome
    data class Unknown(val reason: UnknownReason?) : HistoryOutcome
    data object Cancelled : HistoryOutcome
    /** Nieznany tekst rodzaju wyniku (DATA-09). */
    data object Unrecognized : HistoryOutcome
}

/** DATA-07. toString() stały (DATA-17). */
data class HistoryEntry(
    val id: Long,
    val command: HistoryCommand,
    val carbsGrams: Int?,
    val outcome: HistoryOutcome,
    val startedAtEpochMillis: Long?,
    val commandSentAtEpochMillis: Long?,
    val codeSentAtEpochMillis: Long?,
    val finishedAtEpochMillis: Long?,
    val replyText: String?,
    val unexpectedMessageCount: Int,
) {
    override fun toString(): String = "HistoryEntry(***)"
}

/** Postać zapisana (teksty z tabeli kodów). id = 0 przed zapisem. toString() stały (DATA-17). */
data class HistoryRow(
    val id: Long,
    val command: String,
    val carbsGrams: Int?,
    val outcome: String,
    val reason: String?,
    val startedAtEpochMillis: Long?,
    val commandSentAtEpochMillis: Long?,
    val codeSentAtEpochMillis: Long?,
    val finishedAtEpochMillis: Long?,
    val replyText: String?,
    val unexpectedMessageCount: Int,
) {
    override fun toString(): String = "HistoryRow(***)"
}

/** DATA-07, DATA-09. Czysta funkcja, nigdy nie rzuca. */
object HistoryCodec {
    fun toRow(result: SessionResult): HistoryRow
    fun toEntry(row: HistoryRow): HistoryEntry
}

object HistoryLimits {
    const val MAX_ENTRIES: Int = 200
}

// ---- Historia: publiczne API dla ui ----

/** DATA-11. */
sealed interface HistorySnapshot {
    data class Entries(val entries: List<HistoryEntry>) : HistorySnapshot {
        override fun toString(): String = "Entries(size=${entries.size})"
    }
    data object Unavailable : HistorySnapshot
}

sealed interface RecordResult {
    data object Recorded : RecordResult
    data object Failed : RecordResult
}

sealed interface ClearHistoryResult {
    data object Cleared : ClearHistoryResult
    data object Failed : ClearHistoryResult
}

/** DATA-10 – DATA-12. Dokładnie te trzy składowe (DATA-12). */
interface CommandHistory {
    val entries: Flow<HistorySnapshot>
    suspend fun record(result: SessionResult): RecordResult
    suspend fun clearAll(): ClearHistoryResult
}

// ---- Historia: port platformy ----

/** Funkcje suspend są bezpieczne dla wątku głównego (Room). Mogą rzucać. */
interface HistoryStorage {
    /** Jedna transakcja bazy; wyjątek z block = wycofanie i ten sam wyjątek na zewnątrz. */
    suspend fun <R> inTransaction(block: suspend () -> R): R
    /** Zwraca nadany identyfikator. */
    suspend fun insert(row: HistoryRow): Long
    suspend fun count(): Int
    /** Usuwa [count] wierszy o najmniejszych identyfikatorach. */
    suspend fun deleteOldest(count: Int)
    /** Wszystkie wiersze, malejący identyfikator; nowa lista po każdej zmianie tabeli. */
    fun observeNewestFirst(): Flow<List<HistoryRow>>
    suspend fun deleteAll()
}

@Singleton
class HistoryRepository @Inject constructor(
    private val storage: HistoryStorage,
) : CommandHistory

// ---- Rejestrator ----

/** DATA-13. */
@Singleton
class SessionHistoryRecorder @Inject constructor(
    private val history: CommandHistory,
    @DataScope private val scope: CoroutineScope,
) {
    /** Nieblokujące, bezpieczne z dowolnego wątku. Ten sam obiekt stanu: najwyżej jeden wpis. */
    fun track(state: StateFlow<SessionState>)
}
```

```kotlin
package pl.twojanazwa.aapsremote.data.platform

class SharedPreferencesSettingsStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsStorage {
    companion object {
        const val FILE_NAME = "settings"
        const val KEY_AAPS_NUMBER = "aaps_number"
    }
}

@Entity(tableName = "command_history")
data class HistoryEntity(/* kolumny z tabeli „Tabela command_history” */)

@Dao
interface HistoryDao {
    @Insert suspend fun insert(entity: HistoryEntity): Long
    @Query("SELECT COUNT(*) FROM command_history") suspend fun count(): Int
    @Query("DELETE FROM command_history WHERE id IN (SELECT id FROM command_history ORDER BY id ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
    @Query("SELECT * FROM command_history ORDER BY id DESC") fun observeNewestFirst(): Flow<List<HistoryEntity>>
    @Query("DELETE FROM command_history") suspend fun deleteAll()
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = true)
abstract class AapsRemoteDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    companion object { const val FILE_NAME = "aaps_remote.db" }
}

class RoomHistoryStorage @Inject constructor(
    private val database: AapsRemoteDatabase,
    private val dao: HistoryDao,
) : HistoryStorage

@Module @InstallIn(SingletonComponent::class)
abstract class DataModule { /* wiązania – sekcja „Warstwa Androida” */ }
```

## Model danych i stany

### Stan numeru (DATA-02 – DATA-06)

Pola `AapsNumberSettings`: `storage`, `dispatcher`, `mutex: Mutex`, `_state: MutableStateFlow<AapsNumberState>(Loading)`. Konstruktor: `scope.launch { load() }` (bez I/O na wątku wywołującym).

| Stan | Zdarzenie | Nowy stan | Akcja | Wymaganie |
| --- | --- | --- | --- | --- |
| `Loading` | wczytanie: brak klucza albo `parse` = `null` | `NotConfigured` | – | DATA-02 |
| `Loading` | wczytanie: `parse` ≠ `null` | `Configured(n)` | – | DATA-02 |
| `Loading` | wczytanie: wyjątek | `LoadFailed` | – | DATA-02 |
| inny niż `Loading` | wczytanie | bez zmian | magazyn nie jest czytany | DATA-02 |
| dowolny | `save(raw)`, `parse(raw)` = `null` | bez zmian | `Invalid`, bez blokady i magazynu | DATA-03 |
| dowolny | `save(raw)`, `commit()` = `true` | `Configured(n)` | `Saved` | DATA-03 |
| dowolny | `save(raw)`, `commit()` = `false` / wyjątek | bez zmian | `Failed` | DATA-03 |
| dowolny | `clear()`, `commit()` = `true` | `NotConfigured` | `Cleared` | DATA-04 |
| dowolny | `clear()`, `commit()` = `false` / wyjątek | bez zmian | `Failed` | DATA-04 |

Szkic:

```kotlin
private suspend fun load() = mutex.withLock {
    if (_state.value != AapsNumberState.Loading) return@withLock
    _state.value = try {
        withContext(dispatcher) {
            AapsNumber.parse(storage.readAapsNumber())?.let { AapsNumberState.Configured(it) }
                ?: AapsNumberState.NotConfigured
        }
    } catch (e: CancellationException) { throw e } catch (e: Exception) { AapsNumberState.LoadFailed }
}

override suspend fun save(raw: String): SaveNumberResult {
    val number = AapsNumber.parse(raw) ?: return SaveNumberResult.Invalid
    return mutex.withLock {
        try {
            withContext(dispatcher) {
                if (storage.writeAapsNumber(number.destination)) {
                    _state.value = AapsNumberState.Configured(number)   // przed powrotem z withContext (DATA-03, anulowanie)
                    SaveNumberResult.Saved
                } else SaveNumberResult.Failed
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { SaveNumberResult.Failed }
    }
}

override fun currentNumber(): String? = (_state.value as? AapsNumberState.Configured)?.number?.destination
```

`clear()` analogicznie (`removeAapsNumber()`, `NotConfigured` ustawiany wewnątrz `withContext`). Stan jest zmieniany wewnątrz bloku na dyspozytorze, zaraz po udanym `commit()`, więc anulowanie po zapisie (wyjątek przy powrocie z `withContext`) nie rozjeżdża stanu z plikiem. `AapsNumber` nie ma `equals`, więc ponowny zapis tego samego numeru emituje nowy obiekt `Configured` – bez znaczenia dla `currentNumber()`.

`parse(destination)` zwraca ten sam `destination` (postać znormalizowana składa się wyłącznie z opcjonalnego `+` i cyfr), więc `sms` przy każdym `AapsNumber.parse(currentNumber())` dostaje ten sam numer.

### Tabela `command_history` (DATA-14)

| Kolumna | Typ SQLite | `NOT NULL` | Pole `HistoryRow` | Uwagi |
| --- | --- | --- | --- | --- |
| `id` | `INTEGER` | tak | `id` | `PRIMARY KEY AUTOINCREMENT` (`@PrimaryKey(autoGenerate = true)`, `0` przy wstawianiu) |
| `command` | `TEXT` | tak | `command` | kod polecenia |
| `carbs_grams` | `INTEGER` | nie | `carbsGrams` | tylko `CARBS` |
| `outcome` | `TEXT` | tak | `outcome` | kod rodzaju wyniku |
| `reason` | `TEXT` | nie | `reason` | kod powodu / fazy |
| `started_at` | `INTEGER` | nie | `startedAtEpochMillis` | |
| `command_sent_at` | `INTEGER` | nie | `commandSentAtEpochMillis` | |
| `code_sent_at` | `INTEGER` | nie | `codeSentAtEpochMillis` | |
| `finished_at` | `INTEGER` | nie | `finishedAtEpochMillis` | |
| `reply_text` | `TEXT` | nie | `replyText` | pełna treść odpowiedzi AAPS |
| `unexpected_count` | `INTEGER` | tak | `unexpectedMessageCount` | |

Bez indeksów (najwyżej ~200 wierszy; `id` jest kluczem). `HistoryEntity` ma te same pola co `HistoryRow` (nazwy Kotlin jak w `HistoryRow`, nazwy kolumn przez `@ColumnInfo(name = …)`), `toString()` = `"HistoryEntity(***)"`; `RoomHistoryStorage` przepisuje pola 1:1 w obie strony (`HistoryEntity.fromRow`, `toRow`).

Pola wpisu i wiersza dla testu refleksją (DATA-08): `HistoryEntry` – `id`, `command`, `carbsGrams`, `outcome`, `startedAtEpochMillis`, `commandSentAtEpochMillis`, `codeSentAtEpochMillis`, `finishedAtEpochMillis`, `replyText`, `unexpectedMessageCount`; `HistoryRow` – te same i `reason`.

### Kody tekstowe (DATA-09)

Kodowanie przez jawne `when` (wyczerpujące – nowa wartość wyliczeniowa nie skompiluje się bez kodu), dekodowanie przez stałą mapę `tekst → wartość` zbudowaną z tych samych literałów. Bez `Enum.name`, `valueOf` i `ordinal`: zmiana nazwy stałej w kodzie nie zmienia danych.

| Grupa | Wartość | Tekst |
| --- | --- | --- |
| polecenie (`CommandId`) | `BG`, `PUMP`, `CARBS` | `BG`, `PUMP`, `CARBS` |
| rodzaj wyniku (`SessionOutcome`) | `Completed`, `CarbsConfirmed`, `Failed`, `TimedOut`, `Unknown`, `Cancelled` | `COMPLETED`, `CARBS_CONFIRMED`, `FAILED`, `TIMED_OUT`, `UNKNOWN`, `CANCELLED` |
| powód `FAILED` (`FailureReason`) | każda z 8 wartości | nazwa stałej: `SEND_FAILED`, `UNRECOGNIZED_REPLY`, `AMOUNT_MISMATCH`, `UNEXPECTED_REPLY`, `WRONG_CODE`, `CODE_REQUEST_EXPIRED`, `CODE_PREPARATION_TIMEOUT`, `INTERNAL_ERROR` |
| powód `UNKNOWN` (`UnknownReason`) | każda z 7 wartości | `CONFIRMATION_TIMEOUT`, `CANCELLED_AFTER_CODE`, `CODE_SEND_FAILED`, `UNRECOGNIZED_REPLY`, `AMOUNT_MISMATCH`, `UNEXPECTED_REPLY`, `INTERNAL_ERROR` |
| faza `TIMED_OUT` (`SessionPhase`) | każda z 8 wartości | `NOT_STARTED`, `SENDING_COMMAND`, `AWAITING_REPLY`, `AWAITING_CODE_REQUEST`, `PREPARING_CODE`, `SENDING_CODE`, `AWAITING_CONFIRMATION`, `FINISHED` |

Powód jest dekodowany mapą właściwą dla rodzaju (np. `UNRECOGNIZED_REPLY` przy `FAILED` → `FailureReason`, przy `UNKNOWN` → `UnknownReason`).

`toRow(result)`:

| `outcome` | `outcome` (tekst) | `reason` | `replyText` |
| --- | --- | --- | --- |
| `Completed(reply, text)` | `COMPLETED` | `null` | `text` |
| `CarbsConfirmed(grams, text)` | `CARBS_CONFIRMED` | `null` | `text` |
| `Failed(reason, text)` | `FAILED` | kod `reason` | `text` (może być `null`) |
| `TimedOut(phase)` | `TIMED_OUT` | kod `phase` | `null` |
| `Unknown(reason, text)` | `UNKNOWN` | kod `reason` | `text` (może być `null`) |
| `Cancelled` | `CANCELLED` | `null` | `null` |

Pozostałe: `id = 0`, `command` = kod `result.command.id`, `carbsGrams = (result.command as? Command.Carbs)?.grams`, czasy 1:1, `unexpectedMessageCount = result.unexpectedMessages.size`. `Completed.reply` i treści `unexpectedMessages` nie są czytane.

`toEntry(row)` – reguły z tabeli DATA-09; `carbsGrams` tylko przy `command == CARBS`; funkcja nie potrzebuje `try`/`catch`, bo nie wykonuje operacji rzucających (odczyt map, `when`).

### Repozytorium historii (DATA-10 – DATA-12, DATA-15)

```kotlin
override val entries: Flow<HistorySnapshot> =
    flow { emitAll(storage.observeNewestFirst()) } // błąd przy tworzeniu strumienia też trafia do catch
        .map<List<HistoryRow>, HistorySnapshot> { rows -> HistorySnapshot.Entries(rows.map(HistoryCodec::toEntry)) }
        .catch { e -> if (e is CancellationException) throw e; emit(HistorySnapshot.Unavailable) }

override suspend fun record(result: SessionResult): RecordResult = try {
    val row = HistoryCodec.toRow(result)
    storage.inTransaction {
        storage.insert(row)
        val excess = storage.count() - HistoryLimits.MAX_ENTRIES
        if (excess > 0) storage.deleteOldest(excess)
    }
    RecordResult.Recorded
} catch (e: CancellationException) { throw e } catch (e: Exception) { RecordResult.Failed }

override suspend fun clearAll(): ClearHistoryResult = try {
    storage.deleteAll(); ClearHistoryResult.Cleared
} catch (e: CancellationException) { throw e } catch (e: Exception) { ClearHistoryResult.Failed }
```

Liczba do usunięcia jest liczona w logice czystej (test JVM), platforma wykonuje tylko `COUNT(*)` i `DELETE … ORDER BY id ASC LIMIT :count`. Kolejność wpisów = `id` (kolejność zapisu), nie czas: czasy mogą być `null` (SESSION-19) albo cofać się przy zmianie zegara, a koordynator (SESSION-22) wyklucza równoległe sesje, więc kolejność zapisu = kolejność zakończenia.

Atrapa `InMemoryHistoryStorage` (testy): lista wierszy, licznik `nextId` (nigdy nie maleje – jak `AUTOINCREMENT`), `inTransaction` z kopią listy przywracaną przy wyjątku i licznikiem głębokości (każde wywołanie metody notuje, czy było wewnątrz transakcji), `observeNewestFirst` z `MutableStateFlow`, przełączane wyjątki dla każdej metody i dla strumienia.

### Rejestrator (DATA-13)

```kotlin
private val lock = Any()
/** Klucze słabe, równość tożsamościowa (MutableStateFlow nie nadpisuje equals). */
private val tracked = WeakHashMap<StateFlow<SessionState>, Unit>()

fun track(state: StateFlow<SessionState>) {
    synchronized(lock) { if (tracked.put(state, Unit) != null) return }
    scope.launch {
        val result = state.first { it.phase == SessionPhase.FINISHED }.result ?: return@launch
        try { history.record(result) } catch (e: CancellationException) { throw e } catch (e: Exception) { }
    }
}
```

- `StateFlow` zawsze oddaje bieżącą wartość, a `FINISHED` jest stanem końcowym sesji (Spec 004), więc `first` widzi zakończenie także, gdy stan przekazano po nim; nie ma utraty przez łączenie wartości.
- `result != null` iff `FINISHED` (niezmiennik `SessionState`); `?: return` tylko dla bezpieczeństwa typów.
- Zadanie trzyma `state` do zapisu i kończy się; `WeakHashMap` nie przedłuża życia stanu (ani sesji, która go trzyma). Anulowany `scope` → `launch` nie startuje albo `first` rzuca `CancellationException` → brak zapisu.
- `scope` ma `SupervisorJob`, a wyjątki zapisu są łapane, więc jeden błąd nie zatrzymuje kolejnych zapisów.

### Warstwa Androida (`data.platform`)

- **`SharedPreferencesSettingsStorage`:** `context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)` przy każdym wywołaniu (system trzyma instancję w pamięci podręcznej; pierwsze wywołanie czyta plik – dlatego dyspozytor I/O); `readAapsNumber()` = `getString(KEY_AAPS_NUMBER, null)` (wartość innego typu → `ClassCastException` → `LoadFailed`); `writeAapsNumber(v)` = `edit().putString(KEY_AAPS_NUMBER, v).commit()`; `removeAapsNumber()` = `edit().remove(KEY_AAPS_NUMBER).commit()`. Wynik `commit()` jest używany, więc lint `ApplySharedPref` nie ostrzega.
- **`RoomHistoryStorage`:** `inTransaction` = `database.withTransaction { block() }` (`room-ktx`; jeśli w wybranej wersji Room ta funkcja jest przestarzała – jej odpowiednik z tej wersji o tej samej semantyce: jedna transakcja zapisu, wycofanie przy wyjątku); pozostałe metody delegują do `HistoryDao` z przepisaniem `HistoryEntity` ↔ `HistoryRow`. Wywołania DAO wewnątrz `withTransaction` należą do tej samej transakcji.
- **Baza:** `Room.databaseBuilder(context, AapsRemoteDatabase::class.java, AapsRemoteDatabase.FILE_NAME).build()` – domyślny katalog `databases/`, bez `fallbackToDestructiveMigration*`, bez `allowMainThreadQueries`. Budowa nie otwiera pliku (Room otwiera bazę przy pierwszym zapytaniu na własnym wykonawcy), więc dostawca Hilt może działać na wątku głównym.
- **`DataModule`:**

  ```kotlin
  @Binds abstract fun aapsNumberProvider(impl: AapsNumberSettings): AapsNumberProvider
  @Binds abstract fun aapsNumberSettingsStore(impl: AapsNumberSettings): AapsNumberSettingsStore
  @Binds abstract fun settingsStorage(impl: SharedPreferencesSettingsStorage): SettingsStorage
  @Binds abstract fun commandHistory(impl: HistoryRepository): CommandHistory
  @Binds abstract fun historyStorage(impl: RoomHistoryStorage): HistoryStorage
  companion object {
      @Provides @DataDispatcher fun dispatcher(): CoroutineDispatcher = Dispatchers.IO
      @Provides @Singleton @DataScope fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
      @Provides @Singleton fun database(@ApplicationContext context: Context): AapsRemoteDatabase = /* wyżej */
      @Provides fun historyDao(db: AapsRemoteDatabase): HistoryDao = db.historyDao()
  }
  ```

  `AapsNumberSettings` jest `@Singleton`, więc `AapsNumberProvider` i `AapsNumberSettingsStore` to jedna instancja (DATA-05). KDoc `SmsModule` („`AapsNumberProvider` celowo bez wiązania (spec `data`)”) dostaje odsyłacz do `DataModule`; wiązania w `SmsModule` bez zmian.
- **Manifest i `data_extraction_rules.xml`:** bez zmian (DATA-16).

### Zależności Gradle

`gradle/libs.versions.toml`:

```toml
[versions]
room = "<najnowsza stabilna 2.x zgodna z Kotlin 2.4.21 i KSP 2.3.12 w chwili zadania 5>"

[libraries]
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
```

`app/build.gradle.kts`:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
```

Wtyczka KSP już jest (Hilt). Plik schematu powstaje przy `kspDebugKotlin` w `app/schemas/pl.twojanazwa.aapsremote.data.platform.AapsRemoteDatabase/1.json` i jest dodawany do repo w zadaniu 5. Numer wersji Room wpisuje wykonawca po sprawdzeniu zgodności (`assembleDebug`); spec go nie ustala.

Żadna klasa `data` ani `data.platform` nie wywołuje API logowania (DATA-17).

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| **Numer w osobnym pliku `SharedPreferences` (`settings`), `commit()`** (decyzja użytkownika co do `SharedPreferences`) | DataStore; wspólny plik z `totp_secret_store`; `apply()` | Jedna mała wartość; osobny plik oddziela ustawienia od szyfrogramu Spec 006 (wyczyszczenie sekretu nie dotyka numeru i odwrotnie). `commit()` zwraca wynik, więc `Failed` i „stan bez zmian” są prawdziwe; `apply()` zawsze „udaje się” w pamięci, a błąd zapisu na dysk ginie. Koszt blokującego `commit()` znika na dyspozytorze I/O. DataStore – dodatkowa zależność bez potrzeby (jak w Spec 006). |
| **Zapis postaci znormalizowanej (`AapsNumber.destination`)** | zapis tekstu wpisanego przez użytkownika | Jedna kanoniczna postać: `sms` dostaje ją przez `currentNumber()` i `parse` daje ten sam wynik; brak spacji, nawiasów i myślników, które mogłyby różnie zachowywać się w `SmsManager`. Utrata formatowania wpisanego przez użytkownika jest bez znaczenia – `ui` pokazuje postać, która faktycznie jest używana. |
| **Wczytanie przy utworzeniu singletonu, w `@DataScope`** | wczytanie w `AapsRemoteApp.onCreate`; leniwie przy pierwszym `currentNumber()`; `runBlocking` | Każdy użytkownik numeru (`sms`, `ui`) i tak tworzy singleton przez wstrzyknięcie, więc wczytanie rusza przed pierwszym użyciem bez zmian w klasie aplikacji. Leniwe wczytanie przy `currentNumber()` dałoby `null` przy pierwszym wysłaniu; `runBlocking` łamałoby DATA-05. Okno przed końcem wczytania zamyka `ui` (przyciski nieaktywne w `Loading`), a `null` w tym oknie jest bezpieczny. |
| **`currentNumber()` = odczyt `StateFlow.value`** | blokada; odczyt pliku | Odczyt `volatile` bez I/O i bez czekania, z dowolnego wątku (wymóg `AapsNumberProvider`). |
| **`LoadFailed` osobno od `NotConfigured`; niepoprawna wartość w pliku = `NotConfigured`** | jeden stan; kasowanie złej wartości | `ui` może pokazać „nie udało się odczytać ustawień” zamiast pierwszej konfiguracji. Niepoprawna wartość nie powstaje przez `save` (walidacja), więc jej obecność nie wymaga osobnego komunikatu; brak kasowania = brak zapisu przy odczycie. Bez ponownego wczytania – zapis i tak nadpisuje plik. |
| **`Mutex` + wstrzykiwany dyspozytor; własne kwalifikatory `@DataDispatcher`, `@DataScope`** | `synchronized`; wspólny kwalifikator dla `security` i `data` | Jak SECRET-13: `Mutex` nie blokuje wątku i działa z `suspend`; dyspozytor i zakres wstrzykiwane dla testów. Wspólny kwalifikator wymagałby zmiany Spec 006 – można go wprowadzić później. |
| **Wpis: zamknięta lista pól; z wiadomości nieoczekiwanych tylko liczba** (decyzja użytkownika co do zawartości) | treści lub nadawcy wiadomości nieoczekiwanych; zapis `Completed.reply` | Liczba wystarcza do ostrzeżenia; treść to cudze polecenia (potwierdzenie CARBS wysyłane do wszystkich numerów), a nadawca – numer telefonu. `reply` da się odtworzyć parserem z `replyText`. |
| **Treść odpowiedzi bez skracania** | limit długości | Treść jest pokazywana jako dowód wyniku („sprawdź w AAPS”) – ucięta mogłaby wprowadzać w błąd. Retencja 200 wpisów ogranicza rozmiar bazy. |
| **Kolejność po `id` (kolejność zapisu), `AUTOINCREMENT`** | sortowanie po `started_at`/`finished_at`; `id` bez `AUTOINCREMENT` | Czasy mogą być `null` albo cofać się (zegar ścienny). `AUTOINCREMENT` gwarantuje rosnące identyfikatory także po czyszczeniu i usunięciu najnowszego wiersza – kolejność i retencja „najmniejsze `id`” są zawsze poprawne. |
| **Retencja w logice czystej nad portem z transakcją** | metoda DAO `@Transaction`; wyzwalacz SQL | Liczenie nadmiaru i kolejność kroków są testowane w JVM; platforma ma trzy jednowierszowe zapytania. Wyzwalacz ukryłby regułę w schemacie i utrudnił zmianę limitu. |
| **Jawne kody tekstowe zamiast `Enum.name` / `ordinal`** | `name`/`valueOf`; `ordinal`; konwertery typów Room | Dane w bazie nie zależą od nazw w kodzie; `when` wymusza obsługę nowej wartości przy kompilacji. Kody są dziś równe nazwom – czytelne przy ręcznej weryfikacji `sqlite3`. |
| **Nieznany kod → `UNRECOGNIZED`/`Unrecognized`/powód `null`, bez wyjątku i bez pomijania wiersza** | wyjątek; pominięcie wiersza | Lista historii nie może się wywrócić przez jeden wiersz; wpis z nieznanym wynikiem jest widoczny (użytkownik może sprawdzić w AAPS). |
| **`HistorySnapshot.Unavailable` i koniec strumienia przy błędzie odczytu** | pusta lista; wyjątek do `ui` | Pusta lista sugerowałaby brak poleceń; wyjątek łamałby DATA-15. `ui` może pokazać komunikat i zebrać strumień ponownie. |
| **Rejestrator `track(session.state)` wołany przez `ui` po `Started`, zapis w `@DataScope`** | obserwacja `SessionCoordinator.activeSession`; zapis przez `ui` (`record` w ViewModelu); wywołanie zwrotne w sesji (zmiana Spec 004) | `activeSession` jest `StateFlow` z łączeniem wartości: sesja zakończona natychmiast (np. `SEND_FAILED` bez uprawnienia, anulowanie przed startem) może przejść `sesja → null`, zanim obserwator ją zobaczy – utrata wpisu. Zapis w ViewModelu ginie przy zniszczeniu ekranu w trakcie polecenia i grozi podwójnym zapisem. Wywołanie zwrotne wymaga zmiany Spec 004. `track` na `StateFlow` sesji widzi `FINISHED` zawsze (stan końcowy jest trzymany), działa niezależnie od życia ekranu i nie zmienia `session`. Koszt: `ui` musi wywołać `track` – wymaganie dla `ui` w spec. |
| **Rejestrator przyjmuje `StateFlow<SessionState>`, nie `CommandSession`** | `track(session: CommandSession)` | Testy JVM podają `MutableStateFlow` bez uruchamiania sesji i atrap SMS; rejestrator nie ma dostępu do `cancel()` sesji. |
| **Zbiór śledzonych stanów ze słabymi kluczami** | brak deduplikacji; zbiór zwykły | Deduplikacja chroni przed podwójnym wpisem przy powtórnym wywołaniu z `ui`. Zwykły zbiór trzymałby sesje (z `CodeCredentials` i `TotpSecret`) do końca procesu. |
| **Room: wersja 1, `exportSchema = true` przez argument KSP `room.schemaLocation`, bez `fallbackToDestructiveMigration`** | wtyczka Gradle `androidx.room`; `exportSchema = false`; migracja destrukcyjna | Plik schematu w repo pozwala sprawdzić kolumny testem JVM (DATA-14) i jest podstawą przyszłych testów migracji. Argument KSP nie wymaga nowej wtyczki obok AGP 9. Migracja destrukcyjna cicho kasowałaby historię – każda zmiana schematu ma mieć migrację w nowej spec. |
| **Bez szyfrowania bazy i ustawień** | SQLCipher; szyfrowanie pól kluczem Keystore | Uzasadnienie w spec (Bezpieczeństwo): prywatny katalog aplikacji + brak kopii; klucz z `setUnlockedDeviceRequired` blokowałby zapis przy zablokowanym ekranie, SQLCipher to zależność natywna. |
| **`HistoryRow` w logice czystej, `HistoryEntity` w `platform`** | encja Room w `data` | `data` bez zależności od `androidx.room`; kodek i repozytorium testowane w JVM bez generowanego kodu Room. Koszt: przepisanie 11 pól w `RoomHistoryStorage`. |
| **Brak testów automatycznych `data.platform`** (jak P3 w Spec 005) | Robolectric; `androidTest` z bazą w pamięci | Klasy cienkie, bez gałęzi; reguły w logice czystej. Kolumny i `AUTOINCREMENT` sprawdza test JVM wyeksportowanego schematu; zapytania – recenzja i W3–W5. Projekt nie ma Robolectric ani `androidTest`; ich dodanie dla pięciu zapytań nie jest uzasadnione („Przyszłe zmiany” w spec). |

## Mapowanie wymagań na testy

Testy jednostkowe JUnit 5 w `app/src/test/java/pl/twojanazwa/aapsremote/data/`, `runTest` + Turbine. Nazwa każdej metody zaczyna się od identyfikatora wymagania, np. `` `DATA-05 currentNumber nie czyta magazynu` ``. Wspólne pomocniki testowe:

- `InMemorySettingsStorage` – wartość w polu, liczniki `read`/`write`/`remove`, przełączane wyniki `false` i wyjątki, opcjonalna blokada zapisu (`CountDownLatch`), zapis nazwy wątku, licznik równoczesnych wywołań.
- `InMemoryHistoryStorage` – opis w sekcji „Repozytorium historii”.
- `FakeCommandHistory` – lista wyników przekazanych do `record`, przełączany wynik `Failed` / wyjątek.
- `HistoryFixtures` – wyniki sesji z kryteriów DATA-07 (treści czytane z zasobów testowych parsera, jak w testach sesji).

| Wymaganie | Test (klasa / rodzaj) |
| --- | --- |
| DATA-01 | `AapsNumberSettingsSaveTest` (wartość przekazana do magazynu) – jednostkowy; plik i klucz: recenzja, urządzenie W2 |
| DATA-02 | `AapsNumberSettingsLoadTest` – jednostkowy, Turbine; urządzenie W1, W7 |
| DATA-03 | `AapsNumberSettingsSaveTest` – jednostkowy |
| DATA-04 | `AapsNumberSettingsClearTest` – jednostkowy |
| DATA-05 | `AapsNumberProviderTest` – jednostkowy; wiązanie: `assembleDebug` (zadanie 7), urządzenie W1 |
| DATA-06 | `AapsNumberSettingsConcurrencyTest` – jednostkowy (wątek dyspozytora, 50 równoczesnych zapisów) |
| DATA-07 | `HistoryCodecEncodeTest` – jednostkowy, parametryzowany |
| DATA-08 | `HistoryExclusionTest` – jednostkowy, refleksja na `HistoryEntry` i `HistoryRow` |
| DATA-09 | `HistoryCodecDecodeTest` – jednostkowy, parametryzowany (tabela kodów, wartości nieznane) |
| DATA-10 | `HistoryRepositoryRecordTest` – jednostkowy; urządzenie W4 |
| DATA-11 | `HistoryRepositoryEntriesTest` – jednostkowy, Turbine; urządzenie W3 |
| DATA-12 | `HistoryRepositoryClearTest` – jednostkowy (także refleksja na `CommandHistory`); urządzenie W5 |
| DATA-13 | `SessionHistoryRecorderTest` – jednostkowy (`MutableStateFlow<SessionState>`, `TestScope`, refleksja pól) |
| DATA-14 | `HistorySchemaTest` – jednostkowy JVM, czyta `schemas/pl.twojanazwa.aapsremote.data.platform.AapsRemoteDatabase/1.json` (wyrażenia regularne, bez `org.json` – w testach JVM to atrapa Androida) i przeszukuje `src/main/java/pl/twojanazwa/aapsremote/data/platform/`; urządzenie W3, W5 |
| DATA-15 | `SettingsFailureTest` (część ustawień) i `HistoryFailureTest` (część historii) – jednostkowe |
| DATA-16 | `DataBackupRulesTest` – jednostkowy JVM, czyta `src/main/AndroidManifest.xml` i `src/main/res/xml/data_extraction_rules.xml` (katalog roboczy testów = moduł `app`, jak `SecretBackupRulesTest`); urządzenie W6 |
| DATA-17 | `SettingsRedactionTest`, `HistoryRedactionTest` – jednostkowe; `DataNoLoggingTest` – jednostkowy JVM, przeszukuje `src/main/java/pl/twojanazwa/aapsremote/data/` (z `platform`) |

Brak importów `android.*` i `androidx.*` w `data` (poza `data.platform`) sprawdza recenzja kodu. Weryfikacja W1–W7 jest ręczna i odbywa się w pierwszym PR `ui`, który zapisuje numer i pokazuje historię; zadania tej spec kończą się na `testDebugUnitTest lintDebug assembleDebug`.

## Podział na zadania

Każde zadanie to jeden PR, ok. 200–400 linii z testami. Pliki w nawiasach są rozłączne między zadaniami.

Zależności: 3 ← 2; 4 ← 1, 2; 5 ← 3; 6 ← 1; 7 ← 1, 3, 4, 5, 6. Zadania 1 i 2 mogą iść równolegle; 3, 4 i 6 równolegle po swoich zależnościach; 5 po 3; 7 na końcu.

1. **Ustawienia – logika** – `data/AapsNumberState.kt`, `SaveNumberResult.kt`, `ClearNumberResult.kt`, `AapsNumberSettingsStore.kt`, `SettingsStorage.kt`, `DataDispatcher.kt`, `DataScope.kt`, `AapsNumberSettings.kt` + testy `InMemorySettingsStorage`, `AapsNumberSettingsLoadTest`, `…SaveTest`, `…ClearTest`, `AapsNumberProviderTest`, `AapsNumberSettingsConcurrencyTest`, `SettingsFailureTest`, `SettingsRedactionTest`. Wymagania: DATA-01 (logika), DATA-02, DATA-03, DATA-04, DATA-05 (logika), DATA-06, DATA-15 (ustawienia), DATA-17 (ustawienia). Bez zależności.
2. **Historia – model i kodek** – `data/HistoryCommand.kt`, `HistoryOutcome.kt`, `HistoryEntry.kt`, `HistoryRow.kt`, `HistoryCodec.kt`, `HistoryLimits.kt`, `HistorySnapshot.kt`, `RecordResult.kt`, `ClearHistoryResult.kt`, `CommandHistory.kt` + testy `HistoryFixtures`, `HistoryCodecEncodeTest`, `HistoryCodecDecodeTest`, `HistoryExclusionTest`, `HistoryRedactionTest`. Wymagania: DATA-07, DATA-08, DATA-09, DATA-17 (historia). Bez zależności; równolegle z 1.
3. **Historia – repozytorium z retencją** – `data/HistoryStorage.kt`, `HistoryRepository.kt` + testy `InMemoryHistoryStorage`, `HistoryRepositoryRecordTest`, `…EntriesTest`, `…ClearTest`, `HistoryFailureTest`. Wymagania: DATA-10, DATA-11, DATA-12, DATA-15 (historia). Zależy od 2.
4. **Rejestrator wyników sesji** – `data/SessionHistoryRecorder.kt` + testy `FakeCommandHistory`, `SessionHistoryRecorderTest`. Wymagania: DATA-13. Zależy od 1 (`@DataScope`) i 2 (`CommandHistory`); równolegle z 3.
5. **Platforma: Room** – `gradle/libs.versions.toml`, `app/build.gradle.kts` (Room, argument KSP), `data/platform/HistoryEntity.kt`, `HistoryDao.kt`, `AapsRemoteDatabase.kt`, `RoomHistoryStorage.kt`, wygenerowany `app/schemas/…/1.json` + `HistorySchemaTest`; W3–W5 ręcznie, gdy powstanie `ui`. Wymagania: DATA-14, DATA-10/DATA-11/DATA-12 (część platformowa). Zależy od 3.
6. **Platforma: ustawienia i reguły** – `data/platform/SharedPreferencesSettingsStorage.kt` + `DataBackupRulesTest`, `DataNoLoggingTest`; W2, W6 ręcznie, gdy powstanie `ui`. Wymagania: DATA-01 (platforma), DATA-16, DATA-17 (brak logowania). Zależy od 1; równolegle z 2–5.
7. **Platforma: Hilt** – `data/platform/DataModule.kt` (wszystkie wiązania, `@DataDispatcher` = `Dispatchers.IO`, `@DataScope`, baza, DAO), odsyłacz w KDoc `sms/platform/SmsModule.kt`; `assembleDebug`; W1, W7 ręcznie, gdy powstanie `ui`. Wymagania: DATA-05 (wiązanie), DATA-06 (`Dispatchers.IO`), DATA-13 (zakres aplikacji). Zależy od 1, 3, 4, 5, 6.
