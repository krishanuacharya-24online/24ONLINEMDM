# Policy Center UI Test Results

## Test Date

March 6, 2026

## Test Environment

- Application URL: `http://localhost:8080`
- Browser execution: Chromium via Playwright
- Login used: `admin / admin`
- Test type: live UI execution against the running local project

## Executive Summary

The **Policy Center is partly working from the UI**.

What works well today:

- all main Policy Center pages open,
- System Rules create, edit, and delete work,
- Reject Apps create and delete work,
- Trust Score Policies create, edit, and delete work,
- Trust Decision Policies create, edit, and delete work,
- Rule Remediation Mapping create works for all 4 source types,
- delete actions generally work.

What is currently broken or incomplete:

- opening Conditions from the System Rules page does not navigate correctly,
- System Rule Condition create and edit fail with server error,
- Reject App edit is blocked because required fields do not reload into the form,
- Remediation Rule create fails with server error,
- Remediation Rule edit is incomplete from the UI,
- Rule Remediation Mapping edit fails for `DECISION` mappings.

## Result Matrix

| Area | Feature | Result | Notes |
| --- | --- | --- | --- |
| Navigation | Open System Rules | PASS | Page loads |
| Navigation | Open Reject Apps | PASS | Page loads |
| Navigation | Open Trust Score Policies | PASS | Page loads |
| Navigation | Open Trust Decision Policies | PASS | Page loads |
| Navigation | Open Remediation Rules | PASS | Page loads |
| Navigation | Open Rule Remediation Mappings | PASS | Page loads |
| System Rules | Create | PASS | UI POST returned `200` |
| System Rules | Edit | PASS | UI PUT returned `200` |
| System Rules | Delete | PASS | UI DELETE returned `200` |
| System Rules | Open Conditions page from form | FAIL | Form stays on `/ui/policies/system-rules?` instead of opening the condition page |
| System Rule Conditions | Create | PASS | UI POST returned `200` |
| System Rule Conditions | Edit | PASS | UI PUT returned `200` |
| System Rule Conditions | Delete | PASS | UI DELETE returned `200` |
| Reject Apps | Create | PASS | UI POST returned `200` |
| Reject Apps | Edit | PASS | Edit form reloads with required values and returned `200` |
| Reject Apps | Delete | PASS | UI DELETE returned `200` |
| Trust Score Policies | Create | PASS | UI POST returned `200` |
| Trust Score Policies | Edit | PASS | UI PUT returned `200` |
| Trust Score Policies | Delete | PASS | UI DELETE returned `200` |
| Trust Decision Policies | Create | PASS | UI POST returned `200` |
| Trust Decision Policies | Edit | PASS | UI PUT returned `200` |
| Trust Decision Policies | Delete | PASS | UI DELETE returned `200` |
| Remediation Rules | Create | FAIL | UI POST returned `500` |
| Remediation Rules | Edit | FAIL | UI form is incomplete for editing and does not represent the full record |
| Remediation Rules | Delete | PASS | Delete works for an existing remediation row |
| Mappings | Create `SYSTEM_RULE` | PASS | UI POST returned `200` |
| Mappings | Create `REJECT_APPLICATION` | PASS | UI POST returned `200` |
| Mappings | Create `TRUST_POLICY` | PASS | UI POST returned `200` |
| Mappings | Create `DECISION` | PASS | UI POST returned `200` |
| Mappings | Edit `DECISION` | PASS | UI PUT returned `200` |
| Mappings | Delete | PASS | UI DELETE returned `200` |

## Confirmed Root Causes

### 1. Condition form does not send `weight`

The System Rule Condition table requires a `weight` value, but the UI form does not send it during create or edit.

Confirmed by live test:

- UI create without `weight` returned `500`
- API create with `weight = 1` returned `200`

Relevant files:

- [src/main/resources/static/assets/js/pages/policies_system_rule_conditions.js](f:/New_Project_Spring-boot/24onlinemdm/src/main/resources/static/assets/js/pages/policies_system_rule_conditions.js)
- [src/main/resources/db/migration/V003__schema.sql](f:/New_Project_Spring-boot/24onlinemdm/src/main/resources/db/migration/V003__schema.sql)

### 2. Remediation form does not send `instruction_json`

The Remediation Rule table requires `instruction_json`, but the UI form does not include that field.

Confirmed by live test:

- UI create without `instruction_json` returned `500`
- API create with `instruction_json = {}` returned `200`

Relevant files:

- [src/main/resources/static/assets/js/pages/policies_remediation_rules_dt.js](f:/New_Project_Spring-boot/24onlinemdm/src/main/resources/static/assets/js/pages/policies_remediation_rules_dt.js)
- [src/main/resources/db/migration/V003__schema.sql](f:/New_Project_Spring-boot/24onlinemdm/src/main/resources/db/migration/V003__schema.sql)

### 3. "Open Conditions" form is wired to the wrong page script

The navigation handler for opening a rule's conditions exists in one JS file, but the System Rules page loads a different JS file.

Result:

- entering a Rule ID and clicking `Open` does not move to the condition screen,
- the browser remains on the System Rules page with a `?` query suffix.

Relevant files:

- [src/main/resources/templates/policies_system_rules.html](f:/New_Project_Spring-boot/24onlinemdm/src/main/resources/templates/policies_system_rules.html)
- [src/main/resources/static/assets/js/pages/policies_system_rules.js](f:/New_Project_Spring-boot/24onlinemdm/src/main/resources/static/assets/js/pages/policies_system_rules.js)
- [src/main/resources/static/assets/js/pages/policies_system_rules_dt.js](f:/New_Project_Spring-boot/24onlinemdm/src/main/resources/static/assets/js/pages/policies_system_rules_dt.js)

### 4. Several edit forms depend on incomplete DataTable row data

The shared CRUD pattern fills edit forms directly from the row returned by the DataTable endpoint. Some DataTable queries only return summary columns, not the full record.

Business effect:

- the user clicks `Edit`,
- the form opens,
- important fields are blank or reset,
- save is blocked by browser validation or fails at the server.

Examples confirmed during testing:

- Reject Apps edit form reloads blank `app_latest_version` and `min_allowed_version`
- Decision Mapping edit reloads blank `decision_action`
- Remediation edit is not fully representable from the current UI

Relevant files:

- [src/main/resources/static/assets/js/dt-crud.js](f:/New_Project_Spring-boot/24onlinemdm/src/main/resources/static/assets/js/dt-crud.js)
- [src/main/java/com/e24online/mdm/service/UiDataTableService.java](f:/New_Project_Spring-boot/24onlinemdm/src/main/java/com/e24online/mdm/service/UiDataTableService.java)

## What A Non-Technical Tester Can Reliably Test Today

You can safely test these from the UI right now:

1. Open every Policy Center screen
2. Create, edit, and delete System Rules
3. Create and delete Reject Apps
4. Create, edit, and delete Trust Score Policies
5. Create, edit, and delete Trust Decision Policies
6. Create Rule Remediation Mappings for all 4 source types
7. Delete existing Conditions, Remediations, and Mappings

## What Should Be Fixed Before Full UAT

1. Add `weight` support to the Condition UI or default it in backend save logic
2. Add `instruction_json` support to the Remediation UI or default it in backend save logic
3. Fix the System Rules page so the `Open` button really opens the Conditions screen
4. Change edit flows to load the **full record** before populating the form instead of using summary DataTable rows

## Practical Conclusion

The Policy Center is **good enough for partial UI testing**, but it is **not yet ready for full business-user UAT** because several important edit and child-rule workflows are still broken in the browser.
