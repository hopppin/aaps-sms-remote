package pl.twojanazwa.aapsremote.security

/** Jeden rekord w pliku SharedPreferences (SECRET-16). Metody blokujące – wołane na dyspozytorze I/O. */
interface SecretRecordStorage {
    /** Może rzucić. */
    fun read(): String?

    /** Wynik commit(); może rzucić. */
    fun write(record: String): Boolean

    /** Wynik commit(); true także przy braku rekordu; może rzucić. */
    fun clear(): Boolean
}
