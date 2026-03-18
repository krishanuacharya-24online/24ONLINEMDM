# MDM/Posture Schema Logic Reference

This document explains:
- what each table is for,
- what every column means,
- when each table is written/read in the MDM + device-posture flow.

## 1. End-to-End Runtime Flow

1. Policy setup (admin/config stage):
   - `system_information_rule`, `system_information_rule_condition`
   - `reject_application_list`
   - `os_release_lifecycle_master`
   - `trust_score_policy`
   - `trust_score_decision_policy`
   - `remediation_rule`, `rule_remediation_mapping`
   - lookup master table (`lkp_master`) and `application_catalog`

2. Agent posture API call (`POST /v1/agent/posture-payloads`):
   - Write raw payload to `device_posture_payload`
   - Parse and write:
     - one system snapshot row to `device_system_snapshot`
     - many app rows to `device_installed_application`
   - Resolve/create device row in `device_trust_profile`
   - Resolve OS lifecycle from `os_release_lifecycle_master` using (`os_type`, `os_name`, `os_cycle`)
   - Match system rules and reject-app rules
   - Build lifecycle signals (`OS_EOL`, `OS_EEOL`, `OS_NOT_TRACKED`) as trust-policy matches
   - Write findings to `posture_evaluation_match`
   - Write score audit rows to `device_trust_score_event`
   - Update summary score in `device_trust_profile`
   - Map score to action via `trust_score_decision_policy` (or fallback threshold logic)
   - Resolve remediation from `rule_remediation_mapping` + `remediation_rule`
   - Write remediation tasks to `posture_evaluation_remediation`
   - Write final decision payload to `device_decision_response`

Current implementation executes the full posture parse + evaluate + decide flow inside this single API call transaction.

## 2. Core Business Tables

### `device_posture_payload`
Purpose:
- Raw ingress table for payloads from device agent.

Written when:
- API receives a posture payload from an endpoint.

Read when:
- Agent posture workflow reads payload immediately after ingest.
- Audit/troubleshooting fetches raw input/debug context.

Columns:
- `id`: Payload primary key.
- `tenant_id`: Tenant/workspace identifier (multi-tenant partition key).
- `device_external_id`: Stable device id from agent/MDM.
- `agent_id`: Agent identity/version scope.
- `payload_version`: Payload schema version.
- `payload_hash`: Optional idempotency hash to detect duplicate payloads.
- `payload_json`: Full raw payload body.
- `received_at`: Server receive time.
- `process_status`: Ingestion stage (`RECEIVED/VALIDATED/EVALUATED/FAILED`).
- `process_error`: Last processing error text.
- `processed_at`: Processing completion/failure timestamp.
- `created_at`: Row create time.
- `created_by`: Source identity (`agent-ingest`).

### `device_system_snapshot`
Purpose:
- Normalized, query-friendly system posture snapshot extracted from payload.

Written when:
- Posture workflow extracts system block from `device_posture_payload`.

Read when:
- Rule engine evaluates `system_information_rule`.
- Ops/reporting queries latest device posture.

Columns:
- `id`: Snapshot primary key.
- `device_posture_payload_id`: Source raw payload id.
- `device_trust_profile_id`: Device profile link.
- `capture_time`: Device/system capture time.
- `device_type`: Device class.
- `os_type`: Operating system family.
- `os_name`: Linux distro code when `os_type='LINUX'` (for example `DEBIAN`, `CENTOS`, `FEDORA`).
- `os_cycle`: OS release cycle/build channel used for lifecycle lookup (for example `24.04`, `11-25h2-e`).
- `os_release_lifecycle_master_id`: Linked lifecycle master row used for EOL/EEOL evaluation.
- `os_version`: OS version string.
- `time_zone`: Device timezone.
- `kernel_version`: Kernel version (when available).
- `api_level`: Numeric API level (mainly Android).
- `os_build_number`: Build number string.
- `manufacturer`: OEM/vendor.
- `root_detected`: Root/jailbreak signal.
- `running_on_emulator`: Emulator signal.
- `usb_debugging_status`: USB debugging enabled signal.
- `is_latest`: Latest snapshot marker per device profile.
- `created_at`: Row create time.
- `created_by`: Parse stage identity (`posture-parser`).

