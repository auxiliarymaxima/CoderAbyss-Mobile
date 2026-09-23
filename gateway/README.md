# Coder Abyss account gateway

This is a new, **not yet deployed** production integration implementation. There
was no account gateway in the v0.7 repository. Offline tests use fakes only in
`tests/`; they do not establish that Google sign-in, billing, Firestore or Secret
Manager work in a live project. Android fails closed until configured.

## Deployment prerequisites

1. Create/select a Firebase-enabled Google Cloud project. Enable Google sign-in,
   register `com.coderabyss.mobile`, and register the actual APK/Play signing SHA
   fingerprints. Configure a Web OAuth client for Credential Manager.
2. Enable Firestore Native mode, Secret Manager and Android Publisher API. Deploy
   `firestore.rules`: mobile clients must have no direct access to these server
   collections. Use a dedicated runtime service account and Application Default
   Credentials. Do not generate/download a service-account key into this repo.
3. Grant that service account Firestore access, Firebase Auth user-read/revocation
   access and narrowly scoped access/add-version/disable-version permissions on
   one HF secret. It needs no permission to export other secrets.
4. In Play Console grant the service account the subscription verification and
   acknowledgement permissions. Create subscription products/base plans/offers,
   configure license testers, and use an internal testing track signed with the
   registered key. Prices come from Play ProductDetails, never business constants.
5. Deploy this Dockerfile to Cloud Run over HTTPS with a dedicated service account.
   The mobile route needs public network reachability; application authorization
   still rejects every unauthenticated protected request. Configure ingress/WAF,
   billing budget alerts, request timeouts and concurrency appropriate to quotas.
   Do not enable request-body, Authorization-header, SDK debug or APM payload logs.
6. Set the configuration names below through secure deployment configuration.
   Values are deliberately not included in the repository.
7. Configure Android public identifiers, build, then run the staging tests below.

## Server configuration names

| Name | Purpose |
|---|---|
| `GOOGLE_CLOUD_PROJECT` | Firebase/Firestore project ID |
| `CODER_ABYSS_OWNER_UID` | Preferred stable verified owner UID |
| `CODER_ABYSS_OWNER_EMAIL` | Optional bootstrap alternative; verified Google email only |
| `CODER_ABYSS_PRODUCTS` | Comma-separated allowed Play subscription product IDs |
| `CODER_ABYSS_HF_SECRET` | Secret resource name `projects/.../secrets/...`, not its value |
| `CODER_ABYSS_INITIAL_SPACE` | Initial Space; defaults to the existing private Space |
| `CODER_ABYSS_ROLLBACK_SECONDS` | Explicit safe rollback window |
| `CODER_ABYSS_REQUESTS_PER_MINUTE` | Per-user submission/retry rate |
| `CODER_ABYSS_CONCURRENT_JOBS` | Per-user reserved active-job limit |
| `CODER_ABYSS_DAILY_JOBS` | Explicit daily submission/retry allowance |
| `CODER_ABYSS_ADMIN_BYPASS` | `true` (default) permits ADMIN subscription bypass |
| `CODER_ABYSS_RTDN_AUDIENCE` | Exact authenticated Pub/Sub push audience |
| `CODER_ABYSS_RTDN_SERVICE_ACCOUNT` | Verified Pub/Sub sender service-account email |

Create a server secret named **`coder-abyss-hf-token`** (or an organization-approved
equivalent); configure its resource name in `CODER_ABYSS_HF_SECRET`. Add its value
through Secret Manager or the recently reauthenticated OWNER rotation endpoint,
never through Git. Owner bootstrap configuration also belongs on the server.
If seeding a version manually, set `configuration/provider` with its exact version
resource and `space`; otherwise the OWNER first rotation creates that record.

The owner UID is pinned transactionally in `configuration/owner` after verified
Google sign-in. Changing the email environment variable later cannot replace it.
Only OWNER can grant/revoke ADMIN for already registered UIDs. No client role,
email, entitlement flag or custom claim is accepted as authorization evidence.

## Data and credential boundaries

