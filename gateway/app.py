"""Deployment implementation; requires real Firebase, Firestore, Play and Secret Manager.

No emulator, anonymous identity, fake entitlement or plaintext-secret fallback exists.
"""
import hashlib
import base64
import json
import os
import time
import uuid
from datetime import datetime, timezone
from functools import lru_cache

import firebase_admin
from firebase_admin import auth
from fastapi import FastAPI, Depends, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, StreamingResponse
from google.cloud import firestore, secretmanager
from googleapiclient.discovery import build
from google.oauth2 import id_token
from google.auth.transport.requests import Request as GoogleRequest
from pydantic import BaseModel, ConfigDict, Field, SecretStr

from policy import admin_change, cloud_allowed, entitled, require_role
from provider import Provider, endpoint

app = FastAPI(title="Coder Abyss Gateway", docs_url=None, redoc_url=None)


@lru_cache
def db():
    if os.getenv("FIRESTORE_EMULATOR_HOST") or os.getenv("FIREBASE_AUTH_EMULATOR_HOST"):
        raise RuntimeError("Production gateway cannot use identity/storage emulators")
    return firestore.Client(project=os.environ["GOOGLE_CLOUD_PROJECT"])


@lru_cache
def secrets():
    return secretmanager.SecretManagerServiceClient()


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def audit(uid, action, result="success", **safe):
    # Only explicitly selected scalar metadata is recorded, never request bodies.
    db().collection("audit").add({"actorUid": uid, "action": action, "result": result, "at": timestamp(), **safe})


@app.exception_handler(RequestValidationError)
async def validation_error(request, error):
    # Pydantic errors may contain input (including a submitted credential).
    return JSONResponse(status_code=422, content={"error": "Invalid request parameters"})


@app.middleware("http")
async def body_limit(request, call_next):
    # Bound chunked bodies as well as Content-Length. Do not log their contents.
    body = bytearray()
    async for chunk in request.stream():
        body.extend(chunk)
        if len(body) > 65536:
            return JSONResponse(status_code=413, content={"error": "Request too large"})
    request._body = bytes(body)
    try:
        return await call_next(request)
    except Exception:
        # Catch before the ASGI server can print provider/Play SDK exception URLs.
        return JSONResponse(status_code=503, content={"error": "Service unavailable; saved task IDs remain valid"})


def identity(authorization: str = Header(default="")):
    if not authorization.startswith("Bearer "):
        raise HTTPException(401, "Sign in required")
    try:
        if not firebase_admin._apps:
            firebase_admin.initialize_app(options={"projectId": os.environ["GOOGLE_CLOUD_PROJECT"]})
        claims = auth.verify_id_token(authorization[7:], check_revoked=True)
        if claims.get("firebase", {}).get("sign_in_provider") != "google.com" or not claims.get("email_verified"):
            raise ValueError()
    except Exception:
        raise HTTPException(401, "Invalid identity") from None
    uid = claims["uid"]
    user_ref = db().collection("users").document(uid)
    owner_ref = db().collection("configuration").document("owner")

    @firestore.transactional
    def register(transaction):
        existing = user_ref.get(transaction=transaction).to_dict() or {}
        owner = owner_ref.get(transaction=transaction).to_dict() or {}
        owner_uid = owner.get("uid")
        bootstrap = os.getenv("CODER_ABYSS_OWNER_UID")
        email = os.getenv("CODER_ABYSS_OWNER_EMAIL", "").casefold()
        eligible = uid == bootstrap if bootstrap else bool(email) and claims.get("email", "").casefold() == email
        if not owner_uid and eligible:
            transaction.set(owner_ref, {"uid": uid, "createdAt": timestamp()})
            owner_uid = uid
        role = "OWNER" if uid == owner_uid else existing.get("role", "FREE")
        if role == "OWNER" and uid != owner_uid:
            role = "FREE"
        user = {**existing, "uid": uid, "role": role, "email": claims.get("email", ""), "name": claims.get("name", ""), "lastActive": timestamp()}
        transaction.set(user_ref, user)
        return user
    user = register(db().transaction())
    return {**user, "auth_time": claims.get("auth_time", 0)}


