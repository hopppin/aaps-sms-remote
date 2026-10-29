package pl.twojanazwa.aapsremote.ui.commands

import pl.twojanazwa.aapsremote.commands.BolusAmountError
import pl.twojanazwa.aapsremote.commands.CarbsAmountError
import pl.twojanazwa.aapsremote.commands.Command
import pl.twojanazwa.aapsremote.commands.CommandLimits
import pl.twojanazwa.aapsremote.commands.ProfileChoice
import pl.twojanazwa.aapsremote.commands.ProfileSwitchError
import pl.twojanazwa.aapsremote.data.CommandLimitsState

/** Podsumowanie z PIN-em dla polecenia z kodem (CMDUI-10, BOL-15); `toString()` bez ilości. */
class SummaryState(val command: Command) {
    override fun equals(other: Any?): Boolean = other is SummaryState && other.command == command

    override fun hashCode(): Int = command.hashCode()

    override fun toString(): String = "SummaryState(***)"
}

/** Formularz procentu dla wybranego profilu (PROF-15); `toString()` bez nazwy i procentu. */
class ProfileFormState(val choice: ProfileChoice, val percentText: String, val error: ProfileSwitchError? = null) {
    fun copy(percentText: String = this.percentText, error: ProfileSwitchError? = this.error) =
        ProfileFormState(choice, percentText, error)

    override fun equals(other: Any?): Boolean =
        other is ProfileFormState && other.choice == choice && other.percentText == percentText && other.error == error

    override fun hashCode(): Int = 31 * (31 * choice.hashCode() + percentText.hashCode()) + (error?.hashCode() ?: 0)

    override fun toString(): String = "ProfileFormState(***)"
}

data class CommandsUiState(
    val number: NumberReadiness = NumberReadiness.LOADING,
    val smsGranted: Boolean = false,
    val secret: SecretReadiness = SecretReadiness.UNKNOWN,
    val commandActive: Boolean = false,
    /** LIM-05: bez wartości domyślnych zamiast nieodczytanych limitów. */
    val limits: CommandLimitsState = CommandLimitsState.Loading,
    /** LIM-09: true tylko po „Tak” w oknie „Na pewno” dla ilości z podsumowania. */
    val extraConfirmed: Boolean = false,
    val launching: Boolean = false,
    val carbsInput: String = "",
    val carbsError: CarbsAmountError? = null,
    val bolusInput: String = "",
    val bolusError: BolusAmountError? = null,
    /** Okno „Na pewno X?” (CMDUI-09, BOL-14) dla polecenia powyżej progu. */
    val confirming: Command? = null,
    /** Podsumowanie otwarte dla tego polecenia (CMDUI-10, BOL-15). */
    val summary: SummaryState? = null,
    /** Formularz zmiany profilu (PROF-15), otwarty po wyborze z wyniku listy. */
    val profile: ProfileFormState? = null,
    val pinText: String = "",
    val message: CommandMessage? = null,
    /** Argumenty komunikatu (BOLUS_INTERVAL: N i M minut). */
    val messageArgs: List<Int> = emptyList(),
    /**
     * Przekazanie ochrony okna do przebiegu (CMDUI-23): true od `LaunchResult.Started`, false dopiero,
     * gdy kolektor zobaczy pusty `CurrentRun`.
     */
    val runHandoff: Boolean = false,
) {
    val loadedLimits: CommandLimits? get() = (limits as? CommandLimitsState.Loaded)?.limits

    val summaryGrams: Int? get() = (summary?.command as? Command.Carbs)?.grams

    val confirmingLargeGrams: Int? get() = (confirming as? Command.Carbs)?.grams

    val confirmingLargeCentiUnits: Int? get() = (confirming as? Command.Bolus)?.centiUnits

    /** LIM-08. */
    val quickValues: List<Int> get() = loadedLimits?.carbsQuickValues.orEmpty()

    val carbsExtraConfirmationAboveGrams: Int? get() = loadedLimits?.carbsExtraConfirmationAboveGrams

    private val ready: Boolean
        get() = number == NumberReadiness.CONFIGURED && smsGranted && !commandActive && !launching

    val canSendNoCode: Boolean get() = ready

    val canStartCarbs: Boolean
        get() = ready && loadedLimits != null && secret != SecretReadiness.ABSENT && secret != SecretReadiness.INVALIDATED

    /** BOL-13: dostępność jak CARBS (numer, SMS, sekret, limity `Loaded`, brak polecenia w toku). */
    val canStartBolus: Boolean get() = canStartCarbs

    /** PROF-15: „Dalej” wymaga numeru, SMS, sekretu i braku polecenia; limity nie są potrzebne. */
    val canStartProfile: Boolean
        get() = ready && secret != SecretReadiness.ABSENT && secret != SecretReadiness.INVALIDATED

    val canSubmitPin: Boolean get() = summary != null && pinText.isNotEmpty() && !launching

    /** CMDUI-09, CMDUI-23: okno „Na pewno”, podsumowanie, uruchamianie albo przekazanie do przebiegu. */
    val secureWindow: Boolean get() = confirming != null || summary != null || profile != null || launching || runHandoff

    /** CMDUI-24: Wstecz obsługuje ten ViewModel. */
    val interceptsBack: Boolean get() = confirming != null || summary != null || profile != null

    /** Bez PIN-u, tekstów pól i ilości (CMDUI-26, BOL-18). */
    override fun toString(): String =
        "CommandsUiState(number=$number, smsGranted=$smsGranted, secret=$secret, commandActive=$commandActive, limits=${limits::class.simpleName}, " +
            "launching=$launching, carbsError=$carbsError, bolusError=$bolusError, confirming=${confirming != null}, " +
            "summary=${summary != null}, profile=${profile != null}, message=$message, runHandoff=$runHandoff)"
}
