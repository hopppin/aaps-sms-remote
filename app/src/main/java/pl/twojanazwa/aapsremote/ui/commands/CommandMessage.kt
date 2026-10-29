package pl.twojanazwa.aapsremote.ui.commands

/** Komunikat panelu poleceń (CMDUI-12); [opensSettings] = komunikat ma akcję „Ustawienia”. */
enum class CommandMessage(val opensSettings: Boolean) {
    COMMAND_ACTIVE(false),
    NUMBER_LOADING(false),
    NUMBER_MISSING(true),
    SMS_PERMISSIONS_MISSING(false),
    SECRET_ABSENT(true),
    SECRET_INVALIDATED(true),
    SECRET_LOCKED(false),
    SECRET_UNAVAILABLE(false),
    START_FAILED(false),
    LIMITS_UNAVAILABLE(true),
    AMOUNT_ABOVE_LIMIT(false),
    EXTRA_CONFIRMATION_REQUIRED(false),
    BOLUS_INTERVAL(false),
    BOLUS_HISTORY_UNAVAILABLE(false),
}

/** Odwzorowanie 1:1 po nazwie (CMDUI-12). */
fun LaunchRejection.toMessage(): CommandMessage = when (this) {
    LaunchRejection.COMMAND_ACTIVE -> CommandMessage.COMMAND_ACTIVE
    LaunchRejection.NUMBER_LOADING -> CommandMessage.NUMBER_LOADING
    LaunchRejection.NUMBER_MISSING -> CommandMessage.NUMBER_MISSING
    LaunchRejection.SMS_PERMISSIONS_MISSING -> CommandMessage.SMS_PERMISSIONS_MISSING
    LaunchRejection.SECRET_ABSENT -> CommandMessage.SECRET_ABSENT
    LaunchRejection.SECRET_INVALIDATED -> CommandMessage.SECRET_INVALIDATED
    LaunchRejection.SECRET_LOCKED -> CommandMessage.SECRET_LOCKED
    LaunchRejection.SECRET_UNAVAILABLE -> CommandMessage.SECRET_UNAVAILABLE
    LaunchRejection.START_FAILED -> CommandMessage.START_FAILED
    LaunchRejection.LIMITS_UNAVAILABLE -> CommandMessage.LIMITS_UNAVAILABLE
    LaunchRejection.AMOUNT_ABOVE_LIMIT -> CommandMessage.AMOUNT_ABOVE_LIMIT
    LaunchRejection.EXTRA_CONFIRMATION_REQUIRED -> CommandMessage.EXTRA_CONFIRMATION_REQUIRED
    LaunchRejection.BOLUS_INTERVAL -> CommandMessage.BOLUS_INTERVAL
    LaunchRejection.BOLUS_HISTORY_UNAVAILABLE -> CommandMessage.BOLUS_HISTORY_UNAVAILABLE
}
