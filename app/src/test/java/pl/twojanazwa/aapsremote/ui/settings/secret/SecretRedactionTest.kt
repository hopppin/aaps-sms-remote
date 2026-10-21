package pl.twojanazwa.aapsremote.ui.settings.secret

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SecretRedactionTest {
    @Test
    fun `UI-21 toString stanu sekretu nie zawiera tekstu pola`() {
        val text = TotpSecretUiState(mode = SecretInputMode.PASTE, pasteText = URI_RFC).toString()
        assertFalse(text.contains("otpauth"))
        assertFalse(text.contains("GEZDGNBV"))
        assertFalse(text.contains("secret="))
    }
}
