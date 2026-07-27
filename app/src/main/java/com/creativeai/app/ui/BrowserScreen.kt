package com.creativeai.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.creativeai.app.WebViewHolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    session: BrowserSession?,
    holder: WebViewHolder,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    if (session == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    BackHandler {
        val wv = holder.webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = session.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück zur Übersicht",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { holder.webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Neu laden")
                    }
                    IconButton(onClick = { onOpenExternal(session.url) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Im Systembrowser öffnen",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { ctx ->
                val webView = holder.webView ?: createWebView(ctx).also { holder.webView = it }
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView
            },
            update = { webView ->
                if (holder.loadedUrl != session.url) {
                    holder.loadedUrl = session.url
                    webView.loadUrl(session.url)
                }
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    webViewClient = WebViewClient()
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        builtInZoomControls = true
        displayZoomControls = false
    }
}
