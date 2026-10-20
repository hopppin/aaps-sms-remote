package pl.twojanazwa.aapsremote.security

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.twojanazwa.aapsremote.otp.TotpSecret
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Magazyn jednego sekretu TOTP (spec 006). Pola: tylko zależności, [Mutex] i stan (SECRET-14).
 * Każda operacja: `mutex.withLock { withContext(dispatcher) { ... } }`, a stan jest aktualizowany
 * wewnątrz `withContext` zaraz po wywołaniu portu, które go przesądza (SECRET-13).
 *
 * Stan prac (łańcuch #70-#74):
 * - TODO(#72): wycofanie nieudanego zapisu (SECRET-05); do tego czasu `Failed(STORAGE)` bez wycofania.
 * - TODO(#73): `delete` (SECRET-11) i `refreshState` (SECRET-12) to zaślepki bez I/O.
 */
@Singleton
class KeystoreTotpSecretStore @Inject constructor(
    private val storage: SecretRecordStorage,
    private val cipher: SecretCipher,
    @SecretStoreDispatcher private val dispatcher: CoroutineDispatcher,
) : TotpSecretStore {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SecretState.UNKNOWN)
    override val state: StateFlow<SecretState> = _state.asStateFlow()

    override suspend fun save(secret: TotpSecret): SaveResult = mutex.withLock {
        withContext(dispatcher) {
            val plain = secret.copyBytes()
            try {
                var r = cipher.encrypt(plain)
                if (r is EncryptResult.Failure && r.reason == CryptoFailure.KEY_INVALIDATED) {
                    wipe() // SECRET-08: jedno ponowne zaszyfrowanie po wyczyszczeniu, na tej samej kopii bajtów
                    r = cipher.encrypt(plain)
                }
                when (r) {
                    is EncryptResult.Success -> {
                        if (safeWrite(SealedSecretFormat.encode(r.sealed))) {
                            _state.value = SecretState.PRESENT
                            SaveResult.Saved
                        } else {
                            // TODO(#72): jedna próba wycofania do rekordu sprzed zapisu (SECRET-05).
                            updateStateFromStorage()
                            SaveResult.Failed(SaveFailure.STORAGE)
                        }
                    }
                    is EncryptResult.Failure -> when (r.reason) {
                        CryptoFailure.LOCKED -> SaveResult.Locked
                        CryptoFailure.KEY_GENERATION_FAILED -> SaveResult.Failed(SaveFailure.KEY_GENERATION)
                        // UNAVAILABLE, CORRUPTED, drugie KEY_INVALIDATED
                        else -> SaveResult.Failed(SaveFailure.CRYPTO)
                    }
                }
            } finally {
                plain.fill(0)
            }
        }
    }

    override suspend fun load(): LoadResult = mutex.withLock {
        withContext(dispatcher) {
            val record = try {
                storage.read()
            } catch (e: CancellationException) {
                throw e // podtyp IllegalStateException - nie zamieniać na wynik (SECRET-17)
            } catch (_: Exception) {
                return@withContext LoadResult.Unavailable // stan bez zmian
            }
            if (record == null) {
                if (_state.value != SecretState.INVALIDATED) _state.value = SecretState.ABSENT
                return@withContext LoadResult.Absent
            }
            val sealed = SealedSecretFormat.decode(record) ?: return@withContext wipeAndInvalidate()
            when (val r = cipher.decrypt(sealed)) {
                is DecryptResult.Success -> try {
                    val secret = TotpSecret.fromBytes(r.plaintext) // kopiuje bajty
                    _state.value = SecretState.PRESENT
                    LoadResult.Loaded(secret)
                } catch (_: IllegalArgumentException) {
                    wipeAndInvalidate() // pusty sekret (SECRET-08)
                } finally {
                    r.plaintext.fill(0)
                }
                is DecryptResult.Failure -> when (r.reason) {
                    CryptoFailure.LOCKED -> LoadResult.Locked // SECRET-09: nic nie usuwamy
                    CryptoFailure.KEY_INVALIDATED, CryptoFailure.CORRUPTED -> wipeAndInvalidate()
                    else -> LoadResult.Unavailable // SECRET-10: UNAVAILABLE, KEY_GENERATION_FAILED
                }
            }
        }
    }

    /** TODO(#73): SECRET-11. Zaślepka: nic nie usuwa. */
    override suspend fun delete(): DeleteResult = DeleteResult.Failed

    /** TODO(#73): SECRET-12. Zaślepka: bez I/O, zwraca bieżący stan. */
    override suspend fun refreshState(): SecretState = _state.value

    /**
     * SECRET-08: usuwa rekord, potem klucz (wyniki i wyjątki ignorowane poza anulowaniem), stan INVALIDATED.
     */
    private fun wipe() {
        safeClear()
        try {
            cipher.deleteKey()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // wynik ignorowany
        }
        _state.value = SecretState.INVALIDATED
    }

    private fun wipeAndInvalidate(): LoadResult {
        wipe()
        return LoadResult.Invalidated
    }

    private fun safeClear(): Boolean = try {
        storage.clear()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /** Wynik `write`; wyjątek (poza anulowaniem) to `false`. */
    private fun safeWrite(record: String): Boolean = try {
        storage.write(record)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /**
     * Reguły `refreshState()` (SECRET-12), bez brania blokady: rekord jest -> PRESENT; brak -> ABSENT,
     * chyba że INVALIDATED; błąd odczytu -> bez zmiany. Użyje go też #71 (wycofanie) i #73.
     */
    private fun updateStateFromStorage(): SecretState {
        val record = try {
            storage.read()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return _state.value
        }
        if (record != null) {
            _state.value = SecretState.PRESENT
        } else if (_state.value != SecretState.INVALIDATED) {
            _state.value = SecretState.ABSENT
        }
        return _state.value
    }

    override fun toString(): String = "KeystoreTotpSecretStore(***)"
}
