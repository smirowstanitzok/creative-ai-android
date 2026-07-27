package com.creativeai.app.data

import kotlinx.serialization.Serializable

/** Ausschnitt des Pod-Objekts der RunPod-REST-API, den die App benötigt. */
@Serializable
data class RunpodPod(
    val id: String,
    val name: String? = null,
    val desiredStatus: String? = null,
    val costPerHr: Double? = null,
    val adjustedCostPerHr: Double? = null,
) {
    val effectiveCostPerHr: Double? get() = adjustedCostPerHr ?: costPerHr
    val isTerminated: Boolean get() = desiredStatus == "TERMINATED" || desiredStatus == "EXITED"
}