### `device_installed_application`
Purpose:
- Normalized list of applications from payload, one row per app.

Written when:
- Posture workflow extracts installed apps from payload.

Read when:
- Reject-list matching (`reject_application_list`).
- Risk and remediation analysis.

Columns:
- `id`: Installed-app row primary key.
- `device_posture_payload_id`: Source payload id.
- `device_trust_profile_id`: Device profile id.
- `capture_time`: App list capture time.
- `app_name`: App display name.
- `publisher`: App publisher/vendor.
- `package_id`: Package/bundle/app id.
- `app_os_type`: App platform OS.
- `app_version`: Installed version on device.
- `latest_available_version`: Optional latest known version.
- `is_system_app`: System/preloaded app flag.
- `install_source`: Source store/channel (Play Store, sideload, etc.).
- `status`: App lifecycle state (`ACTIVE/REMOVED/UNKNOWN`).
- `created_at`: Row create time.
- `created_by`: Parse stage identity.
- `application_catalog_id`: Normalized app identity reference.

### `application_catalog`
Purpose:
- De-duplicated application identity master.
- Shared reference between reject-list and installed-app rows.

Written when:
- Normalization migration backfills it.
- Insert/update triggers auto-upsert on app rows.

Read when:
- Fast canonical app matching/reporting across devices and policies.

Columns:
- `id`: Catalog primary key.
- `os_type`: OS family for identity uniqueness.
- `package_id`: Raw package/bundle id.
- `app_name`: Raw app name.
- `app_name_norm`: Lowercase generated app name.
- `package_id_norm`: Coalesced generated package id for matching.
- `publisher`: Canonical publisher text.
- `created_at`: Create timestamp.
- `modified_at`: Last update timestamp.

### `os_release_lifecycle_master`
Purpose:
- Canonical lifecycle catalog for OS/platform release cycles.
- Provides release, EOL, and EEOL dates used by trust-score computation.

Written when:
- Seed/migration imports lifecycle catalog data from managed source.
- Admin updates platform lifecycle cycles.

Read when:
- Posture workflow resolves device snapshot lifecycle state (`SUPPORTED`, `EOL`, `EEOL`, `NOT_TRACKED`).
- Scoring engine applies lifecycle risk signals.

Columns:
- `id`: Lifecycle row primary key.
- `platform_code`: Canonical platform code (`WINDOWS`, `UBUNTU`, `RHEL`, etc.).
- `os_type`: MDM OS family mapping (`ANDROID/IOS/WINDOWS/MACOS/LINUX`) when applicable.
- `os_name`: Linux distro mapping when applicable.
- `cycle`: Lifecycle channel/cycle identifier.
- `released_on`: Cycle release date.
- `eol_on`: End-of-life date.
- `eeol_on`: Extended end-of-life date (optional).
- `latest_version`: Latest known patch/version string.
- `support_state`: Source record state (`TRACKED`, `SUPPORTED`, `NOT_FOUND`).
- `source_name`: Source system identifier (for example `endoflife.date`).
- `source_url`: Source URL for traceability.
- `notes`: Additional source/context notes.
- `status`: Active state.
- `is_deleted`: Soft-delete flag.
- `created_at`: Create timestamp.
- `created_by`: Creator identity.
- `modified_at`: Last modified timestamp.
- `modified_by`: Modifier identity.

### `device_trust_profile`
Purpose:
- Current trust state summary per device.

Written when:
- First seen device is created.
- Each evaluation updates score/posture summary.

Read when:
- Decision service needs current trust status.
- Dashboards need current posture per device.

