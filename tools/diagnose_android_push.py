#!/usr/bin/env python3
"""Read-only live-push diagnostics; never print tokens, cookies or private keys."""
import argparse
import importlib
import json
from pathlib import Path
import sqlite3
import sys
import time


def inspect(conn, owner, now):
    pref = conn.execute('SELECT spec FROM signal_preferences WHERE owner=?', (owner,)).fetchone()
    prefs = {'mobile': False, 'buy_notify': True, 'sell_notify': True, 'coins': {}, 'timeframes': {}}
    if pref:
        prefs.update(json.loads(pref['spec']))
    devices = conn.execute('SELECT id,enabled,sound,cursor,failures,next_try,last_error FROM android_devices WHERE owner=? ORDER BY touched DESC', (owner,)).fetchall()
    rows = conn.execute('SELECT seq,packet FROM events WHERE owner=? ORDER BY seq DESC LIMIT 10', (owner,)).fetchall()
    events = []
    for row in rows:
        event = json.loads(row['packet'])
        symbol, interval = event.get('symbol', ''), event.get('interval', '')
        side = event.get('field', '').removeprefix('decision.')
        mobile = bool(prefs['mobile'] and prefs['coins'].get(symbol, {}).get('mobile', True)
                      and prefs['timeframes'].get(interval, {}).get('mobile', True))
        if side in ('buy', 'sell'):
            mobile = mobile and bool(prefs[side + '_notify'])
        age = round(now - event.get('created', 0) / 1000, 1)
        events.append({'seq': row['seq'], 'symbol': symbol, 'timeframe': interval, 'side': side,
                       'age_seconds': age, 'mobile_allowed': mobile, 'past_five_minute_expiry': age > 300})
    return {'account_mobile_enabled': bool(prefs['mobile']),
            'devices': [{'device': d['id'][:12], 'enabled': bool(d['enabled']), 'sound': bool(d['sound']),
                         'cursor': d['cursor'], 'failures': d['failures'],
                         'retry_in_seconds': max(0, round(d['next_try'] - now, 1)),
                         'delivery_error_recorded': bool(d['last_error'])} for d in devices],
            'latest_events': events}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--backend', required=True)
    p.add_argument('--owner', required=True)
    a = p.parse_args()
    backend = Path(a.backend).expanduser().resolve()
    if not (backend / 'Section_11_UI_UX/server/android_push.py').is_file():
        p.error('Backend path is incorrect')
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(backend))
    auth = importlib.import_module('Section_11_UI_UX.server.auth')
    push = importlib.import_module('Section_11_UI_UX.server.android_push')
    path = auth.USERS_FILE.with_name('alerts.sqlite3').resolve()
    print('Database:', path)
    print('Firebase ready in this Python/profile:', bool(push.status().get('ready')))
    print('This checks configuration, not whether the running backend worker is active.')
    with sqlite3.connect(path.as_uri() + '?mode=ro', uri=True, timeout=5) as conn:
        conn.row_factory = sqlite3.Row
        print(json.dumps(inspect(conn, a.owner, time.time()), indent=2))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        sys.exit('Diagnostic failed: ' + type(error).__name__ + '. Check backend path and Python/profile.')
