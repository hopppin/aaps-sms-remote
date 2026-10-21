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
}
