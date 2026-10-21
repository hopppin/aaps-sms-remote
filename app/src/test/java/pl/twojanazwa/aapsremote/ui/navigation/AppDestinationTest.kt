package pl.twojanazwa.aapsremote.ui.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class AppDestinationTest {
    @Test
    fun `UI-01 trzy miejsca w kolejnosci Polecenia Historia Ustawienia`() {
        assertEquals(
            listOf(AppDestination.COMMANDS, AppDestination.HISTORY, AppDestination.SETTINGS),
            AppDestination.entries,
        )
    }

    @Test
    fun `UI-01 trasy sa rozne`() {
        val routes = AppDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `UI-01 miejsce startowe to Polecenia`() {
        assertEquals(AppDestination.COMMANDS, AppDestination.START)
    }

    @Test
    fun `UI-01 etykiety i ikony niezerowe a etykiety rozne`() {
        AppDestination.entries.forEach {
            assertNotEquals(0, it.labelRes)
            assertNotEquals(0, it.iconRes)
        }
        val labels = AppDestination.entries.map { it.labelRes }
        assertEquals(labels.size, labels.toSet().size)
    }
}
