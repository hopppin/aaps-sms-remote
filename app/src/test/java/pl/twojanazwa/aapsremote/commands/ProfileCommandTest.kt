package pl.twojanazwa.aapsremote.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.parser.ExpectedCommand

class ProfileCommandTest {
    private fun switch(percent: Int) =
        (Command.ProfileSwitch.of(percent) as ProfileSwitchValidation.Valid).command

    private fun err(v: ProfileSwitchValidation) = (v as ProfileSwitchValidation.Invalid).error

    @Test
    fun `PROF-01 dane wpisow profilu`() {
        fun entry(id: CommandId) = CommandCatalog.entries.single { it.id == id }
        val status = entry(CommandId.PROFILE_STATUS)
        val list = entry(CommandId.PROFILE_LIST)
        val sw = entry(CommandId.PROFILE_SWITCH)
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
        assertEquals(CommandParameter.Percent(50, 200, 100), sw.parameter)
        assertEquals(
            listOf(
                CommandId.BG, CommandId.PUMP, CommandId.CARBS, CommandId.BOLUS, CommandId.PROFILE_STATUS,
                CommandId.PROFILE_LIST, CommandId.PROFILE_SWITCH,
            ),
            CommandCatalog.entries.map { it.id },
        )
        val keywords = CommandCatalog.entries.map { it.keyword }
        assertEquals(keywords.size, keywords.toSet().size)
        assertTrue(keywords.all { Regex("[A-Z]+( [A-Z]+)*").matches(it) })
    }

    @Test
    fun `PROF-02 procent 100 50 200 jest poprawny`() {
        for (p in listOf(100, 50, 200)) {
            assertEquals(p, switch(p).percent)
            assertEquals(1, switch(p).number)
        }
        assertEquals(100, Command.ProfileSwitch.DEFAULT_PERCENT)
    }

    @Test
    fun `PROF-02 procent poza zakresem daje blad bez wyjatku`() {
        assertEquals(ProfileSwitchError.BELOW_MIN, err(Command.ProfileSwitch.of(49)))
        assertEquals(ProfileSwitchError.ABOVE_MAX, err(Command.ProfileSwitch.of(201)))
        assertEquals(ProfileSwitchError.BELOW_MIN, err(Command.ProfileSwitch.of(0)))
        assertEquals(ProfileSwitchError.BELOW_MIN, err(Command.ProfileSwitch.of(-10)))
        assertEquals(ProfileSwitchError.ABOVE_MAX, err(Command.ProfileSwitch.of(Int.MAX_VALUE)))
        assertEquals(ProfileSwitchError.BELOW_MIN, err(Command.ProfileSwitch.of(Int.MIN_VALUE)))
    }


    @Test
    fun `PROF-03 parsePercent wedlug tabeli`() {
        fun parse(t: String) = Command.ProfileSwitch.parsePercent(t)
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
        assertEquals(100, (parse("0000000000000100") as ProfileSwitchValidation.Valid).command.percent)
        assertEquals(ProfileSwitchError.BELOW_MIN, err(parse("0000000000000000")))
        assertEquals(ProfileSwitchError.ABOVE_MAX, err(parse("2147483648")))
    }

    @Test
    fun `PROF-04 tresc SMS zawsze z numerem 1 i jawnym procentem`() {
        assertEquals("PROFILE STATUS", Command.ProfileStatus.smsText())
        assertEquals("PROFILE LIST", Command.ProfileList.smsText())
        assertEquals("PROFILE 1 100", switch(100).smsText())
        assertEquals("PROFILE 1 115", switch(115).smsText())
        assertEquals("PROFILE 1 50", switch(50).smsText())
    }

    @Test
    fun `PROF-05 kontekst parsera z samym procentem i brak ujawniania w toString`() {
        assertEquals(ExpectedCommand.ProfileStatus, Command.ProfileStatus.toExpectedCommand())
        assertEquals(ExpectedCommand.ProfileList, Command.ProfileList.toExpectedCommand())
        val command = switch(115)
        val expected = command.toExpectedCommand() as ExpectedCommand.ProfileSwitch
        assertEquals(115, expected.percent)
        val validation = Command.ProfileSwitch.of(115)
        for (t in listOf(command.toString(), expected.toString(), validation.toString())) {
            assertFalse(t.contains("115"), t)
        }
    }

    @Test
    fun `PROF-05 ExpectedCommand ProfileSwitch odrzuca procent mniejszy niz 1`() {
        assertThrows(IllegalArgumentException::class.java) { ExpectedCommand.ProfileSwitch(0) }
        assertThrows(IllegalArgumentException::class.java) { ExpectedCommand.ProfileSwitch(-5) }
    }

    @Test
    fun `PROF-05 equals i hashCode ProfileSwitch`() {
        assertEquals(switch(100), switch(100))
        assertEquals(switch(100).hashCode(), switch(100).hashCode())
        assertFalse(switch(100) == switch(101))
    }
}
