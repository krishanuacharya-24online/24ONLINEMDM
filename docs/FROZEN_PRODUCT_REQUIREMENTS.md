# Frozen Product Requirements

Status: approved baseline for implementation planning

## 1. Product Definition

The product is a multi-tenant SaaS agent-based posture, compliance, and trust-evaluation platform.

It is not a full MDM platform.

The platform does these jobs:

1. receives device posture and installed-application evidence from an installed agent
2. stores the raw evidence
3. normalizes the evidence into queryable device state
4. evaluates the device against policy
5. calculates trust score and final decision
6. returns a structured remediation plan to the agent
7. verifies improvement on the next scan after the user manually applies remediation

The platform does not directly change device state.

## 2. Product Positioning

The product should be positioned as:

- device posture platform
- compliance and trust-evaluation platform
- manual-remediation guidance platform

The product must not be positioned as:

- full MDM
- remote device command platform
- endpoint control plane
- app deployment or config deployment system

## 3. Core Product Principles

1. Evidence-driven, not command-driven.
2. Tenant-isolated by default.
3. Explainable decisions over opaque scoring.
4. Raw evidence retained for audit and troubleshooting.
5. Normalized state retained for fast policy evaluation and reporting.
6. Manual remediation, verified by rescan.
7. Subscription and entitlement controls enforced as core SaaS behavior, not as a later billing add-on.

## 4. Problem Statement

Organizations need a way to continuously assess whether a device is safe enough to access business systems, without relying on remote device control. The product solves this by using an installed agent to collect posture data, evaluating that evidence against configurable policy, and returning clear remediation guidance for the end user to perform manually.

## 5. In-Scope Product Capabilities

### 5.1 Enrollment and Device Identity

- Device enrollment using setup-key and steady-state device credential flows.
- Active/inactive enrollment lifecycle.
- Device token rotation.
- Per-device identity tied to tenant.

### 5.2 Evidence Collection and Ingestion

- Agent sends versioned posture payloads.
- Payload includes system state and installed applications.
- Raw payload is stored as evidence.
- Payload processing is asynchronous and queue-backed.
- Idempotent ingest supported by payload hash and payload identity rules.

### 5.3 Evidence Normalization

- Device trust profile stores current posture summary.
- Device system snapshot stores parsed system evidence per payload.
- Installed application inventory stores normalized installed-app rows per payload.
- OS lifecycle lookup enriches posture with lifecycle state.

### 5.4 Policy Engine

- System rules
- System rule conditions
- Reject applications
- Trust score policies
- Trust decision policies
- Remediation rules
- Rule-remediation mappings

### 5.5 Decisioning

- Final trust score from 0 to 100.
- Final action derived from score and decision policy.
- Structured remediation plan returned to the agent.
- Decision response tracked with delivery status and acknowledgment.

### 5.6 Manual Remediation Workflow

- Agent shows remediation guidance to the user.
- User manually changes the device.
- Later scan verifies whether remediation is resolved.
- Open vs resolved remediation is visible to admins.

### 5.7 Admin and Operations

- Product admin control plane
- Tenant admin control plane
- Auditor access
- Device inventory and history
- Payload history
- Evaluation history
- Policy management
- Audit history
- Native fleet reporting and trend views inside the product admin console

### 5.8 SaaS Subscription and Entitlements

- Plan catalog
- Tenant subscription state
- Usage measurement
- Entitlement enforcement
- Grace and suspension behavior

## 6. Explicit Non-Goals

The following are frozen out of scope:

- remote command execution
- configuration push
- application installation or uninstallation orchestration
- lock, wipe, reboot, or any live device action
- desired-state enforcement engine
- certificate or VPN profile deployment
- dependency on embedded third-party BI dashboards for core reporting, including Superset
- claiming the product is a full MDM

If any of these become required later, that should be treated as a new product line, not a small enhancement.

## 7. Users and Roles

### 7.1 Product Admin

- manages all tenants
- manages plans and subscription states
- manages global policy baselines
- views platform-wide reports and operations

### 7.2 Tenant Admin

- manages users and enrollments inside one tenant
- manages tenant-scoped policies
- reviews fleet posture, decisions, and remediation progress

### 7.3 Auditor

- read-only access to audit, evaluations, device history, and reports

### 7.4 Tenant User

- optional for later end-user-facing visibility only
- not required for server-side MVP

### 7.5 Device Agent

- sends posture evidence
- receives decision and remediation plan
- optionally reports whether plan was shown and acknowledged
- packaged customer-facing desktop app for Windows, Linux, and macOS
- architecture support: Windows `x64`, `x86`, `arm64`; Linux and macOS `x64`, `arm64`
- packaged builds may pin the service `base_url` at build time so endpoint users do not edit it manually
- tenant secrets must use protected OS-backed secure storage rather than plaintext config
- closing the main window must terminate the app rather than leaving a resident background process
- installed Windows builds should support optional startup-on-sign-in and desktop-shortcut preferences

## 8. Core Business Flow

