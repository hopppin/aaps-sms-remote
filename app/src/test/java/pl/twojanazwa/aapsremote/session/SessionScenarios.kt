package pl.twojanazwa.aapsremote.session

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.parser.AapsSamples

/**
 * Wspólna lista scenariuszy sesji (SESSION-03 – SESSION-17) dla testów niezmienników przekrojowych:
 * SESSION-14 (Unknown tylko po kodzie), SESSION-19 (PIN przy FINISHED) i SESSION-20 (liczba SMS-ów).
 */
class ScenarioRun(
    val result: SessionResult,
    val sender: FakeSmsSender,
    /** null dla BG/PUMP. */
    val credentials: CodeCredentials?,
    /** Wartość `pin.isCleared` w chwili, gdy obserwator zobaczył fazę FINISHED; null bez PIN-u. */
    val pinClearedWhenFinished: Boolean?,
)

class SessionScenario(
    val name: String,
    /** Oczekiwany klucz wyniku (patrz [key]); chroni przed scenariuszem, który nie robi tego, co zapowiada. */
    val expectedKey: String,
    val play: suspend TestScope.() -> ScenarioRun,
) {
    override fun toString(): String = name
}

/** Klucz wyniku: rodzaj i powód (bez treści). */
fun SessionOutcome.key(): String = when (this) {
    is SessionOutcome.Completed -> "Completed"
    is SessionOutcome.CarbsConfirmed -> "CarbsConfirmed"
    is SessionOutcome.Failed -> "Failed.$reason"
    is SessionOutcome.TimedOut -> "TimedOut.$phase"
    is SessionOutcome.Unknown -> "Unknown.$reason"
    SessionOutcome.Cancelled -> "Cancelled"
}

private val carbsRequest2g: String get() = AapsSamples.carbsCodeRequest.replace("1g", "2g")

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.play(
    command: Command = carbsCommand(1),
    withCredentials: Boolean = command != Command.Bg,
    totp: UsableTotpProvider? = null,
    clock: EpochClock = virtualClock(),
    sender: FakeSmsSender = FakeSmsSender(),
    steps: suspend TestScope.(CommandSession, FakeIncomingSmsSource, FakeSmsSender) -> Unit,
): ScenarioRun {
    val source = FakeIncomingSmsSource()
    val credentials = if (withCredentials) SessionTestFixtures.credentials() else null
    val session = CommandSession(
        command = command,
        credentials = credentials,
        sender = sender,
        incoming = source,
        clock = clock,
        totp = totp ?: UsableTotpProvider(clock),
        timeSource = testScheduler.timeSource,
    )
    var pinClearedWhenFinished: Boolean? = null
    var result: SessionResult? = null
    // onEach działa synchronnie przy każdej publikacji stanu, więc widzi PIN dokładnie w chwili FINISHED.
    session.state
        .onEach { if (it.phase == SessionPhase.FINISHED) pinClearedWhenFinished = credentials?.pin?.isCleared }
        .test {
            val run = async { session.run() }
            runCurrent()
            steps(session, source, sender)
            result = run.await()
            cancelAndIgnoreRemainingEvents()
        }
    return ScenarioRun(checkNotNull(result), sender, credentials, pinClearedWhenFinished)
}

private fun hangingTotp(): UsableTotpProvider = mockk<UsableTotpProvider>().also {
    coEvery { it.awaitUsableCode(any()) } coAnswers { awaitCancellation() }
}

/** Sesja `CARBS 1`: prośba o kod w 60 000, potem [after] (kod już poszedł do portu). */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.afterCode(
    grams: Int = 1,
    clock: EpochClock = virtualClock(),
    configure: (FakeSmsSender) -> Unit = {},
    after: suspend TestScope.(CommandSession, FakeIncomingSmsSource) -> Unit,
): ScenarioRun = play(carbsCommand(grams), clock = clock) { session, source, sender ->
    configure(sender)
    deliverAt(source, 60_000, if (grams == 2) carbsRequest2g else AapsSamples.carbsCodeRequest)
    after(session, source)
}

