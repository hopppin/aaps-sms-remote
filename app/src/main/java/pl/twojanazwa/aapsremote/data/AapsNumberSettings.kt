package pl.twojanazwa.aapsremote.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.twojanazwa.aapsremote.sms.AapsNumber
import pl.twojanazwa.aapsremote.sms.AapsNumberProvider

/**
 * Numer AAPS: wczytanie, zapis i usunięcie (DATA-01 – DATA-04). Numer nie trafia do logów
 * ani komunikatów wyjątków.
 */
@Singleton
class AapsNumberSettings @Inject constructor(
    private val storage: SettingsStorage,
    @DataDispatcher private val dispatcher: CoroutineDispatcher,
    @DataScope scope: CoroutineScope,
) : AapsNumberSettingsStore, AapsNumberProvider {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<AapsNumberState>(AapsNumberState.Loading)

    override val state: StateFlow<AapsNumberState> = _state.asStateFlow()

    /** DATA-05: tylko odczyt state.value; bez I/O, bez blokady. */
    override fun currentNumber(): String? = (_state.value as? AapsNumberState.Configured)?.number?.destination

    override fun toString(): String = "AapsNumberSettings(***)"

    init {
        scope.launch { load() }
    }

    private suspend fun load() = mutex.withLock {
        if (_state.value != AapsNumberState.Loading) return@withLock
        _state.value = try {
            withContext(dispatcher) {
                AapsNumber.parse(storage.readAapsNumber())?.let { AapsNumberState.Configured(it) }
                    ?: AapsNumberState.NotConfigured
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AapsNumberState.LoadFailed
        }
    }

    override suspend fun save(raw: String): SaveNumberResult {
        val number = AapsNumber.parse(raw) ?: return SaveNumberResult.Invalid
        return mutex.withLock {
            try {
                withContext(dispatcher) {
                    if (storage.writeAapsNumber(number.destination)) {
                        // Przed powrotem z withContext: anulowanie po zapisie nie rozjeżdża stanu z plikiem.
                        _state.value = AapsNumberState.Configured(number)
                        SaveNumberResult.Saved
                    } else {
                        SaveNumberResult.Failed
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SaveNumberResult.Failed
            }
        }
    }

    override suspend fun clear(): ClearNumberResult = mutex.withLock {
        try {
            withContext(dispatcher) {
                if (storage.removeAapsNumber()) {
                    _state.value = AapsNumberState.NotConfigured
                    ClearNumberResult.Cleared
                } else {
                    ClearNumberResult.Failed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ClearNumberResult.Failed
        }
    }
}
