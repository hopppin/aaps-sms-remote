package pl.twojanazwa.aapsremote.otp

/** Milisekundy od epoki Unix. Produkcyjnie: System.currentTimeMillis(). */
fun interface EpochClock {
    fun nowEpochMillis(): Long
}
