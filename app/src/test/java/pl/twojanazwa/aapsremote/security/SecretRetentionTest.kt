package pl.twojanazwa.aapsremote.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpSecret
import java.lang.reflect.Modifier
import javax.crypto.Cipher

class SecretRetentionTest {

    @Test
    fun `SECRET-14 pola AesGcmSecretCipher sa final i nie przechowuja danych`() {
        val forbidden = listOf(
            ByteArray::class.java, CharArray::class.java, String::class.java, CharSequence::class.java,
            TotpSecret::class.java, SealedSecret::class.java, Cipher::class.java,
            java.util.Collection::class.java, java.util.Map::class.java,
        )
        val fields = AesGcmSecretCipher::class.java.declaredFields.filterNot { it.isSynthetic }
        assertTrue(fields.isNotEmpty())
        for (field in fields) {
            assertTrue(Modifier.isFinal(field.modifiers), "pole nie jest final")
            assertFalse(field.type.isArray, "pole jest tablica")
            assertFalse(forbidden.any { it.isAssignableFrom(field.type) }, "pole zabronionego typu")
        }
        assertEquals(
            setOf(SecretKeySource::class.java, KeystoreEnvironment::class.java),
            fields.map { it.type }.filterNot { it.isPrimitive }.toSet(), // bez prymitywow (np. $stable z wtyczki Compose)
        )
    }
}
