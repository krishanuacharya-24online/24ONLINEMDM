# 24Online MDM Feature and Policy Guide

## Purpose

This guide explains the current project in plain language.

It is written for business users, support teams, auditors, project managers, and any non-technical reader who needs to understand:

- what the system does,
- what each feature is for,
- who should use each feature,
- what policy each feature enforces,
- how the device decision process works.

## Plain-Language Summary

This project is a device trust and posture platform.

In simple terms, it checks whether a company device looks safe enough to use. It reviews device information such as operating system, security state, risky apps, and support lifecycle. It then gives the device a trust score and decides whether to:

- allow normal access,
- allow but warn,
- quarantine the device,
- block access.

If a device does not meet policy, the system can also send fix instructions.

## Key Terms

- **Tenant**: One customer, business unit, or managed workspace.
- **Device posture**: The current health and security condition of a device.
- **Trust score**: A score from 0 to 100 that shows how trusted a device is.
- **Remediation**: The action needed to fix a device problem.
- **Lifecycle**: Whether an operating system version is still supported by its vendor.
- **Policy**: A business rule that tells the system what is allowed, risky, or blocked.

## Who Uses the System

- **Product Admin**: Central administrator with full access, including tenant and user management.
- **Tenant User**: Signed-in user who works inside one tenant's data scope.
- **Device Agent**: Software on the device that sends posture information and receives decisions.
- **Security and IT teams**: People who define rules, scoring, actions, and remediation guidance.

## Main Features and the Policy Behind Them

### 1. Sign In and Account Security

**What it does**

- Lets staff sign in to the web system.
- Supports password change, login refresh, logout, and session checking.

**Policy in simple words**

- Only active users can sign in.
- Passwords must be strong.
- When a password is changed, old refresh sessions are cancelled.
- Tenant users are tied to one tenant.
- Product admins are not tied to a tenant and can work across tenants.

**Why it matters**

- This is the front door of the platform.
- It ensures only approved people can manage devices and policies.

### 2. Overview Dashboard

**What it does**

- Shows a quick summary of device trust and remediation workload.
- Displays counts such as total devices, trusted devices, high-risk devices, and open remediation.

**Policy in simple words**

- The dashboard is for monitoring, not for changing decisions.
- It should be used as an early warning view for security and operations teams.

**Why it matters**

- It gives leadership and operations a quick understanding of current device health.

### 3. Devices

**What it does**

- Shows device trust profiles.
- Shows trust score history, decisions, system snapshots, installed apps, payload records, and evaluation runs for each device.

**Policy in simple words**

- A device has one current trust profile that shows its latest status.
- Device activity should be reviewed by tenant scope.
- Trust history should be used for investigation, trend tracking, and audit review.

**Why it matters**

- This is the main place to understand what is happening to a device and why.

### 4. Payloads

**What it does**

- Shows the posture data sent from the device agent.
- Stores the raw input used for evaluation.

**Policy in simple words**

- Payloads are evidence records.
- They should be used for troubleshooting and audit review, not as business policy by themselves.
- A payload can end in one of four states: received, validated, evaluated, or failed.

**Why it matters**

- It gives a traceable record of what the device actually reported.

### 5. Evaluation Runs

**What it does**

- Shows the result of a device evaluation.
- Links the payload, trust score, decision, matches, and remediation for that evaluation.

**Policy in simple words**

- Every evaluation should produce a clear outcome and reason.
- Evaluation records are part of the audit trail.
- If remediation is required, the run should make that clear.

**Why it matters**

- It connects raw device data to the final business action.

### 6. Policy Center

This is the most important feature group in the project. It defines the rules that control device trust.

#### 6.1 System Rules

**What it does**

- Defines what the platform should look for in device information.
- Examples include device type, operating system, root detection, emulator use, USB debugging, or other reported fields.

**Policy in simple words**

- A system rule describes a risk or compliance check.
- Only active rules inside their valid date range are enforced.
- Rules can apply to specific operating systems or device types.
- Each rule can suggest a compliance action and a score impact.

**Why it matters**

- This is where business security expectations become enforceable checks.

#### 6.2 System Rule Conditions

**What it does**

- Breaks each system rule into smaller checks.
- Supports comparisons such as equals, greater than, in list, exists, and pattern match.

**Policy in simple words**

- Conditions define exactly when a rule should match.
- A rule can require all conditions to match or allow any matching condition group to trigger the rule.

**Why it matters**

- It gives fine control without changing application code.

#### 6.3 Reject Applications

**What it does**

- Lists apps that are blocked, restricted, or too old to be allowed.
- Supports matching by package ID or app name.
- Can also enforce a minimum acceptable version.

