package pl.twojanazwa.aapsremote.security

import javax.crypto.SecretKey

/** Klucz w AndroidKeyStore pod stałym aliasem (SECRET-01). Implementacja w warstwie platformy. */
interface SecretKeySource {
    /** Uchwyt klucza albo null, gdy aliasu brak. Może rzucić. */
    fun getKey(): SecretKey?

    /** Generuje klucz wg SECRET-01/SECRET-02 (przez [KeyGenerationPolicy]). Rzuca przy błędzie. */
    fun generateKey(): SecretKey

    /** Usuwa alias; brak aliasu to nie błąd. Może rzucić. */
    fun deleteKey()
}
