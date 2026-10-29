package pl.twojanazwa.aapsremote.commands

/** Katalog poleceń MVP jako dane (CMD-01). */
object CommandCatalog {
    /** BG, PUMP, CARBS, BOLUS - w tej kolejności. */
    val entries: List<CommandDefinition> = listOf(
        CommandDefinition(
            id = CommandId.BG,
            keyword = "BG",
            parameter = CommandParameter.None,
            requiresCode = false,
            approval = Approval.ONE_TAP,
        ),
        CommandDefinition(
            id = CommandId.PUMP,
            keyword = "PUMP",
            parameter = CommandParameter.None,
            requiresCode = false,
            approval = Approval.ONE_TAP,
        ),
        CommandDefinition(
            id = CommandId.CARBS,
            keyword = "CARBS",
            parameter = CommandParameter.Grams(
                min = CarbsLimits.MIN_GRAMS,
                quickValues = CarbsLimits.QUICK_VALUES,
            ),
            requiresCode = true,
            approval = Approval.SUMMARY_WITH_PIN,
        ),
        CommandDefinition(
            id = CommandId.BOLUS,
            keyword = "BOLUS",
            parameter = CommandParameter.InsulinUnits(
                minCentiUnits = BolusLimits.MIN_CENTI_UNITS,
                stepCentiUnits = BolusLimits.STEP_CENTI_UNITS,
            ),
            requiresCode = true,
            approval = Approval.SUMMARY_WITH_PIN,
        ),
    )

    /** Zawsze zwraca wpis; każdy CommandId ma dokładnie jeden. */
    fun definitionOf(id: CommandId): CommandDefinition = entries.first { it.id == id }
}