Firestore collections: `users`, `configuration`, `purchases`, `jobs`, `usage`,
`audit`. These are server-only. Purchase tokens are needed for ongoing Google
verification, protected by IAM/deny-all mobile rules and Google's encryption at
rest; they are never returned to Android or included in logs. HF secret payloads
are **never** Firestore fields. Only explicit Secret Manager version references
are persisted there. Review retention/deletion policies before general release.

Rotation validates HF identity and private Space access before activating a new
Secret Manager version. Configuration and audit activation use a Firestore
transaction with compare-and-swap. Concurrent changes fail rather than overwrite
each other. A failed activation may leave an unused Secret Manager version for
an operator to disable; it is never automatically used. Standard rotation retains
one previous version for the configured window. Emergency rotation clears rollback
and disables previous versions where IAM permits. New requests read the active
version without a credential cache. Already in-flight requests cannot be recalled.
Actual HF token revocation is a separate HF-side action; the API explicitly reports
`providerRevocationPerformed: false`.

Provider changes validate access first. Existing jobs pin their original Space;
the current credential must still authorize it for recovery. No new APK is needed
for rotation or Space changes. Changing the gateway origin itself requires a new
trusted app configuration/build.

## Jobs, quotas and failure semantics

Firestore transaction reserves `(verified UID, clientRequestId)` before any HF
submission. A different payload with the same ID is rejected. The delegated UUID
is deterministic, so uncertain submission recovery only calls `find_job`; it never
blindly generates again. Status/cancel/result/content enforce ownership even after
subscription expiry. Only new/retried GPU work requires current entitlement.
HF itself retains its serialized GPU executor and queue protection.

All roles, including OWNER, have the configured rate/concurrency/daily limits;
subscription bypass is not quota bypass. Failed/unknown submissions may retain a
reservation until an operator reconciles the provider job. An uncertain retry is
held with `retryPending`; it requires reconciliation if no definitive response
arrived. This conservative behavior prevents duplicate expensive inference.
Per-plan differentiated allowances and a user-facing reconciliation console are
future work; the present deployment limits apply uniformly.

Results stream through an authenticated ownership-checked gateway route, including
Range requests. Android never receives the HF credential or a privileged provider
URL. Downloads validate local files before completion. Cloud Run streaming timeouts,
egress costs and large MP4 resumes need staging load tests.

## Google Play and RTDN

`purchases.subscriptionsv2.get` is called with ADC; product allowlist, signed-in UID
hash/obfuscated account association, state and expiration are checked. ACTIVE,
GRACE and cancelled-but-not-expired purchases can retain entitlement. HOLD, PAUSED,
PENDING and expired states cannot. Acknowledgement happens server-side only after
verification. Repeated restore requests are safe; a token cannot move between UIDs.

Configure Play RTDN to an authenticated Pub/Sub push subscription targeting
`/billing/google/rtdn`. The gateway verifies Google's OIDC audience and the exact
configured sender identity, then re-fetches Google state. It never grants access
from notification contents. Unknown tokens do not create a new user association;
the authenticated app purchase/restore flow must first bind them. Entitlement is
also reverified when authorizing paid work, so missing RTDN never permits a stale
cached paid grant. Multi-token plan replacement/linked-token migration requires
additional staging verification before broad distribution.

## Required staging acceptance

- Real Google sign-in, token rejection/revocation, owner bootstrap and recent auth.
- OWNER grant/revoke; ADMIN rejection on every secret/owner operation.
- Actual Play license-test purchase/restore, renewals, grace, hold, cancellation,
  revocation, account mismatch, RTDN and acknowledgement.
- Firestore transaction races for duplicate submission and role/config changes.
- Real invalid/valid rotation, rollback-window expiry and emergency disable IAM.
- Existing Space all five engines through gateway; navigation/process-death,
  sign-out/account-switch, offline resume and byte-range result downloads.
- Load/rate-limit tests, persistent quota reconciliation and security review.

No Cloud Run/Firebase/Play infrastructure was deployed by this code change.
