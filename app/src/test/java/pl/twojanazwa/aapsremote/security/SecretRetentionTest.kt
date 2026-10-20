package pl.twojanazwa.aapsremote.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpSecret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `SECRET-14 pola KeystoreTotpSecretStore sa final i nie maja zabronionych typow`() {
        val forbidden = listOf(
            ByteArray::class.java, CharArray::class.java, String::class.java, CharSequence::class.java,
            TotpSecret::class.java, SealedSecret::class.java, Cipher::class.java,
            java.util.Collection::class.java, java.util.Map::class.java,
        )
        val allowed = setOf(
            SecretRecordStorage::class.java, SecretCipher::class.java,
            kotlinx.coroutines.CoroutineDispatcher::class.java,
            kotlinx.coroutines.sync.Mutex::class.java,
            kotlinx.coroutines.flow.MutableStateFlow::class.java,
            kotlinx.coroutines.flow.StateFlow::class.java,
        )
        val fields = KeystoreTotpSecretStore::class.java.declaredFields.filterNot { it.isSynthetic || it.type.isPrimitive }
        assertTrue(fields.isNotEmpty())
        for (field in fields) {
            assertTrue(Modifier.isFinal(field.modifiers), "pole nie jest final")
            assertFalse(field.type.isArray, "pole jest tablica")
            assertFalse(forbidden.any { it.isAssignableFrom(field.type) }, "pole zabronionego typu")
            assertTrue(field.type in allowed, "nieoczekiwany typ pola")
        }
    }

    @Test
    fun `SECRET-14 po save i load pola magazynu i szyfratora nie wskazuja na sekret ani tablice bajtow`() = runBlocking {
        val cipher = AesGcmSecretCipher(FakeSecretKeySource(), FakeKeystoreEnvironment())
        val store = KeystoreTotpSecretStore(InMemorySecretRecordStorage(), cipher, Dispatchers.Unconfined)
        assertEquals(SaveResult.Saved, store.save(TotpSecret.fromBytes("12345678901234567890".toByteArray())))
        val loaded = store.load() as LoadResult.Loaded
        for (owner in listOf<Any>(store, cipher)) {
            for (field in owner.javaClass.declaredFields.filterNot { it.isSynthetic || it.type.isPrimitive }) {
                field.isAccessible = true
                val value = field.get(owner)
                assertFalse(value === loaded.secret, "pole wskazuje na sekret")
                assertFalse(value is ByteArray, "pole jest tablica bajtow")
                assertFalse(value is TotpSecret, "pole jest TotpSecret")
            }
        }
    }
}
