package pl.twojanazwa.aapsremote.parser

/** Wzorce odpowiedzi AAPS 3.4.2.6, język polski. Zawsze używać matchEntire (PARSER-09). */
internal object AapsPl3426Patterns {
    private const val D = """-?[0-9]+(?:[.,][0-9]+)?"""

    val BG_LINE = Regex(
        """BG: ([0-9]{1,3}), Delta: $D MGDL, IOB: ${D}U \(Bolus: ${D}U Baza: ${D}U\), COB: ${D}g""",
    )

    val PUMP_FIRST_LINE = Regex("""Ost\. Pol\.: [0-9]{1,5} m\. temu""")
}
