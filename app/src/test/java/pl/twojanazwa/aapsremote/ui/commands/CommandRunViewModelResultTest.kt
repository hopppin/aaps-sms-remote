package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.TotpSecret
import pl.twojanazwa.aapsremote.parser.AapsSamples
import pl.twojanazwa.aapsremote.security.AapsPin
import pl.twojanazwa.aapsremote.session.CodeCredentials
import pl.twojanazwa.aapsremote.session.CommandSession
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionResult
import pl.twojanazwa.aapsremote.session.deliverAt
import pl.twojanazwa.aapsremote.ui.MainDispatcherExtension
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class CommandRunViewModelResultTest {
    /** Instancyjne (nie statyczne): świeży scheduler na każdy test, bo testy przesuwają czas. */
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val forbiddenTypes = listOf<Class<*>>(
        CommandSession::class.java,
        CodeCredentials::class.java,
        TotpSecret::class.java,
        AapsPin::class.java,
    )

    private fun instanceFields(type: Class<*>): List<Field> =
        type.declaredFields.filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }

    private fun valueOf(field: Field, target: Any): Any? {
        field.isAccessible = true
        return field.get(target)
    }

    /** Typy z ui.commands osiągalne z [root] po typach pól (rekurencyjnie, z podtypami sealed). */
    private fun reachableTypes(root: Class<*>, seen: MutableSet<Class<*>> = mutableSetOf()): Set<Class<*>> {
        if (!seen.add(root)) return seen
        for (field in instanceFields(root)) {
            val type = field.type
            if (type.name.startsWith("pl.twojanazwa.aapsremote.ui.commands")) reachableTypes(type, seen)
        }
        if (root.isInterface) {
            root.permittedSubclasses?.forEach { reachableTypes(it, seen) }
        }
        return seen
    }

    private suspend fun kotlinx.coroutines.test.TestScope.finishedBg(env: CommandTestEnv): CommandSession {
        env.launcher().launch(Command.Bg, null)
        runCurrent()
        val session = checkNotNull(env.coordinator.activeSession.value)
        deliverAt(env.incoming, 1_000, AapsSamples.bg)
        runCurrent()
        return session
    }

    @Test
    fun `CMDUI-21 wynik Completed i onResultDismissed czysci widok i CurrentRun`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val viewModel = CommandRunViewModel(env.currentRun, env.coordinator)
        finishedBg(env)
        assertTrue(viewModel.uiState.value.view is RunView.Finished)

        viewModel.onResultDismissed()
        runCurrent()

        assertNull(viewModel.uiState.value.view)
        assertNull(env.currentRun.state.value)
    }

    @Test
    fun `CMDUI-21 onResultDismissed w trakcie przebiegu nic nie zmienia`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val viewModel = CommandRunViewModel(env.currentRun, env.coordinator)
        env.launcher().launch(Command.Bg, null)
        runCurrent()
        val before = viewModel.uiState.value

        viewModel.onResultDismissed()
        runCurrent()

        assertEquals(before, viewModel.uiState.value)
        assertNotNull(env.currentRun.state.value)
        env.coordinator.activeSession.value?.cancel()
        runCurrent()
    }

    @Test
    fun `CMDUI-21 pola ViewModelu i stanu wyniku nie trzymaja sesji ani sekretow ani strumieni`() =
        runTest(main.dispatcher) {
            val env = CommandTestEnv(this)
            val viewModel = CommandRunViewModel(env.currentRun, env.coordinator)
            val session = finishedBg(env)
            val state = viewModel.uiState.value

            val types = reachableTypes(RunUiState::class.java) + reachableTypes(CommandRunViewModel::class.java)
            // Gdy brak PermittedSubclasses, przejście po podtypach RunView cicho by się skróciło.
            assertTrue(
                types.containsAll(listOf(RunView.Active::class.java, RunView.Finished::class.java, ResultView::class.java)),
                "reachableTypes nie objęło podtypów RunView",
            )
            for (type in types) {
                for (field in instanceFields(type)) {
                    if (type == CommandRunViewModel::class.java && field.name == "coordinator") continue
                    assertFalse(forbiddenTypes.any { it.isAssignableFrom(field.type) }, "${type.simpleName}.${field.name}")
                }
            }
            for (type in reachableTypes(RunUiState::class.java)) {
                for (field in instanceFields(type)) {
                    assertFalse(Flow::class.java.isAssignableFrom(field.type), "${type.simpleName}.${field.name}")
                }
            }
            // Wartości: stan nie wskazuje sesji ani jej state.
            val view = checkNotNull(state.view)
            for (holder in listOf<Any>(state, view)) {
                for (field in instanceFields(holder.javaClass)) {
                    val value = valueOf(field, holder)
                    assertFalse(value === session, field.name)
                    assertFalse(value === session.state, field.name)
                }
            }
        }

    @Test
    fun `CMDUI-21 po zamknieciu wyniku ViewModel stan i CurrentRun nie wskazuja sesji`() = runTest(main.dispatcher) {
        val env = CommandTestEnv(this)
        val viewModel = CommandRunViewModel(env.currentRun, env.coordinator)
        val session = finishedBg(env)
        viewModel.onResultDismissed()
        runCurrent()

        val holders = listOf<Any>(viewModel, viewModel.uiState.value, env.currentRun)
        for (holder in holders) {
            for (field in instanceFields(holder.javaClass)) {
                val value = valueOf(field, holder)
                assertFalse(value === session, "${holder.javaClass.simpleName}.${field.name}")
                assertFalse(value === session.state, "${holder.javaClass.simpleName}.${field.name}")
            }
        }
        // CurrentRun trzyma tylko MutableStateFlow opakowany wartością null.
        assertNull(env.currentRun.state.value)
    }

    @Test
    fun `CMDUI-11 konstruktor CommandRunViewModel bez SavedStateHandle i zrodla bez rememberSaveable`() {
        val constructor = CommandRunViewModel::class.java.declaredConstructors.single()
        assertNotNull(constructor.getAnnotation(Inject::class.java))
        assertFalse(constructor.parameterTypes.any { it.name.endsWith("SavedStateHandle") })
        assertEquals(2, constructor.parameterCount)
        val sources = java.io.File("src/main/java/pl/twojanazwa/aapsremote/ui/commands")
            .walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.any { it.name == "CommandRunViewModel.kt" })
        val violations = sources.filter { file ->
            val text = file.readText()
            text.contains("rememberSaveable") || text.contains("SavedStateHandle")
        }.map { it.name }
        assertTrue(violations.isEmpty(), "Zakazane: $violations")
    }

    @Test
    fun `CMDUI-26 toString stanu przebiegu z wynikiem nie zawiera tresci ani ilosci`() {
        val bg = SessionResult(
            command = Command.Bg,
            startedAtEpochMillis = 0,
            commandSentAtEpochMillis = 1,
            codeSentAtEpochMillis = null,
            finishedAtEpochMillis = 2,
            outcome = SessionOutcome.Completed(
                pl.twojanazwa.aapsremote.parser.AapsReplyParser.parse(
                    AapsSamples.bg,
                    pl.twojanazwa.aapsremote.parser.ExpectedCommand.Bg,
                ),
                AapsSamples.bg,
            ),
            unexpectedMessages = emptyList(),
        )
        val carbs = SessionResult(
            command = CommandTestFixtures.carbs(37),
            startedAtEpochMillis = 0,
            commandSentAtEpochMillis = 1,
            codeSentAtEpochMillis = 2,
            finishedAtEpochMillis = 3,
            outcome = SessionOutcome.CarbsConfirmed(37, AapsSamples.carbsDone),
            unexpectedMessages = emptyList(),
        )
        for (result in listOf(bg, carbs)) {
            val state = RunUiState(view = RunView.Finished(ResultMapper.map(result)))
            val text = state.toString()
            assertFalse(text.contains("210"), text)
            assertFalse(text.contains("37"), text)
            assertFalse(text.contains("Delta"), text)
        }
        val active = RunView.Active(
            pl.twojanazwa.aapsremote.commands.CommandId.CARBS,
            37,
            null,
            emptyList(),
            CancelMode.CANCEL,
            0,
        )
        assertFalse(RunUiState(view = active).toString().contains("37"))
    }
}
