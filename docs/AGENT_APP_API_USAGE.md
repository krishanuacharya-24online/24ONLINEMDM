# Agent App API Usage Guide

This guide explains how a device/agent app should call backend APIs under `/v1/agent/*`, including installed-app ingestion, trust-score computation, decisioning, and remediation response.

For a runnable desktop agent aligned to this contract, use:

- `tools/24onlineMDMAgent` for the cross-platform desktop app
- `tools/24onlineMDMAgent/README.md` for packaging and usage notes

## 1) Base URL and Protocol

- Local default base URL: `http://localhost:8080`
- Production packaging should prefer an embedded `https://...` base URL via the agent build command.
- All payloads are JSON.
- Global JSON naming is snake_case (`device_external_id`, `payload_version`, etc.).

## 2) Authentication Model for Agent APIs

Agent endpoints are publicly reachable at Spring Security layer (`/v1/agent/**` is `permitAll`), but tenant checks are enforced in controller logic.

Always send these headers:

- `X-Tenant-Id`: tenant identifier (send lowercase).
- `X-Tenant-Key`: tenant API key (required).

Current backend exception:

- `POST /v1/agent/posture-payloads` also accepts `X-Device-Token` for ingest submission.
- `GET /v1/agent/posture-payloads/{payload_id}/result`, `GET /v1/agent/devices/{device_external_id}/decision/latest`, and `POST /v1/agent/decision-responses/{response_id}/ack` still require `X-Tenant-Id` and `X-Tenant-Key`.

Important behavior:

- Ingest normalizes `X-Tenant-Id` to lowercase before tenant lookup.
- All `/v1/agent/*` endpoints now validate tenant access using `X-Tenant-Id`.
- API returns `401` on all agent endpoints for missing/invalid tenant id, missing/invalid tenant key, or missing active tenant key configuration.

## 3) Preconditions (Policy Setup)

Before agents call posture APIs, admin/policy teams should configure:

- `system_information_rule` + `system_information_rule_condition`
- `reject_application_list`
- `os_release_lifecycle_master` (OS cycle, EOL, EEOL data)
- `trust_score_policy`
- `trust_score_decision_policy`
- `remediation_rule` + `rule_remediation_mapping`

Without these, fallback scoring/decision logic still works, but outcomes are less policy-specific.

## 4) Endpoint Contracts

## 4.1 POST `/v1/agent/posture-payloads`

Purpose:

- Submit posture for async evaluation and receive a queue/result handle immediately.

Headers:

- `Content-Type: application/json`
- `X-Tenant-Id: <tenant_id>` (required)
- `X-Tenant-Key: <tenant_key>` (required)

Request body:

```json
{
  "device_external_id": "device-001",
  "agent_id": "android-agent-2.3.0",
  "payload_version": "v2",
  "agent_version": "ios-agent-2.4.0",
  "agent_capabilities": {
    "posture_collection": true,
    "decision_ack": true,
    "platform": "ios"
  },
  "payload_hash": "sha256:8f4b...",
  "payload_json": {
    "capture_time": "2026-02-28T10:00:00Z",
    "device_type": "PHONE",
    "os_type": "ANDROID",
    "os_name": null,
    "os_version": "14.0.0",
    "os_cycle": "14",
    "manufacturer": "Google",
    "time_zone": "Asia/Kolkata",
    "api_level": 34,
    "os_build_number": "UP1A.231105.003",
    "kernel_version": "5.10.190",
    "root_detected": false,
    "running_on_emulator": false,
    "usb_debugging_status": false,
    "installed_apps": [
      {
        "app_name": "Chrome",
        "publisher": "Google LLC",
        "package_id": "com.android.chrome",
        "app_os_type": "ANDROID",
        "app_version": "131.0.0",
        "latest_available_version": "132.0.0",
        "is_system_app": false,
        "install_source": "PLAY_STORE",
        "status": "ACTIVE"
      }
    ]
  }
}
```

Field rules:

- Top-level:
  - `device_external_id`: required, non-blank
  - `agent_id`: required, non-blank
  - `payload_version`: required, non-blank
  - `agent_version`: optional but recommended
  - `agent_capabilities`: optional but recommended
  - `payload_hash`: optional (used for idempotent dedupe)
  - `payload_json`: required JSON object
- `payload_json`:
  - `os_type` is required; evaluation is policy-driven and not limited to a hardcoded OS allowlist
  - `payload_json` must be a JSON object and max serialized size is `1,000,000` bytes
  - `installed_apps` is optional but strongly recommended for app-risk scoring
  - max `installed_apps` per payload is `5000`
  - use `root_detected` (not `is_rooted`)
