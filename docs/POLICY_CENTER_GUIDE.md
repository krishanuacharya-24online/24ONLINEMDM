# Policy Center Guide

## Purpose

This document explains the **Policy Center** of the 24Online MDM platform in full detail.

It is designed for both:

- **non-technical readers**, who need to understand what each policy does and why it matters, and
- **technical readers**, who need to understand how the software evaluates policy records and turns them into trust scores, decisions, and remediation tasks.

## What the Policy Center Is

The Policy Center is the part of the system that decides:

- what device behavior is acceptable,
- what behavior is risky,
- how much a risky condition should reduce trust,
- what final action should be taken,
- what fix instructions should be sent back.

In business terms, the Policy Center is the rulebook.

In technical terms, the Policy Center is the combination of these policy models and APIs:

- `system_information_rule`
- `system_information_rule_condition`
- `reject_application_list`
- `trust_score_policy`
- `trust_score_decision_policy`
- `remediation_rule`
- `rule_remediation_mapping`

The main controller for these is:

- `GET/POST/PUT/DELETE /v1/policies/*`

The Policy Center pages in the current UI are:

- `/ui/policies/system-rules`
- `/ui/policies/system-rules/{id}/conditions`
- `/ui/policies/reject-apps`
- `/ui/policies/trust-score-policies`
- `/ui/policies/trust-decision-policies`
- `/ui/policies/remediation-rules`
- `/ui/policies/rule-remediation-mappings`

## Policy Center at a Glance

The seven policy modules work together in this order:

1. **System Rules**
   Decide what device posture conditions should be checked.
2. **System Rule Conditions**
   Define the exact logic inside each system rule.
3. **Reject Applications**
   Define which apps are not acceptable on devices.
4. **Trust Score Policies**
   Convert findings into score changes.
5. **Trust Decision Policies**
   Convert the final score into a final action.
6. **Remediation Rules**
   Define the fix instructions or actions.
7. **Rule Remediation Mappings**
   Connect findings or decisions to remediation rules.

## The Full Policy Execution Flow

When a device sends a posture payload, the Policy Center affects the result in this sequence:

1. The system normalizes the payload into device snapshot and installed app records.
2. It loads all **active** system rules that match the device `os_type`, optional `os_name`, optional `device_type`, and current time window.
3. It evaluates each rule against its conditions.
4. It loads all **active** reject-app records for the device OS and checks installed apps against them.
5. It resolves the OS lifecycle state and may create a lifecycle posture signal such as `OS_EOL`, `OS_EEOL`, or `OS_NOT_TRACKED`.
6. It loads all **active** trust score policies and tries to match each finding or lifecycle signal to a score rule.
7. It calculates the new trust score.
8. It loads the active trust decision policy that matches the final score.
9. It decides the final action: `ALLOW`, `NOTIFY`, `QUARANTINE`, or `BLOCK`.
10. It loads remediation mappings and generates the remediation list.

## Important Policy Concepts

### Active vs inactive

Every policy type has a `status` field.

- `ACTIVE`: the record can be used.
- `INACTIVE`: the record exists but should not be used.

### Effective time window

Most policy records also have:

- `effective_from`
- `effective_to`

The current engine only uses records whose time window is valid at the moment of evaluation.

### Soft delete

Most policy records also have:

- `is_deleted`

Deleting a policy from the current API usually means the record is marked deleted, not physically removed.

This is good for auditability.

## 1. System Rules

### Business meaning

A **System Rule** is a posture rule about the device itself.

It answers a question such as:

- Is USB debugging enabled?
- Is the device rooted or jailbroken?
- Is the device running on an emulator?
- Is the OS version too old?
- Is the device type restricted?

### Technical meaning

System rules are stored in `system_information_rule`.

The evaluator currently loads only rules that are:

- not deleted,
- `ACTIVE`,
- inside their effective window,
- matching the device `os_type`,
- matching optional `os_name`,
- matching optional `device_type`.

