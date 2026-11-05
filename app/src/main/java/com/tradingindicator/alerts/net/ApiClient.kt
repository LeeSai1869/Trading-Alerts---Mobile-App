package com.tradingindicator.alerts.net

import com.tradingindicator.alerts.data.SignalMessage
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LoginResult(val username: String, val role: String, val cookie: String, val origin: String)
data class FirebaseConfig(
    val projectId: String, val appId: String, val apiKey: String,
    val senderId: String, val packageName: String, val ready: Boolean
)

class SessionExpiredException(message: String) : RuntimeException(message)

class ApiClient(private val rawBaseUrl: String, private var cookie: String? = null) {
    private val json = "application/json; charset=utf-8".toMediaType()

    val baseUrl: String = rawBaseUrl.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it
        else throw IllegalArgumentException("Address must start with https://")
    }

    val origin: String = baseUrl.toHttpUrlOrNull()?.let { u ->
        val portPart = if (u.port != 443 && u.port != 80) ":${u.port}" else ""
        "${u.scheme}://${u.host}$portPart"
    } ?: throw IllegalArgumentException("Invalid URL: $baseUrl")

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .cookieJar(object : CookieJar {
            private var stored: List<Cookie> = emptyList()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                stored = cookies
                if (cookies.isNotEmpty()) cookie = cookies.joinToString("; ") { "${it.name}=${it.value}" }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = stored
        })
        .build()

    private fun builder(url: String) = Request.Builder()
        .url(url)
        .header("Origin", origin)
        .header("Referer", "$origin/")
        .apply { cookie?.let { header("Cookie", it) } }

    private fun fetchSession(): JSONObject {
        val req = builder("$baseUrl/api/session").get().build()
        http.newCall(req).execute().use { r ->
            val text: String = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                if (r.code == 401) throw SessionExpiredException("Sign in required")
                throw RuntimeException("Account API returned HTTP ${r.code}: ${text.take(200)}")
            }
            val session = JSONObject(text)
            val role = session.optString("role")
            if (role !in setOf("guest", "user", "admin") || !session.has("configured")) {
                throw RuntimeException("This server does not support account login")
            }
            if (!session.optBoolean("configured")) {
                throw RuntimeException("Server accounts are not configured")
            }
            return session
        }
    }

    fun checkLoginAvailability() {
        fetchSession()
    }

    fun hasAuthenticatedSession(): Boolean {
        val role = fetchSession().optString("role")
        return role == "user" || role == "admin"
    }

    fun login(username: String, password: String): LoginResult {
        val body = JSONObject().put("username", username).put("password", password).put("remember", false)
            .toString().toRequestBody(json)
        val req = builder("$baseUrl/api/login").post(body).build()
        http.newCall(req).execute().use { r ->
            val text: String = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                val serverErr = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
                throw RuntimeException("HTTP ${r.code}: ${serverErr.ifBlank { text.take(200) }}")
            }
            val o = JSONObject(text)
            val c = cookie ?: throw RuntimeException("Server returned no cookie")
            return LoginResult(o.optString("username", username), o.optString("role", "user"), c, origin)
        }
    }

    fun fetchHistory(owner: String, before: Long? = null, limit: Int = 100): List<SignalMessage> {
        val url = if (before == null) "$baseUrl/api/alerts/history?limit=$limit"
                  else "$baseUrl/api/alerts/history?limit=$limit&before=$before"
        val req = builder(url).get().build()
        http.newCall(req).execute().use { r ->
            val text: String = r.body?.string() ?: ""
            if (!r.isSuccessful) {
                val serverErr = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
                if (r.code == 401) throw SessionExpiredException(serverErr.ifBlank { "Sign in required" })
                throw RuntimeException("HTTP ${r.code}: ${serverErr.ifBlank { text.take(200) }}")
            }
            val o = JSONObject(text)
            val arr = o.optJSONArray("history") ?: return emptyList()
            val out = ArrayList<SignalMessage>(arr.length())
            for (i in 0 until arr.length()) out += parsePacket(arr.getJSONObject(i), owner)
            return out
        }
    }

    private fun parsePacket(p: JSONObject, owner: String): SignalMessage =
        com.tradingindicator.alerts.data.SignalPacket.parse(p, owner)

    fun fetchAlertCatalog(): com.tradingindicator.alerts.data.AlertCatalog {
        val req = builder("$baseUrl/api/alerts/catalog").get().build()
        http.newCall(req).execute().use { response ->
            if (response.code == 401) throw SessionExpiredException("Sign in required")
            if (!response.isSuccessful) throw RuntimeException("Coin catalog returned HTTP ${response.code}")
            return com.tradingindicator.alerts.data.AlertCatalog.parse(JSONObject(response.body?.string().orEmpty()))
        }
    }

    fun fetchFirebaseConfig(): FirebaseConfig {
        val req = builder("$baseUrl/api/android/config").get().build()
        http.newCall(req).execute().use { r ->
            val text: String = r.body?.string() ?: ""
            val o = JSONObject(text)
            return FirebaseConfig(
                projectId = o.optString("project_id", ""),
                appId = o.optString("app_id", ""),
                apiKey = o.optString("api_key", ""),
                senderId = o.optString("sender_id", ""),
                packageName = o.optString("package_name", "com.tradingindicator.alerts"),
                ready = o.optBoolean("ready", false)
            )
        }
    }

    fun registerToken(fcmToken: String, packageName: String, enabled: Boolean, sound: Boolean) {
        val body = JSONObject()
            .put("token", fcmToken).put("package_name", packageName)
            .put("enabled", enabled).put("sound", sound)
            .toString().toRequestBody(json)
        val req = builder("$baseUrl/api/android/subscribe").post(body).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) {
                val t: String = r.body?.string() ?: ""
                throw RuntimeException("HTTP ${r.code}: $t")
            }
        }
    }
}