package pl.twojanazwa.aapsremote.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.ui.commands.ResultKind

/** HISTUI-02 – HISTUI-05: wiersze od najnowszego; dotknięcie otwiera szczegóły. */
@Composable
fun HistoryList(items: List<HistoryItem>, onEntryClicked: (Long) -> Unit) {
    LazyColumn {
        items(items, key = { it.id }) { item ->
            HistoryRow(item, onClick = { onEntryClicked(item.id) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit) {
    val result = item.result
    val command = historyCommandText(item.command, item.grams)
    val time = item.listTimeEpochMillis
        ?.let { HistoryTimeFormat.row(it, ZoneId.systemDefault()) }
        ?: stringResource(R.string.history_time_none)
    val message = result.messageArg
        ?.let { stringResource(result.messageRes(), it) }
        ?: stringResource(result.messageRes())
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        overlineContent = { Text(time) },
        headlineContent = { Text(command, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Column {
                Text(stringResource(result.titleRes()), style = MaterialTheme.typography.labelLarge)
                Text(message, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = { Box(Modifier.size(12.dp).background(result.kind.color(), CircleShape)) },
    )
}

/** Kolor rodzaju jak karta wyniku w Spec 009; nierozpoznany (null) jak anulowany. */
@Composable
internal fun ResultKind?.color(): Color = when (this) {
    ResultKind.SUCCESS -> MaterialTheme.colorScheme.primary
    ResultKind.FAILURE -> MaterialTheme.colorScheme.error
    ResultKind.NO_RESPONSE, ResultKind.UNKNOWN -> MaterialTheme.colorScheme.tertiary
    ResultKind.CANCELLED, null -> MaterialTheme.colorScheme.onSurfaceVariant
}