**Policy in simple words**

- If a device has a blocked app, or an app below the allowed version, the device becomes riskier.
- Severity and blocked reason should explain why the app is not acceptable.
- Only active records inside their valid date range are enforced.

**Why it matters**

- It prevents unsafe or non-approved software from being ignored.

#### 6.4 Trust Score Policies

**What it does**

- Converts findings into score changes.
- Tells the system how much each rule match, app match, or lifecycle signal should raise or lower trust.

**Policy in simple words**

- Each signal can change the trust score.
- A more severe issue should have a stronger negative effect.
- A weight can increase or reduce the impact of a rule.
- If no custom score policy exists, the software uses built-in fallback scoring.

**Why it matters**

- This is how the platform turns many findings into one easy-to-understand trust score.

#### 6.5 Trust Decision Policies

**What it does**

- Converts the final trust score into an action such as allow, notify, quarantine, or block.

**Policy in simple words**

- Score ranges should map to clear business actions.
- The decision policy can also say whether remediation is required.
- A response message can explain the result in business language.
- If no decision policy matches, the software uses built-in fallback thresholds.

**Why it matters**

- This is the final policy layer that controls access outcomes.

#### 6.6 Remediation Rules

**What it does**

- Stores the actions or instructions used to fix a device issue.
- Examples include removing an app, updating the OS, restricting network access, or asking for user action.

**Policy in simple words**

- Remediation instructions should be practical, specific, and easy to follow.
- They can target a specific operating system or device type.
- Only active remediation rules inside their valid date range should be used.

**Why it matters**

- Blocking or quarantining a device is not enough; the user also needs to know how to become compliant again.

#### 6.7 Rule Remediation Mappings

**What it does**

- Connects a finding or final decision to the remediation rule that should be sent.

**Policy in simple words**

- A matched rule, blocked app, trust policy, or final decision can trigger one or more remediation.
- Each mapping can be advisory, manual, or automatic.
- Rank order decides which remediation is shown first when several apply.

**Why it matters**

- It ensures the correct fix is linked to the correct problem.

### 7. OS Lifecycle

**What it does**

- Stores vendor support information for operating system release cycles.
- Tracks whether a release is still supported, at end of life, or beyond extended support.

**Policy in simple words**

- Devices on unsupported or outdated operating system cycles should be treated as higher risk.
- Lifecycle status must be based on a trusted source record.
- OS lifecycle policy helps the company avoid relying on software that no longer receives support.

**Why it matters**

- A secure device is not only about settings and apps. It also depends on whether the operating system is still supported.

### 8. Application Catalog

**What it does**

- Keeps a clean master list of applications.
- Helps the platform match the same app consistently across devices and policies.

**Policy in simple words**

- App identity should be standardized before it is used in policy decisions.
- This feature supports clean reporting and more reliable app matching.

**Why it matters**

- Without a shared catalog, the same app could appear under different names and weaken policy accuracy.

### 9. Lookups

**What it does**

- Stores the allowed lists used across the system, such as device types, lifecycle states, actions, statuses, remediation types, and threat categories.

**Policy in simple words**

- Lookup values should be treated as controlled reference data.
- Business terms should be consistent across screens, reports, and policies.

**Why it matters**

- It keeps the platform understandable and avoids inconsistent labels.

### 10. Tenants

**What it does**

- Creates and manages tenants.
- Rotates tenant API keys used by device agents.

**Policy in simple words**

- A tenant must be active before agents can submit posture data.
- Tenant keys are secret credentials and should be rotated when needed.
- Only one active tenant key is allowed at a time.
- When a tenant is deleted, it is marked inactive and its active key is revoked.

**Why it matters**

- This protects the boundary between customers or business units.

### 11. Users

**What it does**

- Creates and manages platform users.
- Supports `PRODUCT_ADMIN` and `TENANT_USER` roles.

**Policy in simple words**

- Users must have an approved role.
- Tenant users must belong to an active tenant.
- Product admins can exist without a tenant assignment.
- Deleted users are made inactive and cannot sign in.

**Why it matters**

- Good user governance reduces accidental or unauthorized changes.

### 12. Change Password

**What it does**

- Lets a signed-in user change their password.

**Policy in simple words**

- Password changes require the current password.
- New passwords must meet strength rules.
- Existing login refresh tokens are revoked after the password is changed.

**Why it matters**

- It limits the risk of long-lived compromised sessions.

### 13. Agent and Device Integration

**What it does**

- Receives posture submissions from managed devices.
- Returns a decision and remediation in the same workflow.
- Accepts acknowledgement when the device receives or applies the decision.

