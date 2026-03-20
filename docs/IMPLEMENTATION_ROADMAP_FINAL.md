# Final Implementation Roadmap

This roadmap implements the frozen product definition in `docs/FROZEN_PRODUCT_REQUIREMENTS.md`.

The sequencing is intentionally conservative. The goal is to avoid building product surface on top of unstable architecture.

## Roadmap Principles

1. Build entitlement and tenancy foundations before premium features.
2. Remove duplicate architecture before adding new logic.
3. Freeze the agent contract before expanding analytics.
4. Treat trustability and auditability as product features, not cleanup work.
5. Do not add UX polish ahead of data-model correctness.
6. Keep reporting native to the product UI and APIs; do not depend on Superset or other embedded BI tooling for v1.

## Implementation Sync Snapshot

Status as of March 20, 2026:

- Phase 0: in progress; docs are being synced to the frozen scope and the native-reporting decision.
- Phase 1: completed in code for subscription foundation and entitlement enforcement.
- Phase 2: completed in code for workflow status unification and canonical orchestration.
- Phase 3: completed in code for agent contract metadata, compatibility state, and ingest quality visibility.
- Phase 4: pending; hardening work remains.
- Phase 5: partially completed; remediation lifecycle, acknowledgment, rescan verification, and reporting are in place, while remaining authoring/product polish is pending.
- Phase 6: largely completed with native reporting on reports and overview surfaces.
- Phase 7: in progress; queue health, failure categorization, daily pipeline trend, and failed-payload drill-down are in place, while deeper DLQ history, scale validation, and SLO work remain.
- Supporting tooling: a cross-platform packaged desktop agent now exists under `tools/24onlineMDMAgent/` for enrollment, end-to-end posture validation, and customer-facing live-device operation against the current backend contract.

## Phase 0: Canonical Product Alignment

Goal: align the repository and documentation with the frozen product definition.

Outcomes:

- canonical product scope documented
- misleading full-MDM positioning removed from key project docs
- implementation phases approved against the frozen requirement set

Primary repo areas:

- `README.md`
- `docs/`

## Phase 1: SaaS Subscription Foundation

Goal: make tenancy commercially and operationally real before broader implementation.

Required database additions:

- `subscription_plan`
- `tenant_subscription`
- `tenant_usage_snapshot`
- `tenant_feature_override`

Required service additions:

- entitlement evaluation service
- subscription administration service
- tenant usage metering service

Required API additions:

- assign plan to tenant
- view subscription state
- view tenant usage
- manage feature overrides

Required UI additions:

- plan/status/usage section on tenants page
- visibility into trial, grace, suspension, and quota state

Required enforcement points:

- user creation and activation
- enrollment creation or claim
- posture payload ingest
- premium features and reporting

Suggested starting files:

- `src/main/resources/db/migration/`
- `src/main/java/com/e24online/mdm/service/TenantAdminService.java`
- `src/main/java/com/e24online/mdm/web/TenantAdminController.java`
- `src/main/java/com/e24online/mdm/service/UserAdminService.java`
- `src/main/java/com/e24online/mdm/service/DeviceEnrollmentService.java`
- `src/main/java/com/e24online/mdm/web/AgentController.java`
- `src/main/resources/templates/tenants.html`
- `src/main/resources/static/assets/js/pages/tenants.js`

Exit criteria:

- every tenant has a subscription state
- plan limits are enforced in core flows
- over-limit and suspended behavior is explicit

## Phase 2: Architecture Cleanup and Status Unification

Goal: remove flow duplication and normalize lifecycle states before further feature work.

Required changes:

- retire the active use of `AgentPostureWorkflowService`
- keep `WorkflowOrchestrationService` as the only evaluation orchestration path
- normalize payload, run, remediation, and delivery statuses
- remove stale controller assumptions around old statuses

Primary repo areas:

- `src/main/java/com/e24online/mdm/service/AgentPostureWorkflowService.java`
- `src/main/java/com/e24online/mdm/service/WorkflowOrchestrationService.java`
- `src/main/java/com/e24online/mdm/web/EvaluationsController.java`
- `src/main/java/com/e24online/mdm/domain/PostureEvaluationRun.java`
- migration files for status cleanup

Exit criteria:

- one active posture workflow
- one canonical status model
- one end-to-end evaluation path

## Phase 3: Versioned Agent Contract and Evidence Quality

Goal: make the ingest contract stable enough for long-term agent compatibility.

Required changes:

- extend posture payload model with schema version metadata
- capture agent version and capability data
- record ingest quality and validation warnings
- clearly separate raw evidence from normalized evidence
- define compatibility rules between server and agent versions

Primary repo areas:

- `src/main/java/com/e24online/mdm/web/dto/PosturePayloadIngestRequest.java`
- `src/main/java/com/e24online/mdm/web/dto/PosturePayloadIngestResponse.java`
- `src/main/java/com/e24online/mdm/service/PostureIngestionService.java`
- `src/main/java/com/e24online/mdm/service/DeviceStateService.java`
- `src/main/java/com/e24online/mdm/domain/DevicePosturePayload.java`

