package com.creativeai.app

import android.app.Application
import com.creativeai.app.data.ProfileRepository
import com.creativeai.app.data.RunpodClient
import com.creativeai.app.data.SettingsStore

/** Einfacher Service-Container (manuelles DI-Light). */
class CreativeAiApp : Application() {
    val settingsStore by lazy { SettingsStore(this) }
    val profileRepository by lazy { ProfileRepository(this) }
    val runpodClient by lazy { RunpodClient() }
}
