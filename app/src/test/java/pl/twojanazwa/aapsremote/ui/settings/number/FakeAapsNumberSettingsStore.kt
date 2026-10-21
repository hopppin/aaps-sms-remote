package pl.twojanazwa.aapsremote.ui.settings.number

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.twojanazwa.aapsremote.data.AapsNumberSettingsStore
import pl.twojanazwa.aapsremote.data.AapsNumberState
import pl.twojanazwa.aapsremote.data.ClearNumberResult
import pl.twojanazwa.aapsremote.data.SaveNumberResult
import pl.twojanazwa.aapsremote.sms.AapsNumber

/** Atrapa magazynu numeru: stan sterowany z testu, wyniki z kolejki, opcjonalna bramka, rejestr wywołań. */
class FakeAapsNumberSettingsStore(initial: AapsNumberState = AapsNumberState.Loading) : AapsNumberSettingsStore {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<AapsNumberState> = mutableState

    val saveResults = ArrayDeque<SaveNumberResult>()
    val clearResults = ArrayDeque<ClearNumberResult>()
    val saveCalls = mutableListOf<String>()
    var clearCalls = 0
        private set

    /** Gdy ustawiona, save/clear czekają na jej ukończenie. */
    var gate: CompletableDeferred<Unit>? = null

    fun setState(value: AapsNumberState) {
        mutableState.value = value
    }

    fun setConfigured(destination: String) {
        mutableState.value = AapsNumberState.Configured(requireNotNull(AapsNumber.parse(destination)))
    }

    override suspend fun save(raw: String): SaveNumberResult {
        saveCalls += raw
        gate?.await()
        return saveResults.removeFirstOrNull() ?: SaveNumberResult.Saved
    }

    override suspend fun clear(): ClearNumberResult {
        clearCalls++
        gate?.await()
        return clearResults.removeFirstOrNull() ?: ClearNumberResult.Cleared
    }
}