Exit criteria:

- stable versioned contract documented
- validation quality visible
- normalized evidence reproducible from stored input

## Phase 4: Security and Device Identity Hardening

Goal: harden the system enough for production SaaS operation.

Required changes:

- secure cookie behavior outside local development
- refresh-token rotation on refresh
- better device credential metadata and rotation tracking
- anti-replay controls for posture ingest
- suspicious submission detection

Primary repo areas:

- `src/main/java/com/e24online/mdm/web/AuthController.java`
- `src/main/java/com/e24online/mdm/service/DeviceEnrollmentService.java`
- `src/main/java/com/e24online/mdm/domain/DeviceAgentCredential.java`
- `src/main/java/com/e24online/mdm/web/AgentController.java`
- migrations around credential and ingest integrity metadata

Exit criteria:

- admin auth is production-safe
- device credentials are auditable and rotatable
- replayed or suspicious ingest can be detected

## Phase 5: Structured Remediation Productization

Goal: make remediation guidance a first-class product feature.

Required changes:

- move from basic remediation rows to structured remediation guidance
- add remediation display and user-acknowledgment statuses
- track whether remediation resolved on later scans
- expose verification hints and affected evidence in the response

Primary repo areas:

- `src/main/java/com/e24online/mdm/service/RemediationService.java`
- `src/main/java/com/e24online/mdm/domain/PostureEvaluationRemediation.java`
- `src/main/java/com/e24online/mdm/domain/DeviceDecisionResponse.java`
- policy UI pages for remediation authoring

Exit criteria:

- remediation is understandable to end users
- remediation state is measurable over time

## Phase 6: Fleet Visibility and Reporting

Goal: turn posture data into operationally useful fleet views.

Required changes:

- heartbeat and last-seen model
- stale-device detection
- agent-version and capability distribution
- score trend views
- top failing rule and risky-app reporting
- tenant usage and entitlement visibility
- native reports UI and APIs only, with no Superset dependency

Primary repo areas:

- `src/main/java/com/e24online/mdm/service/UiDataTableService.java`
- `src/main/java/com/e24online/mdm/web/DevicesController.java`
- `src/main/resources/templates/overview.html`
- `src/main/resources/templates/devices.html`
- `src/main/resources/templates/reports.html`
- related page JS files

Exit criteria:

- operators can explain fleet health
- admins can find stale and risky devices quickly

## Phase 7: Reliability, Scale, and Operability

Goal: make the system run as a dependable multi-tenant service.

Required changes:

- queue lag visibility
- DLQ visibility
- failure categorization
- load and scale testing for large posture payloads
- stronger indexing for tenant/device/time reporting patterns
- SLOs for ingest and evaluation latency

Primary repo areas:

- `src/main/java/com/e24online/mdm/config/PostureRabbitMqConfig.java`
- `src/main/java/com/e24online/mdm/service/messaging/`
- migrations for indexes
- monitoring docs and dashboards

Exit criteria:

- ingest and evaluation health can be measured
- backlog and failure patterns are visible
- scale behavior is known before production rollout

## Cross-Cutting Workstreams

These must progress alongside the phases above.

### Testing

- integration tests for enroll -> ingest -> evaluate -> decision -> ack
- contract tests for agent payload compatibility
- entitlement enforcement tests
- UI tests for admin policy and tenant flows

### Audit

- subscription events
- user and tenant admin events
- enrollment lifecycle events
- posture pipeline events
- remediation status events

### Documentation

- frozen requirements
- agent API contract
- policy authoring guide
- operations runbooks
- SaaS support playbooks

## Recommended Delivery Order

1. Phase 1
2. Phase 2
3. Phase 3
4. Phase 4
5. Phase 5
6. Phase 6
7. Phase 7

Phase 0 should happen immediately and remain the reference point.

## Definition of a Credible v1 Release

The product is ready for a serious v1 release when all of the following are true:

- subscription and entitlement enforcement is active
- agent ingest contract is versioned and documented
- posture evaluation runs through one canonical workflow
- authentication and token lifecycle are production-safe
- remediation guidance is structured and measurable
- stale-device and fleet-risk reporting exists
- audit coverage spans admin, policy, enrollment, ingest, evaluation, and remediation
- queue health and failure visibility exist

## Stop-the-Line Rules

The following should block feature expansion until resolved:

- a second competing evaluation workflow is reintroduced
- a new feature requires remote device execution
- tenant entitlements are bypassed in core flows
- agent payload compatibility is changed without versioning
- remediation becomes an unstructured text-only concept again

## First Implementation Package

The first implementation package should include only:

1. subscription foundation
2. entitlement enforcement in user, enrollment, and ingest flows
3. status unification and removal of duplicate workflow paths
4. contract metadata for agent version and capabilities

That is the highest-leverage starting point and the least risky place to begin implementation.