Matching is implemented in `AgentPostureWorkflowService.activeSystemRules(...)`.

### Main fields and what they mean

| Field | Meaning for non-technical users | Technical meaning |
| --- | --- | --- |
| `rule_code` | Short unique name of the rule | Primary signal candidate used during trust-score policy matching |
| `rule_tag` | Business label or grouping tag | Secondary signal candidate used during trust-score policy matching |
| `priority` | Which rule should be considered earlier | Lower number is evaluated first |
| `version` | Version number of the rule | Record version marker only; no version conflict engine is built in |
| `match_mode` | Whether all groups must match or only one group is enough | `ALL` or `ANY`, applied across condition groups |
| `compliance_action` | Suggested seriousness of the rule | Normalized action hint used in scoring and audit records |
| `risk_score_delta` | Built-in score impact if no trust-score policy overrides it | Fallback score delta |
| `severity` | How serious the rule is | Can be used when matching trust-score policies |
| `description` | Human explanation of the rule | Stored explanation for admins and audit |
| `os_type` | OS family the rule applies to | Required matching filter |
| `os_name` | Specific OS/distro name if needed | Optional extra OS filter |
| `device_type` | Device class the rule applies to | Optional device filter |
| `status` | Whether this rule is live | `ACTIVE` or `INACTIVE` |

### How system rules behave in the current engine

If a system rule matches:

- a `SYSTEM_RULE` match record is written,
- a score signal is produced,
- the system tries to find a matching trust-score policy using:
  - `source_type = SYSTEM_RULE`
  - `signal_key = rule_code` or `rule_tag`
  - optional matching `severity`
  - optional matching `compliance_action`
- if no trust-score policy matches, the system falls back to `risk_score_delta`.

### When to use system rules

Use a system rule when the problem is about device posture or device-reported properties, not about installed apps.

Examples:

- rooted Android device,
- emulator usage not allowed,
- server device type not allowed in a tenant fleet,
- debugging features enabled,
- disallowed OS major version.

### Authoring guidance

- Keep `rule_code` stable because it is used as a scoring signal.
- Use `rule_tag` when you want multiple related rules to share the same scoring logic.
- Keep `description` business-friendly so operations teams can understand the intent.
- Use `priority` to make rule order predictable during review, even though score calculation still processes all matches.

## 2. System Rule Conditions

### Business meaning

A System Rule Condition is the exact test inside a rule.

If the rule is the policy statement, the condition is the sentence that makes it measurable.

### Technical meaning

Conditions are stored in `system_information_rule_condition`.

Each system rule can have many conditions.

The engine groups conditions by `condition_group`.

Current condition logic works like this:

- inside the same group: **all conditions in that group must be true**
- across groups:
  - if the parent rule `match_mode = ANY`: at least one group must be true
  - if the parent rule `match_mode = ALL`: every group must be true

This behavior is implemented in:

- `matchesSystemRule(...)`
- `evaluateCondition(...)`

### Supported operators in the current engine

| Operator | Plain meaning | Technical behavior |
| --- | --- | --- |
| `EQ` | equals | case-insensitive string compare when not numeric/boolean |
| `NEQ` | not equal | inverse of `EQ` |
| `GT` | greater than | numeric or lexical compare |
| `GTE` | greater than or equal | numeric or lexical compare |
| `LT` | less than | numeric or lexical compare |
| `LTE` | less than or equal | numeric or lexical compare |
| `IN` | value is in a list | compares actual value against list values |
| `NOT_IN` | value is not in a list | inverse of `IN` |
| `REGEX` | matches a pattern | Java regex match |
| `EXISTS` | field exists | true when a value is present |
| `NOT_EXISTS` | field does not exist | true when no value is present |

### Supported condition value types

The backend condition model supports:

- `value_text`
- `value_numeric`
- `value_boolean`
- `value_json`

Current UI form support is simpler:

- text,
- number,
- boolean.

