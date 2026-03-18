# Policy Monitoring And Alerting

This document defines the minimum monitoring and alerting baseline for the policy subsystem.

## Scope

Policy APIs:

- `/v1/policies/system-rules/**`
- `/v1/policies/reject-apps/**`
- `/v1/policies/trust-score-policies/**`
- `/v1/policies/trust-decision-policies/**`
- `/v1/policies/remediation-rules/**`
- `/v1/policies/rule-remediation-mappings/**`

## Data Sources

- `http_server_requests_seconds_*` metrics from Spring/Micrometer.
- `mdm.policy.audit.events` custom counter from `PoliciesCrudService`.
- Actuator health:
  - `/actuator/health`
  - `/actuator/health/readiness`
  - `/actuator/health/liveness`
- Prometheus scrape of `/actuator/prometheus`.

## Service Level Objectives (recommended)

- Availability for policy APIs: `>= 99.9%` monthly.
- 95th percentile latency for policy write APIs: `< 500 ms`.
- 5xx error rate for policy APIs: `< 1%` over 5 minutes.
- Audit write success ratio: `>= 99.9%` over 15 minutes.

## Alert Rules

Reference alert file:

- `docs/ops/prometheus/policy-alert-rules.yml`

Core alerts:

- `PolicyApiHigh5xxRate`
- `PolicyApiHighLatencyP95`
- `PolicyAuditWriteFailures`
- `PolicyReadinessDown`

## Dashboard Panels (minimum)

- Policy API request rate by endpoint and method.
- Policy API 4xx/5xx trend by endpoint.
- Policy API p50/p95/p99 latency.
- Policy audit events by `policy_type`, `operation`, `scope`, `outcome`.
- Readiness/liveness status.

## On-Call Triage Sequence

1. Confirm if `/actuator/health/readiness` is `UP`.
2. Check active alerts for 5xx or latency spikes.
3. Check recent application logs for policy validation/DB errors.
4. Check `mdm.policy.audit.events{outcome="failure"}` trend.
5. If policy writes are degraded, activate rollback runbook:
   - `docs/ops/POLICY_ROLLBACK_RUNBOOK.md`

## Validation Checklist (pre-prod)

1. Trigger a successful policy create; verify `mdm.policy.audit.events{outcome="success"}` increments.
2. Trigger an expected policy validation failure; verify 400/409 response and no 500.
3. Simulate policy endpoint failure in staging; verify alerts fire and route to on-call.
4. Verify alert resolves automatically after recovery.
