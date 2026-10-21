package pl.twojanazwa.aapsremote.session.platform

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.session.IncomingSmsSource
import pl.twojanazwa.aapsremote.session.OutgoingSms
import pl.twojanazwa.aapsremote.session.SendResult
import pl.twojanazwa.aapsremote.session.SessionCoordinator
import pl.twojanazwa.aapsremote.session.SmsSender
import pl.twojanazwa.aapsremote.session.StartResult
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
class SessionModuleTest {

    private fun method(name: String) = SessionModule::class.java.declaredMethods.single { it.name == name }

    @Test
    fun `UI-19 metody modulu maja Provides i Singleton`() {
        listOf("usableTotpProvider", "sessionCoordinator").forEach { name ->
            val m = method(name)
            assertNotNull(m.getAnnotation(dagger.Provides::class.java), "$name bez @Provides")
            assertNotNull(m.getAnnotation(Singleton::class.java), "$name bez @Singleton")
        }
        assertEquals(UsableTotpProvider::class.java, method("usableTotpProvider").returnType)
        assertEquals(SessionCoordinator::class.java, method("sessionCoordinator").returnType)
    }

    @Test
    fun `UI-19 koordynator z modulu bez aktywnej sesji uruchamia BG i wysyla przez atrape`() = runTest {
        val sent = mutableListOf<OutgoingSms>()
        val sender = SmsSender { message ->
            sent += message
            SendResult.Sent
        }
        val incoming = IncomingSmsSource { emptyFlow() }
        val clock = EpochClock { 1_000L }

        val totp = SessionModule.usableTotpProvider(clock)
        val coordinator = SessionModule.sessionCoordinator(sender, incoming, clock, totp)

        assertNull(coordinator.activeSession.value)
        val result = coordinator.start(backgroundScope, Command.Bg, null)
        assertTrue(result is StartResult.Started)
        runCurrent()
        assertEquals(1, sent.size)
        assertTrue(sent.single() is OutgoingSms.CommandText)
    }
}
