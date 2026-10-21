package pl.twojanazwa.aapsremote.ui

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import pl.twojanazwa.aapsremote.session.CommandSession
import pl.twojanazwa.aapsremote.session.SessionCoordinator

/** Atrapa koordynatora z sterowalnym `activeSession` (brak polecenia = null). */
class SessionDoubles {
    val activeSession = MutableStateFlow<CommandSession?>(null)

    val coordinator: SessionCoordinator = mockk(relaxed = true) {
        every { this@mockk.activeSession } returns this@SessionDoubles.activeSession
    }

    /** Ustawia aktywne polecenie (atrapa sesji). */
    fun startCommand(): CommandSession = mockk<CommandSession>().also { activeSession.value = it }

    fun finishCommand() {
        activeSession.value = null
    }
}