Columns:
- `id`: Device profile primary key.
- `tenant_id`: Tenant/workspace id.
- `device_external_id`: Stable device id.
- `device_type`: Device class.
- `os_type`: OS family.
- `os_name`: Linux distro code when `os_type='LINUX'`; null for non-Linux devices.
- `os_release_lifecycle_master_id`: Last resolved lifecycle master row.
- `os_lifecycle_state`: Current lifecycle posture (`SUPPORTED`, `EOL`, `EEOL`, `NOT_TRACKED`).
- `current_score`: Current trust score (0-100).
- `score_band`: Score bucket (`CRITICAL/HIGH_RISK/MEDIUM_RISK/LOW_RISK/TRUSTED`).
- `posture_status`: Compliance state (`COMPLIANT/NON_COMPLIANT/UNKNOWN`).
- `last_event_at`: Last score-affecting event time.
- `last_recalculated_at`: Last score recomputation time.
- `is_deleted`: Soft-delete flag.
- `created_at`: Create timestamp.
- `created_by`: Creator identity.
- `modified_at`: Last modified timestamp.
- `modified_by`: Modifier identity.

### `system_information_rule`
Purpose:
- Master table for system posture rules.

Written when:
- Admin/security team creates or updates posture rules.

Read when:
- Posture workflow selects active rules by target OS/device and time window.

Columns:
- `id`: Rule primary key.
- `rule_code`: Stable unique rule identifier.
- `priority`: Evaluation order priority (lower means earlier).
- `version`: Rule revision/version.
- `match_mode`: Condition mode (`ALL`/`ANY`).
- `compliance_action`: Suggested action (`ALLOW/NOTIFY/QUARANTINE/BLOCK`).
- `risk_score_delta`: Base score impact from this rule.
- `rule_tag`: Grouping/tagging label.
- `status`: Active state.
- `severity`: Severity level (1-5).
- `description`: Human description.
- `device_type`: Target device class filter.
- `os_type`: Target OS filter.
- `os_name`: Optional Linux distro filter; only valid when `os_type='LINUX'`.
- `os_version`: Optional OS version filter.
- `time_zone`: Optional timezone filter.
- `kernel_version`: Optional kernel filter.
- `apilevel`: Optional API level filter.
- `osbuildnumber`: Optional build number filter.
- `manufacturer`: Optional vendor filter.
- `rootdetected`: Optional root/jailbreak condition.
- `runningonemulator`: Optional emulator condition.
- `usb_debigging_status`: Optional USB debugging condition.
- `effective_from`: Rule valid-from timestamp.
- `effective_to`: Rule valid-to timestamp.
- `is_deleted`: Soft-delete flag.
- `created_at`: Create timestamp.
- `created_by`: Creator identity.
- `modified_at`: Last modified timestamp.
- `modified_by`: Modifier identity.

### `system_information_rule_condition`
Purpose:
- Atomic rule conditions for `system_information_rule` (normalized condition model).

Written when:
- Admin defines or edits condition rows for a rule.

Read when:
- Evaluation engine computes rule match result.

Columns:
- `id`: Condition primary key.
- `system_information_rule_id`: Parent rule reference.
- `condition_group`: Logical subgroup id (for grouped evaluation).
- `field_name`: Snapshot field to evaluate.
- `operator`: Condition operator (`EQ`, `IN`, `GTE`, etc.).
- `value_text`: Text comparison value.
- `value_numeric`: Numeric comparison value.
- `value_boolean`: Boolean comparison value.
- `value_json`: Structured/list value.
- `weight`: Relative condition weight.
- `status`: Active state.
- `is_deleted`: Soft-delete flag.
- `created_at`: Create timestamp.
- `created_by`: Creator identity.
- `modified_at`: Last modified timestamp.
- `modified_by`: Modifier identity.

### `reject_application_list`
Purpose:
- App deny/reject policy list (blocked or restricted applications).

Written when:
- Security team adds blocked apps or minimum version policy.

