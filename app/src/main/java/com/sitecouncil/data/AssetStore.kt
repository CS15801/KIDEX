package com.sitecouncil.data

import android.content.Context
import com.sitecouncil.domain.JsonExtractor
import com.sitecouncil.domain.Price
import com.sitecouncil.domain.PricingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Lädt Prompts und Konfiguration aus dem assets-Ordner. */
@Singleton
class AssetStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = ConcurrentHashMap<String, String>()

    fun load(path: String): String = cache.getOrPut(path) {
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** Ersetzt Platzhalter der Form {{name}}. */
    fun render(path: String, vars: Map<String, String>): String {
        var text = load(path)
        for ((key, value) in vars) {
            text = text.replace("{{$key}}", value)
        }
        return text
    }

    val weights: Map<String, Double> by lazy {
        try {
            JsonExtractor.json.decodeFromString(
                MapSerializer(String.serializer(), Double.serializer()),
                load("config/weights.json"),
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private val pricing: PricingConfig by lazy {
        try {
            JsonExtractor.json.decodeFromString(PricingConfig.serializer(), load("config/pricing.json"))
        } catch (e: Exception) {
            PricingConfig()
        }
    }

    fun priceFor(model: String): Price = pricing.priceFor(model)
}
