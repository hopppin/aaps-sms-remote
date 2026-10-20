package pl.twojanazwa.aapsremote.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Katalog roboczy testów to moduł `app`. */
class SecretBackupRulesTest {

    private fun parse(path: String): Document {
        val file = File(path)
        assertTrue(file.isFile, "Brak pliku: $path")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    @Test
    fun `SECRET-16 manifest wylacza kopie zapasowa i wskazuje reguly ekstrakcji`() {
        val app = parse("src/main/AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        assertEquals("false", app.getAttribute("android:allowBackup"))
        assertEquals("false", app.getAttribute("android:fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttribute("android:dataExtractionRules"))
    }

    @Test
    fun `SECRET-16 reguly wykluczaja sharedpref z kopii w chmurze i transferu urzadzenia`() {
        val doc = parse("src/main/res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val nodes = doc.getElementsByTagName(section)
            assertEquals(1, nodes.length, "Sekcja $section")
            val excludes = (nodes.item(0) as Element).getElementsByTagName("exclude")
            val domains = (0 until excludes.length).map { (excludes.item(it) as Element).getAttribute("domain") }
            assertTrue("sharedpref" in domains, "Sekcja $section nie wyklucza sharedpref")
        }
    }

    @Test
    fun `SECRET-16 projekt nie uzywa security-crypto`() {
        for (path in listOf("../gradle/libs.versions.toml", "build.gradle.kts")) {
            val file = File(path)
            assertTrue(file.isFile, "Brak pliku: $path")
            assertFalse(file.readText().contains("security-crypto"), "$path zawiera security-crypto")
        }
    }
}