Read when:
- Installed apps are matched to identify disallowed apps.

Columns:
- `id`: Reject row primary key.
- `policy_tag`: Policy identifier/tag.
- `threat_type`: Threat classification.
- `severity`: Threat severity (1-5).
- `blocked_reason`: Human-readable reason for reject.
- `app_name`: App name for matching.
- `publisher`: App publisher.
- `package_id`: Package/bundle id for exact matching.
- `app_category`: App category (VPN, spyware, etc.).
- `app_os_type`: OS type for app policy.
- `app_latest_version`: Latest known version.
- `min_allowed_version`: Minimum permitted version.
- `latest_ver_major`: Parsed major from latest version.
- `latest_ver_minor`: Parsed minor from latest version.
- `latest_ver_patch`: Parsed patch from latest version.
- `min_ver_major`: Parsed major from minimum version.
- `min_ver_minor`: Parsed minor from minimum version.
- `min_ver_patch`: Parsed patch from minimum version.
- `status`: Active/inactive policy state.
- `effective_from`: Policy valid-from.
- `effective_to`: Policy valid-to.
- `is_deleted`: Soft-delete flag.
- `created_at`: Create timestamp.
- `created_by`: Creator identity.
- `modified_at`: Last modified timestamp.
- `modified_by`: Modifier identity.
- `application_catalog_id`: Canonical app identity.

### `trust_score_policy`
Purpose:
- Mapping rules/signals to trust score impact.

Written when:
- Risk/scoring policy is configured.

Read when:
- Posture workflow converts matches/signals into score deltas.

Columns:
- `id`: Policy primary key.
- `policy_code`: Unique scoring policy code.
- `source_type`: Source kind (`SYSTEM_RULE/REJECT_APPLICATION/POSTURE_SIGNAL/MANUAL`).
- `signal_key`: Signal identifier used for lookup (includes lifecycle signals like `OS_EOL`, `OS_EEOL`, `OS_NOT_TRACKED`).
- `severity`: Optional severity constraint.
- `compliance_action`: Optional action constraint.
- `score_delta`: Score impact from signal.
- `weight`: Multiplier/weight for scoring.
- `status`: Active state.
- `effective_from`: Valid-from timestamp.
- `effective_to`: Valid-to timestamp.
- `is_deleted`: Soft-delete flag.
- `created_at`: Create timestamp.
- `created_by`: Creator identity.
- `modified_at`: Last modified timestamp.
- `modified_by`: Modifier identity.

### `trust_score_decision_policy`
Purpose:
- Converts final trust score ranges into final device action.

Written when:
- Decision thresholds are configured.

Read when:
- Final stage of evaluation chooses allow/quarantine/block/notify.

Columns:
- `id`: Decision policy primary key.
- `policy_name`: Policy set name.
- `score_min`: Inclusive lower score boundary.
- `score_max`: Inclusive upper score boundary.
- `decision_action`: Result action.
- `remediation_required`: Whether remediation tasks must be returned.
- `response_message`: Optional human message for response payload.
- `status`: Active state.
- `effective_from`: Valid-from timestamp.
- `effective_to`: Valid-to timestamp.
- `is_deleted`: Soft-delete flag.
- `created_at`: Create timestamp.
- `created_by`: Creator identity.
- `modified_at`: Last modified timestamp.
- `modified_by`: Modifier identity.

### `posture_evaluation_run`
Purpose:
- One complete evaluation execution per payload/device.

Written when:
- Posture workflow starts/finalizes an evaluation run.

Read when:
- Audit/reporting, troubleshooting, response generation.

