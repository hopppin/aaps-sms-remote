package pl.twojanazwa.aapsremote.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class UiManifestTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val toolsNs = "http://schemas.android.com/tools"

    private fun manifest(): Document {
        val file = File("src/main/AndroidManifest.xml")
        check(file.exists()) { "Brak manifestu: ${file.absolutePath}" }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(file)
    }

    private fun permissionElements(doc: Document): List<Element> {
        val nodes = doc.getElementsByTagName("uses-permission")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun `UI-22 manifest zawiera CAMERA i uses-feature camera any bez wymagalnosci`() {
        val doc = manifest()
        val names = permissionElements(doc).map { it.getAttributeNS(androidNs, "name") }
        assertTrue("android.permission.CAMERA" in names)
        val features = doc.getElementsByTagName("uses-feature")
        val camera = (0 until features.length).map { features.item(it) as Element }
            .single { it.getAttributeNS(androidNs, "name") == "android.hardware.camera.any" }
        assertEquals("false", camera.getAttributeNS(androidNs, "required"))
    }

    @Test
    fun `UI-22 allowBackup wylaczone, bez receiver i service`() {
        val doc = manifest()
        val app = doc.getElementsByTagName("application").item(0) as Element
        assertEquals("false", app.getAttributeNS(androidNs, "allowBackup"))
        assertEquals(0, doc.getElementsByTagName("receiver").length)
        assertEquals(0, doc.getElementsByTagName("service").length)
    }

    @Test
    fun `UI-22 INTERNET i ACCESS_NETWORK_STATE tylko z tools node remove`() {
        val perms = permissionElements(manifest())
        listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE").forEach { name ->
            val entries = perms.filter { it.getAttributeNS(androidNs, "name") == name }
            assertTrue(entries.isNotEmpty(), "Brak wpisu usuwającego: $name")
            entries.forEach {
                assertEquals("remove", it.getAttributeNS(toolsNs, "node"), "Wpis bez tools:node=remove: $name")
            }
        }
    }
}
