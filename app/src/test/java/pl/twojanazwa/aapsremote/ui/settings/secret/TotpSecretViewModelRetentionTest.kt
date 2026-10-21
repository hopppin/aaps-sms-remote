package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.otp.OtpauthResult
import pl.twojanazwa.aapsremote.otp.TotpSecret
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class TotpSecretViewModelRetentionTest {
    companion object {
        @JvmField
        @RegisterExtension
        val main = MainDispatcherExtension()
    }

    private val forbidden = listOf(
        TotpSecret::class.java,
        OtpauthResult::class.java,
        OtpauthResult.Success::class.java,
        LoadResult::class.java,
        LoadResult.Loaded::class.java,
        ByteArray::class.java,
        CharArray::class.java,
    )

    @Test
    fun `UI-14 ViewModel i stan nie maja pol z sekretem`() {
        listOf(TotpSecretViewModel::class.java, TotpSecretUiState::class.java).forEach { cls ->
            cls.declaredFields.forEach { field ->
                assertTrue(
                    forbidden.none { it.isAssignableFrom(field.type) },
                    "${cls.simpleName}.${field.name} ma zabroniony typ ${field.type.simpleName}",
                )
            }
        }
    }

    @Test
    fun `UI-14 pole puste przed zwolnieniem zawieszonego zapisu`() = runTest(main.dispatcher) {
        val f = SecretFixture(main.dispatcher, SecretState.ABSENT)
        f.idle()
        f.store.gate = CompletableDeferred()
        f.viewModel.onPasteClicked()
        f.viewModel.onPasteTextChanged(URI_RFC)
        f.viewModel.onPasteSubmitted()
        f.idle()
        assertEquals(1, f.store.saveCount)
        val s = f.viewModel.uiState.value
        assertTrue(s.busy)
        assertEquals("", s.pasteText)
        f.store.gate!!.complete(Unit)
        f.idle()
        assertEquals("", f.viewModel.uiState.value.pasteText)
    }
}
