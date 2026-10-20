package pl.twojanazwa.aapsremote.sms

/** Atrapa rejestratora: liczniki wywołań i przechwycona funkcja obsługi broadcastu. */
class FakeSmsBroadcastRegistrar : SmsBroadcastRegistrar {
    var registerCount = 0
        private set
    var unregisterCount = 0
        private set
    var isRegistered = false
        private set
    private var handler: ((List<RawSmsPart>) -> Unit)? = null

    override fun register(onBroadcast: (List<RawSmsPart>) -> Unit) {
        registerCount++
        handler = onBroadcast
        isRegistered = true
    }

    override fun unregister() {
        unregisterCount++
        handler = null
        isRegistered = false
    }

    /** Symuluje broadcast; bez zarejestrowanej funkcji nic nie robi. */
    fun deliver(vararg parts: RawSmsPart) {
        handler?.invoke(parts.toList())
    }
}