Columns:
- `id`: Evaluation run primary key.
- `device_posture_payload_id`: Input payload id.
- `device_trust_profile_id`: Target device profile id.
- `trust_score_decision_policy_id`: Policy used for final action.
- `os_release_lifecycle_master_id`: Lifecycle row used in this run.
- `os_lifecycle_state`: Lifecycle state derived for this run.
- `evaluation_status`: Run state (`QUEUED/VALIDATING/RUNNING/IN_PROGRESS/COMPLETED/FAILED/CANCELLED`). In the current agent posture workflow, rows are typically written as `COMPLETED` unless an error/failure/cancel flow updates the run state.
- `trust_score_before`: Score before evaluation.
- `trust_score_delta_total`: Net score delta applied.
- `trust_score_after`: Score after evaluation.
- `decision_action`: Final action for device.
- `decision_reason`: Human/debug decision reason.
- `remediation_required`: Whether remediation should be sent.
- `matched_rule_count`: Number of system-rule matches.
- `matched_app_count`: Number of reject-app matches.
- `evaluated_at`: Evaluation completion timestamp.
- `responded_at`: Time response payload was generated/sent.
- `response_payload`: Cached response body.
- `created_at`: Create timestamp.
- `created_by`: Evaluation identity (`rule-engine`).

### `posture_evaluation_match`
Purpose:
- Stores each match/findings row generated in evaluation.

Written when:
- A rule/app/policy match is detected during run.

Read when:
- Scoring engine aggregates deltas.
- Remediation resolver maps actions.

Columns:
- `id`: Match row primary key.
- `posture_evaluation_run_id`: Parent evaluation run.
- `match_source`: Source type (`SYSTEM_RULE/REJECT_APPLICATION/TRUST_POLICY`).
- `system_information_rule_id`: Matched system rule (if applicable).
- `reject_application_list_id`: Matched reject app policy (if applicable).
- `trust_score_policy_id`: Matched trust scoring policy (if applicable).
- `os_release_lifecycle_master_id`: Lifecycle master row used when match is lifecycle-driven.
- `os_lifecycle_state`: Lifecycle state context for this match.
- `device_installed_application_id`: App row that caused match (app path).
- `remediation_rule_id`: Chosen remediation rule link.
- `matched`: Match boolean marker.
- `severity`: Severity for match.
- `compliance_action`: Match-level action suggestion.
- `score_delta`: Match-level score impact.
- `match_detail`: Detailed match evidence (json).
- `created_at`: Create timestamp.
- `created_by`: Evaluation identity.

### `device_trust_score_event`
Purpose:
- Append-only audit of every score impact event.

Written when:
- Any scoring event is applied (rule/app/manual/posture).

Read when:
- Audit trail, forensics, timeline history, score explainability.

Columns:
- `id`: Event primary key.
- `device_trust_profile_id`: Device profile id.
- `event_source`: Event source type.
- `source_record_id`: Optional source record id from upstream system.
- `trust_score_policy_id`: Scoring policy used.
- `system_information_rule_id`: Source system rule (optional).
- `reject_application_list_id`: Source reject-app policy (optional).
- `os_release_lifecycle_master_id`: Lifecycle master row used when event came from lifecycle scoring.
- `os_lifecycle_state`: Lifecycle state context (`SUPPORTED`, `EOL`, `EEOL`, `NOT_TRACKED`).
- `observed_payload`: Snapshot/evidence for event.
- `score_before`: Score before this event.
- `score_delta`: Event delta.
- `score_after`: Score after this event.
- `event_time`: Event occurrence time.
- `processed_at`: Processing time.
- `processed_by`: Processor identity.
- `notes`: Operator/system notes.
- `posture_evaluation_run_id`: Linked evaluation run.

### `remediation_rule`
Purpose:
- Master table for remediation instructions/actions.

Written when:
- Security/compliance team defines remediation playbooks.

Read when:
- Posture workflow resolves remediation to include in decision response.