def privileged(user, owner=False, recent=False):
    try:
        require_role(user["role"], owner)
    except PermissionError:
        raise HTTPException(403, "Insufficient permissions") from None
    if recent and time.time() - user["auth_time"] > 300:
        raise HTTPException(401, "Recent Google sign-in required")


def products():
    return [item.strip() for item in os.getenv("CODER_ABYSS_PRODUCTS", "").split(",") if item.strip()]


def play_purchase(token):
    # Application Default Credentials; never a JSON key distributed to Android.
    service = build("androidpublisher", "v3", cache_discovery=False)
    purchase = service.purchases().subscriptionsv2().get(packageName="com.coderabyss.mobile", token=token).execute()
    return service, purchase


def verify_purchase(uid, token):
    service, purchase = play_purchase(token)
    try:
        entitlement = entitled(purchase, uid, products())
    except ValueError:
        raise HTTPException(403, "Purchase account mismatch") from None
    key = hashlib.sha256(token.encode()).hexdigest()
    ref = db().collection("purchases").document(key)
    user_ref = db().collection("users").document(uid)

    @firestore.transactional
    def save(transaction):
        old = ref.get(transaction=transaction).to_dict() or {}
        if old and old["uid"] != uid:
            raise HTTPException(403, "Purchase already associated")
        transaction.set(ref, {"uid": uid, "purchaseToken": token, "verifiedAt": timestamp(), **entitlement})
        transaction.update(user_ref, {"purchaseKey": key})
    save(db().transaction())
    if entitlement["active"] and purchase.get("acknowledgementState") == "ACKNOWLEDGEMENT_STATE_PENDING":
        product = next(line["productId"] for line in purchase["lineItems"] if line["productId"] in products())
        service.purchases().subscriptions().acknowledge(packageName="com.coderabyss.mobile", subscriptionId=product, token=token, body={}).execute()
    return entitlement


def entitlement_for(user):
    key = user.get("purchaseKey")
    if not key:
        return {"active": False, "state": "FREE"}
    record = db().collection("purchases").document(key).get().to_dict()
    if not record or record["uid"] != user["uid"]:
        raise HTTPException(403, "Invalid entitlement association")
    # Revalidate for every authorization decision. No stale offline paid grants.
    return verify_purchase(user["uid"], record["purchaseToken"])


def cloud(user):
    bypass = os.getenv("CODER_ABYSS_ADMIN_BYPASS", "true") == "true"
    ent = {} if user["role"] == "OWNER" or user["role"] == "ADMIN" and bypass else entitlement_for(user)
    if not cloud_allowed(user["role"], ent, bypass):
        raise HTTPException(403, "Active subscription required")


@app.get("/account")
def account(user=Depends(identity)):
    bypass = os.getenv("CODER_ABYSS_ADMIN_BYPASS", "true") == "true"
    ent = {"active": False, "state": "ROLE_ACCESS"} if user["role"] == "OWNER" or user["role"] == "ADMIN" and bypass else entitlement_for(user)
    role = user["role"] if user["role"] in {"OWNER", "ADMIN"} else "SUBSCRIBER" if ent.get("active") else "FREE"
    return {"uid": user["uid"], "role": role, "plan": "PRO" if role == "SUBSCRIBER" else role,
            "cloudAccess": cloud_allowed(role, ent, bypass), "entitlementState": ent["state"]}


@app.get("/billing/products")
def billing_products(user=Depends(identity)):
    return {"productIds": products()}


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Purchase(Strict):
    purchaseToken: SecretStr


@app.post("/billing/google/verify")
def verify(body: Purchase, user=Depends(identity)):
    return verify_purchase(user["uid"], body.purchaseToken.get_secret_value())


