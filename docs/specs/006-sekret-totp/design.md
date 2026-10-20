# Design 006: Przechowywanie sekretu TOTP (Keystore)

Dotyczy: [spec.md](spec.md)

## Przegląd

Pakiet `pl.twojanazwa.aapsremote.security` dostaje magazyn sekretu TOTP obok istniejącego `AapsPin`. Podział, jak w Spec 005 i zgodnie z [AGENTS.md](../../../AGENTS.md#architektura):

- **`security` – logika czysta** (Kotlin/JVM, `kotlinx.coroutines`, `javax.crypto`/`java.security` z JDK, `java.util.Base64`; bez importów `android.*`; dozwolone adnotacje `javax.inject`): format rekordu, klasyfikacja błędów, polityka generacji klucza (StrongBox → TEE), szyfrator AES-GCM nad dowolnym `javax.crypto.SecretKey`, magazyn sekretu (zapis, odczyt, wyczyszczenie, usunięcie, stan, blokada, dyspozytor). Testy JUnit 5 w JVM; szyfrator testowany prawdziwym AES-GCM z JCE i kluczem programowym.
- **`security.platform` – cienka warstwa Androida:** generacja i pobieranie klucza w `AndroidKeyStore`, `KeyguardManager.isDeviceLocked`, rozpoznanie typów wyjątków Androida (`is KeyPermanentlyInvalidatedException`, `is UserNotAuthenticatedException`, `is StrongBoxUnavailableException`), `SharedPreferences`, moduł Hilt. Bez decyzji – każda reguła (kolejność klasyfikacji, przejście StrongBox → TEE, kiedy czyścić) jest w logice czystej. Sprawdzana ręcznie (W1–W10), bez Robolectric i `androidTest`.

Kluczowa obserwacja: `Cipher` z JCE działa tak samo z kluczem programowym (`SecretKeySpec`) i z uchwytem klucza z `AndroidKeyStore` (dostawca wybierany przy `Cipher.init` według klucza). Dlatego cały kod szyfrowania (`Cipher.getInstance("AES/GCM/NoPadding")`, `init`, `updateAAD`, `doFinal`, odczyt IV) leży w logice czystej, a platforma dostarcza tylko klucz.

Zależności (kierunek): `security` → `otp` (`TotpSecret`). `otp` i `session` nie importują `security` poza istniejącym `AapsPin`. `ui` (przyszła spec): `OtpauthParser` → `TotpSecretStore.save`; przy poleceniu z kodem `TotpSecretProvider.load` → `CodeCredentials(pin, secret)` → `SessionCoordinator`; `TotpSecretStore.state` → ekran konfiguracji / blokada przycisków.

Przepływ zapisu: `ui` → `KeystoreTotpSecretStore.save` (blokada → dyspozytor I/O → `secret.copyBytes()` → `AesGcmSecretCipher.encrypt` [`SecretKeySource.getKey` albo `generateKey`] → `SealedSecretFormat.encode` → `SecretRecordStorage.write`) → `SaveResult`.

Przepływ odczytu: `ui` → `load` (blokada → dyspozytor → `SecretRecordStorage.read` → `SealedSecretFormat.decode` → `AesGcmSecretCipher.decrypt` → `TotpSecret.fromBytes` → zerowanie) → `LoadResult`; trwały błąd → wyczyszczenie.

## Interfejsy

```kotlin
package pl.twojanazwa.aapsremote.security

// ---- Publiczne API dla ui ----

/** SECRET-12. */
enum class SecretState { UNKNOWN, ABSENT, PRESENT, INVALIDATED }

sealed interface LoadResult {
    /** Sekret należy do wywołującego (SECRET-14). */
    class Loaded(val secret: TotpSecret) : LoadResult {
        override fun toString(): String = "Loaded(***)"
    }
    /** Brak rekordu – „zeskanuj QR z AAPS”. */
    data object Absent : LoadResult
    /** Wyczyszczono po trwałym błędzie – „zeskanuj ponownie QR z AAPS” (SECRET-08). */
    data object Invalidated : LoadResult
    /** Telefon zablokowany – „odblokuj telefon”, nic nie usunięto (SECRET-09). */
    data object Locked : LoadResult
    /** Inny błąd – „spróbuj ponownie”, nic nie usunięto (SECRET-10). */
    data object Unavailable : LoadResult
}

enum class SaveFailure { KEY_GENERATION, CRYPTO, STORAGE }

sealed interface SaveResult {
    data object Saved : SaveResult
    data object Locked : SaveResult
    data class Failed(val reason: SaveFailure) : SaveResult {
        override fun toString(): String = "Failed($reason)" // SECRET-15: "Failed(STORAGE)"
    }
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data object Failed : DeleteResult
}

/** Port dla warstwy składającej CodeCredentials (ui/ViewModel). */
fun interface TotpSecretProvider {
    /** SECRET-06. Nie rzuca poza CancellationException. */
    suspend fun load(): LoadResult
}

/** Magazyn jednego sekretu. Implementacja: KeystoreTotpSecretStore. */
interface TotpSecretStore : TotpSecretProvider {
    /** SECRET-12. Początkowo UNKNOWN. */
    val state: StateFlow<SecretState>

    /** SECRET-04, SECRET-05, SECRET-08. Nie modyfikuje [secret]. */
    suspend fun save(secret: TotpSecret): SaveResult

    /** SECRET-11. */
    suspend fun delete(): DeleteResult

    /** SECRET-12. Tylko obecność rekordu, bez odszyfrowania. */
    suspend fun refreshState(): SecretState
}

// ---- Porty platformy ----

/** Jeden rekord w pliku SharedPreferences (SECRET-16). Metody blokujące – wołane na dyspozytorze I/O. */
interface SecretRecordStorage {
    fun read(): String?          // może rzucić
    fun write(record: String): Boolean  // wynik commit(); może rzucić
    fun clear(): Boolean         // wynik commit(); true także przy braku rekordu; może rzucić
}

/** Klucz w AndroidKeyStore pod stałym aliasem (SECRET-01). */
interface SecretKeySource {
    /** Uchwyt klucza albo null, gdy aliasu brak. Może rzucić. */
    fun getKey(): SecretKey?
    /** Generuje klucz wg SECRET-01/SECRET-02 (przez KeyGenerationPolicy). Rzuca przy błędzie. */
    fun generateKey(): SecretKey
    /** Usuwa alias; brak aliasu to nie błąd. Może rzucić. */
    fun deleteKey()
}

/** Fakty o Androidzie potrzebne do klasyfikacji (SECRET-07). */
interface KeystoreEnvironment {
    /** KeyguardManager.isDeviceLocked. Może rzucić. */
    fun isDeviceLocked(): Boolean
    /** e is KeyPermanentlyInvalidatedException */
    fun isPermanentlyInvalidated(error: Throwable): Boolean
    /** e is UserNotAuthenticatedException */
    fun isUserNotAuthenticated(error: Throwable): Boolean
}

// ---- Logika czysta ----

/** SECRET-03. Tablice nie są kopiowane (szyfrogram nie jest tajny), toString() redagowane. */
class SealedSecret(val version: Byte, val iv: ByteArray, val ciphertext: ByteArray) {
    // require(iv.size == IV_LENGTH) { "invalid iv length" }
    // require(ciphertext.size >= MIN_CIPHERTEXT_LENGTH) { "invalid ciphertext length" }
    override fun toString(): String = "SealedSecret(***)"
}

object SealedSecretFormat {
    const val VERSION_1: Byte = 0x01
    const val IV_LENGTH: Int = 12
    const val TAG_LENGTH: Int = 16
    const val MIN_CIPHERTEXT_LENGTH: Int = TAG_LENGTH + 1
    const val MIN_RECORD_LENGTH: Int = 1 + IV_LENGTH + MIN_CIPHERTEXT_LENGTH // 30

    fun encode(sealed: SealedSecret): String
    /** null = rekord nieprawidłowy (SECRET-03). Nie rzuca. */
    fun decode(record: String): SealedSecret?
    /** AAD dla wersji: byteArrayOf(version). */
    fun aad(version: Byte): ByteArray
}

enum class CryptoFailure { LOCKED, KEY_INVALIDATED, CORRUPTED, KEY_GENERATION_FAILED, UNAVAILABLE }

/** SECRET-07. */
object CryptoFailureClassifier {
    const val MAX_CHAIN: Int = 8
    fun classify(error: Throwable, environment: KeystoreEnvironment): CryptoFailure
    fun classifyMissingKey(environment: KeystoreEnvironment): CryptoFailure
}

/** SECRET-02. */
object KeyGenerationPolicy {
    /** attempt(true); przy StrongBox niedostępnym (w łańcuchu przyczyn) – dokładnie jedno attempt(false). Inne wyjątki propagowane. */
    fun <K> generate(attempt: (strongBox: Boolean) -> K, isStrongBoxUnavailable: (Throwable) -> Boolean): K
}

sealed interface EncryptResult {
    class Success(val sealed: SealedSecret) : EncryptResult { override fun toString() = "Success(***)" }
    data class Failure(val reason: CryptoFailure) : EncryptResult
}

sealed interface DecryptResult {
    /** Wywołujący zeruje [plaintext]. */
    class Success(val plaintext: ByteArray) : DecryptResult { override fun toString() = "Success(***)" }
    data class Failure(val reason: CryptoFailure) : DecryptResult
}

/** Szyfrator rekordu. Nie rzuca poza CancellationException (SECRET-17). */
interface SecretCipher {
    /** Klucz istniejący albo wygenerowany. Nie modyfikuje i nie zeruje [plaintext] (zeruje magazyn). */
    fun encrypt(plaintext: ByteArray): EncryptResult
    /** Nigdy nie generuje klucza (SECRET-01). */
    fun decrypt(sealed: SealedSecret): DecryptResult
    /** false, gdy usunięcie rzuciło. */
    fun deleteKey(): Boolean
}

/** SECRET-03, SECRET-07. Pola: tylko zależności (SECRET-14). */
class AesGcmSecretCipher @Inject constructor(
    private val keySource: SecretKeySource,
    private val environment: KeystoreEnvironment,
) : SecretCipher {
    override fun toString(): String = "AesGcmSecretCipher(***)"
}

/** Dyspozytor I/O magazynu (SECRET-13); w aplikacji Dispatchers.IO. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecretStoreDispatcher

@Singleton
class KeystoreTotpSecretStore @Inject constructor(
    private val storage: SecretRecordStorage,
    private val cipher: SecretCipher,
    @SecretStoreDispatcher private val dispatcher: CoroutineDispatcher,
) : TotpSecretStore {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SecretState.UNKNOWN)
    override val state: StateFlow<SecretState> = _state.asStateFlow()
    override fun toString(): String = "KeystoreTotpSecretStore(***)"
}
```

```kotlin
package pl.twojanazwa.aapsremote.security.platform

class AndroidKeystoreKeySource @Inject constructor() : SecretKeySource {
    companion object {
        const val PROVIDER: String = "AndroidKeyStore"
        const val ALIAS: String = "pl.twojanazwa.aapsremote.totp-secret-key"
    }
}
class AndroidKeystoreEnvironment @Inject constructor(@ApplicationContext private val context: Context) : KeystoreEnvironment
class SharedPreferencesSecretRecordStorage @Inject constructor(@ApplicationContext private val context: Context) : SecretRecordStorage {
    companion object {
        const val FILE_NAME: String = "totp_secret_store"
        const val KEY: String = "sealed_secret"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds abstract fun store(impl: KeystoreTotpSecretStore): TotpSecretStore
    @Binds abstract fun provider(impl: KeystoreTotpSecretStore): TotpSecretProvider
    @Binds abstract fun cipher(impl: AesGcmSecretCipher): SecretCipher
    @Binds abstract fun keySource(impl: AndroidKeystoreKeySource): SecretKeySource
    @Binds abstract fun environment(impl: AndroidKeystoreEnvironment): KeystoreEnvironment
    @Binds abstract fun storage(impl: SharedPreferencesSecretRecordStorage): SecretRecordStorage

    companion object {
        @Provides @SecretStoreDispatcher fun dispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
```

Sygnatury są wiążące dla nazw i typów publicznych; ciała i prywatne pomocniki zależą od wykonawcy. Wszystkie wiązania mają implementacje (Hilt dostarcza `@ApplicationContext`), więc `assembleDebug` przechodzi bez `ui`. `TotpSecret`, `OtpauthParser`, `CodeCredentials`, `AapsPin` – bez zmian.

## Model danych i stany

### Rekord (SECRET-03)

```
bajty: | 0x01 | IV (12) | szyfrogram (n) + tag (16) |      n ≥ 1, razem ≥ 30
rekord = java.util.Base64.getEncoder().encodeToString(bajty)   // standardowy, z '=', bez końców linii
AAD    = byteArrayOf(0x01)
```

`decode(record)`: `Base64.getDecoder().decode(record)` (ścisły; `IllegalArgumentException` → `null`) → `size < 30` → `null` → `bytes[0] != 0x01` → `null` → `SealedSecret(0x01, bytes[1..13), bytes[13..))`. Nieznana wersja to rekord nieprawidłowy (wyczyszczenie) – aplikacja nie obsługuje cofnięcia do starszej wersji. Nowy format w przyszłości = nowa wersja i zmiana spec.

### Szyfrator (`AesGcmSecretCipher`)

`encrypt(plaintext)`:

1. `key = keySource.getKey()`; wyjątek → `Failure(classify(e))`.
2. `key == null` → `keySource.generateKey()`; wyjątek → `Failure(KEY_GENERATION_FAILED)` (bez klasyfikacji – generacja nie usuwa niczego).
3. `c = Cipher.getInstance("AES/GCM/NoPadding")`; `c.init(ENCRYPT_MODE, key)` (bez IV – generuje szyfr; Keystore z `randomizedEncryptionRequired` odrzuca IV od aplikacji); `c.updateAAD(aad(VERSION_1))`; `ct = c.doFinal(plaintext)`; `iv = c.iv`. Wyjątek w kroku 3 → `Failure(classify(e))`.
4. `iv == null || iv.size != 12 || ct.size != plaintext.size + 16` → `Failure(UNAVAILABLE)`.
5. `Success(SealedSecret(VERSION_1, iv, ct))`.

`decrypt(sealed)`:

1. `key = keySource.getKey()`; wyjątek → `Failure(classify(e))`; `null` → `Failure(classifyMissingKey())`.
2. `c.init(DECRYPT_MODE, key, GCMParameterSpec(128, sealed.iv))`; `c.updateAAD(aad(sealed.version))`; `pt = c.doFinal(sealed.ciphertext)` → `Success(pt)`. Wyjątek → `Failure(classify(e))`.

`deleteKey()`: `keySource.deleteKey()`; wyjątek → `false`.

Każda metoda łapie `Exception` (nie `Throwable` – błędy JVM jak `OutOfMemoryError` nie są maskowane); `CancellationException` nie może tu wystąpić (metody nie zawieszają się), ale jest przepuszczany dla spójności z SECRET-17.

### Klasyfikacja (SECRET-07)

```kotlin
fun classify(error: Throwable, env: KeystoreEnvironment): CryptoFailure {
    val locked = try { env.isDeviceLocked() } catch (e: Exception) { return UNAVAILABLE }
    if (locked) return LOCKED
    val chain = causeChain(error)          // error, cause, …; ≤ 8; stop przy powtórzeniu (identyczność)
    if (chain.any { safe { env.isUserNotAuthenticated(it) } }) return LOCKED
    if (chain.any { safe { env.isPermanentlyInvalidated(it) } }) return KEY_INVALIDATED
    if (chain.any { it is AEADBadTagException }) return CORRUPTED
    return UNAVAILABLE
}
fun classifyMissingKey(env): locked-check jak wyżej; odblokowany → KEY_INVALIDATED
// safe { } = wynik predykatu, a wyjątek predykatu = false
```

**Dlaczego tak:**

- **Blokada sprawdzana przed typem wyjątku.** Typ wyjątku przy zablokowanym telefonie zależy od wersji Androida (do Androida 11 zwykle `UserNotAuthenticatedException` przy `Cipher.init`; od Androida 12 Keystore 2.0 może zgłaszać `KeyStoreException` z kodem wewnętrznym albo `ProviderException`). `KeyguardManager.isDeviceLocked` jest stabilnym API od 22 i niezależnym od tych różnic źródłem prawdy. Gdy telefon jest zablokowany, żaden błąd nie jest uznawany za trwały – nawet `KeyPermanentlyInvalidatedException` (po odblokowaniu następny odczyt i tak go rozpozna).
- **Kasowanie tylko po pozytywnym rozpoznaniu** dwóch udokumentowanych sygnałów trwałości (`KeyPermanentlyInvalidatedException`, `AEADBadTagException`) i braku aliasu przy odblokowanym telefonie. Wszystko inne → `UNAVAILABLE` bez kasowania.
- **Asymetria kosztów:** fałszywe „trwałe” = sekret skasowany, polecenia z kodem niedostępne do fizycznego dostępu do telefonu z AAPS (zwykle u innej osoby). Fałszywe „przejściowe” = komunikat „spróbuj ponownie” przy każdej próbie; użytkownik może sam wybrać „usuń sekret” i zeskanować ponownie (SECRET-11). Bezpieczeństwo nie zależy od kasowania: szyfrogram bez klucza jest bezużyteczny.
- **Ryzyko błędnej klasyfikacji i jego ograniczenie:**
  - Keystore uważa telefon za zablokowany, a `isDeviceLocked` = `false` (np. chwila tuż po odblokowaniu, Smart Lock): wyjątek nie jest ani KPI, ani błędem tagu → `UNAVAILABLE` (albo `LOCKED`, gdy to `UserNotAuthenticatedException`) – nic nie jest kasowane. W10.
  - Trwały błąd zgłaszany innym typem niż dwa rozpoznawane (np. po wyłączeniu blokady ekranu na niektórych wersjach): `UNAVAILABLE` przy każdej próbie – brak kasowania, wyjście przez jawne usunięcie. W4 sprawdza ten przypadek; częste wystąpienie = zmiana spec.
  - `AEADBadTagException` przy dobrym kluczu i dobrych danych nie występuje (deterministyczna weryfikacja tagu); gdy rekord został zamieniony z kluczem po awarii, rekord i tak jest nieczytelny.
  - Brak aliasu przy odblokowanym telefonie: `KeyStore.getKey` zwraca `null` tylko przy braku wpisu; błąd usługi Keystore daje wyjątek (→ `UNAVAILABLE`), nie `null`. Aplikacja nie działa przed pierwszym odblokowaniem (brak Direct Boot).

### Polityka generacji (SECRET-02)

```kotlin
fun <K> generate(attempt, isStrongBoxUnavailable): K =
    try { attempt(true) }
    catch (e: Exception) {
        if (causeChain(e).any { isStrongBoxUnavailable(it) }) attempt(false) else throw e
    }
```

Platforma: `attempt(strongBox)` = `KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec(strongBox)) }.generateKey()`, gdzie `spec(sb)` = `KeyGenParameterSpec.Builder(ALIAS, PURPOSE_ENCRYPT or PURPOSE_DECRYPT).setKeySize(256).setBlockModes(BLOCK_MODE_GCM).setEncryptionPaddings(ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).setUnlockedDeviceRequired(true)` + `setIsStrongBoxBacked(true)` tylko dla `sb == true`. `isStrongBoxUnavailable = { it is StrongBoxUnavailableException }`. Nieudana pierwsza próba nie tworzy aliasu, więc druga nie wymaga sprzątania.

### Magazyn sekretu (`KeystoreTotpSecretStore`)

Każda operacja: `mutex.withLock { withContext(dispatcher) { … } }`; aktualizacja `_state` wewnątrz bloku `withContext`, zaraz po wywołaniu portu, które ją przesądza – anulowanie zgłaszane przy powrocie z `withContext` nie gubi zmiany stanu (SECRET-13). Wyjątki portów magazynu klucz-wartość łapane jako `Exception` z przepuszczeniem `CancellationException` (SECRET-17).

`save(secret)`:

```kotlin
val plain = secret.copyBytes()
try {
    var r = cipher.encrypt(plain)
    if (r is Failure && r.reason == KEY_INVALIDATED) { wipe(); r = cipher.encrypt(plain) }
    when (r) {
        is Success -> {
            // Rekord sprzed zapisu (po ewentualnym wipe() – wtedy brak): Known(record | null) albo Unknown,
            // gdy odczyt rzucił (wtedy bez wycofania). Rekord, którego SealedSecretFormat.decode() nie przyjmuje,
            // jest traktowany jak brak (Known(null)) – wycofanie nigdy nie przywraca rekordu nieprawidłowego.
            val previous = readForRollback()
            if (safeWrite(SealedSecretFormat.encode(r.sealed))) { _state.value = PRESENT; Saved }
            else {
                // commit() == false mógł już zmienić stan w pamięci SharedPreferences – jedna próba wycofania (SECRET-05)
                if (previous is Known) { if (previous.record != null) safeWrite(previous.record) else safeClear() }
                updateStateFromStorage()   // reguły refreshState() (pusty + INVALIDATED → INVALIDATED), bez ponownego brania blokady
                Failed(STORAGE)
            }
        }
        is Failure -> when (r.reason) {
            LOCKED -> SaveResult.Locked
            KEY_GENERATION_FAILED -> Failed(KEY_GENERATION)
            else -> Failed(CRYPTO)          // UNAVAILABLE, CORRUPTED, drugie KEY_INVALIDATED
        }
    }
} finally { plain.fill(0) }
```

`load()`:

```kotlin
val record = try { storage.read() }
    catch (e: CancellationException) { throw e }   // podtyp IllegalStateException – nie zamieniać na wynik (SECRET-17)
    catch (e: Exception) { return Unavailable }    // stan bez zmian
if (record == null) { if (_state.value != INVALIDATED) _state.value = ABSENT; return Absent }
val sealed = SealedSecretFormat.decode(record) ?: return wipeAndInvalidate()
when (val r = cipher.decrypt(sealed)) {
    is DecryptResult.Success -> try {
        val secret = TotpSecret.fromBytes(r.plaintext)     // pusty → IllegalArgumentException
        _state.value = PRESENT; Loaded(secret)
    } catch (e: IllegalArgumentException) { wipeAndInvalidate() }
      finally { r.plaintext.fill(0) }
    is DecryptResult.Failure -> when (r.reason) {
        LOCKED -> LoadResult.Locked
        KEY_INVALIDATED, CORRUPTED -> wipeAndInvalidate()
        else -> Unavailable
    }
}
```

`wipe()`: `safeClear()` (wynik ignorowany), `cipher.deleteKey()` (wynik ignorowany), `_state.value = INVALIDATED`. `wipeAndInvalidate()` = `wipe()` + `Invalidated`.

`delete()`: `if (!safeClear()) Failed else { cipher.deleteKey(); _state.value = ABSENT; Deleted }`.

`refreshState()`: `try { storage.read() } catch (CancellationException) → throw; catch (Exception) → return _state.value`; `!= null` → `PRESENT`; `null` → `ABSENT` chyba że `INVALIDATED`.

| Stan | Zdarzenie | Nowy stan | Wynik / akcja | Wymaganie |
| --- | --- | --- | --- | --- |
| dowolny | `refreshState`, rekord jest | `PRESENT` | – | SECRET-12 |
| ≠ `INVALIDATED` | `refreshState` / `load`, brak rekordu | `ABSENT` | `load`: `Absent` | SECRET-06, SECRET-12 |
| `INVALIDATED` | `refreshState` / `load`, brak rekordu | `INVALIDATED` | `load`: `Absent` | SECRET-06, SECRET-12 |
| dowolny | `refreshState` / `load`, wyjątek odczytu | bez zmian | `load`: `Unavailable` | SECRET-10, SECRET-12 |
| dowolny | `load`, odszyfrowano | `PRESENT` | `Loaded`, bufor wyzerowany | SECRET-06 |
| dowolny | `load`, rekord nieprawidłowy / `KEY_INVALIDATED` / `CORRUPTED` / pusty sekret | `INVALIDATED` | wyczyszczenie, `Invalidated` | SECRET-08 |
| dowolny | `load`, `LOCKED` | bez zmian | `Locked` | SECRET-09 |
| dowolny | `load`, `UNAVAILABLE` | bez zmian | `Unavailable` | SECRET-10 |
| dowolny | `save`, zapis `true` | `PRESENT` | `Saved` | SECRET-04, SECRET-05 |
| dowolny | `save`, `KEY_INVALIDATED` | `INVALIDATED`, potem wg drugiej próby | wyczyszczenie + 1 ponowne zaszyfrowanie | SECRET-08 |
| dowolny | `save`, `LOCKED` / `KEY_GENERATION_FAILED` / `UNAVAILABLE` / zapis `false` | bez zmian | `Locked` / `Failed(…)` | SECRET-04 |
| dowolny | `delete`, usunięcie rekordu `true` | `ABSENT` | usunięcie klucza, `Deleted` | SECRET-11 |
| dowolny | `delete`, usunięcie rekordu `false` / wyjątek | bez zmian | `Failed` | SECRET-11 |

### Cykl życia sekretu w pamięci (SECRET-14)

| Obiekt | Gdzie | Do kiedy |
| --- | --- | --- |
| `TotpSecret` z `OtpauthParser` | `ui` (wynik skanu) | do powrotu z `save`; `ui` nie trzyma go dłużej |
| kopia `copyBytes()` przy zapisie | zmienna lokalna `save` | wyzerowana w `finally` |
| `plaintext` z `decrypt` | zmienna lokalna `load` | wyzerowany w `finally` po `TotpSecret.fromBytes` (który kopiuje) |
| `TotpSecret` z `load` | `ui` → `CodeCredentials` → `CommandSession` | do końca sesji, potem odśmiecenie (bez zerowania – „Przyszłe zmiany” w spec) |
| bufory `Cipher`, `SecretKeySpec` w `TotpGenerator` | JCA / Keystore | poza kontrolą aplikacji |
| `SealedSecret`, tekst rekordu | zmienne lokalne | do odśmiecenia (nie są tajne bez klucza) |

Magazyn i szyfrator nie mają pól z danymi (SECRET-14) – sprawdza to test refleksją, jak SMS-13.

### Warstwa Androida (`security.platform`)

- **`AndroidKeystoreKeySource`:** `KeyStore.getInstance(PROVIDER).apply { load(null) }` przy każdym wywołaniu (bez pola – obiekt bez stanu); `getKey()` = `keyStore.getKey(ALIAS, null) as? SecretKey` (wpis innego typu → `null` → wyczyszczenie usuwa alias); `generateKey()` = `KeyGenerationPolicy.generate(…)` (wyżej); `deleteKey()` = `keyStore.deleteEntry(ALIAS)` (brak wpisu nie jest błędem).
- **`AndroidKeystoreEnvironment`:** `isDeviceLocked()` = `context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked ?: throw IllegalStateException("KeyguardManager unavailable")`; predykaty `is KeyPermanentlyInvalidatedException`, `is UserNotAuthenticatedException`.
- **`SharedPreferencesSecretRecordStorage`:** `context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)` przy każdym wywołaniu (system trzyma instancję w pamięci podręcznej; pierwsze wywołanie czyta plik – dlatego dyspozytor I/O); `read()` = `getString(KEY, null)`; `write(r)` = `edit().putString(KEY, r).commit()`; `clear()` = `edit().remove(KEY).commit()`. Wynik `commit()` jest używany, więc lint `ApplySharedPref` nie zgłasza ostrzeżenia (bez `@SuppressLint`).
- **Manifest i `data_extraction_rules.xml`:** bez zmian – już mają `allowBackup="false"`, `fullBackupContent="false"` i wykluczenie `sharedpref` w `cloud-backup` i `device-transfer`; SECRET-16 dodaje tylko test JVM pilnujący tego stanu.

Żadna klasa `security` ani `security.platform` nie wywołuje API logowania (SECRET-15).

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| **AES-256-GCM w Keystore, generowany w aplikacji, `setUnlockedDeviceRequired(true)`, bez uwierzytelnienia użytkownika** (decyzja użytkownika) | biometria; `setUserAuthenticationRequired` z czasem ważności; klucz importowany | Plan: bez biometrii, klucz działa tylko przy odblokowanym telefonie. GCM daje poufność i integralność (wykrycie uszkodzenia = `CORRUPTED`). Klucz generowany w Keystore nigdy nie opuszcza bezpiecznego sprzętu. |
| **StrongBox → TEE tylko przy `StrongBoxUnavailableException`, jedna druga próba** (decyzja użytkownika) | zawsze TEE; kolejne obniżanie parametrów | StrongBox, gdy jest, bez blokowania telefonów bez niego; każdy inny błąd ujawnia problem zamiast cicho słabszego klucza. |
| **Bez rotacji klucza przy zamianie sekretu** | nowy klucz przy każdym zapisie (dwa aliasy A/B z identyfikatorem w rekordzie) | Zamiana jest atomowa z samej natury jednego `commit()` jednej wartości, gdy klucz się nie zmienia. Rotacja pod tym samym aliasem psułaby stary rekord przed zapisem nowego (brak atomowości); dwa aliasy dają atomowość, ale więcej stanów i sprzątania. Korzyść rotacji jest pomijalna: klucz jest nieeksportowalny, IV losowy 96-bitowy, a liczba szyfrowań tym kluczem jest mała (jedno na skan QR), więc ryzyko powtórzenia IV jest pomijalne. Klucz jest i tak usuwany przy jawnym usunięciu i wyczyszczeniu. |
| **Rekord: wersja + IV + szyfrogram z tagiem w jednej wartości Base64, AAD = bajt wersji** (decyzja użytkownika co do zawartości) | IV i szyfrogram w osobnych kluczach; bez AAD | Jedna wartość = jeden atomowy zapis, bez stanu „nowy IV ze starym szyfrogramem”. AAD wiąże wersję z tagiem: zmiana bajtu wersji na znaną wartość też wykrywana jako `CORRUPTED`. `java.util.Base64` zamiast `android.util.Base64` – logika czysta testowalna w JVM. |
| **`SharedPreferences` w osobnym pliku, `commit()`** (decyzja użytkownika) | DataStore; `EncryptedSharedPreferences`; plik własny | Jedna mała wartość; `commit()` zwraca wynik zapisu, a implementacja systemowa zapisuje plik z kopią zapasową poprzedniej wersji (stary albo nowy po awarii – W9). DataStore dodałby zależność i asynchroniczny model bez potrzeby. `androidx.security:security-crypto` jest wycofana. Odstępstwo od wiersza „Bezpieczne dane” w planie zapisane w spec. |
| **Cały kod `Cipher` w logice czystej, platforma daje tylko klucz** | szyfrowanie w `security.platform` | Format, AAD, długości IV/tagu, kolejność `init`/`updateAAD`/`doFinal` i klasyfikacja są testowane prawdziwym AES-GCM z JCE w JVM. Na Androidzie `Cipher.getInstance("AES/GCM/NoPadding")` wybiera dostawcę Keystore przy `init` z uchwytem klucza Keystore – ten sam kod. |
| **Klasyfikacja w logice czystej, platforma tylko rozpoznaje typy i blokadę** | klasyfikacja w całości w `platform` | Instrukcja: „klasyfikacja wyjątków w cienkiej warstwie” – spełniona w zakresie tego, czego JVM nie zna (typy `android.security.keystore.*`, `KeyguardManager`). Kolejność reguł i reakcja (kasować / nie) to decyzja bezpieczeństwa – w JVM ma testy dla każdego przypadku. |
| **Blokada telefonu sprawdzana przed typem wyjątku; w razie wątpliwości nie kasować** | klasyfikacja tylko po typie wyjątku | Uzasadnienie i ryzyka – sekcja „Klasyfikacja” wyżej. |
| **Brak klucza przy odszyfrowaniu = `KEY_INVALIDATED` (przy odblokowanym)** | `UNAVAILABLE` | Decyzja użytkownika: utrata klucza = ponowny skan. `getKey` zwraca `null` tylko przy braku aliasu; błędy usługi dają wyjątek. |
| **Przy zapisie `KEY_INVALIDATED` → wyczyszczenie i jedna ponowna próba** | od razu `Failed` | Użytkownik właśnie skanuje QR po komunikacie „zeskanuj ponownie”; bez nowego klucza zapis nigdy by się nie udał. Jedna próba – bez pętli. To nie jest ponawianie polecenia AAPS. |
| **Stan `UNKNOWN` + jawne `refreshState()`** | odczyt w konstruktorze; `stateIn` z zakresem aplikacji | Konstruktor bez I/O (Hilt tworzy singleton na dowolnym wątku); brak potrzeby zakresu korutyn aplikacji w `security`. `ui` woła `refreshState()` przy starcie ekranu. |
| **`INVALIDATED` tylko w pamięci** | znacznik w pliku | Prostsze; po restarcie `ABSENT` prowadzi do tej samej akcji (skan QR). „Przyszłe zmiany” w spec. |
| **`Mutex` + wstrzykiwany dyspozytor** (decyzja użytkownika) | `synchronized`; wątek wywołującego | `Mutex` nie blokuje wątku w oczekiwaniu i działa z `suspend`. Dyspozytor wstrzykiwany – testy używają `StandardTestDispatcher` / własnego wątku do sprawdzenia SECRET-13. |
| **Kwalifikator `@SecretStoreDispatcher` w `security`** | wspólny `@IoDispatcher` w nowym pakiecie | Projekt nie ma jeszcze wspólnego kwalifikatora; lokalny nie przesądza struktury `data`. Można go później zastąpić wspólnym (zmiana spec). |
| **`SealedSecret` bez kopiowania tablic** | kopie obronne | Szyfrogram i IV nie są tajne bez klucza; obiekt żyje tylko lokalnie. `toString()` i tak redagowane (SECRET-15). |
| **Brak testów automatycznych `security.platform`** (jak P3 w Spec 005) | Robolectric; `androidTest` | Klasy cienkie, bez gałęzi decyzyjnych poza `?:`/`is`; Robolectric nie emuluje Keystore wiernie. Ręcznie W1–W10, gdy powstanie `ui`. |

## Mapowanie wymagań na testy

Testy jednostkowe JUnit 5 w `app/src/test/java/pl/twojanazwa/aapsremote/security/`, `runTest` + Turbine. Nazwa każdej metody zaczyna się od identyfikatora wymagania, np. `` `SECRET-07 zablokowany telefon nigdy nie daje bledu trwalego` ``. Wspólne pomocniki testowe:

- `InMemorySecretRecordStorage` – rekord w polu, liczniki `read`/`write`/`clear`, przełączane wyniki `false` i wyjątki, opcjonalna blokada zapisu (`CountDownLatch`), zapis nazwy wątku.
- `FakeSecretKeySource` – klucz programowy `SecretKeySpec` (K = `0x00`…`0x1F`), liczniki `getKey`/`generateKey`/`deleteKey`, tryby: brak klucza, „utrata” klucza, wyjątki.
- `FakeKeystoreEnvironment` – przełączana blokada (także wyjątek), predykaty rozpoznające testowe klasy `TestKeyInvalidatedException`, `TestUserNotAuthenticatedException` (zwykłe `Exception` w kodzie testów), opcjonalny wyjątek z predykatu.
- `FakeSecretCipher` – kolejka zadanych `EncryptResult`/`DecryptResult`, zapamiętanie referencji przekazanych i zwracanych tablic, licznik wywołań; tryb „przezroczysty” (XOR ze stałą – tylko testy magazynu) do sprawdzania treści.
- Sekrety S1, S2 i `TotpGenerator` (Spec 001) do kontroli kodu `287082` dla chwili 59000 ms.

| Wymaganie | Test (klasa / rodzaj) |
| --- | --- |
| SECRET-01 | `KeystoreTotpSecretStoreSaveTest` (generacja tylko przy braku klucza, odczyt nie generuje) – jednostkowy; parametry `KeyGenParameterSpec`: urządzenie W1, recenzja kodu |
| SECRET-02 | `KeyGenerationPolicyTest` – jednostkowy; `KeystoreTotpSecretStoreSaveTest` (`Failed(KEY_GENERATION)`); urządzenie: W5, W7 |
| SECRET-03 | `SealedSecretFormatTest` – jednostkowy, parametryzowany; `AesGcmSecretCipherTest` – JCE: długości, różne IV, zmiana bitu, inny klucz, AAD |
| SECRET-04 | `KeystoreTotpSecretStoreSaveTest` – jednostkowy (wyniki, zerowanie kopii, anulowanie) |
| SECRET-05 | `KeystoreTotpSecretStoreReplaceTest` – jednostkowy; urządzenie: W9 |
| SECRET-06 | `KeystoreTotpSecretStoreLoadTest` – jednostkowy (zerowanie, dwa odczyty, `Absent`) |
| SECRET-07 | `CryptoFailureClassifierTest` – jednostkowy, parametryzowany; `AesGcmSecretCipherTest` (inny klucz → `CORRUPTED`, brak klucza); urządzenie: W2, W4, W10 |
| SECRET-08 | `KeystoreTotpSecretStoreInvalidationTest` – jednostkowy (atrapa szyfratora i prawdziwy `AesGcmSecretCipher` z „utratą” klucza) |
| SECRET-09 | `KeystoreTotpSecretStoreLockedTest` – jednostkowy (atrapa i prawdziwy szyfrator); urządzenie: W2 |
| SECRET-10 | `KeystoreTotpSecretStoreUnavailableTest` – jednostkowy |
| SECRET-11 | `KeystoreTotpSecretStoreDeleteTest` – jednostkowy |
| SECRET-12 | `KeystoreTotpSecretStoreStateTest` – jednostkowy, Turbine; urządzenie: W6 |
| SECRET-13 | `KeystoreTotpSecretStoreConcurrencyTest` – jednostkowy (blokada zapisu, 50 równoczesnych zapisów, wątek dyspozytora, anulowanie po zapisie) |
| SECRET-14 | `SecretRetentionTest` – jednostkowy, refleksja na `KeystoreTotpSecretStore` i `AesGcmSecretCipher` |
| SECRET-15 | `SecretRedactionTest` – jednostkowy: `toString()` wszystkich typów z `security`, komunikaty wyjątków `SealedSecret` |
| SECRET-16 | `SecretBackupRulesTest` – jednostkowy JVM, czyta `src/main/AndroidManifest.xml`, `src/main/res/xml/data_extraction_rules.xml`, `../gradle/libs.versions.toml`, `build.gradle.kts` (katalog roboczy testów = moduł `app`); urządzenie: W8 |
| SECRET-17 | `KeystoreTotpSecretStoreFailureTest` i `AesGcmSecretCipherTest` (wyjątki portów) – jednostkowe |

Brak wywołań API logowania i brak importów `android.*` w `security` (poza `security.platform`) sprawdza recenzja kodu. Weryfikacja W1–W10 jest ręczna i odbywa się w pierwszym PR `ui`, który zapisuje sekret; zadania tej spec kończą się na `testDebugUnitTest lintDebug assembleDebug`.

## Podział na zadania

Każde zadanie to jeden PR, ok. 200–400 linii z testami. Pliki w nawiasach są rozłączne między zadaniami (poza `SecretRedactionTest` i `SecretRetentionTest`, które każde zadanie rozszerza o swoje typy – przy równoległej pracy konflikt rozwiązuje drugi scalany PR).

Zależności: 3 ← 1, 2; 4 ← 3; 5 ← 4; 6 ← 1; 7 ← 2, 3, 5, 6. Zadania 1 i 2 mogą iść równolegle; 6 równolegle z 2, 3, 4 i 5; 7 na końcu.

1. **Format rekordu i typy publiczne** – `SecretState`, `LoadResult`, `SaveFailure`, `SaveResult`, `DeleteResult`, `TotpSecretProvider`, `TotpSecretStore`, `SecretRecordStorage`, `SealedSecret`, `SealedSecretFormat` + `SealedSecretFormatTest`, część `SecretRedactionTest` (wyniki, `SealedSecret`). Wymagania: SECRET-03 (format bez szyfrowania), SECRET-15 (część). Bez zależności.
2. **Klasyfikacja błędów i polityka generacji klucza** – `CryptoFailure`, `KeystoreEnvironment`, `CryptoFailureClassifier`, `KeyGenerationPolicy` (wspólny prywatny pomocnik łańcucha przyczyn w osobnym pliku) + `FakeKeystoreEnvironment`, `CryptoFailureClassifierTest`, `KeyGenerationPolicyTest`. Wymagania: SECRET-02 (logika), SECRET-07 (bez szyfratora). Bez zależności; równolegle z 1.
3. **Szyfrator AES-GCM** – `SecretKeySource`, `EncryptResult`, `DecryptResult`, `SecretCipher`, `AesGcmSecretCipher` + `FakeSecretKeySource`, `AesGcmSecretCipherTest` (JCE, klucz K), część `SecretRetentionTest` i `SecretRedactionTest`. Wymagania: SECRET-03 (szyfrowanie), SECRET-07 (integracja), SECRET-14 (część), SECRET-17 (szyfrator). Zależy od 1 i 2.
4. **Magazyn sekretu: zapis, odczyt, wyczyszczenie** – `SecretStoreDispatcher`, `KeystoreTotpSecretStore` (`save`, `load`, `wipe`, stan w tych ścieżkach) + `InMemorySecretRecordStorage`, `FakeSecretCipher`, `KeystoreTotpSecretStoreSaveTest`, `…ReplaceTest`, `…LoadTest`, `…InvalidationTest`, `…LockedTest`, `…UnavailableTest`. Wymagania: SECRET-01 (logika), SECRET-04, SECRET-05, SECRET-06, SECRET-08, SECRET-09, SECRET-10. Zależy od 3.
5. **Magazyn sekretu: usuwanie, stan, współbieżność, odporność** – `delete`, `refreshState` (pod tym samym `Mutex` i dyspozytorem co zadanie 4) + `KeystoreTotpSecretStoreDeleteTest`, `…StateTest`, `…ConcurrencyTest`, `…FailureTest`, część `SecretRetentionTest` i `SecretRedactionTest` (magazyn). Wymagania: SECRET-11, SECRET-12, SECRET-13, SECRET-14 (reszta), SECRET-15 (reszta), SECRET-17 (magazyn). Zależy od 4.
6. **Platforma: magazyn klucz-wartość i reguły kopii** – `security.platform.SharedPreferencesSecretRecordStorage` + `SecretBackupRulesTest`; W8, W9 ręcznie, gdy powstanie `ui`. Wymagania: SECRET-16, SECRET-05 (część urządzeniowa). Zależy od 1; równolegle z 2–5.
7. **Platforma: Keystore i Hilt** – `security.platform.AndroidKeystoreKeySource` (generacja przez `KeyGenerationPolicy`, StrongBox → TEE), `AndroidKeystoreEnvironment`, `SecurityModule` (wszystkie wiązania i `@SecretStoreDispatcher`); `assembleDebug`; W1–W7, W10 ręcznie, gdy powstanie `ui`. Wymagania: SECRET-01 (parametry), SECRET-02 (część urządzeniowa), SECRET-07 (rozpoznanie typów), SECRET-13 (`Dispatchers.IO`). Zależy od 2, 3, 5, 6.
