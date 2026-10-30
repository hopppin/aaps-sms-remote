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
import pl.twojanazwa.aapsremote.security.TotpSecretStore
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
    private val profileSelection: ProfileSelection,
    private val secrets: TotpSecretStore,
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
            secrets.state.collect { secret ->
                mutableState.update { it.copy(secret = secret.toReadiness()) }
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
                    commandCentiUnits = (run.command as? Command.Bolus)?.centiUnits,
                    profileNumber = (run.command as? Command.ProfileSwitch)?.number,
                    profilePercent = (run.command as? Command.ProfileSwitch)?.percent,
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

    /** PROF-14: wybór pozycji listy zamyka wynik i przekazuje wybór do panelu; nieaktywny przy ABSENT / INVALIDATED. */
    fun onProfileChosen(number: Int) {
        // Bieżący stan sekretu, nie kolektor (ten może jeszcze nie zdążyć zaktualizować uiState).
        val secret = secrets.state.value.toReadiness()
        if (secret == SecretReadiness.ABSENT || secret == SecretReadiness.INVALIDATED) return
        val item = (mutableState.value.view as? RunView.Finished)?.result?.profileItems?.firstOrNull { it.number == number } ?: return
        profileSelection.select(item)
        onResultDismissed()
    }

    /** Przebieg, dla którego otwarto okno „Przestać czekać?”; potwierdzenie anuluje tylko jego sesję. */
    private var stopWaitingRun: CurrentRunData? = null

    /** CMDUI-18: tryb z bieżącej fazy `activeSession` (widok może się spóźniać); przed kodem `cancel()`, po kodzie okno. */
    fun onCancelClicked() {
        val session = coordinator.activeSession.value ?: return
        when (session.state.value.phase.cancelMode()) {
            CancelMode.CANCEL -> session.cancel()
            CancelMode.STOP_WAITING -> {
                stopWaitingRun = currentRun.state.value
                mutableState.update { it.copy(confirmingStopWaiting = true) }
            }
            CancelMode.NONE -> Unit
        }
    }

    fun onStopWaitingConfirmed() {
        mutableState.update { it.copy(confirmingStopWaiting = false) }
        val opened = stopWaitingRun
        stopWaitingRun = null
        val session = coordinator.activeSession.value ?: return
        // Inna sesja niż ta, dla której otwarto okno, nie jest anulowana.
        if (opened != null && opened.state === session.state) session.cancel()
    }

    fun onStopWaitingDismissed() {
        stopWaitingRun = null
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
