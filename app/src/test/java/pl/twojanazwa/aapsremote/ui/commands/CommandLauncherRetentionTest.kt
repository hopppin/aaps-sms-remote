package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.otp.TotpSecret
import pl.twojanazwa.aapsremote.security.AapsPin
import pl.twojanazwa.aapsremote.security.LoadResult
import pl.twojanazwa.aapsremote.session.CodeCredentials
import pl.twojanazwa.aapsremote.session.CommandSession
import pl.twojanazwa.aapsremote.session.SessionTestFixtures
import java.lang.reflect.Modifier

@OptIn(ExperimentalCoroutinesApi::class)
class CommandLauncherRetentionTest {

    private val forbidden = listOf<Class<*>>(
        TotpSecret::class.java,
        LoadResult::class.java,
        CodeCredentials::class.java,
        AapsPin::class.java,
        CharArray::class.java,
        ByteArray::class.java,
        CommandSession::class.java,
    )

    private val fields = CommandLauncher::class.java.declaredFields.filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }

    @Test
    fun `CMDUI-15 wszystkie pola launchera sa final i zadne nie ma typu wrazliwego`() {
        assertEquals(7, fields.size, fields.joinToString { it.name })
        for (field in fields) {
            assertTrue(Modifier.isFinal(field.modifiers), field.name)
            assertFalse(Modifier.isStatic(field.modifiers), field.name)
            assertFalse(forbidden.any { it.isAssignableFrom(field.type) }, field.name)
        }
    }

    @Test
    fun `CMDUI-15 po Started zadne pole launchera nie jest referencja do sekretu z atrapy`() = runTest {
        val env = CommandTestEnv(this)
        val secret = SessionTestFixtures.secret()
        env.secrets.loadResults.add(LoadResult.Loaded(secret))
        val launcher = env.launcher()

        assertEquals(LaunchResult.Started, launcher.launch(CommandTestFixtures.carbs(20), CommandTestFixtures.pin()))
        runCurrent()

        for (field in fields) {
            field.isAccessible = true
            assertTrue(field.get(launcher) !== secret, field.name)
        }
        env.coordinator.activeSession.value?.cancel()
        runCurrent()
    }
}
