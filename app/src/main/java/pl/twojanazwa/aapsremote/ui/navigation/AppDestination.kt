package pl.twojanazwa.aapsremote.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import pl.twojanazwa.aapsremote.R

enum class AppDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    COMMANDS("commands", R.string.nav_commands, R.drawable.ic_nav_commands),
    HISTORY("history", R.string.nav_history, R.drawable.ic_nav_history),
    SETTINGS("settings", R.string.nav_settings, R.drawable.ic_nav_settings),
    ;

    companion object {
        val START: AppDestination = COMMANDS
    }
}
