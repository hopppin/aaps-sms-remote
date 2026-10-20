package pl.twojanazwa.aapsremote.session

import pl.twojanazwa.aapsremote.otp.TotpSecret
import pl.twojanazwa.aapsremote.security.AapsPin

/** SESSION-02. Wymagane dokładnie dla command.definition.requiresCode == true. */
class CodeCredentials(val pin: AapsPin, val secret: TotpSecret) {
    override fun toString(): String = "CodeCredentials(***)"
}