For technical users: the API model can also store `value_json`, which is useful for list-style checks such as `IN` or `NOT_IN`.

### Common field names the engine understands directly

The engine currently recognizes these common posture fields:

- `tenant_id`
- `device_external_id`
- `agent_id`
- `device_type`
- `os_type`
- `os_name`
- `os_version`
- `os_cycle`
- `time_zone`
- `kernel_version`
- `api_level`
- `os_build_number`
- `manufacturer`
- `root_detected`
- `running_on_emulator`
- `usb_debugging_status`
- `installed_app_count`

It can also read arbitrary JSON fields from the raw posture payload, including dotted paths such as:

- `security.patch_level`
- `hardware.boot_state`

### Condition examples

#### Example A: rooted device

- `field_name = root_detected`
- `operator = EQ`
- `value_boolean = true`

#### Example B: Android API too low

- `field_name = api_level`
- `operator = LT`
- `value_numeric = 30`

#### Example C: allowed time zones only

- `field_name = time_zone`
- `operator = IN`
- `value_json = ["Asia/Kolkata","Asia/Colombo"]`

### Authoring guidance

- Use direct known fields when possible because they are easier to maintain.
- Use dotted JSON paths only when the payload field is stable and well documented.
- Keep groups simple. Too many nested business ideas inside one rule make troubleshooting difficult.

## 3. Reject Applications

### Business meaning

Reject Applications are app-level deny or restriction rules.

They answer questions such as:

- Is this app banned?
- Is this app considered risky?
- Is this app below the minimum safe version?

### Technical meaning

Reject-app policies are stored in `reject_application_list`.

Current engine behavior:

- only active, non-deleted, in-window records are considered,
- only records matching the device app OS are considered,
- app matching is done by package ID first when provided,
- otherwise by app name,
- if `min_allowed_version` is present, the installed version must be greater than or equal to it.

Matching is implemented in `matchesRejectApp(...)`.

### Main fields and meaning

| Field | Meaning for business readers | Technical meaning |
| --- | --- | --- |
| `policy_tag` | Business tag for the app policy | Can be used as a signal key candidate for score policies |
| `threat_type` | What kind of risk this app represents | Business classification |
| `severity` | Seriousness of the app issue | Used in fallback scoring and optional trust-score policy matching |
| `blocked_reason` | Why the app is not allowed | Human explanation |
| `app_name` | Display name of the app | Secondary match key |
| `package_id` | Store/package identifier | Preferred exact match key |
| `app_os_type` | App platform | Required filter for evaluation |
| `app_category` | Category of risk | Reporting and business grouping |
| `app_latest_version` | Latest known version | Informational in current engine |
| `min_allowed_version` | Lowest acceptable version | Used in current engine matching |

### Current scoring behavior for reject apps

When a reject app matches:

- the engine creates a `REJECT_APPLICATION` match,
- the engine tries to find a trust-score policy using:
  - `source_type = REJECT_APPLICATION`
  - `signal_key = package_id`, `app_name`, or `policy_tag`
  - optional matching `severity`
  - compliance action fixed to `BLOCK`
- if no score policy matches, the fallback score delta is:
  - `max(-80, -10 * severity)`
  - default severity is `3` if missing.

### When to use reject apps

Use reject apps when the risk is about specific software on the device.

Examples:

- proxy/VPN app not allowed,
- remote admin tool not allowed,
- old browser version not allowed,
- sideload app store not allowed.

### Authoring guidance

- Prefer `package_id` whenever available.
- Use `app_name` as backup when package ID is not stable or not available.
- Keep `blocked_reason` human-readable because support teams may rely on it.
- Use consistent `policy_tag` names when several apps belong to one risk family.

## 4. Trust Score Policies

### Business meaning

Trust Score Policies convert findings into score changes.

They answer the question:

> "If this issue is found, how much should it change the device's trust score?"

### Technical meaning

Trust score policies are stored in `trust_score_policy`.

