package com.sitecouncil.experts

import androidx.annotation.StringRes
import com.sitecouncil.R

/** Die sechs Fachexperten des Rats. [key] ist zugleich der Schlüssel in weights.json. */
enum class ExpertDef(
    val key: String,
    val promptName: String,
    val asset: String,
    @StringRes val labelRes: Int,
    val needsTech: Boolean,
) {
    LAYOUT("layout_ux", "Layout- und UX-Experte", "prompts/experts/layout_ux.txt", R.string.expert_layout, false),
    TEXTING("texting", "Texting-Experte", "prompts/experts/texting.txt", R.string.expert_text, false),
    SEO("seo", "SEO-Experte", "prompts/experts/seo.txt", R.string.expert_seo, true),
    GEO("geo", "GEO-Experte", "prompts/experts/geo.txt", R.string.expert_geo, true),
    TECH("technik", "Indexierungs- und Technik-Experte", "prompts/experts/technik.txt", R.string.expert_tech, true),
    A11Y("barrierefreiheit", "Barrierefreiheits-Experte", "prompts/experts/barrierefreiheit.txt", R.string.expert_a11y, true);

    companion object {
        fun byKey(key: String): ExpertDef? = entries.firstOrNull { it.key == key }
    }
}
