package pl.twojanazwa.aapsremote.parser

/** Odczyt przykładowych odpowiedzi AAPS 3.4.2.6 (pl) z zasobów testowych, bez zmian. */
object AapsSamples {
    private const val DIR = "parser/aaps-3.4.2.6-pl/"

    val bg: String get() = read("bg.txt")
    val pump: String get() = read("pump.txt")
    val carbsCodeRequest: String get() = read("carbs-prosba-o-kod.txt")
    val carbsDone: String get() = read("carbs-sukces.txt")
    val bolusCodeRequest: String get() = read("bolus-prosba-o-kod-0-10.txt")
    val bolusCodeRequestTruncated: String get() = read("bolus-prosba-o-kod-obcieta-5-00.txt")
    val bolusDone: String get() = read("bolus-sukces-1-20.txt")
    val wrongCode: String get() = read("zly-kod.txt")
    val profileStatus: String get() = read("profile-status.txt")
    val profileList: String get() = read("profile-list.txt")
    val profileCodeRequest: String get() = read("profile-prosba-o-kod.txt")
    val profileBadFormat: String get() = read("profile-bledny-format.txt")

    fun read(name: String): String {
        val url = checkNotNull(AapsSamples::class.java.classLoader?.getResource(DIR + name)) {
            "missing test resource"
        }
        return url.readText(Charsets.UTF_8)
    }
}
