package pl.twojanazwa.aapsremote.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.data.HistoryLimits
import pl.twojanazwa.aapsremote.ui.settings.secret.SecureWindowEffect

/** HISTUI-01, HISTUI-08, HISTUI-09, HISTUI-13: zakładka Historia; FLAG_SECURE na całej zakładce. */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    SecureWindowEffect(true)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.interceptsBack) { viewModel.onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.nav_history),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.history_limit_info, HistoryLimits.MAX_ENTRIES),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        when (val content = state.content) {
            HistoryContent.Loading -> CenteredColumn {
                CircularProgressIndicator()
                Text(stringResource(R.string.history_loading))
            }
            HistoryContent.Empty -> CenteredColumn {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
            }
            HistoryContent.Unavailable -> CenteredColumn {
                Text(stringResource(R.string.history_unavailable), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = viewModel::onRetry) { Text(stringResource(R.string.history_retry)) }
            }
            is HistoryContent.Entries -> HistoryList(content.items, viewModel::onEntryClicked)
        }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) { content() }
}
