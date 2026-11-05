#!/usr/bin/env python3
"""Send a clearly labeled FCM test using an existing laptop backend configuration.

No backend files, event history, device cursors or registrations are modified.
Run with the same Python and TRADINGBOT_DATA_DIR as the running backend.
"""
import argparse
from datetime import datetime
import importlib
from pathlib import Path
import sqlite3
import sys
import time


def test_event(now_ms):
    return {
        "seq": -now_ms,
        "name": "LOCKSCREEN TEST — NOT A TRADE",
        "message": "LOCKSCREEN TEST — NOT A TRADE",
        "symbol": "TEST",
        "interval": "test",
        "created": now_ms,
        "details": {"signal_ms": now_ms},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", help="Path to TradingBotV1 on this Mac")
    parser.add_argument("--owner", help="Username used to sign into the Android app")
    parser.add_argument("--send", action="store_true", help="Send one test; otherwise only list devices")
    args = parser.parse_args()
    backend = Path(args.backend or input("TradingBotV1 folder path: ").strip()).expanduser().resolve()
    if not (backend / "Section_11_UI_UX/server/android_push.py").is_file():
        parser.error("That folder does not contain the TradingBotV1 Android sender")
    owner = args.owner or input("Android app username (not password): ").strip()
    if not owner:
        parser.error("A username is required")
    # Import existing code without creating __pycache__ files inside TradingBotV1.
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(backend))
    push = importlib.import_module("Section_11_UI_UX.server.android_push")
    auth = importlib.import_module("Section_11_UI_UX.server.auth")
    db_path = auth.USERS_FILE.with_name("alerts.sqlite3").resolve()
    with sqlite3.connect(db_path.as_uri() + "?mode=ro", uri=True, timeout=5) as conn:
        conn.row_factory = sqlite3.Row
        devices = conn.execute(
            "SELECT id, token, enabled, sound FROM android_devices WHERE owner=? ORDER BY touched DESC",
            (owner,),
        ).fetchall()
    if not devices:
        parser.error("No Android registration found. Check username/profile, then open the app and sign in.")
    for index, device in enumerate(devices, 1):
        print(f"{index}: device {device['id'][:12]} enabled={bool(device['enabled'])} sound={bool(device['sound'])}")
    if not args.send:
        print("No message sent. Run again with --send to send one test.")
        return
    selected = 1 if len(devices) == 1 else int(input("Send to device number: "))
    if not 1 <= selected <= len(devices):
        parser.error("Invalid device number")
    device = devices[selected - 1]
    if not device['enabled']:
        parser.error("Notifications are disabled for this device. Enable them in the app, reopen it, then retry.")
    if not push.status().get("ready"):
        parser.error("Firebase setup is not ready in this Python/profile. Use the backend's Python and data directory.")
    now_ms = time.time_ns() // 1_000_000
    print("Sending TEST (not a trade):", datetime.now().astimezone().isoformat(timespec="seconds"), flush=True)
    push.send(device['token'], test_event(now_ms), owner, bool(device['sound']), push.configuration())
    print("FCM accepted the test. Confirm receipt/sound on the locked phone; acceptance alone does not prove delivery.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit("Cancelled")
    except Exception as error:
        # Never print tokens, response bodies, config contents or credential values.
        status = getattr(getattr(error, 'response', None), 'status_code', None)
        sys.exit(f"Test failed: {type(error).__name__}" + (f" (HTTP {status})" if status else "") +
                 ". Check the backend Python, profile, Firebase setup and internet connection.")
