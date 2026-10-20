package pl.twojanazwa.aapsremote.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import pl.twojanazwa.aapsremote.security.CryptoFailure.CORRUPTED
import pl.twojanazwa.aapsremote.security.CryptoFailure.KEY_INVALIDATED
import pl.twojanazwa.aapsremote.security.CryptoFailure.LOCKED
import pl.twojanazwa.aapsremote.security.CryptoFailure.UNAVAILABLE
import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import kotlin.coroutines.cancellation.CancellationException

class CryptoFailureClassifierTest {

    class Case(val name: String, val locked: Boolean, val error: () -> Throwable, val expected: CryptoFailure) {
        override fun toString() = name
    }

    companion object {
        @JvmStatic
        fun cases(): List<Case> = listOf(
            Case("zablokowany, klucz uniewazniony", true, { TestKeyInvalidatedException() }, LOCKED),
            Case("zablokowany, AEADBadTag", true, { AEADBadTagException() }, LOCKED),
            Case("zablokowany, IllegalState", true, { IllegalStateException() }, LOCKED),
            Case(
                "odblokowany, InvalidKey z przyczyna uwierzytelnienia",
                false,
                { InvalidKeyException("x", TestUserNotAuthenticatedException()) },
                LOCKED,
            ),
            Case("odblokowany, klucz uniewazniony", false, { TestKeyInvalidatedException() }, KEY_INVALIDATED),
            Case(
                "odblokowany, klucz uniewazniony jako przyczyna",
                false,
                { ProviderException("x", TestKeyInvalidatedException()) },
                KEY_INVALIDATED,
            ),
            Case("odblokowany, AEADBadTag", false, { AEADBadTagException() }, CORRUPTED),
            Case(
                "odblokowany, AEADBadTag jako przyczyna",
                false,
                { ProviderException("x", AEADBadTagException()) },
                CORRUPTED,
            ),
            Case("odblokowany, KeyStoreException", false, { KeyStoreException("x") }, UNAVAILABLE),
            Case("odblokowany, ProviderException bez przyczyny", false, { ProviderException("x") }, UNAVAILABLE),
            Case("odblokowany, IllegalState", false, { IllegalStateException("x") }, UNAVAILABLE),
            Case("odblokowany, InvalidKey bez przyczyny", false, { InvalidKeyException("x") }, UNAVAILABLE),
        )
    }

    @ParameterizedTest(name = "SECRET-07 {0}")
    @MethodSource("cases")
    fun `SECRET-07 klasyfikacja wyjatku`(case: Case) {
        val env = FakeKeystoreEnvironment(locked = case.locked)
        assertEquals(case.expected, CryptoFailureClassifier.classify(case.error(), env))
    }

    @Test
    fun `SECRET-07 blad sprawdzenia blokady daje UNAVAILABLE dla wyjatku i braku klucza`() {
        val env = FakeKeystoreEnvironment(lockedCheckError = IllegalStateException("x"))
        assertEquals(UNAVAILABLE, CryptoFailureClassifier.classify(AEADBadTagException(), env))
        assertEquals(UNAVAILABLE, CryptoFailureClassifier.classify(TestKeyInvalidatedException(), env))
        assertEquals(UNAVAILABLE, CryptoFailureClassifier.classifyMissingKey(env))
    }

    @Test
    fun `SECRET-07 wyjatek rozpoznawania typu jest traktowany jak brak dopasowania`() {
        val env = FakeKeystoreEnvironment(predicateError = RuntimeException("x"))
        assertEquals(CORRUPTED, CryptoFailureClassifier.classify(AEADBadTagException(), env))
    }

    @Test
    fun `SECRET-07 cause wskazujacy na siebie nie zapetla klasyfikacji`() {
        val env = FakeKeystoreEnvironment()
        val self = object : Exception("test") {
            override val cause: Throwable get() = this
        }
        assertEquals(UNAVAILABLE, CryptoFailureClassifier.classify(self, env))
    }

    @Test
    fun `SECRET-07 lancuch 20 wyjatkow z AEADBadTag na koncu daje UNAVAILABLE`() {
        val env = FakeKeystoreEnvironment()
        var error: Throwable = AEADBadTagException()
        repeat(19) { error = Exception("test", error) }
        assertEquals(UNAVAILABLE, CryptoFailureClassifier.classify(error, env))
    }

    @Test
    fun `SECRET-07 AEADBadTagException jako 8 element lancucha daje CORRUPTED`() {
        val env = FakeKeystoreEnvironment()
        var error: Throwable = AEADBadTagException()
        repeat(7) { error = Exception("test", error) }
        assertEquals(CORRUPTED, CryptoFailureClassifier.classify(error, env))
    }

    @Test
    fun `SECRET-07 AEADBadTagException jako 9 element lancucha daje UNAVAILABLE`() {
        val env = FakeKeystoreEnvironment()
        var error: Throwable = AEADBadTagException()
        repeat(8) { error = Exception("test", error) }
        assertEquals(UNAVAILABLE, CryptoFailureClassifier.classify(error, env))
    }

    @Test
    fun `SECRET-07 brak klucza odblokowany to KEY_INVALIDATED a zablokowany LOCKED`() {
        assertEquals(KEY_INVALIDATED, CryptoFailureClassifier.classifyMissingKey(FakeKeystoreEnvironment(locked = false)))
        assertEquals(LOCKED, CryptoFailureClassifier.classifyMissingKey(FakeKeystoreEnvironment(locked = true)))
    }

    @Test
    fun `SECRET-17 CancellationException ze sprawdzenia blokady jest przepuszczany`() {
        val env = FakeKeystoreEnvironment(lockedCheckError = CancellationException("x"))
        assertThrows(CancellationException::class.java) { CryptoFailureClassifier.classify(AEADBadTagException(), env) }
        assertThrows(CancellationException::class.java) { CryptoFailureClassifier.classifyMissingKey(env) }
    }

    @Test
    fun `SECRET-17 CancellationException z rozpoznawania typu jest przepuszczany`() {
        val env = FakeKeystoreEnvironment(predicateError = CancellationException("x"))
        assertThrows(CancellationException::class.java) { CryptoFailureClassifier.classify(AEADBadTagException(), env) }
    }
}
