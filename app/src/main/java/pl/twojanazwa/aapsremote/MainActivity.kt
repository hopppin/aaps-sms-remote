package pl.twojanazwa.aapsremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import pl.twojanazwa.aapsremote.ui.navigation.AppScaffold
import pl.twojanazwa.aapsremote.ui.theme.AapsRemoteTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AapsRemoteTheme {
                AppScaffold()
            }
        }
    }
}
