import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('builder',ROOT / 'tools/build-standalone.py')
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)

class ReleaseBuildTests(unittest.TestCase):
    def test_version_is_read_from_supplied_properties(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            (root / 'version.properties').write_text('versionName=9.8\nversionCode=42\n')
            self.assertEqual(builder.load_version(root),('9.8',42))

    def test_missing_release_credentials_fail_closed(self):
        with patch.dict(os.environ,{},clear=True):
            with self.assertRaisesRegex(ValueError,'RMT_RELEASE_KEYSTORE'):
                builder.signing_options(ROOT,True)

    def test_test_build_does_not_require_private_credentials(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ,{},clear=True):
            root=Path(directory)
            key,alias,_,_ = builder.signing_options(root,False)
            self.assertEqual(key,root / '.local-sign/debug.jks')
            self.assertEqual(alias,'tile-studio')

    def test_legacy_local_key_is_preserved_for_cover_updates(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ,{},clear=True):
            root=Path(directory)
            key=root / '.build-sign/prebuilt.jks'
            key.parent.mkdir();key.write_bytes(b'existing-key-fixture')
            self.assertEqual(builder.signing_options(root,False)[0],key)
            builder.ensure_debug_key(root,key,'tile-studio')
            self.assertEqual(key.read_bytes(),b'existing-key-fixture')

    def test_external_debug_password_stays_out_of_arguments(self):
        with tempfile.NamedTemporaryFile(suffix='.jks') as key:
            with patch.dict(os.environ,{'RMT_KEYSTORE':key.name,'RMT_KEY_ALIAS':'custom','RMT_SIGN_PASS':'fixture-secret'},clear=True):
                result=builder.signing_options(ROOT,False)
            self.assertEqual(result,(Path(key.name),'custom','env:RMT_SIGN_PASS','env:RMT_SIGN_PASS'))

    def test_missing_explicit_debug_key_is_not_generated(self):
        with tempfile.TemporaryDirectory() as directory:
            key=Path(directory) / 'missing.jks'
            with patch.dict(os.environ,{'RMT_KEYSTORE':str(key)},clear=True):
                with self.assertRaisesRegex(ValueError,'existing'):
                    builder.signing_options(ROOT,False)
            self.assertFalse(key.exists())

    def test_release_passwords_stay_out_of_process_arguments(self):
        with tempfile.NamedTemporaryFile(suffix='.jks') as key:
            values=dict(zip(builder.RELEASE_VARIABLES,[key.name,'fixture-secret','release','fixture-key-secret']))
            with patch.dict(os.environ,values,clear=True):
                result=builder.signing_options(ROOT,True)
            self.assertEqual(result[2: ],('env:RMT_RELEASE_STORE_PASSWORD','env:RMT_RELEASE_KEY_PASSWORD'))
            self.assertNotIn('fixture-secret',repr(result))

    def test_release_key_cannot_live_in_source(self):
        values=dict(zip(builder.RELEASE_VARIABLES,[str(ROOT / '.build-sign/prebuilt.jks'),'android','tile-studio','android']))
        with patch.dict(os.environ,values,clear=True):
            with self.assertRaisesRegex(ValueError,'outside'):
                builder.signing_options(ROOT,True)

    def test_public_test_signing_key_is_rejected_for_release(self):
        digest=(ROOT / '.build-sign/test-public-key.sha256').read_text().strip()
        with self.assertRaisesRegex(ValueError,'public test key'):
            builder.validate_release_key('Signer #1 public key SHA-256 digest: '+digest,ROOT)

    def test_unverifiable_signing_report_is_rejected(self):
        with self.assertRaises(ValueError):
            builder.validate_release_key('',ROOT)

    def test_distinct_verified_public_key_is_accepted(self):
        builder.validate_release_key('Signer #1 public key SHA-256 digest: '+'ab'*32,ROOT)

if __name__ == '__main__':
    unittest.main()
