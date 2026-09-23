import unittest
from datetime import datetime, timezone
from policy import admin_change, cloud_allowed, entitled, obfuscated_account, require_role


class PolicyTests(unittest.TestCase):
    def test_cloud_matrix(self):
        self.assertFalse(cloud_allowed("FREE", {}))
        self.assertTrue(cloud_allowed("SUBSCRIBER", {"active": True}))
        self.assertFalse(cloud_allowed("SUBSCRIBER", {"active": False}))
        self.assertTrue(cloud_allowed("OWNER", {}))
        self.assertTrue(cloud_allowed("ADMIN", {}))
        self.assertFalse(cloud_allowed("ADMIN", {}, False))

    def test_owner_only_changes(self):
        for role in ("FREE", "SUBSCRIBER", "ADMIN"):
            with self.assertRaises(PermissionError):
                require_role(role, owner=True)
            with self.assertRaises(PermissionError):
                admin_change(role, "FREE", True)
        self.assertEqual(admin_change("OWNER", "FREE", True), "ADMIN")
        self.assertEqual(admin_change("OWNER", "ADMIN", False), "FREE")
        with self.assertRaises(PermissionError):
            admin_change("OWNER", "OWNER", False)

    def purchase(self, state="ACTIVE", expiry="2030-01-01T00:00:00Z"):
        return {"externalAccountIdentifiers": {"obfuscatedExternalAccountId": obfuscated_account("verified-uid")},
                "subscriptionState": "SUBSCRIPTION_STATE_" + state, "lineItems": [{"productId": "pro", "expiryTime": expiry}]}

    def test_play_states(self):
        now = datetime(2026, 1, 1, tzinfo=timezone.utc)
        for state in ("ACTIVE", "IN_GRACE_PERIOD", "CANCELED"):
            self.assertTrue(entitled(self.purchase(state), "verified-uid", ["pro"], now)["active"])
        for state in ("ON_HOLD", "EXPIRED", "PENDING", "PAUSED"):
            self.assertFalse(entitled(self.purchase(state), "verified-uid", ["pro"], now)["active"])
        self.assertFalse(entitled(self.purchase(expiry="2020-01-01T00:00:00Z"), "verified-uid", ["pro"], now)["active"])

    def test_purchase_bound_to_uid_and_product(self):
        with self.assertRaises(ValueError):
            entitled(self.purchase(), "another-uid", ["pro"])
        self.assertFalse(entitled(self.purchase(), "verified-uid", ["other-product"])["active"])


if __name__ == "__main__":
    unittest.main()
