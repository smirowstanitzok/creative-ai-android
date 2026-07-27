package com.creativeai.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.creativeai.app.ui.BrowserScreen
import com.creativeai.app.ui.DashboardScreen
import com.creativeai.app.ui.DashboardViewModel
import com.creativeai.app.ui.theme.CreativeAiTheme

/** Hält die WebView-Instanz über Navigationswechsel hinweg am Leben. */
class WebViewHolder {
    var webView: WebView? = null
    var loadedUrl: String? = null
}

enum class Screen { Dashboard, Browser }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CreativeAiTheme {
                val app = application as CreativeAiApp
                val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(app))
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: DashboardViewModel) {
    val state by vm.state.collectAsState()
    val browser by vm.browser.collectAsState()
    val context = LocalContext.current

    var screen by rememberSaveable { mutableStateOf(Screen.Dashboard) }
    val webViewHolder = remember { WebViewHolder() }

    // Browser-Sitzung entfernt (z. B. Pod gestoppt) → zurück zur Übersicht.
    LaunchedEffect(browser) {
        if (browser == null && screen == Screen.Browser) screen = Screen.Dashboard
    }

    when (screen) {
        Screen.Dashboard -> DashboardScreen(
            state = state,
            hasBrowserSession = browser != null,
            messages = vm.messages,
            onStart = vm::startPod,
            onStop = vm::stopPod,
            onOpen = { profile, target ->
                vm.openBrowser(profile, target)
                screen = Screen.Browser
            },
            onReturnToBrowser = { screen = Screen.Browser },
            onRefresh = vm::refresh,
            onSetApiKey = vm::setApiKey,
            onTerminateAll = vm::terminateAll,
            onOpenRunpod = { openExternal(context, "https://console.runpod.io/") },
        )

        Screen.Browser -> BrowserScreen(
            session = browser,
            holder = webViewHolder,
            onBack = { screen = Screen.Dashboard },
            onOpenExternal = { url -> openExternal(context, url) },
        )
    }
}

private fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
