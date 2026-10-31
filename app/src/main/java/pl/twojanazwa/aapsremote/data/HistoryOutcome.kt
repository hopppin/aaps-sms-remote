package pl.twojanazwa.aapsremote.data

import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.UnknownReason

/** Rodzaj wyniku polecenia we wpisie historii (DATA-07, DATA-09). */
sealed interface HistoryOutcome {
    data object Completed : HistoryOutcome
    data object CarbsConfirmed : HistoryOutcome
    data object BolusConfirmed : HistoryOutcome
    data object ProfileSwitchConfirmed : HistoryOutcome

    /** reason == null: nieznany albo brakujący powód w wierszu (DATA-09). */
    data class Failed(val reason: FailureReason?) : HistoryOutcome
    data class TimedOut(val phase: SessionPhase?) : HistoryOutcome
    data class Unknown(val reason: UnknownReason?) : HistoryOutcome
    data object Cancelled : HistoryOutcome

    /** Nieznany tekst rodzaju wyniku (DATA-09). */
    data object Unrecognized : HistoryOutcome
}
