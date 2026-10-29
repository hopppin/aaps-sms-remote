package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CarbsValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.session.CommandSession
import pl.twojanazwa.aapsremote.session.SessionPhase
import pl.twojanazwa.aapsremote.session.SessionState

class CurrentRunTest {

    private fun data(command: Command) = CurrentRunData(
        command,
        MutableStateFlow(SessionState(SessionPhase.SENDING_COMMAND, emptyList(), null)),
    )

    @Test
    fun `CMDUI-16 stan na starcie jest null`() {
        assertNull(CurrentRun().state.value)
    }

    @Test
    fun `CMDUI-16 publish ustawia uruchomienie i zastepuje poprzednie`() {
        val run = CurrentRun()
        val first = data(Command.Bg)
        val second = data(Command.Pump)
        run.publish(first)
        assertSame(first, run.state.value)
        run.publish(second)
        assertSame(second, run.state.value)
    }

    @Test
    fun `CMDUI-21 clear ustawia null`() {
        val run = CurrentRun()
        run.publish(data(Command.Bg))
        run.clear()
        assertNull(run.state.value)
    }

    @Test
    fun `CMDUI-26 toString danych uruchomienia nie zawiera ilosci gramow`() {
        val carbs = (Command.Carbs.of(37, CommandLimits.DEFAULT) as CarbsValidation.Valid).command
        val text = data(carbs).toString()
        assertFalse(text.contains("37"), text)
    }

    @Test
    fun `CMDUI-16 dane uruchomienia nie trzymaja referencji do CommandSession`() {
        val fields = CurrentRunData::class.java.declaredFields.map { it.type }
        assertTrue(fields.none { CommandSession::class.java.isAssignableFrom(it) })
    }
}
