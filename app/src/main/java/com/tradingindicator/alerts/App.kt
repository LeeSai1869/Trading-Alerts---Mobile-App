package com.tradingindicator.alerts

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val config = getSharedPreferences("push_config", MODE_PRIVATE)
        val appId = config.getString("app_id", null) ?: return
        if (FirebaseApp.getApps(this).isNotEmpty()) return
        runCatching {
            FirebaseApp.initializeApp(this, FirebaseOptions.Builder()
                .setApplicationId(appId)
                .setProjectId(config.getString("project_id", "")!!)
                .setApiKey(config.getString("api_key", "")!!)
                .setGcmSenderId(config.getString("sender_id", "")!!)
                .build())
        }.onFailure { android.util.Log.w("PushSetup", "Saved Firebase configuration could not be loaded", it) }
    }
}
