package com.tradingindicator.alerts.data

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("trading_alerts", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = sp.getString("base_url", "") ?: ""
        set(v) { sp.edit().putString("base_url", v).apply() }

    var origin: String
        get() = sp.getString("origin", "") ?: ""
        set(v) { sp.edit().putString("origin", v).apply() }

    var cookie: String?
        get() = sp.getString("cookie", null)
        set(v) { sp.edit().putString("cookie", v).apply() }

    var username: String?
        get() = sp.getString("username", null)
        set(v) { sp.edit().putString("username", v).apply() }

    var rememberMe: Boolean
        get() = sp.getBoolean("remember_me", true)
        set(value) { sp.edit().putBoolean("remember_me", value).apply() }

    var notificationsOn: Boolean
        get() = sp.getBoolean("notifications_on", true)
        set(v) { sp.edit().putBoolean("notifications_on", v).apply() }

    var soundOn: Boolean
        get() = sp.getBoolean("sound_on", true)
        set(v) { sp.edit().putBoolean("sound_on", v).apply() }

    var customSoundUri: String?
        get() = sp.getString("custom_sound_uri", null)
        set(v) { sp.edit().putString("custom_sound_uri", v).apply() }

    var mutedSymbols: MutableSet<String>
        get() = (sp.getString("muted_symbols", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()
        set(v) { sp.edit().putString("muted_symbols", v.joinToString(",")).apply() }

    var mutedTimeframes: MutableSet<String>
        get() = (sp.getString("muted_timeframes", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()
        set(v) { sp.edit().putString("muted_timeframes", v.joinToString(",")).apply() }

    var cachedAlertCatalog: String?
        get() = if (sp.getString("catalog_server", null) == baseUrl) sp.getString("alert_catalog", null) else null
        set(value) { sp.edit().putString("catalog_server", baseUrl).putString("alert_catalog", value).apply() }

    fun logout() {
        sp.edit().remove("cookie").remove("username").apply()
    }
}
