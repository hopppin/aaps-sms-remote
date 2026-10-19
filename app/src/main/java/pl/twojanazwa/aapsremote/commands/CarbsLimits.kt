package pl.twojanazwa.aapsremote.commands

/** Stałe dla ilości węglowodanów (CMD-03, CMD-05, CMD-06). */
object CarbsLimits {
    const val MIN_GRAMS: Int = 1
    const val MAX_GRAMS: Int = 50

    /** Ilość > tej wartości wymaga dodatkowego potwierdzenia (CMD-05). */
    const val EXTRA_CONFIRMATION_ABOVE_GRAMS: Int = 40
    val QUICK_VALUES: List<Int> = listOf(10, 20, 30)
}
