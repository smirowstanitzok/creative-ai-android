package com.creativeai.app.ui

import com.creativeai.app.data.Profile

/** Ein öffenbares Ziel einer Card (Anwendung, FileBrowser …). */
data class OpenTarget(val label: String, val url: String)

private const val JUPYTER_PORT = 8888
private const val FILEBROWSER_PORT = 8080

private fun proxyUrl(podId: String, port: Int): String =
    "https://$podId-$port.proxy.runpod.net"

private fun labelForPort(port: Int): String = when (port) {
    JUPYTER_PORT -> "JupyterLab"
    FILEBROWSER_PORT -> "FileBrowser"
    else -> "Anwendung"
}

/**
 * Öffenbare Ziele einer Card. Der JupyterLab-Zusatzport (8888) wird bewusst
 * ausgeblendet – es sei denn, er ist der primäre (und damit einzige) Port des
 * Profils, dann ist JupyterLab selbst die Anwendung.
 *
 * Ergebnis: 1 Ziel → Card öffnet direkt; mehrere Ziele → Auswahldialog.
 */
fun openTargets(profile: Profile, podId: String?): List<OpenTarget> {
    if (podId == null) return emptyList()
    val targets = mutableListOf<OpenTarget>()
    targets += OpenTarget(labelForPort(profile.primaryPort), proxyUrl(podId, profile.primaryPort))
    for (port in profile.extraPorts) {
        if (port == JUPYTER_PORT) continue
        targets += OpenTarget(labelForPort(port), proxyUrl(podId, port))
    }
    return targets
}
