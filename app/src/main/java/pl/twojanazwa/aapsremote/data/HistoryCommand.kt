package pl.twojanazwa.aapsremote.data

/** Polecenie we wpisie historii (DATA-07). UNRECOGNIZED: nieznany tekst w wierszu (DATA-09). */
enum class HistoryCommand { BG, PUMP, CARBS, BOLUS, PROFILE_STATUS, PROFILE_LIST, PROFILE_SWITCH, UNRECOGNIZED }
