package pl.twojanazwa.aapsremote.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** CMDUI-22: utrzymuje ekran włączony tylko podczas aktywnego polecenia. */
@Composable
fun KeepScreenOnEffect(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}
