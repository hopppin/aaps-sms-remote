package pl.twojanazwa.aapsremote.ui.history

enum class HistoryCommandLabel {
    BG, PUMP, CARBS, CARBS_NO_AMOUNT, BOLUS, BOLUS_NO_AMOUNT,
    PROFILE_STATUS, PROFILE_LIST, PROFILE_SWITCH, PROFILE_SWITCH_NO_DETAILS,
    UNRECOGNIZED,
}

enum class HistoryTimeLabel { STARTED, COMMAND_SENT, CODE_SENT, FINISHED }

/** W tym pliku, żeby zadania ViewModelu i tekstów mogły iść równolegle. */
enum class HistoryMessage { CLEARED, CLEAR_FAILED }
