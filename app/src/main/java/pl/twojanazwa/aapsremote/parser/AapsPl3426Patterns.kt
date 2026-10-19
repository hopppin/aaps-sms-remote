package pl.twojanazwa.aapsremote.parser

/** Wzorce odpowiedzi AAPS 3.4.2.6, język polski. Zawsze używać matchEntire (PARSER-09). */
internal object AapsPl3426Patterns {
    private const val G = """(0|[1-9][0-9]{0,3})"""
    private const val H = """(?:[01]?[0-9]|2[0-3]):[0-5][0-9]"""
    private const val D = """-?[0-9]+(?:[.,][0-9]+)?"""

    val BG_LINE = Regex(
        """BG: ([0-9]{1,3}), Delta: $D MGDL, IOB: ${D}U \(Bolus: ${D}U Baza: ${D}U\), COB: ${D}g""",
    )

    val PUMP_FIRST_LINE = Regex("""Ost\. Pol\.: [0-9]{1,5} m\. temu""")

    val CODE_REQUEST = Regex(
        """Aby wprowadzic ${G}g o $H odeslij w SMS kod z aplikacji Authenticator dla: [^\n]+, z doklejonym PIN-em""",
    )

    val WRONG_CODE = Regex("""Zly kod\. Polecenie anulowano\.""")
}
