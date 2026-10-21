package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import pl.twojanazwa.aapsremote.otp.OtpauthError
import pl.twojanazwa.aapsremote.otp.TotpGenerator
import pl.twojanazwa.aapsremote.security.SaveFailure
import pl.twojanazwa.aapsremote.security.SaveResult
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class TotpSecretViewModelSaveTest {
    companion object {
        @JvmField
        @RegisterExtension
        val main = MainDispatcherExtension()

        private const val RFC = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        private const val BASE = "otpauth://totp/AAPS?"
    }

    private fun fixture(): SecretFixture =
        SecretFixture(main.dispatcher, SecretState.ABSENT).also { it.idle() }

    /** Otwiera okno wklejania, wkleja [input], zatwierdza i zwraca komunikat. */
    private fun submit(f: SecretFixture, input: String): SecretMessage? {
        f.viewModel.onPasteClicked()
        f.viewModel.onPasteTextChanged(input)
        f.viewModel.onPasteSubmitted()
        f.idle()
        val message = f.viewModel.uiState.value.message
        f.viewModel.onMessageShown()
        f.idle()
        return message
    }

    @Test
    fun `UI-12 Saved zapisuje raz sekret RFC i daje SAVED`() = runTest(main.dispatcher) {
        val f = fixture()
        assertEquals(SecretMessage.SAVED, submit(f, URI_RFC))
        assertEquals(1, f.store.saveCount)
        val secret = checkNotNull(f.store.savedSecret)
        val code = TotpGenerator.generate(secret, 59_000)
        assertEquals("287082", code.value)
    }

    @ParameterizedTest
    @CsvSource(
        "NOT_OTPAUTH_URI, https://example.com/?secret=GEZDGNBV, NOT_TOTP_QR",
        "UNSUPPORTED_TYPE, otpauth://hotp/AAPS?secret=$RFC&counter=0, NOT_TOTP_QR",
        "MALFORMED_URI, otpauth://totp/AAPS?secret=%ZZ, NOT_TOTP_QR",
        "MISSING_SECRET, otpauth://totp/AAPS?issuer=AAPS, INVALID_SECRET",
        "DUPLICATE_PARAMETER, otpauth://totp/AAPS?secret=GEZDGNBV&secret=GEZDGNBV, INVALID_SECRET",
        "EMPTY_SECRET, otpauth://totp/AAPS?secret=, INVALID_SECRET",
        "INVALID_BASE32, otpauth://totp/AAPS?secret=GEZ1, INVALID_SECRET",
        "UNSUPPORTED_ALGORITHM, otpauth://totp/AAPS?secret=$RFC&algorithm=SHA256, UNSUPPORTED_PARAMETERS",
        "UNSUPPORTED_DIGITS, otpauth://totp/AAPS?secret=$RFC&digits=8, UNSUPPORTED_PARAMETERS",
        "UNSUPPORTED_PERIOD, otpauth://totp/AAPS?secret=$RFC&period=60, UNSUPPORTED_PARAMETERS",
    )
    fun `UI-12 kazdy blad OtpauthError daje komunikat bez zapisu`(
        error: OtpauthError,
        input: String,
        expected: SecretMessage,
    ) = runTest(main.dispatcher) {
        val f = fixture()
        assertEquals(expected, submit(f, input))
        assertEquals(0, f.store.saveCount)
    }

    @Test
    fun `UI-12 wszystkie wartosci OtpauthError sa pokryte`() {
        // Zabezpieczenie: nowa wartosc enuma wymusza uzupelnienie testu parametrycznego (10 wartosci).
        assertEquals(10, OtpauthError.entries.size)
    }

    @Test
    fun `UI-12 wyniki zapisu daja odpowiednie komunikaty`() = runTest(main.dispatcher) {
        val f = fixture()
        val results = listOf(
            SaveResult.Locked to SecretMessage.LOCKED,
            SaveResult.Failed(SaveFailure.KEY_GENERATION) to SecretMessage.KEY_GENERATION_FAILED,
            SaveResult.Failed(SaveFailure.CRYPTO) to SecretMessage.SAVE_FAILED,
            SaveResult.Failed(SaveFailure.STORAGE) to SecretMessage.SAVE_FAILED,
        )
        results.forEach { (result, expected) ->
            f.store.saveResults.add(result)
            assertEquals(expected, submit(f, URI_RFC))
        }
        assertEquals(4, f.store.saveCount)
    }

    @Test
    fun `UI-12 komunikat jest stala wyliczenia bez danych wejscia`() = runTest(main.dispatcher) {
        val f = fixture()
        val message = submit(f, "otpauth://totp/AAPS?secret=GEZ1")
        assertEquals(SecretMessage.INVALID_SECRET, message)
        assertEquals("INVALID_SECRET", message?.name)
        assertEquals(SecretMessage::class.java, message?.javaClass)
    }

    @Test
    fun `UI-12 wyswietlanie po zapisie pochodzi ze stanu magazynu`() = runTest(main.dispatcher) {
        val f = fixture()
        f.store.gate = CompletableDeferred()
        f.viewModel.onPasteClicked()
        f.viewModel.onPasteTextChanged(URI_RFC)
        f.viewModel.onPasteSubmitted()
        f.idle()
        f.store.setState(SecretState.INVALIDATED)
        f.store.gate!!.complete(Unit)
        f.idle()
        val s = f.viewModel.uiState.value
        assertEquals(SecretDisplay.INVALIDATED, s.display)
        assertEquals(SecretMessage.SAVED, s.message)
    }
}
