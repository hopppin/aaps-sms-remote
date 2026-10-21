package pl.twojanazwa.aapsremote.data

/** Blokujący; wołany wyłącznie na @DataDispatcher, pod blokadą (DATA-06). Może rzucać. */
interface SettingsStorage {
    fun readAapsNumber(): String?

    /** Wynik commit(). */
    fun writeAapsNumber(value: String): Boolean

    /** Wynik commit(). */
    fun removeAapsNumber(): Boolean
}
