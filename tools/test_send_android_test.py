import contextlib
import io
from pathlib import Path
import sqlite3
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import send_android_test as sender


class TestAndroidSender(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.backend = root / 'backend'
        entry = self.backend / 'Section_11_UI_UX/server/android_push.py'
        entry.parent.mkdir(parents=True)
        entry.touch()
        self.users = root / 'users.json'
        self.db = root / 'alerts.sqlite3'
        with sqlite3.connect(self.db) as conn:
            conn.execute('CREATE TABLE android_devices(id TEXT, owner TEXT, token TEXT, enabled INTEGER, sound INTEGER, touched REAL)')
            conn.execute("INSERT INTO android_devices VALUES ('device-alice','alice','secret-alice-token',1,1,1)")
            conn.execute("INSERT INTO android_devices VALUES ('device-bob','bob','secret-bob-token',1,1,2)")
        self.push = SimpleNamespace(status=lambda: {'ready': True}, configuration=lambda: {}, send=Mock())
        self.modules = {'Section_11_UI_UX.server.android_push': self.push,
                        'Section_11_UI_UX.server.auth': SimpleNamespace(USERS_FILE=self.users)}

    def run_sender(self, *flags):
        stdout = io.StringIO()
        before = self.db.read_bytes()
        with patch.object(sender.sys, 'argv', ['sender', '--backend', str(self.backend), '--owner', 'alice', *flags]), \
             patch.object(sender.sys, 'path', sender.sys.path.copy()), \
             patch.object(sender.sys, 'dont_write_bytecode', False), \
             patch.object(sender.importlib, 'import_module', side_effect=self.modules.__getitem__), \
             contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(io.StringIO()):
            sender.main()
        self.assertEqual(before, self.db.read_bytes())
        self.assertNotIn('secret-', stdout.getvalue())
        return stdout.getvalue()

    def test_default_lists_without_sending(self):
        self.assertIn('No message sent', self.run_sender())
        self.push.send.assert_not_called()

    def test_explicit_send_targets_only_selected_owner(self):
        self.assertIn('FCM accepted', self.run_sender('--send'))
        args = self.push.send.call_args.args
        self.assertEqual('secret-alice-token', args[0])
        self.assertEqual('alice', args[2])
        self.assertEqual('TEST', args[1]['symbol'])
        self.assertIn('NOT A TRADE', args[1]['message'])
        self.assertLess(args[1]['seq'], 0)

    def test_disabled_device_cannot_send(self):
        with sqlite3.connect(self.db) as conn:
            conn.execute("UPDATE android_devices SET enabled=0 WHERE owner='alice'")
        with self.assertRaises(SystemExit):
            self.run_sender('--send')
        self.push.send.assert_not_called()


if __name__ == '__main__':
    unittest.main()