1. Device is enrolled into a tenant.
2. Agent sends versioned posture payload.
3. Platform stores raw payload.
4. Platform normalizes system and app evidence.
5. Platform resolves OS lifecycle state.
6. Platform evaluates rules and scoring policy.
7. Platform computes trust score and final decision.
8. Platform generates remediation guidance.
9. Agent receives and displays remediation guidance.
10. User manually performs remediation.
11. Next scan verifies whether posture improved and whether remediation is resolved.

## 9. Functional Requirements

### 9.1 SaaS and Tenancy

- Every tenant must have an active subscription record.
- Tenant usage must be measurable independently of billing integration.
- Tenant data must be logically isolated by tenant scope.
- Product admins may operate in global or tenant scope.
- Tenant admins may operate only in their own tenant.

### 9.2 Subscription and Entitlements

Required entities:

- `subscription_plan`
- `tenant_subscription`
- `tenant_usage_snapshot`
- `tenant_feature_override`

Required subscription states:

- `TRIALING`
- `ACTIVE`
- `GRACE`
- `PAST_DUE`
- `SUSPENDED`
- `CANCELLED`
- `EXPIRED`

Required primary entitlements:

- maximum active devices
- maximum tenant users
- maximum monthly payloads
- data retention window
- feature flags for premium reporting or advanced controls

Required enforcement points:

- user creation and activation
- device enrollment and device-token rotation where relevant
- payload ingestion
- advanced feature access
- retention/export behavior

### 9.3 Agent Payload Contract

The payload contract must be frozen as a versioned contract.

Minimum required fields:

- `device_external_id`
- `agent_id`
- `payload_version`
- `payload_hash`
- `payload_json`

Additional fields that must become part of the stable contract:

- `capture_time`
- `agent_version`
- `agent_capabilities`
- `device_external_id` stability requirements
- explicit schema version compatibility expectations

Installed applications are evidence, not policy master data.

### 9.4 Evidence Model

The platform must distinguish clearly between:

- raw evidence
- normalized facts
- derived lifecycle signals
- score events
- final decisions
- remediation guidance

Raw evidence must be preserved.
Normalized evidence must be queryable.
Score and decision changes must be explainable from persisted data.

### 9.5 Policy Model

The following policy families are frozen:

- system rules
- system rule conditions
- reject applications
- trust score policies
- trust decision policies
- remediation rules
- rule-remediation mappings

The product must not introduce a second parallel policy model for the same purpose.

### 9.6 Decision and Remediation Model

Final decision actions:

- `ALLOW`
- `NOTIFY`
- `QUARANTINE`
- `BLOCK`

Decision payload must include:

- evaluation run id
- decision action
- trust score
- decision reason
- remediation-required flag
- remediation list

Each remediation item must support:

- title
- reason
- severity or priority
- user instructions
- verification hint
- affected evidence reference where possible

Remediation lifecycle must support:

- `PROPOSED`
- `DELIVERED`
- `USER_ACKNOWLEDGED`
- `STILL_OPEN`
- `RESOLVED_ON_RESCAN`
- `CLOSED`

### 9.7 Reporting and Operations

Reporting is a native product capability. v1 reporting must work from the platform's own pages and APIs, without depending on an embedded third-party BI surface.

Required operational views:

- device last seen
- current trust score
- posture status
- stale devices
- top failing rules
- top risky applications
- lifecycle risk distribution
- remediation open vs resolved
- tenant-level usage and entitlement health

### 9.8 Audit and Compliance

The platform must record audit events for:

- login and session events
- tenant and user administration
- policy changes
- enrollment and de-enrollment
- payload ingest
- evaluation queueing and completion
- decision delivery and acknowledgment
- remediation status transitions
- subscription and entitlement actions

## 10. Trust Model

This platform is a reported-posture system unless stronger verification is added.

The current trust posture should be described honestly:

- the agent reports device facts
- the server evaluates those reported facts
- the server does not independently prove all facts are true

Security hardening must therefore focus on:

- device identity strength
- anti-replay
- payload integrity
- version and capability awareness
- suspicious submission detection

If platform attestation becomes available later, it should strengthen the product, but the product definition must not depend on it for MVP.

## 11. Architecture Decisions That Must Not Change

1. Queue-backed asynchronous evaluation stays.
2. Raw evidence and normalized evidence remain separate.
3. Policy evaluation remains tenant-aware with global baseline support.
4. Remediation remains guidance-only.
5. Subscription enforcement is part of core platform behavior.
6. The server remains a posture/compliance engine, not a remote device executor.

## 12. v1 Acceptance Criteria

The product is considered a credible v1 when it has:

- secure tenant-aware enrollment
- device identity and token lifecycle management
- versioned posture ingest
- raw evidence retention
- normalized system and application inventory
- deterministic scoring and decisioning
- structured remediation guidance
- manual-remediation verification on later scans
- tenant-scoped administration
- subscription and entitlement enforcement
- auditability across the full workflow
- reporting for stale devices, score trends, and top posture failures through native product views

## 13. Frozen Decisions Summary

These decisions are considered fixed:

- passive posture/compliance SaaS, not full MDM
- no remote commands
- no desired-state enforcement
- subscription-first SaaS model
- versioned evidence contract
- manual remediation with rescan verification
- policy families listed in this document
- multi-tenant admin model with global and tenant scope

Any request that violates these decisions should be treated as a strategic change request, not a normal implementation change.
