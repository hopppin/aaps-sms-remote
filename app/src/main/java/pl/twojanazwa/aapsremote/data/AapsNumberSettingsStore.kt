package pl.twojanazwa.aapsremote.data

import kotlinx.coroutines.flow.StateFlow

interface AapsNumberSettingsStore {
    val state: StateFlow<AapsNumberState>

    /** DATA-03. Nie rzuca poza CancellationException (DATA-15). */
    suspend fun save(raw: String): SaveNumberResult

    /** DATA-04. Nie rzuca poza CancellationException (DATA-15). */
    suspend fun clear(): ClearNumberResult
}
