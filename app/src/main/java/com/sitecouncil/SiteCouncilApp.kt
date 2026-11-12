package com.sitecouncil

import android.app.Application
import android.webkit.WebView
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SiteCouncilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Muss vor der ersten WebView gesetzt sein, sonst zeichnet sie nur den sichtbaren Ausschnitt.
        WebView.enableSlowWholeDocumentDraw()
    }
}
