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

    val CARBS_DONE_FIRST_LINE = Regex("""Pomyslnie wprowadzono $G g weglowodanow""")

    // Ilość bolusa: tylko przecinek i dokładnie 2 miejsca (spec 012, BOL-07).
    private const val U = """(0|[1-9][0-9]{0,2}),([0-9]{2})"""

    val BOLUS_CODE_REQUEST = Regex(
        """Aby dostarczyc bolus ${U}U wprowadz kod z aplikacji Authenticator dla: [^\n]+, z doklejonym PIN-em""",
    )

    val BOLUS_DONE_FIRST_LINE = Regex("""Bolus $U U podany prawidlowo""")

    // Spec 013: profil. Nazwa to dowolny tekst bez LF; procent 1-9999.
    val PROFILE_STATUS = Regex("""([^\n]+) \(([1-9][0-9]{0,3})%\)""")

    val PROFILE_LIST_LINE = Regex("""([1-9][0-9]?)\. ([^\n]+)""")

    val PROFILE_CODE_REQUEST = Regex(
        """Aby przelaczyc profil na ([^\n]+) ([1-9][0-9]{0,3})% odpowiedz kodem z aplikacji Authenticator dla: [^\n]+, z doklejonym PIN-em""",
    )

    val WRONG_CODE = Regex("""Zly kod\. Polecenie anulowano\.""")
}
