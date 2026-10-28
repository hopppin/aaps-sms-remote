package pl.twojanazwa.aapsremote.ui.commands

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.twojanazwa.aapsremote.R
import pl.twojanazwa.aapsremote.commands.CommandId

@Composable
fun RunScreen(run: RunView.Active, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val titleRes = when (run.commandId) {
            CommandId.BG -> R.string.run_title_bg
            CommandId.PUMP -> R.string.run_title_pump
            CommandId.CARBS -> R.string.run_title_carbs
        }
        if (run.commandGrams == null) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.headlineSmall)
        } else {
            Text(stringResource(titleRes, run.commandGrams), style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(run.steps) { step ->
                ListItem(
                    headlineContent = {
                        val arg = ProgressSteps.minutesArg(step.id)
                        Text(if (arg == null) stringResource(step.id.textRes()) else stringResource(step.id.textRes(), arg))
                    },
                    leadingContent = {
                        when (step.status) {
                            StepStatus.DONE -> StepCheckmark()
                            StepStatus.CURRENT -> CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                            StepStatus.PENDING -> StepCircle()
                        }
                    },
                )
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Text(stringResource(R.string.run_foreground_hint), Modifier.padding(16.dp))
        }
        if (run.unexpectedCount > 0) {
            Text(stringResource(R.string.run_unexpected, run.unexpectedCount), style = MaterialTheme.typography.bodyMedium)
        }
        when (run.cancelMode) {
            CancelMode.CANCEL -> OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.run_cancel))
            }
            CancelMode.STOP_WAITING -> OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.run_stop_waiting))
            }
            CancelMode.NONE -> Unit
        }
    }
}

@Composable
private fun StepCheckmark() {
    Canvas(Modifier.padding(4.dp).size(24.dp)) {
        val check = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.52f)
            lineTo(size.width * 0.43f, size.height * 0.75f)
            lineTo(size.width * 0.82f, size.height * 0.25f)
        }
        drawPath(check, color = Color(0xFF2E7D32), style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun StepCircle() {
    Canvas(Modifier.padding(4.dp).size(24.dp)) {
        drawCircle(
            color = Color.Gray,
            radius = size.minDimension / 3,
            center = Offset(size.width / 2, size.height / 2),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
