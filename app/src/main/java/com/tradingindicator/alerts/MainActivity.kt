package com.tradingindicator.alerts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.tradingindicator.alerts.data.AppDatabase
import com.tradingindicator.alerts.data.Prefs
import com.tradingindicator.alerts.data.SignalMessage
import com.tradingindicator.alerts.net.ApiClient
import com.tradingindicator.alerts.net.SessionExpiredException
import com.tradingindicator.alerts.push.AlertNotifier
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var inboxObservation: Job? = null

    companion object {
        private const val DEFAULT_BASE_URL = "https://jhanzeebs-macbook-pro.tail6076d9.ts.net"
    }
    
    private val pickSoundLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data?.data != null) {
            val uri = result.data!!.data.toString()
            try {
                contentResolver.takePersistableUriPermission(result.data!!.data!!, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                Toast.makeText(this, "Choose a sound from a document provider", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            prefs.customSoundUri = uri
            Toast.makeText(this, "Custom sound selected!", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickPhoneToneLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                prefs.customSoundUri = if (android.media.RingtoneManager.isDefault(uri)) null else uri.toString()
                Toast.makeText(this, "Notification tone selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (!prefs.cookie.isNullOrBlank() && !prefs.username.isNullOrBlank()) validateSavedSession()
        else showLogin()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("send_test_notification", false)
        ) {
            postTestNotification()
        }
    }

    private fun versionLabel(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        return "Trading Alerts v${info.versionName}"
    }

    private fun createBlueSwitch(text: String, isChecked: Boolean): SwitchCompat {
        return SwitchCompat(this).apply {
            this.text = text
            this.isChecked = isChecked
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
            val colors = intArrayOf(Color.parseColor("#2196F3"), Color.parseColor("#BDBDBD"))
            thumbTintList = ColorStateList(states, colors)
            trackTintList = ColorStateList(states, colors)
            setPadding(0, 16, 0, 16)
            textSize = 16f
        }
    }

    private fun showLogin(message: String? = null) {
        inboxObservation?.cancel()
        val loginScroll = ScrollView(this)
        val lp = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        loginScroll.addView(lp)
        val url = EditText(this).apply { hint = "https://jhanzeebs-macbook-pro.tail6076d9.ts.net" }
        val user = EditText(this).apply { hint = "username" }
        val pass = EditText(this).apply { hint = "password"; inputType = 0x00000081 }
        val remember = CheckBox(this).apply {
            text = "Keep me signed in (30 days)"
            isChecked = prefs.rememberMe
        }
        val btn = Button(this).apply { text = "Sign in" }
        val status = TextView(this).apply { setTextColor(Color.RED) }
        val networkNote = TextView(this).apply {
            text = "Enter your alert server’s HTTPS address. A public HTTPS address works without Tailscale; a private .ts.net address requires it."
            textSize = 14f
        }
        lp.addView(TextView(this).apply { text = versionLabel(); textSize = 18f }); lp.addView(networkNote); lp.addView(url); lp.addView(user); lp.addView(pass); lp.addView(remember); lp.addView(btn); lp.addView(status)
        setContentView(loginScroll)
        
        url.setText(prefs.baseUrl.ifBlank { DEFAULT_BASE_URL })
        status.text = message.orEmpty()
        btn.setOnClickListener {
            val b = url.text.toString().trim().trimEnd('/')
            val username = user.text.toString()
            val password = pass.text.toString()
            val keepSignedIn = remember.isChecked
            prefs.rememberMe = keepSignedIn
            prefs.baseUrl = b
            status.text = "Checking server…"
            btn.isEnabled = false
            scope.launch {
                try {
                    val api = ApiClient(b)
                    api.checkLoginAvailability()
                    withContext(Dispatchers.Main) { status.text = "Signing in…" }
                    val r = api.login(username, password, keepSignedIn)
                    prefs.cookie = r.cookie
                    prefs.username = r.username
                    withContext(Dispatchers.Main) { showInbox() }
                    configurePush(api)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = e.message ?: e.javaClass.simpleName
                    android.util.Log.e("LoginDebug", "Login failed", e)
                    val userMessage = if (e is IOException) {
                        "Can't reach server. Check the address and, for a .ts.net address, connect this phone to Tailscale. ($msg)"
                    } else {
                        "Login failed: $msg"
                    }
                    withContext(Dispatchers.Main) {
                        status.text = userMessage
                        btn.isEnabled = true
                    }
                }
            }
        }
    }

    private fun validateSavedSession() {
        val baseUrl = prefs.baseUrl
        val cookie = prefs.cookie
        setContentView(TextView(this).apply {
            text = "Checking sign-in…"
            gravity = Gravity.CENTER
            textSize = 16f
        })
        scope.launch {
            try {
                val authenticated = ApiClient(baseUrl, cookie).hasAuthenticatedSession()
                withContext(Dispatchers.Main) {
                    if (authenticated) {
                        showInbox()
                        configurePush(ApiClient(baseUrl, cookie))
                    } else {
                        prefs.logout()
                        showLogin("Your sign-in expired. Please sign in again.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                prefs.logout()
                withContext(Dispatchers.Main) {
                    showLogin("Your sign-in expired. Please sign in again.")
                }
            } catch (e: Exception) {
                android.util.Log.w("SessionCheck", "Could not verify saved sign-in", e)
                withContext(Dispatchers.Main) {
                    showInbox()
                }
            }
        }
    }

    private fun postTestNotification() {
        val now = System.currentTimeMillis()
        val item = SignalMessage(
            id = "test:$now",
            owner = "test",
            symbol = "BTCUSDT",
            timeframe = "5m",
            price = 67234.5,
            confidence = 92.0,
            upperEdge = 68100.0,
            lowerEdge = 66500.0,
            receivedAt = now
        )
        AlertNotifier.show(this, item, "BUY", sound = true)
    }

    private fun configurePush(api: ApiClient) {
        scope.launch {
            try {
                val cfg = api.fetchFirebaseConfig()
                if (!cfg.ready) return@launch
                getSharedPreferences("push_config", MODE_PRIVATE).edit()
                    .putString("app_id", cfg.appId).putString("project_id", cfg.projectId)
                    .putString("api_key", cfg.apiKey).putString("sender_id", cfg.senderId).commit()
                val opts = FirebaseOptions.Builder()
                    .setProjectId(cfg.projectId)
                    .setApplicationId(cfg.appId)
                    .setApiKey(cfg.apiKey)
                    .apply { if (cfg.senderId.isNotBlank()) setGcmSenderId(cfg.senderId) }
                    .build()
                withContext(Dispatchers.Main) {
                    if (FirebaseApp.getApps(this@MainActivity).isEmpty()) FirebaseApp.initializeApp(this@MainActivity, opts)
                }
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        scope.launch {
                            try {
                                api.registerToken(token, cfg.packageName, prefs.notificationsOn, prefs.soundOn)
                            } catch (e: Exception) {
                                android.util.Log.w("PushSetup", "Token registration failed", e)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.w("PushSetup", "Could not retrieve Firebase token", e)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("PushSetup", "Push setup failed", e)
            }
        }
    }

    private fun showInbox() {
        inboxObservation?.cancel()
        val scroll = ScrollView(this)
        val lp = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        scroll.addView(lp)
        
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(52))
        }
        val title = TextView(this).apply {
            text = versionLabel()
            textSize = 18f
            setTypeface(typeface, Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val settingsBtn = ImageButton(this).apply {
            setImageResource(com.tradingindicator.alerts.R.drawable.ic_settings_24)
            contentDescription = "Settings"
            val backgroundValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, backgroundValue, true)
            setBackgroundResource(backgroundValue.resourceId)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        topBar.addView(title); topBar.addView(settingsBtn)
        lp.addView(topBar)

        val globalControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(48))
        }
        fun addGlobalToggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            }
            val text = TextView(this).apply {
                this.text = label
                textSize = 14f
                setTypeface(typeface, Typeface.NORMAL)
                setTextColor(Color.DKGRAY)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val toggle = SwitchCompat(this).apply {
                isChecked = checked
                showText = false
                setPadding(0, 0, 0, 0)
                setOnCheckedChangeListener { _, value -> onChanged(value) }
            }
            item.addView(text)
            item.addView(toggle)
            globalControls.addView(item)
        }
        addGlobalToggle("Notifications", prefs.notificationsOn) { prefs.notificationsOn = it; configurePush(ApiClient(prefs.baseUrl, prefs.cookie)) }
        addGlobalToggle("Sound", prefs.soundOn) { prefs.soundOn = it; configurePush(ApiClient(prefs.baseUrl, prefs.cookie)) }
        lp.addView(globalControls)

        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 32, 0, 0) }
        lp.addView(listContainer)

        settingsBtn.setOnClickListener { showSettingsMenu() }
        setContentView(scroll)
        observeInbox(listContainer)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showSettingsMenu() {
        inboxObservation?.cancel()
        val scroll = ScrollView(this)
        val lp = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        scroll.addView(lp)
        setContentView(scroll)

        val backBtn = Button(this).apply { text = "← Back to Alerts" }
        lp.addView(backBtn)
        backBtn.setOnClickListener { showInbox() }

        lp.addView(TextView(this).apply { text = "Settings · ${versionLabel()}"; textSize = 24f; setPadding(0, 32, 0, 32) })

        val coinBtn = Button(this).apply { text = "Manage Coins (Symbols)"; setPadding(0, 16, 0, 16) }
        val tfBtn = Button(this).apply { text = "Manage Timeframes"; setPadding(0, 16, 0, 16) }
        val soundBtn = Button(this).apply { text = "Pick Custom Sound from Phone"; setPadding(0, 16, 0, 16) }
        
        lp.addView(coinBtn); lp.addView(tfBtn); lp.addView(soundBtn)
        lp.addView(Button(this).apply {
            text = "Choose phone notification tone"
            setOnClickListener {
                val picker = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    .putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        prefs.customSoundUri?.let(android.net.Uri::parse)
                            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION))
                try { pickPhoneToneLauncher.launch(picker) }
                catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this@MainActivity, "Use Android notification settings to choose a tone", Toast.LENGTH_LONG).show()
                }
            }
        })
        val audio = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        lp.addView(TextView(this).apply {
            text = "Notification volume: ${audio.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION)}/${audio.getStreamMaxVolume(android.media.AudioManager.STREAM_NOTIFICATION)}. Android sound settings and Do Not Disturb control alert volume."
        })
        lp.addView(Button(this).apply {
            text = "Phone sound and volume settings"
            setOnClickListener { startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)) }
        })
        lp.addView(Button(this).apply {
            text = "Test notification sound"
            setOnClickListener { postTestNotification() }
        })
        lp.addView(Button(this).apply {
            text = "Android notification settings"
            setOnClickListener {
                val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                val channel = AlertNotifier.soundChannelId(this@MainActivity)
                val action = if (manager.getNotificationChannel(channel) != null)
                    android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                    else android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                startActivity(Intent(action)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                    .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, AlertNotifier.soundChannelId(this@MainActivity)))
            }
        })
        lp.addView(Button(this).apply {
            text = "Use default notification sound"
            setOnClickListener { prefs.customSoundUri = null; Toast.makeText(this@MainActivity, "Default sound selected", Toast.LENGTH_SHORT).show() }
        })

        coinBtn.setOnClickListener { showSubSettings("Coins", true) }
        tfBtn.setOnClickListener { showSubSettings("Timeframes", false) }
        soundBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).apply { type = "audio/*" }
            pickSoundLauncher.launch(intent)
        }

        lp.addView(Button(this).apply { 
            text = "Sign Out"; setPadding(0,64,0,0)
            setOnClickListener {
                prefs.logout()
                showLogin()
            }
        })
    }

    private fun showSubSettings(titleStr: String, isCoin: Boolean) {
        inboxObservation?.cancel()
        val scroll = ScrollView(this)
        val lp = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        scroll.addView(lp)
        setContentView(scroll)
        lp.addView(Button(this).apply {
            text = "← Back to Settings"
            setOnClickListener { showSettingsMenu() }
        })
        lp.addView(TextView(this).apply { text = "Manage $titleStr"; textSize = 22f })
        val status = TextView(this)
        lp.addView(status)
        val refresh = Button(this).apply { text = "Refresh list" }
        lp.addView(refresh)
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lp.addView(rows)
        fun display(catalog: com.tradingindicator.alerts.data.AlertCatalog) {
            rows.removeAllViews()
            val items = if (isCoin) catalog.symbols else catalog.intervals
            val mutedSet = if (isCoin) prefs.mutedSymbols else prefs.mutedTimeframes
            status.text = if (items.isEmpty()) "No $titleStr available from the server." else "${items.size} $titleStr"
            items.forEach { item ->
                rows.addView(createBlueSwitch(item, item !in mutedSet).apply {
                    setOnCheckedChangeListener { _, checked ->
                        // Read current preferences so refreshes cannot restore stale toggle state.
                        val muted = if (isCoin) prefs.mutedSymbols else prefs.mutedTimeframes
                        if (checked) muted.remove(item) else muted.add(item)
                        if (isCoin) prefs.mutedSymbols = muted else prefs.mutedTimeframes = muted
                    }
                })
            }
        }
        val cached = prefs.cachedAlertCatalog?.let {
            runCatching { com.tradingindicator.alerts.data.AlertCatalog.parse(org.json.JSONObject(it)) }.getOrNull()
        }
        if (cached != null) display(cached)
        fun load() {
            refresh.isEnabled = false
            status.text = if (rows.childCount > 0) "Refreshing $titleStr…" else "Loading $titleStr…"
            val server = prefs.baseUrl
            val cookie = prefs.cookie
            scope.launch {
                try {
                    val catalog = ApiClient(server, cookie).fetchAlertCatalog()
                    withContext(Dispatchers.Main) {
                        if (status.isAttachedToWindow && prefs.baseUrl == server) {
                            prefs.cachedAlertCatalog = catalog.toJson()
                            display(catalog)
                            refresh.isEnabled = true
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        if (status.isAttachedToWindow) {
                            status.text = if (rows.childCount > 0) "Showing saved list. Refresh failed: ${e.message}"
                                else "Could not load $titleStr: ${e.message}. Check your server connection and retry."
                            refresh.isEnabled = true
                        }
                    }
                }
            }
        }
        refresh.setOnClickListener { load() }
        load()
    }

    private fun observeInbox(container: LinearLayout) {
        val owner = prefs.username ?: return
        inboxObservation = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Refresh on entering the foreground; push inserts update the same observed table.
                loadHistory()
                AppDatabase.get(this@MainActivity).signalDao().observeForOwner(owner).collectLatest { items ->
                    if (prefs.username == owner && container.isAttachedToWindow) {
                        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
                        renderHistory(container, items.filter { it.receivedAt >= cutoff })
                    }
                }
            }
        }
    }

    private fun loadHistory() {
        val owner = prefs.username ?: return
        scope.launch {
            val dao = AppDatabase.get(this@MainActivity).signalDao()
            val oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
            dao.normalizeLegacyTimestamps()
            dao.deleteOlderThan(oneDayAgo)

            try {
                val api = ApiClient(prefs.baseUrl, prefs.cookie)
                dao.insertAll(api.fetchHistory(owner))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionExpiredException) {
                if (prefs.username != owner) return@launch
                prefs.logout()
                android.util.Log.i("HistoryDebug", "Saved sign-in expired")
                withContext(Dispatchers.Main) {
                    showLogin("Your sign-in expired. Please sign in again.")
                }
            } catch (e: Exception) {
                android.util.Log.e("HistoryDebug", "History refresh failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Signed in, but alert history could not be refreshed: ${e.message ?: e.javaClass.simpleName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun renderHistory(container: LinearLayout, items: List<SignalMessage>) {
        container.removeAllViews()
        val uniqueItems = com.tradingindicator.alerts.data.AlertPresentation.uniqueItems(items)
        if (uniqueItems.isEmpty()) {
            container.addView(TextView(this).apply { text = "No messages found in the last 24h." })
            return
        }

        val now = System.currentTimeMillis()
        val recent = uniqueItems.filter { now - it.receivedAt < TimeUnit.HOURS.toMillis(1) }
        val oneHour = uniqueItems.filter { now - it.receivedAt in TimeUnit.HOURS.toMillis(1) until TimeUnit.HOURS.toMillis(12) }
        val twelveHours = uniqueItems.filter { now - it.receivedAt in TimeUnit.HOURS.toMillis(12) until TimeUnit.DAYS.toMillis(1) }

        if (recent.isNotEmpty()) {
            container.addView(createHeader("Recent"))
            recent.forEach { container.addView(render(it)) }
        }
        if (oneHour.isNotEmpty()) {
            container.addView(createHeader("1 Hour Ago"))
            oneHour.forEach { container.addView(render(it)) }
        }
        if (twelveHours.isNotEmpty()) {
            container.addView(createHeader("12 Hours Ago"))
            twelveHours.forEach { container.addView(render(it)) }
        }
    }

    private fun createHeader(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#2196F3"))
        textSize = 18f
        setPadding(0, 32, 0, 16)
    }

    private fun render(m: SignalMessage): TextView = TextView(this).apply {
        val title = com.tradingindicator.alerts.data.AlertPresentation.title(m)
        val body = com.tradingindicator.alerts.data.AlertPresentation.lines(m).joinToString("\n")
        text = android.text.SpannableString("$title\n$body").apply {
            setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, title.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textSize = 16f
        setLineSpacing(dp(4).toFloat(), 1f)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.parseColor("#808080"))
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
