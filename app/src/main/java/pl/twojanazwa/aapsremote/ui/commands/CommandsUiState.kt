package pl.twojanazwa.aapsremote.ui.commands

import pl.twojanazwa.aapsremote.commands.CarbsAmountError
import pl.twojanazwa.aapsremote.commands.CarbsLimits

data class CommandsUiState(
    val number: NumberReadiness = NumberReadiness.LOADING,
    val smsGranted: Boolean = false,
    val secret: SecretReadiness = SecretReadiness.UNKNOWN,
    val commandActive: Boolean = false,
    val launching: Boolean = false,
    val carbsInput: String = "",
    val carbsError: CarbsAmountError? = null,
    /** Okno „Na pewno X g?” (CMDUI-09). */
    val confirmingLargeGrams: Int? = null,
    /** Podsumowanie otwarte dla tej ilości (CMDUI-10). */
    val summaryGrams: Int? = null,
    val pinText: String = "",
    val message: CommandMessage? = null,
    /**
     * Przekazanie ochrony okna do przebiegu (CMDUI-23): true od `LaunchResult.Started`, false dopiero,
     * gdy kolektor zobaczy pusty `CurrentRun`.
     */
    val runHandoff: Boolean = false,
) {
    val quickValues: List<Int> get() = CarbsLimits.QUICK_VALUES

    private val ready: Boolean
        get() = number == NumberReadiness.CONFIGURED && smsGranted && !commandActive && !launching

    val canSendNoCode: Boolean get() = ready

    val canStartCarbs: Boolean
        get() = ready && secret != SecretReadiness.ABSENT && secret != SecretReadiness.INVALIDATED

    val canSubmitPin: Boolean get() = summaryGrams != null && pinText.isNotEmpty() && !launching

    /** CMDUI-09, CMDUI-23: okno „Na pewno”, podsumowanie, uruchamianie albo przekazanie do przebiegu. */
    val secureWindow: Boolean get() = confirmingLargeGrams != null || summaryGrams != null || launching || runHandoff

    /** CMDUI-24: Wstecz obsługuje ten ViewModel. */
    val interceptsBack: Boolean get() = confirmingLargeGrams != null || summaryGrams != null

    /** Bez PIN-u, tekstu pola i gramów (CMDUI-26). */
    override fun toString(): String =
        "CommandsUiState(number=$number, smsGranted=$smsGranted, secret=$secret, commandActive=$commandActive, " +
            "launching=$launching, carbsError=$carbsError, confirming=${confirmingLargeGrams != null}, " +
            "summary=${summaryGrams != null}, message=$message, runHandoff=$runHandoff)"
}
