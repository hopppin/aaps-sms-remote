package pl.twojanazwa.aapsremote.ui.settings.secret

/** Port schowka systemowego. Implementacja: ui.platform.AndroidSecretClipboard. Nie loguje, nie przechowuje tekstu. */
interface SecretClipboard {
    /** Tekst pierwszego elementu bieżącego schowka; null, gdy brak, brak dostępu albo wyjątek. */
    fun currentText(): String?

    /** Czyści schowek; wyjątki są połykane (bez logowania). */
    fun clear()
}

/** Czysta logika (UI-24): czyści schowek tylko, gdy [pasted] jest niepusty i schowek zawiera dokładnie ten tekst. */
internal fun clearClipboardIfPasted(clipboard: SecretClipboard, pasted: String) {
    if (pasted.isEmpty()) return
    val current = try {
        clipboard.currentText()
    } catch (e: RuntimeException) {
        null
    }
    if (current == pasted) clipboard.clear()
}
