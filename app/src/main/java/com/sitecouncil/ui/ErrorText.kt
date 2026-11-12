package com.sitecouncil.ui

import android.content.Context
import com.sitecouncil.R
import com.sitecouncil.domain.AnalysisError
import java.text.NumberFormat
import java.util.Locale

/** Übersetzt einen Analysefehler in eine Meldung aus strings.xml. */
fun AnalysisError.toMessage(context: Context): String = when (this) {
    is AnalysisError.NoApiKey -> context.getString(R.string.error_no_key)
    is AnalysisError.InvalidUrl -> context.getString(R.string.error_invalid_url)
    is AnalysisError.PageUnreachable -> context.getString(R.string.error_unreachable, detail)
    is AnalysisError.Unauthorized -> context.getString(R.string.error_unauthorized)
    is AnalysisError.RateLimit -> context.getString(R.string.error_rate_limit)
    is AnalysisError.Api -> context.getString(R.string.error_api, detail)
    is AnalysisError.Network -> context.getString(R.string.error_network, detail)
    is AnalysisError.InvalidJson -> context.getString(R.string.error_invalid_json)
    is AnalysisError.Timeout -> context.getString(R.string.error_timeout)
    is AnalysisError.AllExpertsFailed -> context.getString(R.string.error_all_experts_failed, detail)
    is AnalysisError.Unknown -> context.getString(R.string.error_unknown, detail)
}

fun formatTokens(value: Int): String = NumberFormat.getIntegerInstance(Locale.GERMANY).format(value)

fun formatUsd(value: Double): String = String.format(Locale.GERMANY, "%.2f", value)