- `installed_apps` item handling:
  - `app_name` required to persist app row
  - `app_os_type` optional; fallback is app `os_type`, then device `os_type`
  - duplicate app rows in same payload are deduplicated by `app_os_type + package_id + app_name`
  - app rows are kept for any non-blank `app_os_type`; matching is driven by configured policy

Schema compatibility:

- verified payload versions: `v1`, `1.0`, `v2`, `2.0`
- missing `agent_version` or `agent_capabilities` does not fail ingest, but produces validation warnings

Response:

- `200 OK` (body contains current workflow outcome, usually `QUEUED` at ingest time)

```json
{
  "payload_id": 12345,
  "result_status_url": "/v1/agent/posture-payloads/12345/result",
  "schema_compatibility_status": "SUPPORTED",
  "status": "QUEUED",
  "validation_warnings": []
}
```

Server-side processing sequence:

1. Raw ingest into `device_posture_payload` (`RECEIVED`).
2. Queue the payload for posture evaluation (`QUEUED`).
3. Background worker parses and normalizes system snapshot + installed apps.
4. Upsert `device_trust_profile`.
5. Resolve OS lifecycle via `os_release_lifecycle_master`.
6. Match system rules and reject-app rules.
7. Compute trust score and decision.
8. Generate remediation.
9. Persist run/matches/events/decision response.
10. Mark payload `EVALUATED`.

Current trust score calculation (implemented behavior):

1. Start from `device_trust_profile.current_score` (new device starts at `100`).
2. Build score signals from:
   - matched system rules (`SYSTEM_RULE`)
   - matched reject-app rules (`REJECT_APPLICATION`)
   - lifecycle posture signal (`POSTURE_SIGNAL`) when lifecycle is not `SUPPORTED`
3. For each signal, attempt to apply active `trust_score_policy` by:
   - `source_type`
   - `signal_key` candidate match
   - optional `severity` and `compliance_action`
   - highest `weight` wins (then lowest id)
4. Delta per signal:
   - if policy matched: `round(score_delta * weight)` (bounded to `[-1000, 1000]` before score clamp)
   - fallback for reject app: `max(-80, -10 * severity)` (default severity `3`)
   - fallback for lifecycle: `EEOL=-40`, `EOL=-25`, `NOT_TRACKED=-15`
   - fallback for system rule: `risk_score_delta` (or `0`)
5. Apply deltas sequentially and clamp running score to `[0, 100]`.
6. Final decision:
   - first try active `trust_score_decision_policy` range match for final score
   - fallback thresholds: `<40 BLOCK`, `<60 QUARANTINE`, `<80 NOTIFY`, otherwise `ALLOW`
7. `remediation_required`:
   - from decision policy if matched
   - otherwise `true` for non-`ALLOW`, `false` for `ALLOW`
8. Update score band:
   - `<25 CRITICAL`, `<50 HIGH_RISK`, `<70 MEDIUM_RISK`, `<90 LOW_RISK`, else `TRUSTED`

Common errors:

- `401 Unauthorized`: missing/invalid tenant id, missing tenant key, invalid tenant key, or no active tenant key configured
- `400 Bad Request`: invalid payload structure (for example missing `payload_json.os_type`)

Example curl:

```bash
curl -X POST "http://localhost:8080/v1/agent/posture-payloads" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant_a" \
  -H "X-Tenant-Key: <tenant_key>" \
  -d '{
    "device_external_id":"device-001",
    "agent_id":"android-agent-2.3.0",
    "payload_version":"v2",
    "agent_version":"ios-agent-2.4.0",
    "agent_capabilities":{"posture_collection":true,"decision_ack":true,"platform":"ios"},
    "payload_hash":"sha256:8f4b...",
    "payload_json":{
      "os_type":"ANDROID",
      "os_version":"14.0.0",
      "root_detected":false,
      "installed_apps":[{"app_name":"Chrome","package_id":"com.android.chrome","app_version":"131.0.0"}]
    }
  }'
```

## 4.2 GET `/v1/agent/posture-payloads`

Purpose:

- List posture payloads (paginated).

Headers:

- `X-Tenant-Id: <tenant_id>` (required)
- `X-Tenant-Key: <tenant_key>` (required)

Query params:

