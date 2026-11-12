package com.sitecouncil.ui.start

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sitecouncil.R
import com.sitecouncil.data.HistoryRow
import com.sitecouncil.ui.formatTokens
import com.sitecouncil.ui.formatUsd
import com.sitecouncil.ui.theme.scoreColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    onStarted: () -> Unit,
    onOpenResult: (Long) -> Unit,
    onSettings: () -> Unit,
    viewModel: StartViewModel = hiltViewModel(),
) {
    val history by viewModel.items.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    val submit = {
        if (viewModel.start()) onStarted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.start_title), style = MaterialTheme.typography.headlineSmall)
            }

            if (!viewModel.hasKey) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.start_no_key))
                            TextButton(onClick = onSettings) {
                                Text(stringResource(R.string.start_open_settings))
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = viewModel.url,
                    onValueChange = {
                        viewModel.url = it
                        viewModel.clearError()
                    },
                    label = { Text(stringResource(R.string.start_url_label)) },
                    placeholder = { Text(stringResource(R.string.start_url_placeholder)) },
                    singleLine = true,
                    isError = viewModel.errorRes != null,
                    supportingText = viewModel.errorRes?.let { res -> { Text(stringResource(res)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.audience,
                    onValueChange = { viewModel.audience = it },
                    label = { Text(stringResource(R.string.start_audience_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.goal,
                    onValueChange = { viewModel.goal = it },
                    label = { Text(stringResource(R.string.start_goal_label)) },
                    placeholder = { Text(stringResource(R.string.start_goal_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !viewModel.desktop,
                        onClick = { viewModel.selectDesktop(false) },
                        label = { Text(stringResource(R.string.start_device_mobile)) },
                    )
                    FilterChip(
                        selected = viewModel.desktop,
                        onClick = { viewModel.selectDesktop(true) },
                        label = { Text(stringResource(R.string.start_device_desktop)) },
                    )
                }
            }
            item {
                val estimate = viewModel.estimate
                Text(
                    stringResource(
                        R.string.start_estimate,
                        formatTokens(estimate.inputTokens + estimate.outputTokens),
                        formatUsd(estimate.costUsd),
                        estimate.calls,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Button(onClick = submit, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.start_analyze))
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.start_history), style = MaterialTheme.typography.titleMedium)
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.start_history_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(history, key = { it.id }) { row ->
                    HistoryItem(
                        row = row,
                        onClick = { onOpenResult(row.id) },
                        onDelete = { viewModel.delete(row.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(row: HistoryRow, onClick: () -> Unit, onDelete: () -> Unit) {
    val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(row.createdAt))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.totalScore.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scoreColor(row.totalScore),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(row.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}
