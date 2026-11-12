package com.sitecouncil.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.sitecouncil.R
import com.sitecouncil.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
) : ViewModel() {
    var apiKey by mutableStateOf(settings.apiKey)
        private set
    var model by mutableStateOf(settings.model)
        private set
    var personaCount by mutableIntStateOf(settings.personaCount)
        private set
    var language by mutableStateOf(settings.language)
        private set

    fun updateApiKey(value: String) {
        apiKey = value
        settings.apiKey = value
    }

    fun updateModel(value: String) {
        model = value
        settings.model = value
    }

    fun updatePersonaCount(value: Int) {
        personaCount = value
        settings.personaCount = value
    }

    fun updateLanguage(value: String) {
        language = value
        settings.language = value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = viewModel.apiKey,
                onValueChange = viewModel::updateApiKey,
                label = { Text(stringResource(R.string.settings_api_key)) },
                supportingText = { Text(stringResource(R.string.settings_api_key_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.model,
                onValueChange = viewModel::updateModel,
                label = { Text(stringResource(R.string.settings_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                Text(
                    stringResource(R.string.settings_personas, viewModel.personaCount),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = viewModel.personaCount.toFloat(),
                    onValueChange = { viewModel.updatePersonaCount(it.roundToInt()) },
                    valueRange = SettingsStore.MIN_PERSONAS.toFloat()..SettingsStore.MAX_PERSONAS.toFloat(),
                    steps = SettingsStore.MAX_PERSONAS - SettingsStore.MIN_PERSONAS - 1,
                )
            }
            OutlinedTextField(
                value = viewModel.language,
                onValueChange = viewModel::updateLanguage,
                label = { Text(stringResource(R.string.settings_language)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
