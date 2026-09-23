"""Offline boundary tests. Fakes are confined to tests; these are not live Google verification."""
import time
import unittest
from unittest.mock import MagicMock, patch
from fastapi.testclient import TestClient
import app as gateway


class SecurityTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(gateway.app, raise_server_exceptions=False)

    def tearDown(self):
        gateway.app.dependency_overrides.clear()

    def user(self, role):
        gateway.app.dependency_overrides[gateway.identity] = lambda: {"uid": "verified-user", "role": role, "auth_time": time.time()}

    def test_unauthenticated_protected_endpoints(self):
        for path in ("/account", "/admin/users", "/admin/provider/status", "/ai/capabilities", "/tasks/abc"):
            self.assertEqual(self.client.get(path).status_code, 401)

    def test_non_owner_cannot_rotate_or_promote(self):
        for role in ("FREE", "SUBSCRIBER", "ADMIN"):
            self.user(role)
            self.assertEqual(self.client.post("/admin/provider/credential/rotate", json={"credential": "unit-test-secret-only"}).status_code, 403)
            self.assertEqual(self.client.post("/admin/users/target/grant-admin", json={}).status_code, 403)

    def test_no_secret_read_endpoint(self):
        self.user("OWNER")
        self.assertEqual(self.client.get("/admin/provider/credential").status_code, 404)
        with patch.object(gateway, "config", return_value={"version": "projects/test/secrets/provider/versions/4", "space": "owner/test"}):
            data = self.client.get("/admin/provider/status").json()
            self.assertEqual(data["version"], "4")
            self.assertNotIn("credential", data)

    def test_validation_never_echoes_secret(self):
        self.user("OWNER")
        result = self.client.post("/admin/provider/credential/rotate", json={"credential": "unit-test-secret-only", "role": "OWNER"})
        self.assertEqual(result.status_code, 422)
        self.assertNotIn("unit-test-secret-only", result.text)

    def test_client_cannot_supply_entitlement_or_role(self):
        self.user("FREE")
        result = self.client.post("/tasks", json={"clientRequestId": "00000000-0000-0000-0000-000000000001", "engine": "wan", "prompt": "test", "role": "OWNER", "subscriptionActive": True})
        self.assertEqual(result.status_code, 422)

    def test_invalid_rotation_preserves_active_secret(self):
        self.user("OWNER")
        with patch.object(gateway, "config", return_value={"space": "owner/test", "version": "old"}), patch.object(gateway, "Provider") as provider, patch.object(gateway, "audit") as audit, patch.object(gateway, "replace_config") as replace, patch.object(gateway, "secrets") as secrets:
            provider.return_value.validate.side_effect = ValueError("private diagnostic")
            response = self.client.post("/admin/provider/credential/rotate", json={"credential": "unit-test-secret-only"})
            self.assertEqual(response.status_code, 422)
            replace.assert_not_called(); secrets.assert_not_called()
            self.assertNotIn("unit-test-secret-only", str(audit.call_args))
            self.assertNotIn("private diagnostic", response.text)

    def test_emergency_rotation_disallows_rollback(self):
        self.user("OWNER")
        old = {"space": "owner/test", "version": "old", "previous": "older"}
        secret = MagicMock(); secret.add_secret_version.return_value.name = "new-version"
        with patch.dict("os.environ", {"CODER_ABYSS_HF_SECRET": "projects/test/secrets/provider"}), patch.object(gateway, "config", return_value=old), patch.object(gateway, "Provider"), patch.object(gateway, "secrets", return_value=secret), patch.object(gateway, "replace_config") as replace:
            response = self.client.post("/admin/provider/credential/emergency-rotate", json={"credential": "unit-test-secret-only"})
            self.assertEqual(response.status_code, 200)
            active = replace.call_args.args[1]
            self.assertIsNone(active["previous"])
            self.assertEqual(active["rollbackUntil"], 0)
            self.assertEqual(active["version"], "new-version")
            self.assertEqual(secret.disable_secret_version.call_count, 2)
            self.assertFalse(response.json()["providerRevocationPerformed"])
            self.assertNotIn("unit-test-secret-only", response.text)

    def test_recent_auth_required(self):
        gateway.app.dependency_overrides[gateway.identity] = lambda: {"uid": "owner", "role": "OWNER", "auth_time": 0}
        self.assertEqual(self.client.post("/admin/provider/credential/rotate", json={"credential": "unit-test-secret-only"}).status_code, 401)

    def test_gradio_engine_contracts(self):
        base = {"clientRequestId": "00000000-0000-0000-0000-000000000001", "prompt": "test"}
        for engine, fields in (("wan", {"duration", "resolution", "aspectRatio"}), ("ltx", {"duration", "resolution", "aspectRatio"}), ("sdxl", {"negativePrompt", "resolution", "seed"}), ("qwen-coder", {"systemPrompt", "maxTokens"})):
            value = gateway.provider_parameters(gateway.Job(**base, engine=engine))
            self.assertEqual(set(value), {"clientRequestId", "engine", "prompt"} | fields)
        with self.assertRaises(Exception):
            gateway.Job(**base, engine="wan", duration=True)


if __name__ == "__main__":
    unittest.main()
