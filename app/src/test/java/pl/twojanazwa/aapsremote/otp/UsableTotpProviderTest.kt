package pl.twojanazwa.aapsremote.otp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsableTotpProviderTest {

    private val secret = TotpSecret.fromBytes("12345678901234567890".toByteArray(Charsets.US_ASCII))

    @Test
    fun `TOTP-02 pozostalo 29000 ms zwraca 050471 bez czekania`() = runTest {
        val clock = EpochClock { 1111111111000 + currentTime }
        val code = UsableTotpProvider(clock).awaitUsableCode(secret)
        assertEquals("050471", code.value)
        assertEquals(0, currentTime)
    }

    @Test
    fun `TOTP-02 pozostalo dokladnie 5000 ms zwraca 081804 bez czekania`() = runTest {
        val clock = EpochClock { 1111111105000 + currentTime }
        val code = UsableTotpProvider(clock).awaitUsableCode(secret)
        assertEquals("081804", code.value)
        assertEquals(0, currentTime)
    }

    @Test
    fun `TOTP-02 pozostalo 4999 ms czeka 4999 ms i zwraca 050471`() = runTest {
        val clock = EpochClock { 1111111105001 + currentTime }
        val code = UsableTotpProvider(clock).awaitUsableCode(secret)
        assertEquals("050471", code.value)
        assertEquals(1111111140000, code.validUntilEpochMillis)
        assertEquals(4999, currentTime)
    }

    @Test
    fun `TOTP-02 pozostalo 1000 ms czeka 1000 ms i zwraca 050471`() = runTest {
        val clock = EpochClock { 1111111109000 + currentTime }
        val code = UsableTotpProvider(clock).awaitUsableCode(secret)
        assertEquals("050471", code.value)
        assertEquals(1000, currentTime)
    }

    @Test
    fun `TOTP-02 zatrzymany zegar nie daje kodu 081804 tylko czeka dalej`() = runTest {
        val provider = UsableTotpProvider(EpochClock { 1111111109000 })
        val result = withTimeoutOrNull(120_000) { provider.awaitUsableCode(secret) }
        assertNull(result)
        assertEquals(120_000, currentTime)
    }

    @Test
    fun `TOTP-02 anulowanie w trakcie czekania konczy sie CancellationException`() = runTest {
        val provider = UsableTotpProvider(EpochClock { 1111111109000 + currentTime })
        val deferred = async { provider.awaitUsableCode(secret) }
        runCurrent()
        advanceTimeBy(500)
        deferred.cancel()
        var thrown: Throwable? = null
        try {
            deferred.await()
        } catch (e: CancellationException) {
            thrown = e
        }
        assertTrue(thrown is CancellationException)
        assertTrue(deferred.isCancelled)
    }

    @Test
    fun `TOTP-02 prog to stala 5000 ms a zwrocony kod ma co najmniej tyle waznosci`() = runTest {
        assertEquals(5_000L, UsableTotpProvider.MIN_REMAINING_MILLIS)
        for (start in longArrayOf(1111111100000, 1111111105001, 1111111107500, 1111111109999)) {
            val base = start - currentTime
            var read = 0L
            val clock = EpochClock { (base + currentTime).also { read = it } }
            val code = UsableTotpProvider(clock).awaitUsableCode(secret)
            assertTrue(code.remainingMillis(read) >= 5_000)
        }
    }

    @Test
    fun `TOTP-04 wynik zalezy tylko od wstrzyknietego zrodla czasu`() = runTest {
        val code = UsableTotpProvider(EpochClock { 1111111111000 }).awaitUsableCode(secret)
        assertEquals("050471", code.value)
    }
}
