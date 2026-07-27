package com.creativeai.app.ui

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.creativeai.app.CreativeAiApp
import com.creativeai.app.data.Profile
import com.creativeai.app.data.RunpodPod
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PodPhase { STARTING, RUNNING, STOPPING }

/**
 * Laufzeitzustand eines Profils (kein Eintrag = im Leerlauf).
 *
 * [PodPhase.RUNNING] wird – wie im Python-Skript – **ausschließlich** gesetzt,
 * wenn der Anwendungsport per HTTP-Probe antwortet. `desiredStatus == RUNNING`
 * allein reicht nicht: RunPod liefert das sofort, während im Container noch
 * Modelle geladen werden. Bis dahin bleibt der Zustand [PodPhase.STARTING].
 */
data class PodRuntime(
    val phase: PodPhase,
    val podId: String? = null,
    val url: String? = null,
    val costPerHr: Double? = null,
    /** Zeitpunkt (ms) des Startbeginns – für die Laufzeitanzeige im Wartezustand. */
    val startedAt: Long? = null,
    /** Kurzer Fortschrittstext im Wartezustand. */
    val statusText: String? = null,
)

/** Aktuell im In-App-Browser geöffnete Anwendung. */
data class BrowserSession(val profileKey: String, val url: String, val title: String)

data class DashboardUiState(
    val loading: Boolean = true,
    val profiles: List<Profile> = emptyList(),
    val pods: Map<String, PodRuntime> = emptyMap(),
    val apiKeyConfigured: Boolean = false,
    val refreshing: Boolean = false,
) {
    /** True, sobald irgendein Profil aktiv ist – dann sind die übrigen gesperrt. */
    val busy: Boolean get() = pods.isNotEmpty()
}

class DashboardViewModel(private val app: CreativeAiApp) : AndroidViewModel(app) {

    private val client = app.runpodClient
    private val settings = app.settingsStore

    private val _state = MutableStateFlow(DashboardUiState())
    val state = _state.asStateFlow()

    private val _browser = MutableStateFlow<BrowserSession?>(null)
    val browser = _browser.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var apiKey: String = ""
    private val pollJobs = mutableMapOf<String, Job>()

    init {
        val profiles = runCatching { app.profileRepository.load() }.getOrElse { emptyList() }
        _state.update { it.copy(profiles = profiles, loading = false) }

        viewModelScope.launch {
            settings.apiKey.collect { key ->
                apiKey = key
                _state.update { it.copy(apiKeyConfigured = key.isNotBlank()) }
            }
        }
        // Sobald ein Key bekannt ist, den Ist-Stand von RunPod holen.
        viewModelScope.launch {
            settings.apiKey.first { it.isNotBlank() }
            refresh()
        }
    }

    // --------------------------------------------------------------- Aktionen

    fun startPod(profile: Profile) {
        if (apiKey.isBlank()) {
            emit("Bitte zuerst den API-Key konfigurieren (Menü → API Key konfigurieren).")
            return
        }
        if (_state.value.busy) return // Es darf jeweils nur ein Profil aktiv sein.

        val startedAt = System.currentTimeMillis()
        setPod(
            profile.key,
            PodRuntime(
                phase = PodPhase.STARTING,
                startedAt = startedAt,
                statusText = "Hardware wird angefordert …",
            ),
        )
        viewModelScope.launch {
            try {
                val pod = client.createPod(apiKey, profile)
                val url = client.proxyUrl(pod.id, profile.primaryPort)
                setPod(
                    profile.key,
                    PodRuntime(
                        phase = PodPhase.STARTING,
                        podId = pod.id,
                        url = url,
                        costPerHr = pod.effectiveCostPerHr,
                        startedAt = startedAt,
                        statusText = "Container startet …",
                    ),
                )
                trackReadiness(profile, pod.id, url, startedAt)
            } catch (e: Exception) {
                clearPod(profile.key)
                emit(e.message ?: "Start fehlgeschlagen.")
            }
        }
    }

