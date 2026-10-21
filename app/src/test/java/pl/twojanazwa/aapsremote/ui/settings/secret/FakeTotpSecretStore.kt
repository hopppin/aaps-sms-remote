package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.twojanazwa.aapsremote.otp.TotpSecret
import pl.twojanazwa.aapsremote.security.DeleteResult
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.security.SaveResult
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.security.TotpSecretStore

/** Atrapa magazynu: stan sterowany z testu, wyniki z kolejek, opcjonalna bramka na `save`/`delete`. */
class FakeTotpSecretStore(initial: SecretState = SecretState.UNKNOWN) : TotpSecretStore {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<SecretState> = mutableState

    val saveResults = ArrayDeque<SaveResult>()
    val deleteResults = ArrayDeque<DeleteResult>()

    /** Kolejne wyniki `load()`; pusta kolejka = `Absent`. */
    val loadResults = ArrayDeque<LoadResult>()

    /** Gdy ustawiona, `load()` czeka na jej zakończenie (po zliczeniu wywołania). */
    var loadGate: CompletableDeferred<Unit>? = null

    /** Gdy true, oczekiwanie na [loadGate] nie reaguje na anulowanie wywołującego. */
    var loadIgnoresCancellation = false

    /** Gdy ustawiona, `save`/`delete` czekają na jej zakończenie. */
    var gate: CompletableDeferred<Unit>? = null

    var savedSecret: TotpSecret? = null
        private set
    var refreshCount = 0
        private set
    var saveCount = 0
        private set
    var loadCount = 0
        private set
    var deleteCount = 0
        private set

    fun setState(value: SecretState) {
        mutableState.value = value
    }

    override suspend fun save(secret: TotpSecret): SaveResult {
        saveCount++
        savedSecret = secret
        gate?.await()
        return saveResults.removeFirstOrNull() ?: SaveResult.Saved
    }

    override suspend fun delete(): DeleteResult {
        deleteCount++
        gate?.await()
        return deleteResults.removeFirstOrNull() ?: DeleteResult.Deleted
    }

    override suspend fun refreshState(): SecretState {
        refreshCount++
        return mutableState.value
    }

    override suspend fun load(): LoadResult {
        loadCount++
        val gate = loadGate
        if (gate != null) {
            // Nieprzerywalne: load wraca z wynikiem mimo anulowania wywołującego (test strażnika ensureActive).
            if (loadIgnoresCancellation) withContext(NonCancellable) { gate.await() } else gate.await()
        }
        return loadResults.removeFirstOrNull() ?: LoadResult.Absent
    }
}
