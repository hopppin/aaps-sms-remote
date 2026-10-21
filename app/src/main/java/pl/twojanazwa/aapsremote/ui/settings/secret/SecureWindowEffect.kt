package pl.twojanazwa.aapsremote.ui.settings.secret

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * UI-23: dodaje FLAG_SECURE do okna Activity, gdy [secure] = true; zdejmuje przy false
 * i przy usunięciu z kompozycji. Okno wklejania zabezpiecza się osobno (`SecureFlagPolicy.SecureOn`).
 */
@Composable
fun SecureWindowEffect(secure: Boolean) {
    val window = LocalActivity.current?.window
    DisposableEffect(window, secure) {
        if (secure) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
