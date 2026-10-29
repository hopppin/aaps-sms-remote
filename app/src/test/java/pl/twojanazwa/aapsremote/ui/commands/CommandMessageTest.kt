package pl.twojanazwa.aapsremote.ui.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandMessageTest {
    @Test
    fun `CMDUI-12 kazdy powod odrzucenia ma komunikat o tej samej nazwie`() {
        LaunchRejection.entries.forEach { reason ->
            assertEquals(reason.name, reason.toMessage().name)
        }
        assertEquals(LaunchRejection.entries.size, CommandMessage.entries.size)
    }

    @Test
    fun `CMDUI-12 akcja Ustawienia tylko dla braku numeru i sekretu`() {
        val opening = LaunchRejection.entries.filter { it.toMessage().opensSettings }
        assertEquals(
            setOf(
                LaunchRejection.NUMBER_MISSING,
                LaunchRejection.SECRET_ABSENT,
                LaunchRejection.LIMITS_UNAVAILABLE,
                LaunchRejection.SECRET_INVALIDATED,
            ),
            opening.toSet(),
        )
        assertTrue(CommandMessage.NUMBER_MISSING.opensSettings)
        assertFalse(CommandMessage.SECRET_LOCKED.opensSettings)
    }
}