    fun stopPod(profile: Profile) {
        val runtime = _state.value.pods[profile.key] ?: return
        pollJobs.remove(profile.key)?.cancel()
        updatePod(profile.key) { it.copy(phase = PodPhase.STOPPING) }
        viewModelScope.launch {
            try {
                runtime.podId?.let { deleteWithRetry(it) }
                clearPod(profile.key)
                if (_browser.value?.profileKey == profile.key) _browser.value = null
                emit("Instanz „${profile.name}“ gelöscht – keine weiteren Kosten.")
            } catch (e: Exception) {
                updatePod(profile.key) { it.copy(phase = PodPhase.RUNNING) }
                emit(e.message ?: "Löschen fehlgeschlagen – bitte erneut versuchen.")
            }
        }
    }

    fun refresh() {
        if (apiKey.isBlank()) {
            emit("Kein API-Key konfiguriert.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                reconcile(client.listPods(apiKey))
            } catch (e: Exception) {
                emit(e.message ?: "Aktualisieren fehlgeschlagen.")
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun terminateAll() {
        if (apiKey.isBlank()) {
            emit("Kein API-Key konfiguriert.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                val pods = client.listPods(apiKey).filter { !it.isTerminated }
                if (pods.isEmpty()) {
                    emit("Keine laufenden Pods gefunden.")
                } else {
                    var ok = 0
                    for (pod in pods) {
                        runCatching { deleteWithRetry(pod.id) }
                            .onSuccess { ok++ }
                            .onFailure { emit("Pod ${pod.id} konnte nicht gelöscht werden: ${it.message}") }
                    }
                    emit("$ok Pod(s) terminiert.")
                }
                pollJobs.values.forEach { it.cancel() }
                pollJobs.clear()
                _state.update { it.copy(pods = emptyMap()) }
                _browser.value = null
            } catch (e: Exception) {
                emit(e.message ?: "Fehler beim Terminieren.")
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun openBrowser(profile: Profile, target: OpenTarget) {
        val title = if (target.label == "Anwendung") {
            profile.name
        } else {
            "${profile.name} · ${target.label}"
        }
        _browser.value = BrowserSession(profile.key, target.url, title)
    }

    fun setApiKey(value: String) {
        viewModelScope.launch { settings.setApiKey(value) }
    }

    // ------------------------------------------------------------- Intern

    /**
     * Wartet, bis der Anwendungsport tatsächlich per HTTP antwortet
     * (STARTING → RUNNING). Bereitschaft wird – wie im Python-Skript – nur über
     * den Probe festgestellt, nicht über `desiredStatus`.
     */
    private fun trackReadiness(profile: Profile, podId: String, url: String, startedAt: Long) {
        pollJobs.remove(profile.key)?.cancel()
        pollJobs[profile.key] = viewModelScope.launch {
            val deadline = startedAt + BOOT_TIMEOUT_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                val pod = runCatching { client.getPod(apiKey, podId) }.getOrNull()
                if (pod != null) {
                    if (pod.isTerminated) {
                        clearPod(profile.key)
                        emit("Pod '${profile.name}' wurde beendet.")
                        return@launch
                    }
                    updatePod(profile.key) {
                        it.copy(
                            costPerHr = pod.effectiveCostPerHr ?: it.costPerHr,
                            statusText = "KI-Stack wird eingerichtet, Modelle werden geladen …",
                        )
                    }
                }
                if (client.probeReady(url)) {
                    updatePod(profile.key) {
                        it.copy(phase = PodPhase.RUNNING, statusText = null)
                    }
                    return@launch
                }
                delay(POLL_INTERVAL_MS)
            }
            // Zeitlimit erreicht, aber Pod läuft noch: Wartezustand mit Hinweis lassen.
            updatePod(profile.key) {
                if (it.phase == PodPhase.STARTING) {
                    it.copy(statusText = "Dauert länger als üblich – Logs in der RunPod-Console prüfen.")
                } else {
                    it
                }
            }
        }
    }

    /** Startet den Bereitschafts-Probe, falls für dieses Profil keiner läuft. */
    private fun ensureTracking(profile: Profile, podId: String, url: String, startedAt: Long) {
        val job = pollJobs[profile.key]
        if (job == null || !job.isActive) {
            trackReadiness(profile, podId, url, startedAt)
        }
    }

    private suspend fun deleteWithRetry(podId: String) {
        var lastError: Exception? = null
        for (attempt in 1..TERMINATE_ATTEMPTS) {
            try {
                client.deletePod(apiKey, podId)
                return
            } catch (e: Exception) {
                lastError = e
                delay(minOf(1000L * (1 shl attempt), 15_000L))
            }
        }
        throw lastError ?: IllegalStateException("Löschen fehlgeschlagen.")
    }

    /**
     * Gleicht die Cards mit den tatsächlich bei RunPod laufenden Pods ab.
     *
     * Entdeckte Pods gelten NICHT automatisch als bereit – ihre tatsächliche
     * Erreichbarkeit wird per Hintergrund-Probe geprüft ([ensureTracking]).
     * Nur ein bereits per Probe bestätigter RUNNING-Zustand bleibt erhalten.
     */
    private fun reconcile(remotePods: List<RunpodPod>) {
        val byName = remotePods.filter { !it.isTerminated }.associateBy { it.name }
        val profiles = _state.value.profiles
        val current = _state.value.pods
        val next = LinkedHashMap<String, PodRuntime>()
        val toTrack = mutableListOf<Triple<Profile, String, Long>>() // profile, podId, startedAt

        for (profile in profiles) {
            val remote = byName[profile.podName]
            val existing = current[profile.key]
            when {
                remote != null -> {
                    val url = client.proxyUrl(remote.id, profile.primaryPort)
                    val cost = remote.effectiveCostPerHr ?: existing?.costPerHr
                    when {
                        // Bereits per Probe bestätigt bereit und gleicher Pod → behalten.
                        existing?.phase == PodPhase.RUNNING && existing.podId == remote.id ->
                            next[profile.key] = existing.copy(url = url, costPerHr = cost)

                        existing?.phase == PodPhase.STOPPING ->
                            next[profile.key] = existing

                        // Neu entdeckt oder noch im Start: Wartezustand + Probe starten.
                        else -> {
                            val startedAt = existing?.startedAt ?: System.currentTimeMillis()
                            next[profile.key] = PodRuntime(
                                phase = PodPhase.STARTING,
                                podId = remote.id,
                                url = url,
                                costPerHr = cost,
                                startedAt = startedAt,
                                statusText = existing?.statusText ?: "Wird vorbereitet …",
                            )
                            toTrack += Triple(profile, remote.id, startedAt)
                        }
                    }
                }
                // Lokal frisch gestartet/gestoppt, aber (noch) nicht in der Liste: behalten.
                existing != null && existing.phase != PodPhase.RUNNING -> {
                    next[profile.key] = existing
                }
                // Sonst: Pod ist entfernt verschwunden → Card wieder freigeben.
                else -> pollJobs.remove(profile.key)?.cancel()
            }
        }
        _state.update { it.copy(pods = next) }
        // Erst nach dem State-Update Probes starten (sonst überschreibt das Update sie).
        toTrack.forEach { (profile, podId, startedAt) ->
            ensureTracking(profile, podId, client.proxyUrl(podId, profile.primaryPort), startedAt)
        }
    }

    private fun setPod(key: String, runtime: PodRuntime) =
        _state.update { it.copy(pods = it.pods + (key to runtime)) }

    private fun updatePod(key: String, transform: (PodRuntime) -> PodRuntime) =
        _state.update { st ->
            val existing = st.pods[key] ?: return@update st
            st.copy(pods = st.pods + (key to transform(existing)))
        }

    private fun clearPod(key: String) {
        pollJobs.remove(key)?.cancel()
        _state.update { it.copy(pods = it.pods - key) }
    }

    private fun emit(message: String) {
        _messages.tryEmit(message)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 6_000L
        private const val BOOT_TIMEOUT_MS = 25L * 60 * 1000
        private const val TERMINATE_ATTEMPTS = 5

        fun factory(app: CreativeAiApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    DashboardViewModel(app) as T
            }
    }
}