@app.post("/billing/google/rtdn")
async def rtdn(request: Request, authorization: str = Header(default="")):
    try:
        claims = id_token.verify_oauth2_token(authorization.removeprefix("Bearer "), GoogleRequest(), os.environ["CODER_ABYSS_RTDN_AUDIENCE"])
        if claims.get("email") != os.environ["CODER_ABYSS_RTDN_SERVICE_ACCOUNT"] or not claims.get("email_verified"):
            raise ValueError()
    except Exception:
        raise HTTPException(401, "Invalid notification identity") from None
    envelope = await request.json()
    event = json.loads(base64.b64decode(envelope["message"]["data"], validate=True))
    if event.get("packageName") != "com.coderabyss.mobile":
        raise HTTPException(422, "Invalid package")
    token = event.get("subscriptionNotification", {}).get("purchaseToken")
    if token:
        ref = db().collection("purchases").document(hashlib.sha256(token.encode()).hexdigest())
        stored = ref.get().to_dict()
        if stored:
            verify_purchase(stored["uid"], token)
    # Notification contents never grant access: Google Developer API remains authoritative.
    return {"status": "Received"}


def config():
    return db().collection("configuration").document("provider").get().to_dict() or {}


def provider(space=None, settings=None):
    settings = settings if settings is not None else config()
    version = settings.get("version")
    if not version:
        raise HTTPException(503, "AI provider not configured")
    token = secrets().access_secret_version(request={"name": version}).payload.data.decode()
    return Provider(space or settings["space"], token)


@app.get("/ai/health")
def health(user=Depends(identity)):
    provider().call("capabilities")
    return {"status": "Connected"}


@app.get("/ai/capabilities")
def capabilities(user=Depends(identity)):
    return provider().call("capabilities")[0]


@app.get("/admin/users")
def users(user=Depends(identity)):
    privileged(user)
    return {"users": [{k: item.to_dict().get(k) for k in ("uid", "name", "email", "role", "lastActive")} for item in db().collection("users").limit(100).stream()]}


@app.post("/admin/users/{uid}/{operation}")
def change_admin(uid: str, operation: str, user=Depends(identity)):
    privileged(user, owner=True, recent=True)
    if operation not in {"grant-admin", "revoke-admin"}:
        raise HTTPException(404)
    ref = db().collection("users").document(uid)
    @firestore.transactional
    def change(transaction):
        target = ref.get(transaction=transaction).to_dict()
        if not target:
            raise HTTPException(404, "User must sign in first")
        try:
            role = admin_change(user["role"], target["role"], operation == "grant-admin")
        except PermissionError:
            raise HTTPException(403, "Owner is immutable") from None
        transaction.update(ref, {"role": role})
        transaction.set(db().collection("audit").document(), {"actorUid": user["uid"], "targetUid": uid, "action": operation, "result": "success", "at": timestamp()})
    change(db().transaction())
    return {"status": "Updated"}


@app.get("/admin/audit")
def audit_log(user=Depends(identity)):
    privileged(user, owner=True)
    return {"events": [item.to_dict() for item in db().collection("audit").order_by("at", direction=firestore.Query.DESCENDING).limit(100).stream()]}


@app.get("/admin/provider/status")
def provider_status(user=Depends(identity)):
    privileged(user, owner=True)
    settings = config()
    return {"space": settings.get("space"), "configured": bool(settings.get("version")), "version": settings.get("version", "").split("/")[-1], "changedAt": settings.get("changedAt"), "changedBy": settings.get("changedBy")}


@app.post("/admin/provider/test")
def test_provider(user=Depends(identity)):
    privileged(user, owner=True)
    provider().validate()
    return {"status": "Connected"}


class Space(Strict):
    space: str = Field(min_length=3, max_length=193)


def replace_config(old, new, user, action):
    ref = db().collection("configuration").document("provider")
    @firestore.transactional
    def update(transaction):
        current = ref.get(transaction=transaction).to_dict() or {}
        if current != old:
            raise HTTPException(409, "Configuration changed; reload before retrying")
        transaction.set(ref, {**new, "changedAt": timestamp(), "changedBy": user["uid"]})
        transaction.set(db().collection("audit").document(), {"actorUid": user["uid"], "action": action, "result": "success", "at": timestamp(), "version": new.get("version", "").split("/")[-1]})
    update(db().transaction())


