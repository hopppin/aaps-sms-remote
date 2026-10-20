package pl.twojanazwa.aapsremote.security

import kotlinx.coroutines.flow.StateFlow
import pl.twojanazwa.aapsremote.otp.TotpSecret

/** Port dla warstwy składającej CodeCredentials (ui/ViewModel). */
fun interface TotpSecretProvider {
    /** SECRET-06. Nie rzuca poza CancellationException. */
    suspend fun load(): LoadResult
}

/** Magazyn jednego sekretu. Implementacja: KeystoreTotpSecretStore. */
interface TotpSecretStore : TotpSecretProvider {
    /** SECRET-12. Początkowo UNKNOWN. */
    val state: StateFlow<SecretState>

    /** SECRET-04, SECRET-05, SECRET-08. Nie modyfikuje [secret]. */
    suspend fun save(secret: TotpSecret): SaveResult

    /** SECRET-11. */
    suspend fun delete(): DeleteResult

    /** SECRET-12. Tylko obecność rekordu, bez odszyfrowania. */
    suspend fun refreshState(): SecretState
}