Each policy matches a signal by:

- `source_type`
- `signal_key`
- optional `severity`
- optional `compliance_action`

The current engine supports these source types:

- `SYSTEM_RULE`
- `REJECT_APPLICATION`
- `POSTURE_SIGNAL`
- `MANUAL`

The current UI exposes the first three plus `MANUAL` for authoring, though the automated evaluator mainly uses:

- `SYSTEM_RULE`
- `REJECT_APPLICATION`
- `POSTURE_SIGNAL`

### Main fields and meaning

| Field | Plain meaning | Technical meaning |
| --- | --- | --- |
| `policy_code` | Unique name of the scoring policy | Administrative identifier |
| `source_type` | Where the signal comes from | Must match the source emitted by the evaluator |
| `signal_key` | The exact signal to score | Matching key used by the evaluator |
| `severity` | Only apply when severity matches | Optional filter |
| `compliance_action` | Only apply when action matches | Optional filter |
| `score_delta` | Raw score increase or decrease | Base score impact before weighting |
| `weight` | Multiplier on the score delta | Final delta is `round(score_delta * weight)` |

### Important current matching rules

#### For system rules

The engine looks for `signal_key` equal to:

- `rule_code`, or
- `rule_tag`

#### For reject apps

The engine looks for `signal_key` equal to:

- `package_id`, or
- `app_name`, or
- `policy_tag`

#### For lifecycle posture

The engine looks for `signal_key` equal to:

- `OS_EOL`
- `OS_EEOL`
- `OS_NOT_TRACKED`

### What happens when multiple trust score policies match

The engine sorts candidate score policies by:

1. highest `weight`
2. lowest `id`

So for technical and policy owners:

- overlapping trust-score policies are allowed by the code,
- but the highest weight wins,
- if weights tie, the earliest row ID wins.

### Fallback scoring when no trust-score policy matches

If no matching trust-score policy is found, the engine falls back to:

- system rule: use `risk_score_delta` from the system rule
- reject app: `max(-80, -10 * severity)`
- lifecycle:
  - `EEOL = -40`
  - `EOL = -25`
  - `NOT_TRACKED = -15`

### Score boundaries

After all deltas are applied, the score is clamped to:

- minimum `0`
- maximum `100`

### When to use trust score policies

Use them when you want scoring logic to be centrally controlled and reusable.

Examples:

- all `ROOT_*` rules should reduce score by 40,
- all blocked VPN apps should reduce score by 20,
- all `OS_EEOL` signals should reduce score by 50.

### Authoring guidance

- Keep `signal_key` naming consistent across policy types.
- Prefer central trust-score policies over many one-off `risk_score_delta` values if you want easier governance.
- Avoid accidental overlaps unless you clearly understand the current “highest weight wins” behavior.

## 5. Trust Decision Policies

### Business meaning

Trust Decision Policies map the final score to the final business action.

They answer:

> "Given the device's final trust score, what should the platform do?"

### Technical meaning

Decision policies are stored in `trust_score_decision_policy`.

The evaluator looks for the active policy where:

- `score_min <= final_score`
- `score_max >= final_score`
- record is active and inside effective window

Current repository matching is:

- ordered by `score_min DESC`
- then `score_max ASC`
- then `id ASC`

This means overlapping score ranges are allowed by the code, but may produce unexpected outcomes if not governed carefully.

### Main fields and meaning

| Field | Plain meaning | Technical meaning |
| --- | --- | --- |
| `policy_name` | Name of the decision band | Administrative name |
| `score_min` | Lowest score in this band | Inclusive |
| `score_max` | Highest score in this band | Inclusive |
| `decision_action` | What to do | `ALLOW`, `NOTIFY`, `QUARANTINE`, `BLOCK` |
| `remediation_required` | Whether fix instructions must be included | Boolean used in final response |
| `response_message` | Human explanation | Used as decision reason when present |

### Current fallback decision logic

