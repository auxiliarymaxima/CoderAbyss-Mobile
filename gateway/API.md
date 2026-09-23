# Android / gateway contract

All routes below require `Authorization: Bearer <Firebase ID token>` issued for the
configured project and a verified Google provider identity. HTTPS only. No role or
email body field authenticates anyone. RTDN uses its separate Pub/Sub OIDC identity.
No response contains a credential; error bodies are sanitized.

| Method / route | Request / response |
|---|---|
| GET `/account` | `{uid,role,plan,cloudAccess,entitlementState}` |
| GET `/billing/products` | `{productIds:[]}` |
| POST `/billing/google/verify` | `{purchaseToken}` → verified state/expiry |
| GET `/ai/health` | `{status}` |
| GET `/ai/capabilities` | `{engines:[...]}` compatible with existing registry |
| POST `/tasks` | engine-specific request below → `{jobId,status}` |
| GET `/tasks/find/{clientRequestId}` | same owned task or `{status:"UNKNOWN"}` |
| GET `/tasks/{jobId}` | `{jobId,status,stage,progress?,partial?,error?}` |
| POST `/tasks/{jobId}/cancel` | empty object → status |
| POST `/tasks/{jobId}/retry` | empty object; explicit paid retry, same task |
| GET `/tasks/{jobId}/result` | `{metadata:{filename,fileSize,mimeType,...}}` |
| GET `/tasks/{jobId}/content` | authenticated bytes; optional `Range: bytes=N-` |
| GET `/admin/users` | up to 100 verified user records; OWNER/ADMIN |
| POST `/admin/users/{uid}/grant-admin` | OWNER and recent Google authentication |
| POST `/admin/users/{uid}/revoke-admin` | OWNER; owner UID cannot be changed |
| GET `/admin/audit` | OWNER; last 100 whitelisted audit events |
| GET `/admin/provider/status` | OWNER; metadata only, never secret value |
| POST `/admin/provider/test` | OWNER; validates current private provider |
| POST `/admin/provider/space` | OWNER/recent auth; `{space}` |
| POST `/admin/provider/credential/rotate` | OWNER/recent auth; `{credential}` |
| POST `/admin/provider/credential/emergency-rotate` | same; no rollback |
| POST `/admin/provider/credential/rollback` | OWNER/recent auth; empty object |

Submission base fields: `clientRequestId` (UUID), `engine`, `prompt`.
Wan/LTX: `duration`, `resolution`, `aspectRatio` from capabilities.
SDXL: `resolution`, `negativePrompt`, `seed` (-1 or nonnegative).
Qwen: `systemPrompt`, `maxTokens` (16–2048).
Engine-inappropriate fields, client role flags and unsupported fields are rejected.
Wan is at most 100 nonempty whitespace-separated words. Payloads are capped at64KiB.

Task IDs returned to Android identify UID-owned gateway records, not raw HF jobs.
Gateway internals retain the Space/delegated ID. Never claim a pre-authentication
HF job from a client-supplied ID; historical migration needs owner-reviewed server
evidence. Android retains those legacy IDs and outputs without resubmission.

401: sign in/reauthenticate; 403: entitlement/role; 409: conflict/uncertain operation;
422: validation; 429: quota; 503: service unavailable. None imply safe automatic
generation resubmission. Android checks the saved request/job instead.
