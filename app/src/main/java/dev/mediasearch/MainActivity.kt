package dev.mediasearch

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import dev.mediasearch.ui.MediaSearchApp

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        androidx.lifecycle.ViewModelProvider(this)[SearchViewModel::class.java].refreshAccounts()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // uiMode is handled in place to retain the login WebView; refresh system-bar contrast too.
        enableEdgeToEdge()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: SearchViewModel = viewModel()
            MediaSearchApp(model)
            LaunchedEffect(Unit) {
                withFrameNanos { }
                reportFullyDrawn()
            }
        }
    }
}
