package com.creativeai.app.data

import android.content.Context
import kotlinx.serialization.json.Json

/** Lädt die Profile aus `assets/profiles.json` (aus der Python-Referenz erzeugt). */
class ProfileRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<Profile> {
        val text = context.assets.open("profiles.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(text)
    }
}
