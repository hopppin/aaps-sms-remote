package pl.twojanazwa.aapsremote.ui.permissions

/** Wynik prośby o uprawnienia (UI-10, UI-18). */
enum class PermissionDenial { NONE, DENIED, PERMANENTLY_DENIED }

object PermissionDenialClassifier {
    /**
     * [missing] – uprawnienia nadal brakujące po wyniku prośby (odczyt po prośbie).
     * Pusta → NONE; którekolwiek z rationale = true → DENIED; inaczej PERMANENTLY_DENIED.
     */
    fun classify(missing: List<String>, shouldShowRationale: (String) -> Boolean): PermissionDenial =
        when {
            missing.isEmpty() -> PermissionDenial.NONE
            missing.any(shouldShowRationale) -> PermissionDenial.DENIED
            else -> PermissionDenial.PERMANENTLY_DENIED
        }
}