If no decision policy matches, the engine falls back to:

- score `< 40` => `BLOCK`
- score `< 60` => `QUARANTINE`
- score `< 80` => `NOTIFY`
- otherwise => `ALLOW`

If no decision policy matches:

- remediation is required for any non-`ALLOW` action.

### When to use decision policies

Use them whenever the business wants explicit control over score bands and responses.

Examples:

- 90 to 100 => allow
- 70 to 89 => notify
- 40 to 69 => quarantine
- 0 to 39 => block

### Authoring guidance

- Avoid overlapping score bands unless the priority is intentional and documented.
- Make response messages business-friendly because they become part of the user-facing reason.
- Decide clearly whether remediation is mandatory for each band.

## 6. Remediation Rules

### Business meaning

A Remediation Rule defines the action or instruction needed to fix a problem.

Examples:

- uninstall a blocked app,
- update the operating system,
- disable USB debugging,
- acknowledge policy,
- apply network restriction.

### Technical meaning

Remediation rules are stored in `remediation_rule`.

The current engine will use a remediation rule only if:

- it is active,
- not deleted,
- inside the effective window,
- and optional `os_type` / `device_type` restrictions match the current device.

This targeting is implemented in `matchesRemediationTarget(...)`.

### Main fields and meaning

| Field | Plain meaning | Technical meaning |
| --- | --- | --- |
| `remediation_code` | Unique remediation identifier | Stable machine-friendly code |
| `title` | Short title of the fix | User/support facing |
| `description` | Full explanation of the fix | User/support facing |
| `remediation_type` | Category of fix | Lookup-backed classification |
| `os_type` | Optional OS restriction | Only applies to matching OS |
| `device_type` | Optional device restriction | Only applies to matching device type |
| `instruction_json` | Structured instructions | Stored in backend model; useful for agents or workflow automation |
| `priority` | Importance/order of the remediation rule | Informational in current engine; mapping rank is used for selection order |

### Note about the current UI

The current UI form exposes:

- code
- title
- description
- type
- os_type
- device_type
- priority
- status
- effective dates

The backend model also has `instruction_json`, but the current UI does not directly expose it in the remediation page form.

### When to use remediation rules

Use them to define reusable fix actions once, then map them many times.

### Authoring guidance

- Write titles and descriptions in plain language.
- Keep codes stable for integration use.
- If devices or automation engines will consume machine-readable instructions, keep `instruction_json` structured and versioned.

## 7. Rule Remediation Mappings

### Business meaning

Mappings connect a finding or decision to the fix that should be recommended or enforced.

They answer:

> "When this issue happens, which remediation rule should apply?"

### Technical meaning

Mappings are stored in `rule_remediation_mapping`.

The current engine supports these mapping source types:

- `SYSTEM_RULE`
- `REJECT_APPLICATION`
- `TRUST_POLICY`
- `DECISION`

### How each source type works

| Source type | What it maps from | Required reference in practice |
| --- | --- | --- |
| `SYSTEM_RULE` | matched system rule | `system_information_rule_id` |
| `REJECT_APPLICATION` | matched reject app policy | `reject_application_list_id` |
| `TRUST_POLICY` | matched trust policy record | `trust_score_policy_id` |
| `DECISION` | final decision action | `decision_action` |

### Main fields and meaning

| Field | Plain meaning | Technical meaning |
| --- | --- | --- |
| `source_type` | What kind of event triggers this mapping | Matching strategy for mapping selection |
| `system_information_rule_id` | Link to system rule | Used only when source is `SYSTEM_RULE` |
| `reject_application_list_id` | Link to reject app | Used only when source is `REJECT_APPLICATION` |
| `trust_score_policy_id` | Link to trust policy | Used only when source is `TRUST_POLICY` |
| `decision_action` | Link to final action | Used only when source is `DECISION` |
| `remediation_rule_id` | Which remediation to apply | Required |
| `enforce_mode` | How strongly to apply it | `AUTO`, `MANUAL`, `ADVISORY` |
| `rank_order` | Display/selection order | Lower rank is processed first |

