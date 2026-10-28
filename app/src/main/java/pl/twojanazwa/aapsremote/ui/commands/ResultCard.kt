package pl.twojanazwa.aapsremote.ui.commands

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.twojanazwa.aapsremote.R

@Composable
fun ResultCard(result: ResultView, onResultDismissed: () -> Unit, modifier: Modifier = Modifier) {
    val container = when (result.kind) {
        ResultKind.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        ResultKind.FAILURE -> MaterialTheme.colorScheme.errorContainer
        ResultKind.NO_RESPONSE, ResultKind.UNKNOWN -> MaterialTheme.colorScheme.tertiaryContainer
        ResultKind.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (result.commandGrams == null) {
            Text(stringResource(result.commandId.titleRes()), style = MaterialTheme.typography.headlineSmall)
        } else {
            Text(stringResource(result.commandId.titleRes(), result.commandGrams), style = MaterialTheme.typography.headlineSmall)
        }
        Card(colors = CardDefaults.cardColors(containerColor = container)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(result.kind.titleRes()), style = MaterialTheme.typography.titleLarge)
                Text(if (result.messageArg == null) stringResource(result.message.textRes()) else stringResource(result.message.textRes(), result.messageArg))
                if (result.codeNotSent) Text(stringResource(R.string.result_code_not_sent))
                if (result.unknownWarning) Text(stringResource(R.string.result_unknown_warning))
                if (result.unexpectedCount > 0) Text(stringResource(R.string.run_unexpected, result.unexpectedCount))
                if (result.replyText != null) {
                    Text(stringResource(R.string.result_reply_label), style = MaterialTheme.typography.titleSmall)
                    Text(
                        result.replyText,
                        Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Button(onClick = onResultDismissed, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.result_close))
        }
    }
}
