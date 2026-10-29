package pl.twojanazwa.aapsremote.ui.commands

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.BolusValidation
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits

class CommandsRedactionTest {
    @Test
    fun `CMDUI-26 toString stanu panelu nie zawiera PIN-u ani ilosci`() {
        val summary = SummaryState(CommandTestFixtures.carbs(37))
        val text = CommandsUiState(carbsInput = "37", summary = summary, pinText = "4321").toString()
        assertFalse(text.contains("4321"))
        assertFalse(text.contains("37"))
        assertFalse(summary.toString().contains("37"))
    }

    @Test
    fun `BOL-18 toString stanu z bolusem 2,35 nie zawiera ilosci ani PIN-u`() {
        val command = (Command.Bolus.of(235, CommandLimits.DEFAULT) as BolusValidation.Valid).command
        val state = CommandsUiState(bolusInput = "2,35", summary = SummaryState(command), pinText = "4321")
        val text = state.toString()
        assertFalse(text.contains("2,35"))
        assertFalse(text.contains("235"))
        assertFalse(text.contains("4321"))
    }
}
