package pl.twojanazwa.aapsremote.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.data.HistoryLimits
import pl.twojanazwa.aapsremote.ui.settings.secret.SecureWindowEffect

/** HISTUI-01, HISTUI-07 – HISTUI-13: zakładka Historia; FLAG_SECURE na całej zakładce. */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    SecureWindowEffect(true)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.interceptsBack) { viewModel.onBack() }

    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it.textRes()) }
    // onMessageShown dopiero po zniknięciu Snackbara, inaczej zmiana klucza anulowałaby go od razu.
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.onMessageShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val details = state.details
        if (details != null) {
            HistoryDetailsView(details, onDetailsClosed = viewModel::onDetailsClosed)
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                    TextButton(onClick = viewModel::onClearClicked, enabled = state.canClear) {
                        Text(stringResource(R.string.history_clear))
                    }
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
                    is HistoryContent.Entries -> HistoryList(content.items, onEntryClicked = viewModel::onEntryClicked)
                }
            }
        }

        state.confirmingClearCount?.let { count ->
            HistoryClearDialog(
                count = count,
                onClearConfirmed = viewModel::onClearConfirmed,
                onClearDismissed = viewModel::onClearDismissed,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
