package pl.twojanazwa.aapsremote.ui.settings.number

/** Błąd pola numeru (UI-05). */
enum class NumberInputError { INVALID }

/** Jednorazowy komunikat sekcji numeru; tekst dobiera warstwa widoku (UI-20). */
enum class NumberMessage { SAVED, SAVE_FAILED, CLEARED, CLEAR_FAILED, COMMAND_ACTIVE }