@OptIn(ExperimentalCoroutinesApi::class)
val sessionScenarios: List<SessionScenario> = listOf(
    // --- Bez kodu w porcie (SESSION-03 – SESSION-11) ---
    SessionScenario("SESSION-04 port odrzuca polecenie", "Failed.SEND_FAILED") {
        play(sender = FakeSmsSender().apply { commandResult = SendResult.Failed }) { _, _, _ -> }
    },
    SessionScenario("SESSION-04 port rzuca wyjatek przy poleceniu", "Failed.SEND_FAILED") {
        play(sender = FakeSmsSender().apply { failure = IllegalStateException("x") }) { _, _, _ -> }
    },
    SessionScenario("SESSION-07 port nie zwraca wyniku polecenia", "TimedOut.SENDING_COMMAND") {
        play(sender = FakeSmsSender().apply { suspendForever = true }) { _, _, _ ->
            advanceTimeBy(180_000)
            runCurrent()
        }
    },
    SessionScenario("SESSION-06 BG dostaje odpowiedz", "Completed") {
        play(Command.Bg) { _, source, _ -> deliverAt(source, 5_000, AapsSamples.bg) }
    },
    SessionScenario("SESSION-06 BG dostaje nierozpoznana tresc", "Failed.UNRECOGNIZED_REPLY") {
        play(Command.Bg) { _, source, _ -> deliverAt(source, 5_000, "Czesc, kupisz chleb?") }
    },
    SessionScenario("SESSION-07 BG bez odpowiedzi", "TimedOut.AWAITING_REPLY") {
        play(Command.Bg) { _, _, _ ->
            advanceTimeBy(180_000)
            runCurrent()
        }
    },
    SessionScenario("SESSION-07 CARBS bez prosby o kod", "TimedOut.AWAITING_CODE_REQUEST") {
        play { _, _, _ ->
            advanceTimeBy(180_000)
            runCurrent()
        }
    },
    SessionScenario("SESSION-11 prosba o kod z inna iloscia", "Failed.AMOUNT_MISMATCH") {
        play { _, source, _ -> deliverAt(source, 60_000, carbsRequest2g) }
    },
    SessionScenario("SESSION-11 nierozpoznana tresc przed kodem", "Failed.UNRECOGNIZED_REPLY") {
        play { _, source, _ -> deliverAt(source, 60_000, "Czesc, kupisz chleb?") }
    },
    SessionScenario("SESSION-11 zly kod przed wyslaniem kodu", "Failed.UNEXPECTED_REPLY") {
        play { _, source, _ -> deliverAt(source, 60_000, AapsSamples.wrongCode) }
    },
    SessionScenario("SESSION-10 prosba z chwila odbioru w przyszlosci", "Failed.CODE_REQUEST_EXPIRED") {
        play { _, source, _ ->
            advanceTimeBy(60_000)
            runCurrent()
            source.emit(IncomingSms(AapsSamples.carbsCodeRequest, SessionTestFixtures.T0 + 70_000))
            runCurrent()
        }
    },
    SessionScenario("SESSION-10 kod niedostepny przez limit przygotowania", "Failed.CODE_PREPARATION_TIMEOUT") {
        play(totp = hangingTotp()) { _, source, _ ->
            requestCodeAt(source, 60_000)
            advanceTimeBy(10_000)
            runCurrent()
        }
    },
    SessionScenario("SESSION-11 druga prosba w czasie przygotowania kodu", "Failed.UNEXPECTED_REPLY") {
        play(totp = hangingTotp()) { _, source, _ ->
            requestCodeAt(source, 60_000)
            requestCodeAt(source, 61_000)
        }
    },
    SessionScenario("SESSION-19 dostawca TOTP rzuca wyjatek", "Failed.INTERNAL_ERROR") {
        val totp = mockk<UsableTotpProvider>()
        coEvery { totp.awaitUsableCode(any()) } throws IllegalStateException("x")
        play(totp = totp) { _, source, _ -> requestCodeAt(source, 60_000) }
    },
    SessionScenario("SESSION-17 anulowanie przed kodem", "Cancelled") {
        play { session, _, _ ->
            advanceTimeBy(30_000)
            session.cancel()
            runCurrent()
        }
    },
    // --- Kod trafil do portu (SESSION-13, SESSION-15 – SESSION-17) ---
    SessionScenario("SESSION-13 potwierdzenie CARBS", "CarbsConfirmed") {
        afterCode { _, source -> deliverAt(source, 120_000, AapsSamples.carbsDone) }
    },
    SessionScenario("SESSION-13 zly kod po wyslaniu kodu", "Failed.WRONG_CODE") {
        afterCode { _, source -> deliverAt(source, 70_000, AapsSamples.wrongCode) }
    },
    SessionScenario("SESSION-13 nierozpoznana tresc po kodzie", "Unknown.UNRECOGNIZED_REPLY") {
        afterCode { _, source -> deliverAt(source, 70_000, "Czesc, kupisz chleb?") }
    },
    SessionScenario("SESSION-13 potwierdzenie innej ilosci", "Unknown.AMOUNT_MISMATCH") {
        afterCode(grams = 2) { _, source -> deliverAt(source, 70_000, AapsSamples.carbsDone) }
    },
    SessionScenario("SESSION-13 ponowna prosba o kod po kodzie", "Unknown.UNEXPECTED_REPLY") {
        afterCode { _, source -> deliverAt(source, 70_000, AapsSamples.carbsCodeRequest) }
    },
    SessionScenario("SESSION-15 brak potwierdzenia w limicie", "Unknown.CONFIRMATION_TIMEOUT") {
        afterCode { _, _ ->
            advanceTimeBy(360_000)
            runCurrent()
        }
    },
    SessionScenario("SESSION-16 port odrzuca kod", "Unknown.CODE_SEND_FAILED") {
        afterCode(configure = { it.codeResult = SendResult.Failed }) { _, _ -> }
    },
    SessionScenario("SESSION-16 port rzuca wyjatek przy kodzie", "Unknown.CODE_SEND_FAILED") {
        afterCode(configure = { it.failure = IllegalStateException("x") }) { _, _ -> }
    },
    SessionScenario("SESSION-17 anulowanie po kodzie", "Unknown.CANCELLED_AFTER_CODE") {
        afterCode { session, _ ->
            session.cancel()
            runCurrent()
        }
    },
    SessionScenario("SESSION-17 anulowanie w czasie wysylania kodu", "Unknown.CANCELLED_AFTER_CODE") {
        afterCode(configure = { it.suspendForever = true }) { session, _ ->
            session.cancel()
            runCurrent()
        }
    },
    SessionScenario("SESSION-19 zegar rzuca po wyslaniu kodu", "Unknown.INTERNAL_ERROR") {
        val clock = EpochClock {
            if (testScheduler.currentTime >= 61_000) throw IllegalStateException("zegar")
            SessionTestFixtures.T0 + testScheduler.currentTime
        }
        afterCode(clock = clock, configure = { it.delayMillis = 1000 }) { _, _ ->
            advanceTimeBy(1000)
            runCurrent()
        }
    },
)

/** Odtwarza scenariusz i sprawdza, że kończy się zapowiedzianym wynikiem w fazie FINISHED. */
suspend fun TestScope.playChecked(scenario: SessionScenario): ScenarioRun {
    val run = scenario.play(this)
    assertEquals(scenario.expectedKey, run.result.outcome.key(), scenario.name)
    return run
}
