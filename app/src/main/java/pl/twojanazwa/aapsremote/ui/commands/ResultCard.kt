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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.CommandId

@Composable
fun ResultCard(
    result: ResultView,
    secret: SecretReadiness,
    onProfileChosen: (Int) -> Unit,
    onResultDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        Text(
            commandTitle(
                result.commandId, result.commandGrams, result.commandCentiUnits,
                result.profileNumber, result.profilePercent,
            ),
            style = MaterialTheme.typography.headlineSmall,
        )
        Card(colors = CardDefaults.cardColors(containerColor = container)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(result.kind.titleRes()), style = MaterialTheme.typography.titleLarge)
                Text(resultMessageText(result.message, result.messageArg))
                if (result.codeNotSent) Text(stringResource(R.string.result_code_not_sent))
                if (result.unknownWarning) {
                    Text(stringResource(unknownWarningRes(result.commandId == CommandId.BOLUS)))
                }
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
        result.profileItems?.takeIf { it.isNotEmpty() }?.let { items ->
            // PROF-14: pozycje nieaktywne przy braku sekretu, z banerem.
            val enabled = secret != SecretReadiness.ABSENT && secret != SecretReadiness.INVALIDATED
            secret.bannerRes()?.let { Card { Text(stringResource(it), Modifier.padding(16.dp)) } }
            items.forEach { item ->
                OutlinedButton(
                    onClick = { onProfileChosen(item.number) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.result_profile_item, item.number, item.name)) }
            }
        }
        Button(onClick = onResultDismissed, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.result_close))
        }
    }
}
