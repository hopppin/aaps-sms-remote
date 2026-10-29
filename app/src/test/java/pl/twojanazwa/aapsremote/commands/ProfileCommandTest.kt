package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.AapsReply
import pl.twojanazwa.aapsremote.parser.AapsReplyParser
import pl.twojanazwa.aapsremote.parser.ExpectedCommand

class ProfileCommandTest {
    private val choice = ProfileChoice(1, "LocalProfile1")

    private fun switch(number: Int, percent: Int) =
        (Command.ProfileSwitch.of(ProfileChoice(number, "LocalProfile$number"), percent) as ProfileSwitchValidation.Valid).command

    private fun err(v: ProfileSwitchValidation) = (v as ProfileSwitchValidation.Invalid).error

    @Test
    fun `PROF-01 dane wpisow profilu`() {
        val (status, list, sw) = CommandCatalog.entries.drop(4)
        assertEquals("PROFILE STATUS", status.keyword)
        assertEquals("PROFILE LIST", list.keyword)
        assertEquals("PROFILE", sw.keyword)
        assertFalse(status.requiresCode)
        assertFalse(list.requiresCode)
        assertTrue(sw.requiresCode)
        assertEquals(Approval.ONE_TAP, status.approval)
        assertEquals(Approval.ONE_TAP, list.approval)
        assertEquals(Approval.SUMMARY_WITH_PIN, sw.approval)
        assertEquals(CommandParameter.None, status.parameter)
        assertEquals(CommandParameter.ProfileAndPercent(50, 200, 100), sw.parameter)
    }

    @Test
    fun `PROF-02 procent 100 50 200 jest poprawny`() {
        for (p in listOf(100, 50, 200)) {
            assertEquals(p, switch(1, p).percent)
        }
        assertEquals(100, Command.ProfileSwitch.DEFAULT_PERCENT)
    }

    @Test
    fun `PROF-02 procent poza zakresem daje blad bez wyjatku`() {
        assertEquals(ProfileSwitchError.BELOW_MIN, err(Command.ProfileSwitch.of(choice, 49)))
        assertEquals(ProfileSwitchError.ABOVE_MAX, err(Command.ProfileSwitch.of(choice, 201)))
        assertEquals(ProfileSwitchError.BELOW_MIN, err(Command.ProfileSwitch.of(choice, 0)))
        assertEquals(ProfileSwitchError.BELOW_MIN, err(Command.ProfileSwitch.of(choice, -10)))
        assertEquals(ProfileSwitchError.ABOVE_MAX, err(Command.ProfileSwitch.of(choice, Int.MAX_VALUE)))
    }

    @Test
    fun `PROF-02 bledny wybor numer 0 100 pusta nazwa lub LF`() {
        val bad = listOf(
            ProfileChoice(0, "A"),
            ProfileChoice(100, "A"),
            ProfileChoice(-1, "A"),
            ProfileChoice(1, ""),
            ProfileChoice(1, "A\nB"),
        )
        for (c in bad) {
            assertEquals(ProfileSwitchError.INVALID_CHOICE, err(Command.ProfileSwitch.of(c, 100)))
        }
        assertEquals(99, switch(99, 100).number)
    }

    @Test
    fun `PROF-03 parsePercent wedlug tabeli`() {
        fun parse(t: String) = Command.ProfileSwitch.parsePercent(choice, t)
        assertEquals(100, (parse("100") as ProfileSwitchValidation.Valid).command.percent)
        assertEquals(115, (parse("115") as ProfileSwitchValidation.Valid).command.percent)
        assertEquals(50, (parse("050") as ProfileSwitchValidation.Valid).command.percent)
        assertEquals(ProfileSwitchError.EMPTY, err(parse("")))
        assertEquals(ProfileSwitchError.BELOW_MIN, err(parse("49")))
        assertEquals(ProfileSwitchError.BELOW_MIN, err(parse("0")))
        assertEquals(ProfileSwitchError.ABOVE_MAX, err(parse("201")))
        assertEquals(ProfileSwitchError.ABOVE_MAX, err(parse("99999999999999999999")))
        assertEquals(ProfileSwitchError.INVALID_CHARACTERS, err(parse("110%")))
        assertEquals(ProfileSwitchError.INVALID_CHARACTERS, err(parse("1,5")))
        assertEquals(ProfileSwitchError.INVALID_CHARACTERS, err(parse(" 100")))
    }

    @Test
    fun `PROF-04 tresc SMS bez nazwy profilu`() {
        assertEquals("PROFILE STATUS", Command.ProfileStatus.smsText())
        assertEquals("PROFILE LIST", Command.ProfileList.smsText())
        assertEquals("PROFILE 1", switch(1, 100).smsText())
        assertEquals("PROFILE 2 115", switch(2, 115).smsText())
        assertEquals("PROFILE 12 50", switch(12, 50).smsText())
        assertFalse(switch(2, 115).smsText().contains("LocalProfile"))
    }

    @Test
    fun `PROF-05 kontekst parsera i brak ujawniania w toString`() {
        assertEquals(ExpectedCommand.ProfileStatus, Command.ProfileStatus.toExpectedCommand())
        assertEquals(ExpectedCommand.ProfileList, Command.ProfileList.toExpectedCommand())
        val command = switch(2, 115)
        val expected = command.toExpectedCommand() as ExpectedCommand.ProfileSwitch
        assertEquals("LocalProfile2", expected.profileName)
        assertEquals(115, expected.percent)
        val validation = Command.ProfileSwitch.of(ProfileChoice(2, "LocalProfile2"), 115)
        val texts = listOf(command.toString(), expected.toString(), validation.toString(), ProfileChoice(2, "LocalProfile2").toString())
        for (t in texts) {
            assertFalse(t.contains("LocalProfile"), t)
            assertFalse(t.contains("115"), t)
        }
    }

    @Test
    fun `PROF-05 ExpectedCommand ProfileSwitch odrzuca nazwe pusta z LF i procent mniejszy niz 1`() {
        assertThrows(IllegalArgumentException::class.java) { ExpectedCommand.ProfileSwitch("", 100) }
        assertThrows(IllegalArgumentException::class.java) { ExpectedCommand.ProfileSwitch("A\nB", 100) }
        assertThrows(IllegalArgumentException::class.java) { ExpectedCommand.ProfileSwitch("A", 0) }
    }

    @Test
    fun `PROF-05 parser do zad 2 zwraca Unrecognized dla polecen profilu`() {
        val all = listOf(ExpectedCommand.ProfileStatus, ExpectedCommand.ProfileList, ExpectedCommand.ProfileSwitch("A", 100))
        for (e in all) {
            assertTrue(AapsReplyParser.parse("A (100%)", e) is AapsReply.Unrecognized)
        }
    }
}
