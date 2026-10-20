package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class SessionUnknownInvariantTest {

    @TestFactory
    fun `SESSION-14 wynik Unknown tylko gdy kod trafil do portu wysylania`(): List<DynamicTest> =
        sessionScenarios.map { scenario ->
            DynamicTest.dynamicTest("SESSION-14 ${scenario.name}") {
                runTest {
                    val run = playChecked(scenario)
                    val codeReachedPort = run.sender.sentCodeCopies.isNotEmpty()
                    val unknown = run.result.outcome is SessionOutcome.Unknown
                    if (!codeReachedPort) assertFalse(unknown, "bez kodu w porcie wynik nie moze byc Unknown")
                    if (unknown) assertTrue(codeReachedPort, "Unknown wymaga SMS-a z kodem w porcie")
                }
            }
        }

    @TestFactory
    fun `SESSION-14 lista scenariuszy obejmuje kazdy powod Unknown i Failed`(): List<DynamicTest> {
        val keys = sessionScenarios.map { it.expectedKey }.toSet()
        return listOf(
            DynamicTest.dynamicTest("SESSION-14 pokrycie powodow Unknown") {
                UnknownReason.entries.forEach { assertTrue("Unknown.$it" in keys, "brak scenariusza Unknown.$it") }
            },
            DynamicTest.dynamicTest("SESSION-14 pokrycie powodow Failed") {
                FailureReason.entries.forEach { assertTrue("Failed.$it" in keys, "brak scenariusza Failed.$it") }
            },
        )
    }
}
