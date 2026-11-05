import json
import sqlite3
import unittest
from diagnose_android_push import inspect


class DiagnosePushTest(unittest.TestCase):
    def test_filters_expiry_cursor_and_no_credential_output(self):
        db = sqlite3.connect(':memory:')
        self.addCleanup(db.close)
        db.row_factory = sqlite3.Row
        db.executescript('CREATE TABLE signal_preferences(owner TEXT,spec TEXT);'
                         'CREATE TABLE android_devices(id TEXT,owner TEXT,token TEXT,enabled INTEGER,sound INTEGER,cursor INTEGER,failures INTEGER,next_try REAL,last_error TEXT,touched REAL);'
                         'CREATE TABLE events(seq INTEGER,owner TEXT,packet TEXT);')
        db.execute('INSERT INTO signal_preferences VALUES (?,?)', ('alice',json.dumps({'mobile': True, 'coins': {'BTCUSDT': {'mobile': False}}})))
        db.execute("INSERT INTO android_devices VALUES ('abc','alice','SECRET_TOKEN',1,1,7,2,1020,'PRIVATE_ERROR',1)")
        for seq, owner, symbol, created in [(8,'alice','BTCUSDT',990000),(9,'alice','ETHUSDT',600000),(10,'bob','ETHUSDT',999000)]:
            db.execute('INSERT INTO events VALUES (?,?,?)', (seq,owner,json.dumps({'symbol':symbol,'interval':'5m','field':'decision.buy','created':created})))
        result = inspect(db, 'alice', 1000)
        self.assertEqual([9,8], [e['seq'] for e in result['latest_events']])
        self.assertTrue(result['latest_events'][0]['past_five_minute_expiry'])
        self.assertFalse(result['latest_events'][1]['mobile_allowed'])
        self.assertEqual(7, result['devices'][0]['cursor'])
        self.assertEqual(20, result['devices'][0]['retry_in_seconds'])
        self.assertNotIn('SECRET_TOKEN', json.dumps(result))
        self.assertNotIn('PRIVATE_ERROR', json.dumps(result))


if __name__ == '__main__':
    unittest.main()
