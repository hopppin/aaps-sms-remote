package pl.twojanazwa.aapsremote.ui.commands

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.session.SessionCoordinator
import pl.twojanazwa.aapsremote.session.SessionPhase
import javax.inject.Inject

/**
 * Przebieg i wynik bieżącego uruchomienia (CMDUI-16, CMDUI-20, CMDUI-21). Źródłem jest [CurrentRun];
 * [coordinator] służy tylko do `activeSession` (ekran włączony). Sesja nie jest polem: [observe]
 * widzi ją jedynie jako parametr.
 */
@HiltViewModel
class CommandRunViewModel @Inject constructor(
    private val currentRun: CurrentRun,
    private val coordinator: SessionCoordinator,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RunUiState())
    val uiState: StateFlow<RunUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            currentRun.state.collectLatest { run ->
                if (run == null) {
                    mutableState.update {
                        it.copy(view = null, confirmingStopWaiting = false, confirmingExit = false)
                    }
                } else {
                    observe(run)
                }
            }
        }
        viewModelScope.launch {
            coordinator.activeSession.collect { session ->
                mutableState.update { it.copy(commandActive = session != null) }
            }
        }
    }

    /** Zbiera stan sesji do FINISHED włącznie (StateFlow zachowuje ostatnią wartość, więc dochodzi do FINISHED zawsze). */
    private suspend fun observe(run: CurrentRunData) {
        run.state.transformWhile { state ->
            emit(state)
            state.phase != SessionPhase.FINISHED
        }.collect { state ->
            val result = state.result
            if (state.phase == SessionPhase.FINISHED && result != null) {
                mutableState.update {
                    it.copy(
                        view = RunView.Finished(ResultMapper.map(result)),
                        confirmingStopWaiting = false,
                        confirmingExit = false,
                    )
                }
            } else {
                val view = RunView.Active(
                    commandId = run.command.id,
                    commandGrams = (run.command as? Command.Carbs)?.grams,
                    steps = ProgressSteps.of(run.command.id, state.phase),
                    cancelMode = state.phase.cancelMode(),
                    unexpectedCount = state.unexpectedMessages.size,
                )
                mutableState.update { it.copy(view = view) }
            }
        }
    }

    /** CMDUI-21: tylko przy wyniku; przy przebiegu bez zmian. */
    fun onResultDismissed() {
        if (mutableState.value.view is RunView.Finished) {
            currentRun.clear()
            mutableState.update { it.copy(view = null) }
        }
    }

    /** CMDUI-18: przed kodem od razu `cancel()`, po kodzie najpierw okno; sesja z `activeSession` w chwili akcji. */
    fun onCancelClicked() {
        when ((mutableState.value.view as? RunView.Active)?.cancelMode) {
            CancelMode.CANCEL -> coordinator.activeSession.value?.cancel()
            CancelMode.STOP_WAITING -> mutableState.update { it.copy(confirmingStopWaiting = true) }
            CancelMode.NONE, null -> Unit
        }
    }

    fun onStopWaitingConfirmed() {
        mutableState.update { it.copy(confirmingStopWaiting = false) }
        coordinator.activeSession.value?.cancel()
    }

    fun onStopWaitingDismissed() {
        mutableState.update { it.copy(confirmingStopWaiting = false) }
    }

    /** CMDUI-24: wynik -> zamknięcie; przebieg -> okno „Trwa polecenie”; inaczej nic. */
    fun onBack() {
        when (mutableState.value.view) {
            is RunView.Finished -> onResultDismissed()
            is RunView.Active -> mutableState.update { it.copy(confirmingExit = true) }
            null -> Unit
        }
    }

    /** „Wyjdź” nie anuluje sesji i nie czyści `CurrentRun`. */
    fun onExitConfirmed() {
        mutableState.update { it.copy(confirmingExit = false, exitRequested = true) }
    }

    fun onExitDismissed() {
        mutableState.update { it.copy(confirmingExit = false) }
    }

    fun onExitHandled() {
        mutableState.update { it.copy(exitRequested = false) }
    }
}