Columns:
- `id`: Remediation primary key.
- `remediation_code`: Unique remediation code.
- `title`: Short remediation title.
- `description`: Full remediation description.
- `remediation_type`: Type (`USER_ACTION/AUTO_ACTION/...`).
- `os_type`: Optional OS targeting.
- `device_type`: Optional device-type targeting.
- `instruction_json`: Structured machine-readable instructions.
- `priority`: Remediation priority.
- `status`: Active state.
- `effective_from`: Valid-from timestamp.
- `effective_to`: Valid-to timestamp.
- `is_deleted`: Soft-delete flag.
- `created_at`: Create timestamp.
- `created_by`: Creator identity.
- `modified_at`: Last modified timestamp.
- `modified_by`: Modifier identity.

### `rule_remediation_mapping`
Purpose:
- Maps source findings/actions to remediation rules.

Written when:
- Admin configures which remediation applies for each rule/policy/decision.

Read when:
- Remediation resolver builds device remediation list.

Columns:
- `id`: Mapping primary key.
- `source_type`: Mapping source (`SYSTEM_RULE/REJECT_APPLICATION/TRUST_POLICY/DECISION`).
- `system_information_rule_id`: Source rule id when source is system-rule.
- `reject_application_list_id`: Source reject-app id when source is reject-app.
- `trust_score_policy_id`: Source trust policy id when source is trust-policy.
- `decision_action`: Source final action when source is decision.
- `remediation_rule_id`: Target remediation id.
- `enforce_mode`: Enforcement mode (`AUTO/MANUAL/ADVISORY`).
- `rank_order`: Ordering if multiple remediation map.
- `status`: Active state.
- `effective_from`: Valid-from timestamp.
- `effective_to`: Valid-to timestamp.
- `is_deleted`: Soft-delete flag.
- `created_at`: Create timestamp.
- `created_by`: Creator identity.
- `modified_at`: Last modified timestamp.
- `modified_by`: Modifier identity.

### `posture_evaluation_remediation`
Purpose:
- Per-run remediation tasks generated for the device.

Written when:
- Posture workflow selects remediation rules from match/decision mapping.

Read when:
- Response builder composes remediation list.
- Delivery/ack pipeline tracks remediation state.

Columns:
- `id`: Remediation task primary key.
- `posture_evaluation_run_id`: Parent run id.
- `remediation_rule_id`: Referenced remediation master id.
- `posture_evaluation_match_id`: Source match row (optional).
- `source_type`: Remediation source (`MATCH`/`DECISION`).
- `remediation_status`: Runtime task status (`PENDING/SENT/ACKED/SKIPPED/FAILED`).
- `due_at`: Due timestamp.
- `completed_at`: Completion timestamp.
- `instruction_override`: Per-run override instruction json.
- `created_at`: Create timestamp.
- `created_by`: Evaluation identity.

### `device_decision_response`
Purpose:
- Final outbound decision payload tracking per evaluation run.

Written when:
- Policy service emits decision back to endpoint or queue.

Read when:
- Delivery tracking and endpoint sync status checks.
- Audit/reporting for decision history.

Columns:
- `id`: Response row primary key.
- `posture_evaluation_run_id`: One-to-one link with run.
- `tenant_id`: Tenant/workspace id.
- `device_external_id`: Device id for addressing/routing.
- `decision_action`: Final action sent to device.
- `trust_score`: Score included in response.
- `remediation_required`: Whether remediation list included.
- `response_payload`: Full outbound payload.
- `delivery_status`: Delivery lifecycle (`PENDING/SENT/ACKED/FAILED/TIMEOUT`).
- `sent_at`: Sent timestamp.
- `acknowledged_at`: Device ack timestamp.
- `error_message`: Error details if delivery failed.
- `created_at`: Create timestamp.
- `created_by`: Producer identity (`policy-service`).

## 3. Lookup Master Table (`lkp_master`)

The schema now uses one physical lookup table:
- `lkp_master`

Columns:
- `lookup_type`: Logical lookup domain key (for example `lkp_os_type`, `lkp_device_type`, `lkp_compliance_action`).
- `code`: Lookup value code inside a domain.
- `description`: Human-readable meaning.

Primary key:
- `(lookup_type, code)`

