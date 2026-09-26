import copy
import importlib.util
import json
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("verify_firebase_signing", Path(__file__).with_name("verify-firebase-signing.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class FirebaseSigningTest(unittest.TestCase):
    def setUp(self):
        self.config = json.loads((Path(__file__).resolve().parents[1] / "app/google-services.json").read_text())
        self.report = "Signer #1 certificate SHA-1 digest: " + checker.PRODUCTION_SHA1 + "\nSigner #1 certificate SHA-256 digest: " + checker.PRODUCTION_SHA256

    def test_production_identity_registered(self):
        checker.verify(self.config, self.report)

    def test_debug_only_configuration_rejected(self):
        config = copy.deepcopy(self.config)
        config["client"][0]["oauth_client"] = [c for c in config["client"][0]["oauth_client"] if c.get("android_info", {}).get("certificate_hash") != "773dbfcba6d9c2933914b8a7c19c491adbc85361"]
        with self.assertRaises(ValueError):
            checker.verify(config, self.report)

    def test_new_signer_or_missing_report_rejected(self):
        for report in ("", "Signer #1 certificate SHA-1 digest: " + "0" * 40):
            with self.assertRaises(ValueError):
                checker.verify(self.config, report)

    def test_android_client_cannot_replace_web_audience(self):
        self.config["client"][0]["oauth_client"] = [c for c in self.config["client"][0]["oauth_client"] if c.get("client_type") != 3]
        with self.assertRaises(ValueError):
            checker.verify(self.config, self.report)

    def test_registered_debug_identity_cannot_replace_production(self):
        with self.assertRaises(ValueError):
            checker.verify(self.config, self.report.replace(checker.PRODUCTION_SHA1, "45fc10f57cfc4f233ab88e082c13a601f7c4b9ab"))
        with self.assertRaises(ValueError):
            checker.verify(self.config, self.report.replace(checker.PRODUCTION_SHA256, "0" * 64))

    def test_different_app_or_project_rejected(self):
        for mutate in (lambda c: c["project_info"].update(project_id="different-project"), lambda c: c["client"][0]["client_info"].update(mobilesdk_app_id="different-app")):
            config = copy.deepcopy(self.config)
            mutate(config)
            with self.assertRaises(ValueError):
                checker.verify(config, self.report)


if __name__ == "__main__":
    unittest.main()
