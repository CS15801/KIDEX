package com.sitecouncil.ui.result

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sitecouncil.R
import com.sitecouncil.data.HistoryRepository
import com.sitecouncil.domain.AnalysisResult
import com.sitecouncil.domain.ReportExporter
import com.sitecouncil.domain.ReportLabels
import com.sitecouncil.experts.ExpertDef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ResultUiState {
    data object Loading : ResultUiState
    data object Missing : ResultUiState
    data class Loaded(val result: AnalysisResult) : ResultUiState
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val history: HistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    init {
        val id = savedStateHandle.get<Long>("id") ?: -1L
        viewModelScope.launch {
            val result = history.load(id)
            _state.value = if (result == null) ResultUiState.Missing else ResultUiState.Loaded(result)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    onBack: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val current = state

    val tabs = listOf(
        R.string.tab_overview,
        R.string.tab_heatmap,
        R.string.tab_experts,
        R.string.tab_personas,
        R.string.tab_tech,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (current as? ResultUiState.Loaded)?.result?.url
                        ?: stringResource(R.string.result_title)
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (current is ResultUiState.Loaded) {
                        IconButton(onClick = { shareReport(context, current.result) }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (current) {
                is ResultUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ResultUiState.Missing -> Text(
                    stringResource(R.string.result_not_found),
                    modifier = Modifier.padding(16.dp),
                )
                is ResultUiState.Loaded -> {
                    ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                        tabs.forEachIndexed { index, res ->
                            Tab(
                                selected = tab == index,
                                onClick = { tab = index },
                                text = { Text(stringResource(res)) },
                            )
                        }
                    }
                    val result = current.result
                    when (tab) {
                        0 -> OverviewTab(result)
                        1 -> HeatmapTab(result)
                        2 -> ExpertsTab(result)
                        3 -> PersonasTab(result)
                        else -> TechTab(result)
                    }
                }
            }
        }
    }
}

private fun shareReport(context: Context, result: AnalysisResult) {
    val labels = ReportLabels(
        title = context.getString(R.string.report_title),
        date = context.getString(R.string.report_date),
        audience = context.getString(R.string.report_audience),
        goal = context.getString(R.string.report_goal),
        total = context.getString(R.string.overview_total),
        categories = context.getString(R.string.overview_categories),
        summary = context.getString(R.string.overview_summary),
        conflicts = context.getString(R.string.overview_conflicts),
        topActions = context.getString(R.string.overview_top_actions),
        experts = context.getString(R.string.tab_experts),
        personas = context.getString(R.string.tab_personas),
        successRate = context.getString(R.string.report_success_rate),
        exitReasons = context.getString(R.string.personas_exit_reasons),
        tech = context.getString(R.string.tab_tech),
        heatmapNote = context.getString(R.string.report_heatmap_note),
        statusOk = context.getString(R.string.tech_status_ok),
        statusWarn = context.getString(R.string.tech_status_warn),
        statusError = context.getString(R.string.tech_status_error),
        categoryNames = ExpertDef.entries.associate { it.key to context.getString(it.labelRes) },
    )
    val markdown = ReportExporter.toMarkdown(result, labels)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${labels.title}: ${result.url}")
        putExtra(Intent.EXTRA_TEXT, markdown)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.report_share_title)))
}
