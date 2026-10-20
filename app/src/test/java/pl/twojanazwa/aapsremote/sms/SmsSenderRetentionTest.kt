package pl.twojanazwa.aapsremote.sms

import java.lang.reflect.Modifier
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.session.OutgoingSms
import pl.twojanazwa.aapsremote.session.SendResult

class SmsSenderRetentionTest {
    @Test
    fun `SMS-13 pola AapsSmsSender sa niezmienne i nie przechowuja tresci`() {
        val fields = AapsSmsSender::class.java.declaredFields.filter { !it.isSynthetic }
        assertTrue(fields.isNotEmpty())
        for (field in fields) {
            val type = field.type
            assertTrue(Modifier.isFinal(field.modifiers), "pole nie jest final")
            assertFalse(type == String::class.java, "pole typu String")
            assertFalse(type == CharArray::class.java, "pole typu CharArray")
            assertFalse(type.isArray, "pole tablicowe")
            assertFalse(CharSequence::class.java.isAssignableFrom(type), "pole CharSequence")
            assertFalse(Collection::class.java.isAssignableFrom(type), "pole kolekcji")
            assertFalse(Map::class.java.isAssignableFrom(type), "pole mapy")
        }
    }

    @Test
    fun `SMS-13 CodeReply i wyjatek warstwy systemowej daje Failed bez kodu w toString`() = runTest {
        val system = FakeSmsSystemApi().apply { sendPartsException = SecurityException("x") }
        val sender = AapsSmsSender({ "+447700900123" }, FakeSmsPermissions(), system)
        val result = sender.send(OutgoingSms.CodeReply("0504714321".toCharArray()))
        assertEquals(SendResult.Failed, result)
        for (text in listOf(sender.toString(), system.toString())) {
            assertFalse(text.contains("050471"))
            assertFalse(text.contains("4321"))
        }
    }

    @Test
    fun `SMS-16 toString AapsSmsSender nie zawiera numeru ani tresci`() {
        val sender = AapsSmsSender({ "+447700900123" }, FakeSmsPermissions(), FakeSmsSystemApi())
        val text = sender.toString()
        assertFalse(text.contains("7700900123"))
        assertFalse(text.contains("BG"))
    }
}