When used:
- Written during migration/seed.
- Updated by normalization migrations for dynamic categories (`lkp_app_category`, `lkp_threat_type`).
- Read by config/reporting APIs and admin tools.

Example query:
- `SELECT * FROM lkp_master ORDER BY lookup_type, code;`

## 4. Practical Implementation Logic (Service Layer)

### Posture ingestion service (`PostureIngestionService`)
Writes:
- `device_posture_payload`

Behavior:
- Optional idempotent dedupe by (`tenant_id`, `payload_hash`) returns existing payload id.
- Stores raw `payload_json` as JSON string.
- Initializes payload with `process_status='RECEIVED'`.

### Agent posture workflow service (`AgentPostureWorkflowService`)
Reads:
- `device_posture_payload` (raw payload content for parse)
- `device_system_snapshot` (latest for device)
- `device_installed_application` (current payload)
- `system_information_rule` + `system_information_rule_condition`
- Rule targeting uses `os_type` + `os_name` + `device_type` (`os_name` only for Linux devices/rules)
- `os_release_lifecycle_master` (resolve by `os_type` + `os_name` + `os_cycle`)
- `reject_application_list`
- `trust_score_policy`
- `trust_score_decision_policy`
- `rule_remediation_mapping` + `remediation_rule`

Writes:
- `device_system_snapshot`
- `device_installed_application`
- `device_trust_profile` (create/update)
- `posture_evaluation_run`
- `posture_evaluation_match`
- `device_trust_score_event`
- `posture_evaluation_remediation`
- `device_decision_response`

Updates:
- `device_trust_profile` (`current_score`, `score_band`, `posture_status`, `os_lifecycle_state`)
- `posture_evaluation_run` final fields (`trust_score_after`, `decision_action`, `responded_at`)
- `device_posture_payload.process_status`: `RECEIVED` -> `VALIDATED` -> `EVALUATED` (or `FAILED`)

Trust score logic (current implementation):
- Start from `device_trust_profile.current_score` (default `100` for new device).
- Build score signals from:
  - system-rule matches (`SYSTEM_RULE`)
  - reject-app matches (`REJECT_APPLICATION`)
  - lifecycle signals (`POSTURE_SIGNAL`: `OS_EOL`, `OS_EEOL`, `OS_NOT_TRACKED`)
- Apply matching `trust_score_policy` where available (`source_type`, `signal_key`, optional `severity`, optional `compliance_action`).
- Policy delta is `round(score_delta * weight)`; then final score is clamped to `[0,100]`.
- Fallback deltas if no policy matched:
  - reject app: `max(-80, -10 * severity)` (severity default `3`)
  - lifecycle: `EOL=-25`, `EEOL=-40`, `NOT_TRACKED=-15`
  - system rule: `risk_score_delta` (or `0`)

Decision logic:
- Prefer active `trust_score_decision_policy` range mapping for final score.
- Fallback thresholds:
  - `<40 BLOCK`
  - `<60 QUARANTINE`
  - `<80 NOTIFY`
  - `>=80 ALLOW`
- `remediation_required` comes from decision policy when matched, else defaults to `decision_action != ALLOW`.

### Delivery/ack service
Reads:
- `device_decision_response`
- `posture_evaluation_remediation`

Updates:
- `device_decision_response.delivery_status`, `sent_at`, `acknowledged_at`, `error_message`
- `posture_evaluation_remediation.remediation_status`, `completed_at`

## 5. Notes for Developers

- PostgreSQL folds unquoted column names to lowercase, so columns declared as `apiLevel`/`osBuildNumber`/`rootDetected` are stored as `apilevel`/`osbuildnumber`/`rootdetected`.
- Enum-like text values are centralized in `lkp_master` (`lookup_type` + `code`), while business tables keep readable text columns for compatibility.
- `application_catalog_id` on app-related tables is the canonical app join key; prefer it for analytics and deduplicated matching.
