package pl.twojanazwa.aapsremote.ui.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.ProfileChoice

class ProfileSelectionTest {
    @Test
    fun `PROF-14 select zapisuje wybor a clear go czysci`() {
        val selection = ProfileSelection()
        assertNull(selection.choice.value)
        selection.select(ProfileChoice(2, "LocalProfile2"))
        assertEquals(ProfileChoice(2, "LocalProfile2"), selection.choice.value)
        selection.clear()
        assertNull(selection.choice.value)
    }

    @Test
    fun `PROF-18 toString ProfileSelection nie zawiera nazwy profilu`() {
        val selection = ProfileSelection()
        selection.select(ProfileChoice(2, "LocalProfile2"))
        assertFalse(selection.toString().contains("LocalProfile"), selection.toString())
    }
}
