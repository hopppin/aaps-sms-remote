package pl.twojanazwa.aapsremote.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.ProviderException
import kotlin.coroutines.cancellation.CancellationException

class KeyGenerationPolicyTest {

    private class NoStrongBox(cause: Throwable? = null) : Exception("test", cause)

    private val isNoStrongBox: (Throwable) -> Boolean = { it is NoStrongBox }

    @Test
    fun `SECRET-02 sukces ze StrongBox to jedna proba`() {
        val calls = mutableListOf<Boolean>()
        val result = KeyGenerationPolicy.generate({ calls += it; "klucz" }, isNoStrongBox)
        assertEquals("klucz", result)
        assertEquals(listOf(true), calls)
    }

    @Test
    fun `SECRET-02 StrongBox niedostepny bezposrednio daje druga probe bez StrongBox`() {
        val calls = mutableListOf<Boolean>()
        val result = KeyGenerationPolicy.generate(
            { calls += it; if (it) throw NoStrongBox() else "tee" },
            isNoStrongBox,
        )
        assertEquals("tee", result)
        assertEquals(listOf(true, false), calls)
    }

    @Test
    fun `SECRET-02 StrongBox niedostepny jako przyczyna ProviderException daje druga probe`() {
        val calls = mutableListOf<Boolean>()
        val result = KeyGenerationPolicy.generate(
            { calls += it; if (it) throw ProviderException("x", NoStrongBox()) else "tee" },
            isNoStrongBox,
        )
        assertEquals("tee", result)
        assertEquals(listOf(true, false), calls)
    }

    @Test
    fun `SECRET-02 ProviderException bez przyczyny StrongBox to jedna proba i propagacja`() {
        val calls = mutableListOf<Boolean>()
        val error = ProviderException("x")
        val thrown = assertThrows(ProviderException::class.java) {
            KeyGenerationPolicy.generate<String>({ calls += it; throw error }, isNoStrongBox)
        }
        assertSame(error, thrown)
        assertEquals(listOf(true), calls)
    }

    @Test
    fun `SECRET-02 obie proby rzucaja to dwie proby i propagacja wyjatku drugiej`() {
        val calls = mutableListOf<Boolean>()
        val second = IllegalStateException("drugi")
        val thrown = assertThrows(IllegalStateException::class.java) {
            KeyGenerationPolicy.generate<String>(
                { calls += it; if (it) throw NoStrongBox() else throw second },
                isNoStrongBox,
            )
        }
        assertSame(second, thrown)
        assertEquals(listOf(true, false), calls)
    }

    @Test
    fun `SECRET-17 CancellationException jest przepuszczany bez drugiej proby`() {
        val calls = mutableListOf<Boolean>()
        assertThrows(CancellationException::class.java) {
            KeyGenerationPolicy.generate<String>({ calls += it; throw CancellationException("x") }, { true })
        }
        assertEquals(listOf(true), calls)
    }
}
