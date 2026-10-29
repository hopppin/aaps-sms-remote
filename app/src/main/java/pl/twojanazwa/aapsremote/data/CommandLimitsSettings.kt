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
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.commands.LimitsValidation

/** Limity poleceń: wczytanie i zapis (LIM-04). Wartości nie trafiają do logów ani wyjątków. */
@Singleton
class CommandLimitsSettings @Inject constructor(
    private val storage: CommandLimitsStorage,
    @DataDispatcher private val dispatcher: CoroutineDispatcher,
    @DataScope scope: CoroutineScope,
) : CommandLimitsStore {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<CommandLimitsState>(CommandLimitsState.Loading)

    override val state: StateFlow<CommandLimitsState> = _state.asStateFlow()

    override fun toString(): String = "CommandLimitsSettings(***)"

    init {
        scope.launch { load() }
    }

    private suspend fun load() = mutex.withLock {
        if (_state.value != CommandLimitsState.Loading) return@withLock
        _state.value = try {
            withContext(dispatcher) { read() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CommandLimitsState.LoadFailed
        }
    }

    private fun read(): CommandLimitsState {
        val carbs = storage.readInt(CommandLimitsStorage.KEY_CARBS_MAX_GRAMS)
        val bolus = storage.readInt(CommandLimitsStorage.KEY_BOLUS_MAX_CENTI_UNITS)
        val interval = storage.readInt(CommandLimitsStorage.KEY_BOLUS_MIN_INTERVAL_MINUTES)
        if (carbs == null && bolus == null && interval == null) return CommandLimitsState.Loaded(CommandLimits.DEFAULT)
        // Zapis zawsze zapisuje trzy klucze naraz, więc brak części kluczy oznacza uszkodzenie.
        if (carbs == null || bolus == null || interval == null) return CommandLimitsState.LoadFailed
        return when (val v = CommandLimits.of(carbs, bolus, interval)) {
            is LimitsValidation.Valid -> CommandLimitsState.Loaded(v.limits)
            is LimitsValidation.Invalid -> CommandLimitsState.LoadFailed
        }
    }

    override suspend fun save(limits: CommandLimits): SaveLimitsResult = mutex.withLock {
        try {
            withContext(dispatcher) {
                if (storage.writeAll(limits.carbsMaxGrams, limits.bolusMaxCentiUnits, limits.bolusMinIntervalMinutes)) {
                    // Przed powrotem z withContext: anulowanie po zapisie nie rozjeżdża stanu z plikiem.
                    _state.value = CommandLimitsState.Loaded(limits)
                    SaveLimitsResult.Saved
                } else {
                    SaveLimitsResult.Failed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SaveLimitsResult.Failed
        }
    }
}
