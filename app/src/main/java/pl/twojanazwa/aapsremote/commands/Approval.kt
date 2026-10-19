package pl.twojanazwa.aapsremote.commands

/** Sposób zatwierdzenia przez użytkownika (CMD-02). */
enum class Approval {
    /** Wysłanie od razu po wybraniu, bez ekranu potwierdzenia. */
    ONE_TAP,

    /** Ekran podsumowania i PIN AAPS przed wysłaniem. */
    SUMMARY_WITH_PIN,
}
