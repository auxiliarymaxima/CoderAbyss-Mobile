"""Pure authorization and entitlement policy. No Android-supplied role is consulted."""
from datetime import datetime, timezone
from hashlib import sha256


def obfuscated_account(uid):
    return sha256(uid.encode()).hexdigest()


def entitled(purchase, uid, products, now=None):
    now = now or datetime.now(timezone.utc)
    identifiers = purchase.get("externalAccountIdentifiers", {})
    if identifiers.get("obfuscatedExternalAccountId") != obfuscated_account(uid):
        raise ValueError("Purchase account mismatch")
    state = purchase.get("subscriptionState", "SUBSCRIPTION_STATE_UNSPECIFIED")
    allowed = {"SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"}
    lines = [line for line in purchase.get("lineItems", []) if line.get("productId") in products]
    expiries = [datetime.fromisoformat(line["expiryTime"].replace("Z", "+00:00")) for line in lines if line.get("expiryTime")]
    expiry = max(expiries, default=datetime.min.replace(tzinfo=timezone.utc))
    return {"active": state in allowed and expiry > now, "state": state, "expiresAt": expiry.isoformat()}


def cloud_allowed(role, entitlement, admin_bypass=True):
    return role == "OWNER" or (role == "ADMIN" and admin_bypass) or bool(entitlement.get("active", False))


def require_role(role, owner=False):
    if role not in ({"OWNER"} if owner else {"OWNER", "ADMIN"}):
        raise PermissionError("Insufficient permissions")


def admin_change(actor_role, target_role, grant):
    require_role(actor_role, owner=True)
    if target_role == "OWNER":
        raise PermissionError("Owner is immutable")
    return "ADMIN" if grant else "FREE"
