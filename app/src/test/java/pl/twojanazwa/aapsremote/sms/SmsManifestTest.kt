package pl.twojanazwa.aapsremote.sms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SmsManifestTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun manifest(): Document {
        val file = File("src/main/AndroidManifest.xml")
        check(file.exists()) { "Brak manifestu: ${file.absolutePath}" }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(file)
    }

    private fun permissions(doc: Document): Set<String> {
        val nodes = doc.getElementsByTagName("uses-permission")
        return (0 until nodes.length)
            .map { (nodes.item(it) as org.w3c.dom.Element).getAttributeNS(androidNs, "name") }
            .toSet()
    }

    @Test
    fun `SMS-15 manifest zawiera SEND_SMS i RECEIVE_SMS`() {
        val perms = permissions(manifest())
        assertTrue("android.permission.SEND_SMS" in perms)
        assertTrue("android.permission.RECEIVE_SMS" in perms)
    }

    @Test
    fun `SMS-15 manifest nie zawiera zbednych uprawnien SMS`() {
        val perms = permissions(manifest())
        val forbidden =
            listOf("READ_SMS", "RECEIVE_MMS", "RECEIVE_WAP_PUSH", "BROADCAST_SMS", "WRITE_SMS")
                .map { "android.permission.$it" }
        forbidden.forEach { assertTrue(it !in perms, "Niedozwolone uprawnienie: $it") }
    }

    @Test
    fun `SMS-15 manifest nie deklaruje receiver ani service`() {
        val doc = manifest()
        assertEquals(0, doc.getElementsByTagName("receiver").length)
        assertEquals(0, doc.getElementsByTagName("service").length)
    }

    @Test
    fun `SMS-15 allowBackup jest wylaczone`() {
        val app = manifest().getElementsByTagName("application").item(0) as org.w3c.dom.Element
        assertEquals("false", app.getAttributeNS(androidNs, "allowBackup"))
    }
}