- `device_external_id` (optional)
- `process_status` (optional): `RECEIVED`, `QUEUED`, `VALIDATED`, `EVALUATED`, `FAILED`
- `page` (optional, default `0`)
- `size` (optional, default `50`, max `500`)

Pagination normalization behavior:

- negative `page` values are normalized to `0`
- `size <= 0` is normalized to `50`
- `size > 500` is capped to `500`

Response:

- `200 OK`
- array of payload rows

Note:

- `payload_json` in response is a JSON string, not a JSON object.

## 4.3 GET `/v1/agent/posture-payloads/{payload_id}`

Purpose:

- Fetch one payload by id.

Headers:

- `X-Tenant-Id: <tenant_id>` (required)
- `X-Tenant-Key: <tenant_key>` (required)

Responses:

- `200 OK`
- `404 Not Found`

## 4.4 GET `/v1/agent/devices/{device_external_id}/decision/latest`

Purpose:

- Fetch latest decision for a device.

Headers:

- `X-Tenant-Id: <tenant_id>` (required)
- `X-Tenant-Key: <tenant_key>` (required)

Possible values:

- `decision_action`: `ALLOW`, `QUARANTINE`, `BLOCK`, `NOTIFY`
- `delivery_status`: `PENDING`, `DELIVERED`, `ACKNOWLEDGED`, `FAILED`, `TIMEOUT`

Errors:

- `404 Not Found`: no decision exists yet for this tenant+device
- `400 Bad Request`: blank `device_external_id`

Note:

- `response_payload` is returned as JSON string.
- first successful fetch of a `PENDING` decision promotes it to `DELIVERED` and stamps `sent_at` when missing

## 4.5 POST `/v1/agent/decision-responses/{response_id}/ack`

Purpose:

- Acknowledge decision delivery/application outcome.

Headers:

- `Content-Type: application/json`
- `X-Tenant-Id: <tenant_id>` (required)
- `X-Tenant-Key: <tenant_key>` (required)

Request body:

```json
{
  "delivery_status": "ACKNOWLEDGED",
  "acknowledged_at": "2026-02-28T10:02:00Z",
  "error_message": null
}
```

Field rules:

- `delivery_status` required; one of `PENDING`, `DELIVERED`, `ACKNOWLEDGED`, `FAILED`, `TIMEOUT`
- `acknowledged_at` optional ISO-8601 timestamp
  - for `ACKNOWLEDGED`, if omitted server auto-fills current UTC time
  - if provided earlier than `sent_at`, server normalizes it up to `sent_at`
- `error_message` optional (`max 2000` chars; longer values are truncated)
- legacy aliases are accepted for compatibility: `SENT -> DELIVERED`, `ACKED -> ACKNOWLEDGED`

Response:

- `200 OK`

```json
{
  "response_id": 987,
  "delivery_status": "ACKNOWLEDGED",
  "acknowledged_at": "2026-02-28T10:02:00Z"
}
```

## 5) Recommended Agent App Flow

1. Build posture payload including device info and `installed_apps`, then compute stable `payload_hash`.
2. Call `POST /v1/agent/posture-payloads`.
3. If response `status` is `RECEIVED`, `QUEUED`, or `VALIDATED`, poll `result_status_url` until terminal status.
4. Read `decision_action`, `trust_score`, `decision_reason`, and `remediation` from the evaluated result.
5. Apply enforcement/remediation on device.
6. Send result using `POST /v1/agent/decision-responses/{response_id}/ack`.
7. Optionally call latest-decision endpoint for reconciliation/retry.

## 6) Client-Side Reliability Guidance

- Always send lowercase `X-Tenant-Id`.
- Reuse `payload_hash` on retries for idempotent behavior.
- Handle network retry with exponential backoff.
- Parse `payload_json` and `response_payload` as strings when reading stored/list APIs.
- Store `X-Tenant-Key` securely; never log it.

## 7) Minimal Postman/cURL Variables

- `BASE_URL = http://localhost:8080`
- `TENANT_ID = tenant_a`
- `TENANT_KEY = <secret>`
- `DEVICE_ID = device-001`

## 8) Known Integration Gotchas

- Use `root_detected` in `payload_json`; `is_rooted` is not consumed by evaluator.
- If `installed_apps[].app_name` is missing, that app row is skipped.
- If tenant id casing differs from stored value, decision lookups can miss records; always lowercase.
- For list APIs, server normalizes invalid paging values (`page < 0` -> `0`, `size <= 0` -> `50`, `size > 500` -> `500`).
