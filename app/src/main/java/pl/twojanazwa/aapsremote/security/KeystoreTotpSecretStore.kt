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
                var r = safeEncrypt(plain)
                if (r is EncryptResult.Failure && r.reason == CryptoFailure.KEY_INVALIDATED) {
                    wipe() // SECRET-08: jedno ponowne zaszyfrowanie po wyczyszczeniu, na tej samej kopii bajtów
                    r = safeEncrypt(plain)
                }
                when (r) {
                    is EncryptResult.Success -> {
                        // Rekord sprzed zapisu (po ewentualnym wipe() - wtedy brak) albo Unknown, gdy odczyt rzucił.
                        val previous = readForRollback()
                        if (safeWrite(SealedSecretFormat.encode(r.sealed))) {
                            _state.value = SecretState.PRESENT
                            SaveResult.Saved
                        } else {
                            // commit() == false mógł już zmienić stan w pamięci SharedPreferences:
                            // jedna próba wycofania (SECRET-05), tylko gdy znamy poprzednią zawartość.
                            if (previous is PreviousRecord.Known) {
                                if (previous.record != null) safeWrite(previous.record) else safeClear()
                            }
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
            when (val r = safeDecrypt(sealed)) {
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

    /** SECRET-11: usuwa rekord, potem klucz (wynik ignorowany). Nieudane usunięcie rekordu: klucz zostaje. */
    override suspend fun delete(): DeleteResult = mutex.withLock {
        withContext(dispatcher) {
            if (!safeClear()) {
                DeleteResult.Failed
            } else {
                _state.value = SecretState.ABSENT
                try {
                    cipher.deleteKey()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // wynik ignorowany
                }
                DeleteResult.Deleted
            }
        }
    }

    /** SECRET-12: tylko obecność rekordu, bez dekodowania i odszyfrowania. */
    override suspend fun refreshState(): SecretState = mutex.withLock {
        withContext(dispatcher) { updateStateFromStorage() }
    }

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

    /** Kontrakt szyfratora: nie rzuca; wyjątek (poza anulowaniem) traktujemy jak `UNAVAILABLE` (SECRET-17). */
    private fun safeEncrypt(plain: ByteArray): EncryptResult = try {
        cipher.encrypt(plain)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        EncryptResult.Failure(CryptoFailure.UNAVAILABLE)
    }

    private fun safeDecrypt(sealed: SealedSecret): DecryptResult = try {
        cipher.decrypt(sealed)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        DecryptResult.Failure(CryptoFailure.UNAVAILABLE)
    }

    private fun safeClear(): Boolean = try {
        storage.clear()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /** Zawartość magazynu sprzed zapisu, potrzebna do wycofania (SECRET-05). */
    private sealed interface PreviousRecord {
        /** [record] == null: magazyn był pusty (albo zawierał rekord nieprawidłowy - nigdy go nie przywracamy). */
        class Known(val record: String?) : PreviousRecord {
            override fun toString(): String = "Known(***)"
        }

        data object Unknown : PreviousRecord
    }

    private fun readForRollback(): PreviousRecord {
        val record = try {
            storage.read()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return PreviousRecord.Unknown
        }
        return PreviousRecord.Known(record?.takeIf { SealedSecretFormat.decode(it) != null })
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
     * chyba że INVALIDATED; błąd odczytu -> bez zmiany. Używają go: wycofanie w `save` i `refreshState`.
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