### Current remediation generation behavior

The engine currently:

1. collects mappings linked to each saved match,
2. also collects mappings linked directly to the final `decision_action`,
3. sorts candidates by `rank_order`,
4. removes duplicates using run ID + remediation rule ID + match ID,
5. keeps only remediation rules whose target OS/device match the posture,
6. creates remediation tasks with initial status `PENDING`.

### Important implementation detail

For `TRUST_POLICY` source mappings to work in the current engine:

- there must be a saved match with `match_source = TRUST_POLICY`.

That currently happens for lifecycle trust-policy matches only.

So today:

- `TRUST_POLICY` mappings are most useful for lifecycle-driven trust-policy matches,
- they are not a generic hook for every score event.

### Authoring guidance

- Keep source references clean. Only one source reference should be meaningful for a given mapping.
- Use `DECISION` mappings for broad “whenever blocked, do this” behavior.
- Use `SYSTEM_RULE` or `REJECT_APPLICATION` mappings when the remediation should be tied to a specific cause.
- Use `rank_order` to put the most important or most actionable remediation first.

## How All Seven Modules Work Together

### Example 1: Rooted phone

#### Business view

- Rule says rooted phones are not compliant.
- Score policy says root issues reduce trust strongly.
- Decision policy says low trust means quarantine.
- Remediation says remove root access or re-enroll the device.

#### Technical flow

1. `system_information_rule` for rooted Android phones is active.
2. `system_information_rule_condition` checks `root_detected = true`.
3. Match is saved as `SYSTEM_RULE`.
4. `trust_score_policy` with `source_type=SYSTEM_RULE` and `signal_key=root_device_rule` is applied.
5. Trust score drops.
6. `trust_score_decision_policy` matches the final score band.
7. `rule_remediation_mapping` links the system rule or final decision to a remediation rule.
8. Remediation task is created.

### Example 2: Blocked VPN app

#### Business view

- VPN app is on the banned list.
- Device should not be fully trusted until the app is removed.

#### Technical flow

1. Installed app matches `reject_application_list`.
2. Match is saved as `REJECT_APPLICATION`.
3. Scoring policy is looked up using package ID, app name, or policy tag.
4. If not found, fallback app penalty is used.
5. Final action is chosen from score band.
6. Mapping connects the reject-app rule to “uninstall blocked app” remediation.

### Example 3: End-of-life OS

#### Business view

- Device runs an unsupported OS cycle.
- Even if the device is otherwise healthy, it should be treated as higher risk.

#### Technical flow

1. OS lifecycle is resolved to `EOL`, `EEOL`, or `NOT_TRACKED`.
2. A posture signal such as `OS_EOL` is created.
3. Trust-score policy tries to match `source_type=POSTURE_SIGNAL` and that signal key.
4. If no policy exists, fallback lifecycle penalty is applied.
5. If a lifecycle trust-policy row matched, a `TRUST_POLICY` match record is saved.
6. Remediation can be generated from the trust-policy mapping or the final decision mapping.

## Policy Writing Best Practices

### For non-technical policy owners

- Write policies in business language first, then encode them.
- Avoid creating too many similar rules that do the same thing.
- Decide whether the goal is:
  - detect,
  - warn,
  - restrict,
  - block,
  - fix.
- Review score ranges and remediation text together so the outcome feels consistent.

### For technical implementers

- Keep signal naming stable over time.
- Avoid overlapping trust-score and decision-policy ranges unless precedence is understood.
- Prefer exact identifiers such as `package_id` over display names.
- Test lifecycle and rule interactions together, not separately.
- Remember that the current UI does not expose every field the backend model supports.

## Current UI Coverage vs Backend Coverage

### What the UI supports well today

