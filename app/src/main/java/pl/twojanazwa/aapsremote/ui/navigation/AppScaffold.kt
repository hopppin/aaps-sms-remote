package pl.twojanazwa.aapsremote.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pl.twojanazwa.aapsremote.ui.commands.CommandsPlaceholderScreen
import pl.twojanazwa.aapsremote.ui.history.HistoryPlaceholderScreen
import pl.twojanazwa.aapsremote.ui.settings.SettingsScreen

/** Scaffold z NavigationBar (kolejność = AppDestination.entries) i NavHost(startDestination = START.route). */
@Composable
fun AppScaffold(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    val label = stringResource(destination.labelRes)
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.START.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.COMMANDS.route) { CommandsPlaceholderScreen() }
            composable(AppDestination.HISTORY.route) { HistoryPlaceholderScreen() }
            composable(AppDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}
