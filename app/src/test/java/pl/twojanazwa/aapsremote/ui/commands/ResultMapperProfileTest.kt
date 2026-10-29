package pl.twojanazwa.aapsremote.ui.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.ProfileChoice
import pl.twojanazwa.aapsremote.commands.ProfileSwitchValidation
import pl.twojanazwa.aapsremote.parser.AapsReply
import pl.twojanazwa.aapsremote.session.FailureReason
import pl.twojanazwa.aapsremote.session.SessionOutcome
import pl.twojanazwa.aapsremote.session.SessionResult
import pl.twojanazwa.aapsremote.session.UnknownReason

class ResultMapperProfileTest {
    private val listText = "1. LocalProfile1\n2. LocalProfile2"
    private val switch =
        (Command.ProfileSwitch.of(ProfileChoice(2, "LocalProfile2"), 115) as ProfileSwitchValidation.Valid).command

    private fun result(command: Command, outcome: SessionOutcome) = SessionResult(
        command = command,
        startedAtEpochMillis = 1L,
        commandSentAtEpochMillis = 2L,
        codeSentAtEpochMillis = null,
        finishedAtEpochMillis = 3L,
        outcome = outcome,
        unexpectedMessages = emptyList(),
    )

    private fun listCompleted() = SessionOutcome.Completed(
        AapsReply.ProfileListReport(
            listOf(AapsReply.ProfileListItem(1, "LocalProfile1"), AapsReply.ProfileListItem(2, "LocalProfile2")),
            listText,
        ),
        listText,
    )

    @Test
    fun `PROF-14 wynik PROFILE LIST ma pozycje komunikat i tresc SMS`() {
        val view = ResultMapper.map(result(Command.ProfileList, listCompleted()))
        assertEquals(listOf(ProfileChoice(1, "LocalProfile1"), ProfileChoice(2, "LocalProfile2")), view.profileItems)
        assertEquals(ResultKind.SUCCESS, view.kind)
        assertEquals(ResultMessage.PROFILE_LIST_REPLY, view.message)
        assertEquals(listText, view.replyText)
    }

    @Test
    fun `PROF-14 wynik PROFILE STATUS i Failed dla PROFILE LIST nie maja pozycji`() {
        val status = ResultMapper.map(
            result(
                Command.ProfileStatus,
                SessionOutcome.Completed(AapsReply.ProfileStatusReport("LocalProfile1 (100%)"), "LocalProfile1 (100%)"),
            ),
        )
        assertNull(status.profileItems)
        val failed = ResultMapper.map(
            result(Command.ProfileList, SessionOutcome.Failed(FailureReason.UNRECOGNIZED_REPLY, "x")),
        )
        assertNull(failed.profileItems)
    }

    @Test
    fun `PROF-16 Completed PROFILE STATUS to sukces z komunikatem i trescia`() {
        val view = ResultMapper.map(
            result(
                Command.ProfileStatus,
                SessionOutcome.Completed(AapsReply.ProfileStatusReport("Tresc"), "Tresc"),
            ),
        )
        assertEquals(ResultKind.SUCCESS, view.kind)
        assertEquals(ResultMessage.PROFILE_STATUS_REPLY, view.message)
        assertEquals("Tresc", view.replyText)
    }

    @Test
    fun `PROF-16 Failed AMOUNT_MISMATCH zmiany profilu to blad z komunikatem profilu i trescia`() {
        val view = ResultMapper.map(result(switch, SessionOutcome.Failed(FailureReason.AMOUNT_MISMATCH, "Tresc")))
        assertEquals(ResultKind.FAILURE, view.kind)
        assertEquals(ResultMessage.PROFILE_AMOUNT_MISMATCH, view.message)
        assertEquals("Tresc", view.replyText)
        assertEquals("LocalProfile2", view.profileName)
        assertEquals(115, view.profilePercent)
    }

    @Test
    fun `PROF-16 Unknown AMOUNT_MISMATCH i UNRECOGNIZED_REPLY zmiany profilu maja komunikaty profilu`() {
        val mismatch = ResultMapper.map(result(switch, SessionOutcome.Unknown(UnknownReason.AMOUNT_MISMATCH, "Tresc")))
        assertEquals(ResultKind.UNKNOWN, mismatch.kind)
        assertEquals(ResultMessage.PROFILE_UNKNOWN_AMOUNT_MISMATCH, mismatch.message)
        assertEquals("Tresc", mismatch.replyText)
        val unrecognized =
            ResultMapper.map(result(switch, SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, "Tresc")))
        assertEquals(ResultKind.UNKNOWN, unrecognized.kind)
        assertEquals(ResultMessage.PROFILE_UNKNOWN_UNRECOGNIZED_REPLY, unrecognized.message)
        assertEquals("Tresc", unrecognized.replyText)
    }

    @Test
    fun `PROF-16 inne polecenia nie dostaja tekstow profilu`() {
        val bg = ResultMapper.map(result(Command.Bg, SessionOutcome.Failed(FailureReason.AMOUNT_MISMATCH, "x")))
        assertEquals(ResultMessage.AMOUNT_MISMATCH, bg.message)
        assertNull(bg.profileName)
    }

    @Test
    fun `PROF-18 toString wyniku listy i zmiany profilu nie zawiera nazw ani procentu`() {
        for (view in listOf(
            ResultMapper.map(result(Command.ProfileList, listCompleted())),
            ResultMapper.map(result(switch, SessionOutcome.Unknown(UnknownReason.UNRECOGNIZED_REPLY, "Tresc"))),
        )) {
            assertFalse(view.toString().contains("LocalProfile"), view.toString())
            assertFalse(view.toString().contains("115"), view.toString())
            assertFalse(RunView.Finished(view).toString().contains("LocalProfile"))
        }
    }
}
