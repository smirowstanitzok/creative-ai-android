package com.creativeai.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.creativeai.app.data.Profile
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    hasBrowserSession: Boolean,
    messages: SharedFlow<String>,
    onStart: (Profile) -> Unit,
    onStop: (Profile) -> Unit,
    onOpen: (Profile, OpenTarget) -> Unit,
    onReturnToBrowser: () -> Unit,
    onRefresh: () -> Unit,
    onSetApiKey: (String) -> Unit,
    onTerminateAll: () -> Unit,
    onOpenRunpod: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        messages.collect { snackbarHostState.showSnackbar(it) }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showTerminateAllDialog by remember { mutableStateOf(false) }
    var stopTarget by remember { mutableStateOf<Profile?>(null) }
    var openChoice by remember { mutableStateOf<Pair<Profile, List<OpenTarget>>?>(null) }
    val scope = rememberCoroutineScope()

    // Öffnen erst, wenn der Anwendungsport tatsächlich antwortet (Phase RUNNING).
    // Vorher Hinweis statt einer „Seite nicht gefunden"-Fehlseite.
    val handleOpen: (Profile) -> Unit = { profile ->
        val runtime = state.pods[profile.key]
        when {
            runtime == null -> Unit
            runtime.phase != PodPhase.RUNNING -> {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "'${profile.name}' wird noch vorbereitet – gleich erreichbar.",
                    )
                }
            }
            else -> {
                val targets = openTargets(profile, runtime.podId)
                when {
                    targets.isEmpty() -> Unit
                    targets.size == 1 -> onOpen(profile, targets.first())
                    else -> openChoice = profile to targets
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CreativeAI",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menü")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Alle Pods terminieren") },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                            onClick = {
                                menuExpanded = false
                                showTerminateAllDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("API Key konfigurieren") },
                            leadingIcon = { Icon(Icons.Default.Key, null) },
                            onClick = {
                                menuExpanded = false
                                showApiKeyDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Zu Runpod") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                            onClick = {
                                menuExpanded = false
                                onOpenRunpod()
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            DashboardContent(
                state = state,
                hasBrowserSession = hasBrowserSession,
                onStart = onStart,
                onStopRequest = { stopTarget = it },
                onOpen = handleOpen,
                onReturnToBrowser = onReturnToBrowser,
                onConfigureApiKey = { showApiKeyDialog = true },
            )
        }
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            onDismiss = { showApiKeyDialog = false },
            onSave = { key ->
                onSetApiKey(key)
                showApiKeyDialog = false
            },
        )
    }

    if (showTerminateAllDialog) {
        ConfirmDialog(
            title = "Alle Pods terminieren?",
            text = "Es werden alle bei RunPod laufenden Pods abgefragt und endgültig gelöscht.",
            confirmLabel = "Ja, alle löschen",
            onConfirm = {
                showTerminateAllDialog = false
                onTerminateAll()
            },
            onDismiss = { showTerminateAllDialog = false },
        )
    }

    stopTarget?.let { profile ->
        ConfirmDialog(
            title = "KI Agent stoppen?",
            text = "Soll der KI Agent gestoppt werden? Der Pod wird terminiert und die Abrechnung endet.",
            confirmLabel = "Ja",
            dismissLabel = "Nein",
            onConfirm = {
                onStop(profile)
                stopTarget = null
            },
            onDismiss = { stopTarget = null },
        )
    }

    openChoice?.let { (profile, targets) ->
        OpenChoiceDialog(
            targets = targets,
            onPick = { target ->
                openChoice = null
                onOpen(profile, target)
            },
            onDismiss = { openChoice = null },
        )
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    hasBrowserSession: Boolean,
    onStart: (Profile) -> Unit,
    onStopRequest: (Profile) -> Unit,
    onOpen: (Profile) -> Unit,
    onReturnToBrowser: () -> Unit,
    onConfigureApiKey: () -> Unit,
) {
    if (state.loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Wähle ein Kreativ-Profil. Beim Start wird ein GPU-Pod auf " +
                    "RunPod angelegt und der KI-Stack automatisch eingerichtet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!state.apiKeyConfigured) {
            item { ApiKeyBanner(onConfigure = onConfigureApiKey) }
        }

        if (hasBrowserSession) {
            item { ReturnToBrowserBanner(onClick = onReturnToBrowser) }
        }

        items(state.profiles, key = { it.key }) { profile ->
            val runtime = state.pods[profile.key]
            ProfileCard(
                profile = profile,
                runtime = runtime,
                // Andere Cards sind gesperrt, sobald irgendein Pod aktiv ist.
                lockedByOther = state.busy && runtime == null,
                onStart = { onStart(profile) },
                onStopRequest = { onStopRequest(profile) },
                onOpen = { onOpen(profile) },
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