@app.post("/admin/provider/space")
def change_space(body: Space, user=Depends(identity)):
    privileged(user, owner=True, recent=True)
    endpoint(body.space)
    old = config()
    provider(body.space).validate()
    replace_config(old, {**old, "space": body.space}, user, "change-space")
    return {"status": "Updated"}


class Rotation(Strict):
    credential: SecretStr | None = None


@app.post("/admin/provider/credential/{operation}")
def rotate(operation: str, body: Rotation | None = None, user=Depends(identity)):
    privileged(user, owner=True, recent=True)
    old = config()
    if operation == "rollback":
        version = old.get("previous")
        if not version or time.time() > old.get("rollbackUntil", 0):
            raise HTTPException(409, "No safe rollback available")
        token = secrets().access_secret_version(request={"name": version}).payload.data.decode()
        Provider(old["space"], token).validate()
        replace_config(old, {**old, "version": version, "previous": None, "rollbackUntil": 0}, user, operation)
    elif operation in {"rotate", "emergency-rotate"} and body and body.credential:
        token = body.credential.get_secret_value().strip()
        if not 10 <= len(token) <= 4096:
            raise HTTPException(422, "Invalid credential")
        space = old.get("space", os.getenv("CODER_ABYSS_INITIAL_SPACE", "andrewmonize/Coder-Abyss-Space"))
        try:
            Provider(space, token).validate()
        except Exception:
            audit(user["uid"], operation, "validation-failed")
            raise HTTPException(422, "Credential or Space validation failed; current credential unchanged") from None
        parent = os.environ["CODER_ABYSS_HF_SECRET"]
        version = secrets().add_secret_version(request={"parent": parent, "payload": {"data": token.encode()}}).name
        emergency = operation == "emergency-rotate"
        new = {"space": space, "version": version, "previous": None if emergency else old.get("version"),
               "rollbackUntil": 0 if emergency else time.time() + int(os.environ["CODER_ABYSS_ROLLBACK_SECONDS"])}
        replace_config(old, new, user, operation)
        # No cached token and no automatic fallback. HF-side revocation is a separate action.
        if emergency:
            for prior in {old.get("version"), old.get("previous")} - {None}:
                try:
                    secrets().disable_secret_version(request={"name": prior})
                except Exception:
                    audit(user["uid"], "disable-old-secret", "manual-disable-required")
    else:
        raise HTTPException(422, "Invalid credential operation")
    return {"status": "Updated", "providerRevocationPerformed": False}


class Job(Strict):
    clientRequestId: uuid.UUID
    engine: str = Field(pattern="^(wan|ltx|sdxl|qwen-coder|qwen-research)$")
    prompt: str = Field(min_length=1, max_length=24000)
    duration: int | None = Field(default=None, strict=True)
    resolution: str | None = None
    aspectRatio: str | None = None
    negativePrompt: str = Field(default="", max_length=2000)
    seed: int = Field(default=-1, ge=-1, le=2147483647, strict=True)
    systemPrompt: str = Field(default="", max_length=8000)
    maxTokens: int = Field(default=1024, ge=16, le=2048, strict=True)


def provider_parameters(body):
    fields = {"clientRequestId", "engine", "prompt"}
    fields |= {"duration", "resolution", "aspectRatio"} if body.engine in {"wan", "ltx"} else {"negativePrompt", "resolution", "seed"} if body.engine == "sdxl" else {"systemPrompt", "maxTokens"}
    if body.model_fields_set - fields:
        raise HTTPException(422, "Parameters do not match selected engine")
    return {key: value for key, value in body.model_dump(mode="json").items() if key in fields}


def task_ref(uid, request_id):
    return db().collection("jobs").document(hashlib.sha256((uid + ":" + request_id).encode()).hexdigest())


def owned(job_id, user):
    ref = db().collection("jobs").document(job_id)
    job = ref.get().to_dict()
    if not job or job["uid"] != user["uid"]:
        raise HTTPException(404, "Task not found")
    return ref, job


