package com.creativeai.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Ein Produktionsprofil – 1:1 aus der Python-Referenz (`main.py`) übernommen.
 *
 * [dockerOverride] enthält die vom Python-Skript erzeugten Felder
 * `dockerStartCmd` bzw. `dockerEntrypoint` unverändert; sie werden beim
 * Pod-Start byte-genau in die Anfrage übernommen, damit der Container exakt so
 * provisioniert wird wie vom Skript.
 */
@Serializable
data class Profile(
    val key: String,
    val name: String,
    val provides: String,
    val hint: String,
    val image: String,
    val primaryPort: Int,
    val extraPorts: List<Int> = emptyList(),
    val httpPorts: List<Int> = emptyList(),
    val diskGb: Int,
    val gpuTypeIds: List<String> = emptyList(),
    val cudaVersions: List<String> = emptyList(),
    val jupyterEnv: String = "JUPYTER_PASSWORD",
    val needsJupyterCred: Boolean = false,
    val needsFilebrowserCred: Boolean = false,
    val dockerOverride: JsonObject = JsonObject(emptyMap()),
) {
    /** Interner Pod-Name, identisch zur Python-Namensgebung. */
    val podName: String get() = "creative-suite-$key".take(191)
}
