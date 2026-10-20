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

    /** Jeśli ustawiony, [register] rzuca ten wyjątek (symulacja np. SecurityException). */
    var registerException: Exception? = null

    override fun register(onBroadcast: (List<RawSmsPart>) -> Unit) {
        registerException?.let { throw it }
        registerCount++
        handler = onBroadcast
        isRegistered = true
    }

    /** Jeśli ustawiony, [unregister] rzuca ten wyjątek (po zliczeniu wywołania). */
    var unregisterException: Exception? = null

    override fun unregister() {
        unregisterCount++
        unregisterException?.let { throw it }
        handler = null
        isRegistered = false
    }

    /** Symuluje broadcast; bez zarejestrowanej funkcji nic nie robi. */
    fun deliver(vararg parts: RawSmsPart) {
        handler?.invoke(parts.toList())
    }
}
