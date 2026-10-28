package pl.twojanazwa.aapsremote.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CMDUI-23: licznik żądań FLAG_SECURE w jednym Activity. [onChange] dostaje `true` przy przejściu 0 -> 1
 * i `false` przy 1 -> 0. Zdjęcie ochrony przez jedno źródło nie odsłania okna, dopóki chroni je inne.
 * Wywoływany z wątku głównego (kompozycja).
 */
class SecureWindowCounter(private val onChange: (Boolean) -> Unit) {
    private var count = 0

    fun acquire() {
        count++
        if (count == 1) onChange(true)
    }

    /** Przy liczniku 0 bez efektu (nie schodzi poniżej zera). */
    fun release() {
        if (count == 0) return
        count--
        if (count == 0) onChange(false)
    }
}

val LocalSecureWindow: ProvidableCompositionLocal<SecureWindowCounter> =
    // Domyślnie licznik bez skutku (podgląd/test poza AppScaffold); AppScaffold podstawia prawdziwy.
    staticCompositionLocalOf { SecureWindowCounter { } }
