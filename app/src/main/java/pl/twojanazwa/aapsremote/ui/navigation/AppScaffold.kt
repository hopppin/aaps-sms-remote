package pl.twojanazwa.aapsremote.ui.navigation

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pl.twojanazwa.aapsremote.ui.commands.CommandRunViewModel
import pl.twojanazwa.aapsremote.ui.commands.CommandsScreen
import pl.twojanazwa.aapsremote.ui.commands.CommandsViewModel
import pl.twojanazwa.aapsremote.ui.history.HistoryScreen
import pl.twojanazwa.aapsremote.ui.platform.LocalSecureWindow
import pl.twojanazwa.aapsremote.ui.platform.SecureWindowCounter
import pl.twojanazwa.aapsremote.ui.settings.SettingsScreen

/** Scaffold z NavigationBar (kolejność = AppDestination.entries) i NavHost(startDestination = START.route). */
@Composable
fun AppScaffold(modifier: Modifier = Modifier) {
    val window = LocalActivity.current?.window
    // CMDUI-23: jedna flaga FLAG_SECURE dla wszystkich chronionych ekranów (licznik żądań).
    val secureWindow = remember(window) {
        SecureWindowCounter { on ->
            if (on) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    CompositionLocalProvider(LocalSecureWindow provides secureWindow) {
        AppScaffoldContent(modifier)
    }
}

@Composable
private fun AppScaffoldContent(modifier: Modifier) {
    val navController = rememberNavController()
    // ViewModele pozostają w zakresie Activity, także po przejściu do innej zakładki.
    val commandsViewModel: CommandsViewModel = hiltViewModel()
    val runViewModel: CommandRunViewModel = hiltViewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val navigateTo: (AppDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    val label = stringResource(destination.labelRes)
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = { navigateTo(destination) },
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
            composable(AppDestination.COMMANDS.route) {
                CommandsScreen(
                    commandsViewModel = commandsViewModel,
                    runViewModel = runViewModel,
                    onOpenSettings = { navigateTo(AppDestination.SETTINGS) },
                )
            }
            composable(AppDestination.HISTORY.route) { HistoryScreen() }
            composable(AppDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}