**Policy in simple words**

- Devices do not use normal website login.
- Agents must send a tenant ID and active tenant key.
- Device posture submissions are rate-limited to protect the service.
- A device submission can include system details and installed apps.
- The current service accepts payloads up to about 1 MB and up to 5000 installed-app entries per submission.

**Why it matters**

- This is the live enforcement path of the platform.

### 14. Health Check

**What it does**

- Provides a basic service health response.

**Policy in simple words**

- Health status is for support and operational monitoring.
- It should not expose sensitive business data.

**Why it matters**

- It helps support teams quickly confirm whether the service is up.

## How the Decision Process Works

The current project follows this business flow:

1. A device sends posture information.
2. The platform saves the report and reads important details such as operating system, device type, app list, root state, emulator state, and debugging state.
3. The platform checks system rules.
4. The platform checks whether the device has blocked or too-old apps.
5. The platform checks whether the operating system cycle is still supported.
6. The platform adjusts the trust score based on active scoring policies or fallback scoring.
7. The platform chooses a final action based on decision policies or fallback thresholds.
8. The platform adds remediation instructions when required.
9. The platform stores the decision and waits for device acknowledgement.

## Meaning of the Final Actions

- **ALLOW**: The device is acceptable for normal access.
- **NOTIFY**: The device is allowed for now, but there is a warning or lower-confidence issue.
- **QUARANTINE**: The device should have limited access until the issue is fixed.
- **BLOCK**: The device should not be allowed to continue until risk is removed.

## Default Decision Logic in the Current Code

If a custom decision policy does not match, the software currently uses these fallback thresholds:

- Score below 40: `BLOCK`
- Score from 40 to 59: `QUARANTINE`
- Score from 60 to 79: `NOTIFY`
- Score 80 and above: `ALLOW`

The current score bands are:

- Below 25: `CRITICAL`
- 25 to 49: `HIGH_RISK`
- 50 to 69: `MEDIUM_RISK`
- 70 to 89: `LOW_RISK`
- 90 and above: `TRUSTED`

## Access Policy in the Current Software

This section describes how access works today in the codebase.

- Product admins can manage everything, including tenants and users.
- Tenant users are limited to their own tenant when viewing tenant-scoped device and evaluation data.
- Product admins must provide a tenant context when using tenant-scoped device and evaluation APIs.
- Device agents use tenant headers and tenant keys, not the website login process.
- Tenant and user administration is restricted to the `PRODUCT_ADMIN` role.
- Shared policy, lookup, application catalog, and OS lifecycle features are currently accessible to any signed-in user.

## Operational and Governance Policy

- Policy changes should be made only by approved staff because they directly affect device access decisions.
- Remediation text should be written in clear language because end users may receive it directly.
- OS lifecycle data should be kept current because it affects risk and access outcomes.
- Tenant keys should be rotated whenever exposure is suspected or on a planned security schedule.
- User accounts that are no longer needed should be disabled or deleted promptly.
- Audit records such as decisions, scores, matches, and remediation should be retained according to company compliance requirements.

## Current Implementation Notes to Be Aware Of

These notes describe how the system behaves today. They are important for managers, auditors, and product owners.

- Full posture parsing, rule matching, trust scoring, and remediation generation happen during the agent posture submission flow.
- The manual evaluation create and retry endpoints currently reuse the device's existing trust score to produce a decision; they do not rerun the full rule-and-app matching pipeline.
- The current ingest logic reuses the latest payload row for a device instead of always creating a brand-new payload record for every submission.
- Policy, lookup, application catalog, and OS lifecycle management are currently available to any signed-in user. If the business wants only central administrators to change these areas, more role restrictions should be added.
- A default `admin` user is seeded by database migrations for local setup. That password should be changed immediately outside development use.
- Authentication cookies are currently issued without the browser `Secure` flag. Production use should enforce HTTPS and stronger cookie hardening.

## Recommended Business Ownership Model

- **Security team**: Own system rules, reject apps, trust score policies, decision policies, and remediation mappings.
- **IT operations**: Own remediation content, tenant rollout, key rotation, and day-to-day device follow-up.
- **Platform administration**: Own tenants, users, and shared master data governance.
- **Audit and compliance**: Review decision logic, change control, and evidence records.

## Short Conclusion

This project already contains the core parts of a device trust platform:

- user and tenant administration,
- device posture ingestion,
- rule-based policy evaluation,
- trust scoring,
- lifecycle awareness,
- remediation generation,
- decision tracking.

Its policy section is the heart of the product. The policy center decides what is risky, how serious that risk is, what action the platform takes, and what the user must do next.
