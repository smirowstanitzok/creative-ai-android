package com.creativeai.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Persistiert den RunPod-API-Key im app-privaten DataStore. */
class SettingsStore(private val context: Context) {

    private val apiKeyPref = stringPreferencesKey("runpod_api_key")

    val apiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[apiKeyPref].orEmpty()
    }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { prefs -> prefs[apiKeyPref] = value.trim() }
    }
}
