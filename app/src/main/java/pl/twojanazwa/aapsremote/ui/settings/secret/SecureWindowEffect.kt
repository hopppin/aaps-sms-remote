package pl.twojanazwa.aapsremote.ui.settings.secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import pl.twojanazwa.aapsremote.ui.platform.LocalSecureWindow

/**
 * UI-23, CMDUI-23: zgłasza do licznika okna żądanie FLAG_SECURE, gdy [secure] = true; zdejmuje je przy false
 * i przy usunięciu z kompozycji. Flagę ustawia licznik w `AppScaffold`, gdy chroni ją choć jedno źródło.
 * Okno wklejania zabezpiecza się osobno (`SecureFlagPolicy.SecureOn`).
 */
@Composable
fun SecureWindowEffect(secure: Boolean) {
    val counter = LocalSecureWindow.current
    DisposableEffect(counter, secure) {
        if (secure) counter.acquire()
        onDispose { if (secure) counter.release() }
    }
}
