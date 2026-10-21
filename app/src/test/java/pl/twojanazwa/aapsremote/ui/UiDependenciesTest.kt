package pl.twojanazwa.aapsremote.ui

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class UiDependenciesTest {

    private val catalog: String
        get() {
            val file = File("../gradle/libs.versions.toml")
            check(file.exists()) { "Brak katalogu wersji: ${file.absolutePath}" }
            return file.readText()
        }

    @Test
    fun `UI-22 ML Kit to artefakt z modelem w paczce, nie wariant Google Play`() {
        val text = catalog
        assertTrue("com.google.mlkit:barcode-scanning" in text)
        assertTrue("play-services-mlkit-barcode-scanning" !in text)
        assertTrue("com.google.android.gms" !in text)
    }
}