def public_status(ref, job):
    p = provider(job["space"])
    if not job.get("remoteId"):
        found = p.call("find_job", job["providerRequestId"])[0]
        if found.get("status") == "UNKNOWN":
            return {"jobId": ref.id, "status": "UNKNOWN", "stage": "Submission not confirmed; no automatic resubmission"}
        ref.update({"remoteId": found["jobId"]})
        job["remoteId"] = found["jobId"]
    result = p.call("get_job_status", job["remoteId"])[0]
    ref.update({"status": result["status"], "updatedAt": timestamp()})
    if result["status"] in {"COMPLETED", "FAILED", "CANCELLED"}:
        db().collection("usage").document(job["uid"]).update({"active": firestore.ArrayRemove([ref.id])})
    # Whitelist response fields; no provider URL or server credential is returned.
    allowed = ("status", "stage", "progress", "partial", "createdAt", "updatedAt", "engine")
    safe = {key: result[key] for key in allowed if key in result}
    if result.get("error"):
        safe["error"] = {"message": "Generation failed. Retry explicitly or contact support."}
    return {**safe, "jobId": ref.id}


@app.post("/tasks")
def submit(body: Job, user=Depends(identity)):
    cloud(user)
    settings = config()
    p = provider(settings=settings)
    caps = p.call("capabilities")[0]
    cap = next((c for c in caps["engines"] if c["engine"] == body.engine and c.get("available")), None)
    maximum = 4000 if body.engine == "wan" else 2000 if body.engine in {"ltx", "sdxl"} else 24000
    if not cap or not body.prompt.strip() or len(body.prompt) > cap.get("maxPromptCharacters", maximum):
        raise HTTPException(422, "Unsupported engine or prompt")
    if body.engine == "wan" and len(body.prompt.split()) > 100:
        raise HTTPException(422, "Wan prompts are limited to 100 words")
    if body.engine in {"wan", "ltx"} and (body.duration not in cap.get("durations", []) or body.resolution not in cap.get("resolutions", [])):
        raise HTTPException(422, "Unsupported video settings")
    if body.engine == "sdxl" and body.resolution not in cap.get("resolutions", []):
        raise HTTPException(422, "Unsupported image resolution")
    if body.engine in {"wan", "ltx"}:
        ratios = {"256x256": "1:1", "480x272": "30:17", "272x480": "17:30", "320x192": "5:3"}
        if body.aspectRatio != ratios.get(body.resolution):
            raise HTTPException(422, "Aspect ratio does not match resolution")
    params = provider_parameters(body)
    ref = task_ref(user["uid"], str(body.clientRequestId))
    fingerprint = hashlib.sha256(json.dumps(params, sort_keys=True).encode()).hexdigest()
    quota = db().collection("usage").document(user["uid"])
    @firestore.transactional
    def reserve(transaction):
        existing = ref.get(transaction=transaction).to_dict()
        usage = quota.get(transaction=transaction).to_dict() or {}
        if existing:
            if existing["fingerprint"] != fingerprint:
                raise HTTPException(409, "Request ID reused with different parameters")
            return existing, False
        # Explicit deployment settings; no invented plan allowance.
        limit = int(os.environ["CODER_ABYSS_REQUESTS_PER_MINUTE"])
        window = int(time.time() // 60)
        count = usage.get("count", 0) if usage.get("window") == window else 0
        if count >= limit:
            raise HTTPException(429, "Request limit reached")
        active = usage.get("active", [])
        if len(active) >= int(os.environ["CODER_ABYSS_CONCURRENT_JOBS"]):
            raise HTTPException(429, "Concurrent job limit reached; check existing tasks")
        day = datetime.now(timezone.utc).date().isoformat()
        daily = usage.get("daily", 0) if usage.get("day") == day else 0
        if daily >= int(os.environ["CODER_ABYSS_DAILY_JOBS"]):
            raise HTTPException(429, "Configured daily allowance reached")
        job = {"uid": user["uid"], "fingerprint": fingerprint, "space": settings["space"], "providerRequestId": str(uuid.uuid5(uuid.NAMESPACE_URL, ref.id)), "createdAt": timestamp(), "status": "SUBMITTING"}
        transaction.set(ref, job)
        transaction.set(quota, {"window": window, "count": count + 1, "day": day, "daily": daily + 1, "active": active + [ref.id]})
        return job, True
    job, fresh = reserve(db().transaction())
    if fresh:
        params["clientRequestId"] = job["providerRequestId"]
        result = p.call("submit_job", params)[0]
        ref.update({"remoteId": result["jobId"], "status": result["status"]})
        return {"jobId": ref.id, "status": result["status"]}
    return public_status(ref, job)


@app.get("/tasks/find/{request_id}")
def find(request_id: uuid.UUID, user=Depends(identity)):
    ref = task_ref(user["uid"], str(request_id)); job = ref.get().to_dict()
    return public_status(ref, job) if job else {"status": "UNKNOWN"}


@app.get("/tasks/{job_id}")
def status(job_id: str, user=Depends(identity)):
    return public_status(*owned(job_id, user))


@app.post("/tasks/{job_id}/{operation}")
def task_action(job_id: str, operation: str, user=Depends(identity)):
    ref, job = owned(job_id, user)
    if operation not in {"cancel", "retry"}:
        raise HTTPException(404)
    if operation == "retry":
        cloud(user)
    if not job.get("remoteId"):
        raise HTTPException(409, "Resolve submission status first")
    p = provider(job["space"])
    if operation == "retry":
        current = p.call("get_job_status", job["remoteId"])[0]
        if current.get("status") != "FAILED":
            return {"jobId": ref.id, "status": current["status"]}
        quota = db().collection("usage").document(user["uid"])
        @firestore.transactional
        def reserve_retry(transaction):
            saved = ref.get(transaction=transaction).to_dict()
            usage = quota.get(transaction=transaction).to_dict() or {}
            if saved.get("retryPending"):
                raise HTTPException(409, "Retry pending; check the same job")
            window = int(time.time() // 60)
            day = datetime.now(timezone.utc).date().isoformat()
            count = usage.get("count", 0) if usage.get("window") == window else 0
            daily = usage.get("daily", 0) if usage.get("day") == day else 0
            active = [key for key in usage.get("active", []) if key != ref.id]
            if count >= int(os.environ["CODER_ABYSS_REQUESTS_PER_MINUTE"]) or daily >= int(os.environ["CODER_ABYSS_DAILY_JOBS"]) or len(active) >= int(os.environ["CODER_ABYSS_CONCURRENT_JOBS"]):
                raise HTTPException(429, "Configured allowance reached")
            transaction.update(ref, {"retryPending": True})
            transaction.set(quota, {"window": window, "count": count + 1, "day": day, "daily": daily + 1, "active": active + [ref.id]})
        reserve_retry(db().transaction())
    result = p.call(operation + "_job", job["remoteId"])[0]
    ref.update({"status": result["status"]})
    if operation == "retry":
        ref.update({"retryPending": False})
    if result["status"] in {"COMPLETED", "FAILED", "CANCELLED"}:
        db().collection("usage").document(job["uid"]).update({"active": firestore.ArrayRemove([ref.id])})
    return {"jobId": ref.id, "status": result["status"]}


def result_for(job_id, user):
    ref, job = owned(job_id, user)
    if not job.get("remoteId"):
        raise HTTPException(409, "Result not ready")
    p = provider(job["space"])
    result = p.call("get_result", job["remoteId"])
    return p, result


@app.get("/tasks/{job_id}/result")
def result(job_id: str, user=Depends(identity)):
    _, result = result_for(job_id, user)
    metadata = {k: v for k, v in result[0].items() if k in {"filename", "fileSize", "mimeType", "duration", "resolution", "outputTokens", "promptTokens", "totalTokens", "tokensPerSecond"}}
    return {"metadata": metadata}


@app.get("/tasks/{job_id}/content")
def content(job_id: str, range: str | None = Header(default=None), user=Depends(identity)):
    p, result = result_for(job_id, user)
    response = p.content(result[1]["url"], range)
    def stream():
        try:
            yield from response.iter_content(128 * 1024)
        finally:
            response.close()
    headers = {key: response.headers[key] for key in ("Content-Length", "Content-Range", "Accept-Ranges") if key in response.headers}
    return StreamingResponse(stream(), status_code=response.status_code, media_type=result[0]["mimeType"], headers=headers)
