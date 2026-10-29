package pl.twojanazwa.aapsremote.parser

/** Odczyt przykładowych odpowiedzi AAPS 3.4.2.6 (pl) z zasobów testowych, bez zmian. */
object AapsSamples {
    private const val DIR = "parser/aaps-3.4.2.6-pl/"

    val bg: String get() = read("bg.txt")
    val pump: String get() = read("pump.txt")
    val carbsCodeRequest: String get() = read("carbs-prosba-o-kod.txt")
    val carbsDone: String get() = read("carbs-sukces.txt")
    val bolusCodeRequest: String get() = read("bolus-prosba-o-kod-0-10.txt")
    val wrongCode: String get() = read("zly-kod.txt")

    fun read(name: String): String {
        val url = checkNotNull(AapsSamples::class.java.classLoader?.getResource(DIR + name)) {
            "missing test resource"
        }
        return url.readText(Charsets.UTF_8)
    }
}
