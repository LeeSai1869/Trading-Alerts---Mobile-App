# Trading Alerts Android app

Android 8+ inbox for the existing TradingBotV1 backend. This repository only
contains the Android receiver; the laptop still generates and sends alerts.

## Build

Use a full Java 21 JDK, Android SDK platform 36 and build-tools 34.0.0.

```sh
./gradlew exportDebugApk testDebugUnitTest
```

The uniquely named installable package for v1.5 is
`app/build/outputs/apk/share/TradingAlerts-1.5-build6.apk`.
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

The inbox and expanded notifications use the same presentation: BUY/SELL,
symbol, timeframe, price, confidence, Sell OZ upper, Buy OZ lower and signal time.
The database update preserves existing alerts and adds the saved direction.
Previously cached direction is unknown until those alerts refresh from server
history; missing evidence displays as a dash rather than an inferred value.

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

## Folder layout

- `app/src/`: app source, resources and tests.
- `gradle/`, `gradlew`, `gradlew.bat`: required Gradle wrapper, not duplicate builds.
- `temporary-downloads/`: current verified cloud-built test APK. It is signed
  with the cloud debug key; a Mac-built package uses that Mac's debug key.
- `app/build/` and root `build/`: generated app outputs and project reports.
- `.gradle/` and `.kotlin/`: generated tool caches. Do not commit them.

To remove generated build outputs, run `./gradlew clean`. This does not remove
source, signing keys, the APK in `temporary-downloads/`, or phone data. Building
recreates the output folders. Do not delete `gradle/`, which contains the wrapper.
New releases use a new versioned filename. Superseded download APKs can be removed
in a separate cleanup, as in this change; Git history retains earlier versions.

## Locked-screen Android push test

From the app checkout on the laptop, with the phone connected/signed in,
notifications enabled and screen locked:

```sh
python3 tools/send_android_test.py --send
```

Enter the local TradingBotV1 folder path and your app username when prompted.
Use the same Python environment and `TRADINGBOT_DATA_DIR`, if set, as your
running laptop backend. The helper reads existing Firebase configuration and
registrations locally; never paste service-account credentials or tokens into chat.
It sends one visibly labeled TEST packet to the selected account/device, without
modifying backend files, alert history, registrations or delivery cursors.
Without `--send`, it only lists matching devices. It does not test signal generation
or the backend delivery queue. FCM acceptance is not proof of phone receipt.
Capture phone timing with `adb logcat -v time -s AlertDelivery PushSetup`.
