package pl.twojanazwa.aapsremote.security

import pl.twojanazwa.aapsremote.otp.TotpSecret

sealed interface LoadResult {
    /** Sekret należy do wywołującego (SECRET-14). */
    class Loaded(val secret: TotpSecret) : LoadResult {
        override fun toString(): String = "Loaded(***)"
    }

    /** Brak rekordu – „zeskanuj QR z AAPS”. */
    data object Absent : LoadResult

    /** Wyczyszczono po trwałym błędzie – „zeskanuj ponownie QR z AAPS” (SECRET-08). */
    data object Invalidated : LoadResult

    /** Telefon zablokowany – „odblokuj telefon”, nic nie usunięto (SECRET-09). */
    data object Locked : LoadResult

    /** Inny błąd – „spróbuj ponownie”, nic nie usunięto (SECRET-10). */
    data object Unavailable : LoadResult
}
