package pl.twojanazwa.aapsremote.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import pl.twojanazwa.aapsremote.commands.Command

class SessionStateInvariantTest {

    private val result = SessionResult(
        command = Command.Bg,
        startedAtEpochMillis = SessionTestFixtures.T0,
        commandSentAtEpochMillis = null,
        codeSentAtEpochMillis = null,
        finishedAtEpochMillis = SessionTestFixtures.T0,
        outcome = SessionOutcome.Cancelled,
        unexpectedMessages = emptyList(),
    )

    @Test
    fun `SESSION-21 FINISHED z wynikiem jest poprawny`() {
        assertEquals(result, SessionState(SessionPhase.FINISHED, emptyList(), result).result)
    }

    @Test
    fun `SESSION-21 FINISHED bez wyniku rzuca wyjatek`() {
        assertThrows<IllegalArgumentException> { SessionState(SessionPhase.FINISHED, emptyList(), null) }
    }

    @ParameterizedTest
    @EnumSource(SessionPhase::class, names = ["FINISHED"], mode = EnumSource.Mode.EXCLUDE)
    fun `SESSION-21 wynik poza FINISHED rzuca wyjatek`(phase: SessionPhase) {
        assertThrows<IllegalArgumentException> { SessionState(phase, emptyList(), result) }
        assertEquals(phase, SessionState(phase, emptyList(), null).phase)
    }
}
