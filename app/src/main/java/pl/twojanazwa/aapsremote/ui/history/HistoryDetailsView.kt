package pl.twojanazwa.aapsremote.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import androidx.lifecycle.compose.LifecycleResumeEffect
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.ui.commands.formatCentiUnits
import pl.twojanazwa.aapsremote.ui.commands.unknownWarningRes

/** HISTUI-05 – HISTUI-07: szczegóły wpisu; treść SMS jako zwykły tekst bez linków (HISTUI-06). */
@Composable
fun HistoryDetailsView(details: HistoryDetails, onDetailsClosed: () -> Unit) {
    val zone = rememberHistoryZone()
    val result = details.result
    val messageText = historyMessageText(result)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.history_details_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = historyCommandText(details.command, details.grams, details.bolusCentiUnits),
            style = MaterialTheme.typography.titleMedium,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(result.titleRes()),
                    style = MaterialTheme.typography.titleMedium,
                    color = result.kind.color(),
                )
                Text(messageText, style = MaterialTheme.typography.bodyMedium)
                if (result.codeNotSent) {
                    Text(stringResource(R.string.result_code_not_sent), style = MaterialTheme.typography.bodyMedium)
                }
                if (result.unknownWarning) {
                    val bolus = details.command == HistoryCommandLabel.BOLUS ||
                        details.command == HistoryCommandLabel.BOLUS_NO_AMOUNT
                    Text(stringResource(unknownWarningRes(bolus)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        details.times.forEach { (label, millis) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(label.textRes()), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = millis?.let { HistoryTimeFormat.details(it, zone) }
                        ?: stringResource(R.string.history_time_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        details.replyText?.let { reply ->
            Text(stringResource(R.string.result_reply_label), style = MaterialTheme.typography.labelLarge)
            Text(reply, style = MaterialTheme.typography.bodyMedium)
        }
        details.unexpectedCount?.let { count ->
            Text(stringResource(R.string.run_unexpected, count), style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onDetailsClosed) { Text(stringResource(R.string.result_close)) }
    }
}

/** HISTUI-05: strefa z chwili wyświetlenia – odświeżana po powrocie na ekran (np. po zmianie strefy w ustawieniach). */
@Composable
internal fun rememberHistoryZone(): ZoneId {
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    LifecycleResumeEffect(Unit) {
        zone = ZoneId.systemDefault()
        onPauseOrDispose {}
    }
    return zone
}

/** Nazwa polecenia z ilością dla CARBS (HISTUI-03). */
@Composable
internal fun historyCommandText(command: HistoryCommandLabel, grams: Int?, bolusCentiUnits: Int?): String = when (command) {
    HistoryCommandLabel.CARBS -> stringResource(command.textRes(), grams ?: 0)
    HistoryCommandLabel.BOLUS -> stringResource(command.textRes(), formatCentiUnits(bolusCentiUnits ?: 0))
    else -> stringResource(command.textRes())
}
