# Policy Rollback Runbook

This runbook is for production incidents where policy changes cause incorrect device decisions, high error rates, or policy write instability.

## Trigger Conditions

Start rollback when one or more are true:

- `PolicyApiHigh5xxRate` is firing and unresolved after initial mitigation.
- A policy release causes incorrect BLOCK/QUARANTINE behavior.
- Policy audit writes are failing continuously.
- Tenant-impacting policy behavior cannot be corrected quickly with forward fix.

## Roles

- Incident Commander: owns timeline and go/no-go.
- Policy Owner: validates policy intent and rollback target.
- Platform Engineer: executes API/DB rollback steps.
- SRE/On-call: monitors alerts and confirms stabilization.

## Rollback Strategy

Use rollback in this order:

1. **Policy-data rollback** (preferred): revert specific policy rows to prior known-good state.
2. **Application rollback**: deploy previous stable app build.
3. **Database restore** (last resort): restore from snapshot/PITR if schema/data corruption exists.

## Preconditions

1. Confirm last known-good release version and timestamp.
2. Confirm affected policy type(s), tenant scope(s), and policy IDs.
3. Export current affected rows for evidence and recovery.
4. Record incident ticket and approver.

## Procedure A: Policy-Data Rollback (Preferred)

1. Identify changed records in `policy_change_audit` by time window and actor.
2. For each affected record, reconstruct target state from `before_state_json`.
3. Apply rollback in a transaction per policy type and tenant scope.
4. Re-validate business constraints:
   - decision ranges do not overlap,
   - trust-score windows do not overlap,
   - mapping integrity remains valid.
5. Re-test with a canary tenant and representative devices.

## Procedure B: Application Rollback

1. Deploy previous stable artifact version.
2. Keep DB schema at current version unless incompatible.
3. Run smoke tests for:
   - policy list/get/create/update/delete,
   - system-rule clone,
   - evaluation + remediation path.
4. Verify alert recovery.

## Procedure C: Database Restore (Last Resort)

1. Announce maintenance mode.
2. Restore DB using approved backup/PITR process.
3. Run Flyway validation and schema check.
4. Run data integrity checks for policy tables.
5. Bring service back and run smoke tests.

## Post-Rollback Validation

1. `/actuator/health/readiness` is `UP`.
2. Policy API 5xx alerts resolved.
3. Policy audit events are writing with `outcome="success"`.
4. Sample tenants show expected policy decisions.
5. Incident ticket updated with:
   - root cause,
   - corrective action,
   - prevention action.

## Rollback Drill (Quarterly)

1. Select a non-production environment with realistic data.
2. Introduce a controlled bad policy change.
3. Detect via alerts.
4. Execute Procedure A end-to-end.
5. Time each phase and record gaps.
6. Publish drill report with owners and due dates.

## Drill Evidence Template

- Date:
- Environment:
- Incident commander:
- Trigger used:
- Time to detect:
- Time to rollback:
- Time to recover:
- Validation results:
- Follow-up actions:
