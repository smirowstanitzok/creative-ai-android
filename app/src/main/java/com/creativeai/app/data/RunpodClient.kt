package com.creativeai.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** Fehler bei der Kommunikation mit der RunPod-API (mit lesbarer Meldung). */
class RunpodException(message: String) : Exception(message)

/**
 * Dünner Wrapper um die Pod-Endpunkte der RunPod-REST-API (v1).
 *
 * Entspricht funktional `RunpodClient` aus der Python-Referenz: Pods anlegen,
 * abfragen, auflisten und löschen. Der Erstell-Payload wird identisch aufgebaut.
 */
class RunpodClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Kürzerer Client für Bereitschafts-Proben (Fehler sind hier normal). */
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    // ------------------------------------------------------------------ Pods

    suspend fun createPod(apiKey: String, profile: Profile): RunpodPod =
        withContext(Dispatchers.IO) {
            val payload = buildCreatePayload(profile)
            val request = authed(apiKey)
                .url("$BASE_URL/pods")
                .post(payload.toString().toRequestBody(jsonMedia))
                .build()
            val body = execute(request, "POST", "/pods")
            json.decodeFromString<RunpodPod>(body)
        }

    suspend fun listPods(apiKey: String): List<RunpodPod> =
        withContext(Dispatchers.IO) {
            val request = authed(apiKey).url("$BASE_URL/pods").get().build()
            val body = execute(request, "GET", "/pods")
            json.decodeFromString<List<RunpodPod>>(body)
        }

    suspend fun getPod(apiKey: String, podId: String): RunpodPod =
        withContext(Dispatchers.IO) {
            val request = authed(apiKey).url("$BASE_URL/pods/$podId").get().build()
            val body = execute(request, "GET", "/pods/$podId")
            json.decodeFromString<RunpodPod>(body)
        }

    /** Löscht den Pod endgültig. HTTP 404 gilt – wie in Python – als Erfolg. */
    suspend fun deletePod(apiKey: String, podId: String) =
        withContext(Dispatchers.IO) {
            val request = authed(apiKey).url("$BASE_URL/pods/$podId").delete().build()
            try {
                execute(request, "DELETE", "/pods/$podId")
            } catch (e: RunpodException) {
                if (e.message?.contains("HTTP 404") == true) return@withContext
                throw e
            }
            Unit
        }

    // ------------------------------------------------------------ Readiness

    fun proxyUrl(podId: String, port: Int): String = "https://$podId-$port.proxy.runpod.net"

    /**
     * True, sobald hinter dem RunPod-Proxy ein echter Dienst antwortet.
     * Logik aus `probe_http` der Python-Referenz: 502/503/504 und Verbindungs-
     * fehler heißen „noch nicht da“; eine Nginx-Warteseite wird am Seitenanfang
     * erkannt. Alles andere gilt als bereit.
     */
    suspend fun probeReady(url: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "creative-ai-suite/healthcheck")
            .get()
            .build()
        try {
            probeClient.newCall(request).execute().use { response ->
                if (response.code in PROXY_NOT_READY_CODES) return@withContext false
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("html")) return@withContext true
                val head = response.peekBody(PROBE_BODY_BYTES).string()
                PROXY_NOT_READY_TEXTS.none { head.contains(it) }
            }
        } catch (_: Exception) {
            false
        }
    }

    // ----------------------------------------------------------- intern

    private fun authed(apiKey: String): Request.Builder = Request.Builder()
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "application/json")
        .header("User-Agent", "creative-ai-suite/2.0")

    private fun execute(request: Request, method: String, path: String): String {
        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw RunpodException("$method $path fehlgeschlagen: ${e.message}")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            when {
                it.code == 401 -> throw RunpodException(
                    "RunPod lehnt den API-Key ab (401). Ist der Key aktuell und " +
                        "hat er Schreibrechte?",
                )
                it.code >= 400 -> throw RunpodException(
                    "$method $path -> HTTP ${it.code}: ${errorDetail(text)}",
                )
                else -> return text
            }
        }
    }

    private fun errorDetail(text: String): String {
        if (text.isBlank()) return "(leere Antwort)"
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            for (key in listOf("error", "message", "detail", "errors")) {
                obj[key]?.let { return it.toString().take(300) }
            }
            text.take(300)
        } catch (_: Exception) {
            text.take(300)
        }
    }

    /**
     * Baut den POST-/pods-Payload identisch zur Python-Referenz auf. Der
     * profilspezifische Docker-Override wird unverändert eingemischt; die
     * Zusatzdienste (JupyterLab/FileBrowser) erhalten Zufallspasswörter.
     */
    private fun buildCreatePayload(profile: Profile): JsonObject {
        val credentials = buildCredentials(profile)
        return buildJsonObject {
            put("name", profile.podName)
            put("imageName", profile.image)
            put("computeType", "GPU")
            put("gpuCount", 1)
            putJsonArray("gpuTypeIds") { profile.gpuTypeIds.forEach { add(it) } }
            put("gpuTypePriority", "custom")
            put("cloudType", "SECURE")
            put("containerDiskInGb", profile.diskGb)
            put("volumeInGb", 0)
            putJsonArray("ports") {
                profile.httpPorts.forEach { add("$it/http") }
                add("22/tcp")
            }
            put("interruptible", false)
            putJsonObject("env") {
                credentials.forEach { (k, v) -> put(k, v) }
            }
            if (profile.cudaVersions.isNotEmpty()) {
                putJsonArray("allowedCudaVersions") { profile.cudaVersions.forEach { add(it) } }
            }
            // Docker-Override (dockerStartCmd / dockerEntrypoint) byte-genau übernehmen.
            profile.dockerOverride.forEach { (k, v) -> put(k, v) }
        }
    }

    private fun buildCredentials(profile: Profile): Map<String, String> {
        val creds = LinkedHashMap<String, String>()
        if (profile.needsJupyterCred) creds[profile.jupyterEnv] = tokenUrlSafe(18)
        if (profile.needsFilebrowserCred) creds["FILEBROWSER_PASSWORD"] = tokenUrlSafe(18)
        return creds
    }

    /** Wie Python `secrets.token_urlsafe(nbytes)`: URL-safe Base64 ohne Padding. */
    private fun tokenUrlSafe(nbytes: Int): String {
        val bytes = ByteArray(nbytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private companion object {
        const val BASE_URL = "https://rest.runpod.io/v1"
        const val PROBE_TIMEOUT_SECONDS = 6L
        const val PROBE_BODY_BYTES = 4096L
        val PROXY_NOT_READY_CODES = setOf(502, 503, 504)
        val PROXY_NOT_READY_TEXTS = listOf("Port Not Up Yet", "The port is not up yet")
    }
}
