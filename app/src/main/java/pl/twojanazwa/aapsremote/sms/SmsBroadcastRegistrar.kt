package pl.twojanazwa.aapsremote.sms

/** Cienka warstwa nad Context.registerReceiver dla SMS_RECEIVED. Implementacja: platform. */
interface SmsBroadcastRegistrar {
    /** Rejestruje receiver; [onBroadcast] wołane z onReceive (wątek główny). Może rzucić (np. SecurityException). */
    fun register(onBroadcast: (List<RawSmsPart>) -> Unit)

    /** Wyrejestrowuje; może rzucić IllegalArgumentException, gdy nie zarejestrowano. */
    fun unregister()
}
