package com.sitecouncil.ui.progress

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sitecouncil.R
import com.sitecouncil.analysis.AnalysisController
import com.sitecouncil.analysis.AnalysisState
import com.sitecouncil.analysis.Step
import com.sitecouncil.analysis.StepStatus
import com.sitecouncil.experts.ExpertDef
import com.sitecouncil.ui.theme.ScoreBad
import com.sitecouncil.ui.theme.ScoreGood
import com.sitecouncil.ui.toMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val controller: AnalysisController,
) : ViewModel() {
    val state = controller.state
    fun cancel() = controller.cancel()
    fun reset() = controller.reset()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onDone: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val current = state
    LaunchedEffect(current) {
        if (current is AnalysisState.Done) {
            viewModel.reset()
            onDone(current.resultId)
        }
    }

    val leave = {
        if (current is AnalysisState.Running) viewModel.cancel() else viewModel.reset()
        onBack()
    }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (current is AnalysisState.Failed) R.string.progress_failed_title
                            else R.string.progress_title
                        )
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            val steps = when (current) {
                is AnalysisState.Running -> current.steps
                is AnalysisState.Failed -> current.steps
                else -> emptyList()
            }

            if (current is AnalysisState.Running) {
                val done = steps.count { it.status == StepStatus.DONE || it.status == StepStatus.FAILED }
                LinearProgressIndicator(
                    progress = { if (steps.isEmpty()) 0f else done / steps.size.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (current is AnalysisState.Failed) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(current.error.toMessage(context), modifier = Modifier.padding(16.dp))
                }
            }

            if (current is AnalysisState.Idle) {
                Text(stringResource(R.string.progress_idle), modifier = Modifier.padding(vertical = 16.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(steps, key = { it.id }) { step -> StepRow(step) }
            }

            OutlinedButton(
                onClick = leave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    stringResource(
                        if (current is AnalysisState.Running) R.string.progress_cancel else R.string.action_back
                    )
                )
            }
        }
    }
}

@Composable
private fun StepRow(step: Step) {
    val label = stepLabel(step.id)
    val statusText = stringResource(
        when (step.status) {
            StepStatus.PENDING -> R.string.step_status_pending
            StepStatus.RUNNING -> R.string.step_status_running
            StepStatus.DONE -> R.string.step_status_done
            StepStatus.FAILED -> R.string.step_status_failed
        }
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $statusText" },
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (step.status) {
                StepStatus.PENDING -> Unit
                StepStatus.RUNNING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                StepStatus.DONE -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ScoreGood)
                StepStatus.FAILED -> Icon(Icons.Default.Warning, contentDescription = null, tint = ScoreBad)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                label,
                color = if (step.status == StepStatus.PENDING) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (step.detail.isNotBlank()) {
                Text(
                    step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun stepLabel(id: String): String {
    val expertKey = AnalysisController.expertKeyOf(id)
    if (expertKey != null) {
        val def = ExpertDef.byKey(expertKey)
        val name = if (def != null) stringResource(def.labelRes) else expertKey
        return stringResource(R.string.step_expert, name)
    }
    return stringResource(
        when (id) {
            AnalysisController.STEP_LOAD -> R.string.step_load
            AnalysisController.STEP_TECH -> R.string.step_tech
            AnalysisController.STEP_PERSONAS_GEN -> R.string.step_personas_gen
            AnalysisController.STEP_PERSONAS_RUN -> R.string.step_personas_run
            AnalysisController.STEP_MODERATOR -> R.string.step_moderator
            AnalysisController.STEP_HEATMAP -> R.string.step_heatmap
            else -> R.string.step_save
        }
    )
}
