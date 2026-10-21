package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.otp.OtpauthResult
import pl.twojanazwa.aapsremote.otp.TotpSecret
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension

class TotpSecretViewModelRetentionTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

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
    fun `UI-14 wejscie ze skanera nie zostaje w polach ViewModelu ani stanu`() = runTest(main.dispatcher) {
        val f = SecretFixture(main.dispatcher, SecretState.ABSENT)
        f.camera.granted = true
        f.idle()
        f.viewModel.onScanClicked()
        f.viewModel.onQrScanned(URI_RFC)
        f.idle()
        val secret = checkNotNull(f.store.savedSecret)
        assertTrue(f.viewModel.uiState.value.message == SecretMessage.SAVED)

        // Korzenie: ViewModel i stan; jeden poziom w głąb dla obiektów aplikacji (bez współpracowników z fixture).
        val collaborators = setOf<Any>(f.store, f.session.coordinator, f.camera, f.clipboard)
        val roots = mutableListOf<Any>(f.viewModel, f.viewModel.uiState.value)
        val local = TotpSecretViewModel::class.java.getDeclaredField("local").apply { isAccessible = true }
        roots += (local.get(f.viewModel) as kotlinx.coroutines.flow.StateFlow<*>).value!!
        roots.forEach { root ->
            root.javaClass.declaredFields.forEach { field ->
                field.isAccessible = true
                val value = field.get(root) ?: return@forEach
                assertFalse(value === secret, "${root.javaClass.simpleName}.${field.name} trzyma sekret")
                if (value.javaClass.name.startsWith("pl.twojanazwa") && collaborators.none { it === value }) {
                    value.javaClass.declaredFields.forEach { inner ->
                        inner.isAccessible = true
                        assertFalse(inner.get(value) === secret, "${field.name}.${inner.name} trzyma sekret")
                    }
                }
            }
        }
        assertFalse(f.viewModel.uiState.value.toString().contains("GEZDGNBV"))
    }

    @Test
    fun `UI-14 onStop czysci pole i zamyka okno wklejania bez save`() = runTest(main.dispatcher) {
        val f = SecretFixture(main.dispatcher, SecretState.ABSENT)
        f.idle()
        f.viewModel.onPasteClicked()
        f.viewModel.onPasteTextChanged(URI_RFC)
        f.idle()
        f.viewModel.onStop()
        f.idle()
        val s = f.viewModel.uiState.value
        assertEquals("", s.pasteText)
        assertEquals(SecretInputMode.NONE, s.mode)
        assertEquals(0, f.store.saveCount)
    }

    @Test
    fun `UI-14 onStop zamyka skaner bez save`() = runTest(main.dispatcher) {
        val f = SecretFixture(main.dispatcher, SecretState.ABSENT)
        f.camera.granted = true
        f.idle()
        f.viewModel.onScanClicked()
        f.idle()
        assertEquals(SecretInputMode.SCANNER, f.viewModel.uiState.value.mode)
        f.viewModel.onStop()
        f.idle()
        assertEquals(SecretInputMode.NONE, f.viewModel.uiState.value.mode)
        assertEquals(0, f.store.saveCount)
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
