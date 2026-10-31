package com.gabrieleforghieri.peak

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gabrieleforghieri.peak.design.PeakTheme
import com.gabrieleforghieri.peak.design.rememberReduceMotion
import com.gabrieleforghieri.peak.domain.AppSettings
import com.gabrieleforghieri.peak.ui.PeakRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // L'interfaccia è solo scura (come su iPhone): barre di sistema trasparenti con icone chiare.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val app = application as PeakApplication
        setContent {
            val settings by app.settings.settings.collectAsState(initial = AppSettings())
            PeakTheme(settings.accent, rememberReduceMotion()) { PeakRoot() }
        }
    }
}
