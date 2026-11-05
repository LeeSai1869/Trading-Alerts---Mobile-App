# Trading Alerts Android app

Android 8+ inbox for the existing TradingBotV1 backend. This repository only
contains the Android receiver; the laptop still generates and sends alerts.

## Build

Use a full Java 21 JDK, Android SDK platform 36 and build-tools 34.0.0.

```sh
./gradlew exportDebugApk testDebugUnitTest
```

The uniquely named installable package for v1.4 is
`app/build/outputs/apk/share/TradingAlerts-1.4-build5.apk`.
`exportDebugApk` builds before copying and includes version and build number in
the filename. Previous exported versions are retained.
It includes app dependencies. Google Play services, notification permission,
internet access and a reachable backend are still required. No Firebase private
key belongs in the APK. Firebase public configuration is fetched after login
and retained locally so push can initialize after the app process restarts.

Build on the same machine/with the same signing key as your existing installation
to install as an update. An APK signed with another key cannot replace it.
Do not uninstall merely to work around this without preserving needed local data.
For long-term team distribution, use a backed-up private release signing key;
the debug APK is intended for testing.

## Notifications

Settings offers a local sound test, the phone’s native notification-tone picker,
default/custom sound selection, a notification-volume readout, and shortcuts
to Android sound settings and the active alert channel’s settings. Android channel settings, silent mode and Do Not
Disturb take precedence. Existing muted channels remain under the user's control.
Custom sound files use Android's document picker with persistent read permission.
If access is lost, new notifications fall back to the default tone.

The receiver respects global, symbol and timeframe preferences. The server's
sound=false flag also suppresses audio. BUY/SELL can come from structured fields,
the rule name, or the sender's message. Sell OZ uses upper.high and Buy OZ uses
lower.low, matching the backend's current notification presentation. Unknown
values remain unknown.

For device diagnosis, capture `adb logcat -s AlertDelivery PushSetup` while sending
one new laptop alert. AlertDelivery logs transport delay and event age without
logging signal contents or tokens. Compare foreground, background and locked-screen
receipt. A local test verifies phone presentation, not laptop-to-Firebase delivery.

## Connecting without Tailscale

The address field already accepts a public HTTPS backend address. A stable,
authenticated public HTTPS tunnel/reverse proxy to the laptop can remove the
requirement to install Tailscale on teammates' phones. This requires separate
network/tunnel configuration; changing the APK alone cannot expose a private
laptop. Keep the laptop awake, online and running. Do not publish an unauthenticated
endpoint or include sender service-account keys in the app.

TradingBotV1 is not modified by this app upgrade. End-to-end latency depends on
its event generation, delivery queue, connectivity, Firebase and phone settings;
the APK cannot eliminate delays upstream.