- creating and editing all seven core policy types,
- selecting lookup-backed values,
- soft deleting policy records,
- editing system rule conditions with text, number, and boolean values.

### What the backend supports beyond the current UI

- richer system rule fields such as `os_name`, `os_version`, `time_zone`, `kernel_version`, and fuller effective-window control,
- condition `value_json`,
- arbitrary payload field paths in conditions,
- remediation `instruction_json`,
- trust-policy matching based on multiple signal candidates.

## Current Implementation Caveats

These are important for technical leads and governance reviewers.

### 1. Policy access and scope enforcement

In the current codebase:

- policy APIs are restricted to `PRODUCT_ADMIN` and `TENANT_ADMIN`,
- tenant scope is validated through `X-Tenant-Id` and authenticated tenant context,
- tenant admins can read global fallback records but cannot modify global records.

This aligns policy access with enterprise tenant-governance expectations.

### 2. Manual evaluation endpoints do not rerun the full policy engine

`/v1/evaluations/runs` create and retry endpoints currently reuse the device's existing trust score to map a decision.

They do **not** rebuild matches, score events, reject-app matches, or remediation the same way the agent ingestion path does.

The full policy engine currently runs in the agent posture workflow.

### 3. Payload rows are reused per device in current ingest logic

The current ingest service updates the latest payload row for the device instead of always inserting a brand-new row.

That matters if an audit team expects one raw payload row per submission.

### 4. Decision policy overlap is validated before save

The service now blocks overlapping `score_min` / `score_max` ranges when effective windows overlap in the same tenant scope.

Conflicts are returned as `409 Conflict` so admins can fix policy ranges before activation.

### 5. Trust-score policy semantic conflicts are validated

The service now blocks overlapping effective windows for the same `source_type + signal_key + severity + compliance_action` in the same tenant scope.

Conflicts are returned as `409 Conflict` to keep scoring behavior deterministic.

### 6. Policy change audit trail is now append-only

Policy create/update/delete/clone operations now write to `policy_change_audit` with:

- policy type and policy ID,
- operation type (`CREATE`, `UPDATE`, `DELETE`, `CLONE`),
- tenant scope and actor,
- before/after JSON snapshots,
- optional approval ticket field.

### 7. Monitoring and alerting baseline for policy operations

Policy monitoring and alert definitions are documented in:

- `docs/ops/POLICY_MONITORING_ALERTING.md`
- `docs/ops/prometheus/policy-alert-rules.yml`

These cover policy API error rate, policy API latency, policy-audit write failures, and service readiness.

### 8. Rollback runbook and drill

Policy rollback procedure and quarterly drill template are documented in:

- `docs/ops/POLICY_ROLLBACK_RUNBOOK.md`

This includes trigger criteria, rollback sequencing, and post-rollback validation checkpoints.

## Recommended Governance Model for Policy Center

### Business owner

- Security policy owner or compliance owner

### Technical owner

- Platform engineering or security engineering

### Operational reviewers

- Service desk
- endpoint operations
- tenant administrators

### Change control recommendation

Every material policy change should record:

- why the change is needed,
- who approved it,
- which devices or tenants are expected to be affected,
- rollback plan,
- validation result after release.

## Suggested Policy Authoring Checklist

Before activating a new policy, confirm:

- the rule has a clear business purpose,
- the signal naming is consistent,
- the score impact is proportionate,
- the final decision band is still sensible,
- remediation exists if the action is not `ALLOW`,
- the message and description are understandable to support teams,
- there is no accidental overlap with existing active policies,
- the effective dates are correct.

## Short Summary

The Policy Center is the control layer of this MDM platform.

For non-technical readers:

- it defines what is allowed,
- what is risky,
- how serious a problem is,
- and how the device should be fixed.

For technical readers:

- it is a structured evaluation pipeline made of rules, conditions, scoring, decision bands, remediation definitions, and mapping records.

If you want the platform to behave predictably, the Policy Center must be treated as governed business logic, not only as raw configuration data.
