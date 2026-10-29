package pl.twojanazwa.aapsremote.commands

/** Zakresy limitów poleceń (LIM-02). Bolus w setnych jednostki. */
object LimitRanges {
    const val CARBS_MAX_MIN = 10
    const val CARBS_MAX_MAX = 100
    const val BOLUS_MAX_MIN = 5
    const val BOLUS_MAX_MAX = 1000
    const val BOLUS_STEP = 5
    const val INTERVAL_MIN = 0
    const val INTERVAL_MAX = 60
}
