package com.sitecouncil.ui.start

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitecouncil.R
import com.sitecouncil.analysis.AnalysisController
import com.sitecouncil.data.AssetStore
import com.sitecouncil.data.HistoryRepository
import com.sitecouncil.data.HistoryRow
import com.sitecouncil.data.SettingsStore
import com.sitecouncil.domain.AnalysisRequest
import com.sitecouncil.domain.CostEstimate
import com.sitecouncil.domain.CostEstimator
import com.sitecouncil.domain.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StartViewModel @Inject constructor(
    private val controller: AnalysisController,
    private val history: HistoryRepository,
    private val settings: SettingsStore,
    private val assets: AssetStore,
) : ViewModel() {

    var url by mutableStateOf("")
    var audience by mutableStateOf("")
    var goal by mutableStateOf("")
    var desktop by mutableStateOf(settings.desktopMode)
        private set
    var errorRes by mutableStateOf<Int?>(null)
        private set
    var hasKey by mutableStateOf(settings.apiKey.isNotBlank())
        private set
    var estimate by mutableStateOf(currentEstimate())
        private set

    val items: StateFlow<List<HistoryRow>> = history.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Beim Zurückkehren aus den Einstellungen können sich Key und Kosten geändert haben. */
    fun refresh() {
        hasKey = settings.apiKey.isNotBlank()
        estimate = currentEstimate()
    }

    private fun currentEstimate(): CostEstimate =
        CostEstimator.estimate(settings.personaCount, assets.priceFor(settings.model))

    fun selectDesktop(value: Boolean) {
        desktop = value
        settings.desktopMode = value
    }

    fun clearError() {
        errorRes = null
    }

    /** @return true, wenn die Analyse gestartet wurde. */
    fun start(): Boolean {
        if (settings.apiKey.isBlank()) {
            errorRes = R.string.error_no_key
            return false
        }
        val normalized = UrlValidator.normalize(url)
        if (normalized == null) {
            errorRes = R.string.error_invalid_url
            return false
        }
        errorRes = null
        url = normalized
        controller.start(AnalysisRequest(normalized, audience.trim(), goal.trim(), desktop))
        return true
    }

    fun delete(id: Long) {
        viewModelScope.launch { history.delete(id) }
    }
}
