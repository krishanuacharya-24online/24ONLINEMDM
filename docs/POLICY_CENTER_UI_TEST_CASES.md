# Policy Center UI Test Cases

## Purpose

This document is the full **UI test plan** for the Policy Center.

It is meant to help you test every major Policy Center feature from the web interface in a controlled way.

It covers:

- page access,
- navigation,
- create,
- edit,
- delete,
- form behavior,
- lookup behavior,
- source-specific mapping behavior,
- expected results,
- current UI limitations.

## Scope

These UI modules are in scope:

1. System Rules
2. System Rule Conditions
3. Reject Applications
4. Trust Score Policies
5. Trust Decision Policies
6. Remediation Rules
7. Rule Remediation Mappings

## Preconditions

Before you begin, confirm:

- the application is running on `http://localhost:8080`
- the database is up and migrations are applied
- you can log in to the Admin UI

Current local default login from this project:

- Username: `admin`
- Password: `admin`

Important:

- change this password if you are not in a disposable local test environment

## Recommended Test User

Use a signed-in user with access to Policy Center pages.

Current implementation note:

- Policy Center pages are currently available to any signed-in user
- tenant/user administration is more restricted than Policy Center itself

## Recommended Browser Setup

Use one of:

- Chrome
- Edge
- Firefox

For repeatable results:

- start with a fresh session
- disable cached autofill when possible
- use one browser window for the main pass

## Test Data Naming Convention

Use a unique suffix for all records created during testing.

Suggested suffix:

- `UIQA_<date>_<initials>`

Example values:

- `RULE_UIQA_20260306_BM`
- `TAG_UIQA_20260306_BM`
- `POLICY_UIQA_20260306_BM`
- `REMED_UIQA_20260306_BM`

This makes cleanup easy.

## High-Level Coverage Matrix

| Module | Load page | Create | Edit | Delete | Lookup controls | Dependency checks |
| --- | --- | --- | --- | --- | --- | --- |
| System Rules | Yes | Yes | Yes | Yes | Yes | Yes |
| System Rule Conditions | Yes | Yes | Yes | Yes | Yes | Yes |
| Reject Apps | Yes | Yes | Yes | Yes | Yes | Yes |
| Trust Score Policies | Yes | Yes | Yes | Yes | Yes | Yes |
| Trust Decision Policies | Yes | Yes | Yes | Yes | Yes | Yes |
| Remediation Rules | Yes | Yes | Yes | Yes | Yes | Yes |
| Rule Remediation Mappings | Yes | Yes | Yes | Yes | Yes | Yes |

## Test Sequence Recommendation

Run the cases in this order because some later tests depend on earlier records:

1. Login
2. Policy Center navigation
3. System Rule create/edit/delete
4. System Rule Condition create/edit/delete
5. Reject App create/edit/delete
6. Trust Score Policy create/edit/delete
7. Trust Decision Policy create/edit/delete
8. Remediation Rule create/edit/delete
9. Rule Remediation Mapping create/edit/delete for all source types
10. Cleanup review

## 1. Login Test

### TC-LOGIN-001: Login to Admin UI

**Steps**

1. Open `http://localhost:8080/login`
2. Enter `admin`
3. Enter `admin`
4. Click `Login`

**Expected**

- Login succeeds
- Browser redirects to `/ui`
- Top navigation is visible
- `Policies` menu is available

## 2. Policy Center Navigation

### TC-NAV-001: Open System Rules page

**Steps**

1. Click `Policies`
2. Confirm the page shows `System information rules`

**Expected**

- URL is `/ui/policies/system-rules`
- Policy tabs are visible

### TC-NAV-002: Open all Policy Center tabs

**Steps**

1. Open `System rules`
2. Open `Reject apps`
3. Open `Trust policies`
4. Open `Decision policies`
5. Open `Remediations`
6. Open `Mappings`

**Expected**

- each page opens without redirect or error
- the page title matches the selected tab
- the active tab state changes correctly

## 3. System Rules

### UI fields currently visible

- Rule code
- Rule tag
- OS type
- Device type
- Status
- Severity
- Priority
- Version
- Match mode
- Compliance action
- Risk delta
- Description

### TC-SR-001: Create a System Rule

**Test data**

- `rule_code = RULE_UIQA_<suffix>`
- `rule_tag = TAG_UIQA_<suffix>`
- `os_type = ANDROID`
- `device_type = PHONE`
- `status = ACTIVE`
- `severity = 3`
- `priority = 100`
- `version = 1`
- `match_mode = ALL`
- `compliance_action = NOTIFY`
- `risk_score_delta = -5`
- `description = UI test system rule`

**Steps**

1. Open `System rules`
2. Fill all visible required fields
3. Click `Save`

**Expected**

- success toast/message appears
- new rule appears in the table
- new row has correct code, OS, device, status, and priority

### TC-SR-002: Edit a System Rule

**Steps**

1. Search or locate the created rule
2. Click `Edit`
3. Change:
   - `severity` from `3` to `4`
   - `risk_score_delta` from `-5` to `-15`
   - `description` to `UI test system rule updated`
4. Click `Save`

**Expected**

- success toast/message appears
- row remains visible
- updated values are retained after page refresh

### TC-SR-003: Reset the System Rule form

**Steps**

1. Click `Edit` on any rule
2. Confirm form is populated
3. Click `Reset`

**Expected**

- hidden edit state clears
- form returns to default new-record values

### TC-SR-004: Delete a System Rule

**Steps**

1. Locate the created test rule
2. Click `Delete`
3. Confirm the delete dialog

**Expected**

- success toast/message appears
- row disappears from table
- row should no longer appear in the list view

### TC-SR-005: Required-field validation

**Steps**

1. Clear `rule_code`
2. Try to save

**Expected**

- browser blocks submission or UI reports the field is required

### System Rule observations to note

- current UI does not expose all backend model fields such as `os_name`, `os_version`, `time_zone`, `kernel_version`, and some deeper targeting values
- `effective_from` is set automatically in the current page script

## 4. System Rule Conditions

### Important dependency

You need a valid system rule ID before testing this page.

### How to open the page

Use either:

- the `Open conditions` mini-form on the System Rules page
- direct URL: `/ui/policies/system-rules/{ruleId}/conditions`

### UI fields currently visible

- Condition group
- Field name
- Operator
- Status
- Value text
- Value numeric
- Value boolean

### TC-SRC-001: Open Conditions page from System Rules page

**Steps**

1. Open `System rules`
2. In `System rule ID`, enter the ID of the test rule
3. Submit the form

**Expected**

- browser navigates to `/ui/policies/system-rules/{id}/conditions`
- heading shows the selected rule ID

### TC-SRC-002: Create a boolean condition

**Test data**

- `condition_group = 1`
- `field_name = root_detected`
- `operator = EQ`
- `value_boolean = true`
- `status = ACTIVE`

**Steps**

1. Open the Conditions page for your test rule
2. Fill the fields above
3. Click `Save`

**Expected**

- condition appears in the table
- table shows correct group, field, operator, boolean value, and status

### TC-SRC-003: Edit a condition

**Steps**

1. Click `Edit` on the created condition
2. Change:
   - `field_name = running_on_emulator`
   - keep `operator = EQ`
   - keep `value_boolean = true`
3. Click `Save`

**Expected**

- updated field name appears in the row

### TC-SRC-004: Create a numeric condition

**Test data**

- `condition_group = 2`
- `field_name = api_level`
- `operator = LT`
- `value_numeric = 30`

**Expected**

- condition is saved and visible in the table

### TC-SRC-005: Create a text condition

**Test data**

- `condition_group = 3`
- `field_name = os_version`
- `operator = EQ`
- `value_text = 14.0`

**Expected**

- condition is saved and visible in the table

### TC-SRC-006: Delete a condition

**Steps**

1. Delete one of the created conditions
2. Confirm the dialog

**Expected**

- condition disappears from the list

### Condition page limitations to note

- current UI does not directly expose `value_json`
- current UI supports text, numeric, and boolean entry only

## 5. Reject Applications

### UI fields currently visible

- Policy tag
- Threat type
- Severity
- Blocked reason
- App OS type
- App category
- App name
- Publisher
- Package ID
- Latest version
- Min allowed version
- Status
- Effective from
- Effective to

### TC-RA-001: Create a Reject App policy

**Test data**

- `policy_tag = REJECT_UIQA_<suffix>`
- `threat_type = VPN`
- `severity = 4`
- `blocked_reason = Test blocked app`
- `app_os_type = ANDROID`
- `app_category = VPN_PROXY`
- `app_name = Test VPN App UIQA`
- `publisher = Test Publisher`
- `package_id = com.uiqa.testvpn`
- `app_latest_version = 10.0.0`
- `min_allowed_version = 9.0.0`
- `status = ACTIVE`

**Steps**

1. Open `Reject apps`
2. Fill the form
3. Click `Save`

**Expected**

- row appears in table
- row shows OS, app, package, severity, and status

### TC-RA-002: Edit a Reject App policy

**Steps**

1. Click `Edit`
2. Change:
   - `severity = 5`
   - `blocked_reason = Updated test blocked app`
   - `min_allowed_version = 9.5.0`
3. Save

**Expected**

- updated values persist after refresh

### TC-RA-003: Delete a Reject App policy

**Expected**

- row disappears from list after delete

### TC-RA-004: OS lookup restriction

**Steps**

1. Open the `App OS type` dropdown

**Expected**

- only supported app OS values are shown in the current UI

## 6. Trust Score Policies

### UI fields currently visible

- Policy code
- Source type
- Signal key
- Severity
- Compliance action
- Score delta
- Weight
- Status
- Effective from
- Effective to

### Supported source types in the current UI

- `SYSTEM_RULE`
- `REJECT_APPLICATION`
- `POSTURE_SIGNAL`
- `MANUAL`

### TC-TSP-001: Create a Trust Score Policy for a system rule

**Test data**

- `policy_code = SCORE_UIQA_<suffix>`
- `source_type = SYSTEM_RULE`
- `signal_key = TAG_UIQA_<suffix>` or the system rule code/tag you created
- `severity = 4`
- `compliance_action = NOTIFY`
- `score_delta = -20`
- `weight = 1.0`
- `status = ACTIVE`

**Steps**

1. Open `Trust policies`
2. Fill the form
3. Click `Save`

**Expected**

- new scoring rule appears in table

### TC-TSP-002: Edit a Trust Score Policy

**Steps**

1. Click `Edit`
2. Change:
   - `score_delta = -25`
   - `weight = 1.5`
3. Save

**Expected**

- updated values persist

### TC-TSP-003: Create a lifecycle trust policy

**Test data**

- `policy_code = SCORE_LIFE_UIQA_<suffix>`
- `source_type = POSTURE_SIGNAL`
- `signal_key = OS_EOL`
- `score_delta = -30`
- `weight = 1.0`

**Expected**

- lifecycle signal policy saves successfully

### TC-TSP-004: Delete a Trust Score Policy

**Expected**

- deleted row no longer appears in list view

## 7. Trust Decision Policies

### UI fields currently visible

- Policy name
- Score min
- Score max
- Decision action
- Remediation required
- Response message
- Status
- Effective from
- Effective to

### TC-TDP-001: Create a Decision Policy

**Test data**

- `policy_name = DECISION_UIQA_<suffix>`
- `score_min = 60`
- `score_max = 79`
- `decision_action = NOTIFY`
- `remediation_required = true`
- `response_message = Device needs review`
- `status = ACTIVE`

**Steps**

1. Open `Decision policies`
2. Fill the form
3. Save

**Expected**

- row appears in table
- action is shown correctly

### TC-TDP-002: Edit a Decision Policy

**Steps**

1. Edit the created row
2. Change:
   - `score_min = 55`
   - `score_max = 75`
   - `response_message = Updated policy message`
3. Save

**Expected**

- updated values persist

### TC-TDP-003: Checkbox behavior

**Steps**

1. Edit the row again
2. Toggle `Remediation required`
3. Save

**Expected**

- checkbox state is saved correctly

### TC-TDP-004: Delete a Decision Policy

**Expected**

- row disappears from list

### Decision policy caution

- current UI allows overlapping score ranges
- current backend also allows overlapping ranges
- if you test overlaps, record the behavior as a governance finding

## 8. Remediation Rules

### UI fields currently visible

- Code
- Title
- Description
- Type
- OS type
- Device type
- Priority
- Status
- Effective from
- Effective to

### TC-RR-001: Create a Remediation Rule

**Test data**

- `remediation_code = REMED_UIQA_<suffix>`
- `title = Remove unsafe app`
- `description = Uninstall the blocked application and rerun posture check`
- `remediation_type = APP_REMOVAL`
- `os_type = ANDROID`
- `device_type = PHONE`
- `priority = 10`
- `status = ACTIVE`

**Steps**

1. Open `Remediations`
2. Fill the form
3. Save

**Expected**

- new remediation row appears

### TC-RR-002: Edit a Remediation Rule

**Steps**

1. Edit the created remediation
2. Change:
   - `title = Remove unsafe app immediately`
   - `priority = 5`
3. Save

**Expected**

- updated values persist

### TC-RR-003: Delete a Remediation Rule

**Expected**

- row disappears from list

### Remediation rule limitation

- current UI does not expose `instruction_json`

## 9. Rule Remediation Mappings

This page should be tested with all source-type variations.

### UI fields currently visible

- Source type
- System rule ID
- Reject app ID
- Trust policy ID
- Decision action
- Remediation rule ID
- Enforce mode
- Rank order
- Status
- Effective from
- Effective to

### Supported source types in current UI

- `SYSTEM_RULE`
- `REJECT_APPLICATION`
- `TRUST_POLICY`
- `DECISION`

### TC-RM-001: Create a SYSTEM_RULE mapping

**Dependencies**

- one System Rule
- one Remediation Rule

**Test data**

- `source_type = SYSTEM_RULE`
- `system_information_rule_id = <system rule id>`
- `remediation_rule_id = <remediation rule id>`
- `enforce_mode = ADVISORY`
- `rank_order = 1`
- `status = ACTIVE`

**Expected**

- mapping saves successfully
- row appears in list

### TC-RM-002: Create a REJECT_APPLICATION mapping

**Dependencies**

- one Reject App
- one Remediation Rule

**Test data**

- `source_type = REJECT_APPLICATION`
- `reject_application_list_id = <reject app id>`
- `remediation_rule_id = <remediation rule id>`
- `enforce_mode = MANUAL`

**Expected**

- mapping saves successfully

### TC-RM-003: Create a TRUST_POLICY mapping

**Dependencies**

- one Trust Score Policy
- one Remediation Rule

**Test data**

- `source_type = TRUST_POLICY`
- `trust_score_policy_id = <trust score policy id>`
- `remediation_rule_id = <remediation rule id>`
- `enforce_mode = ADVISORY`

**Expected**

- mapping saves successfully

### TC-RM-004: Create a DECISION mapping

**Dependencies**

- one Remediation Rule

**Test data**

- `source_type = DECISION`
- `decision_action = BLOCK`
- `remediation_rule_id = <remediation rule id>`
- `enforce_mode = AUTO`

**Expected**

- mapping saves successfully

### TC-RM-005: Edit a mapping

**Steps**

1. Edit one of the created mappings
2. Change:
   - `enforce_mode`
   - `rank_order`
3. Save

**Expected**

- updated values persist

### TC-RM-006: Delete mappings

**Expected**

- deleted mappings no longer appear in list

### Mapping page behavior to observe

- only the relevant source reference should be meaningful for the selected source type
- current UI does not automatically hide unrelated ID fields

## 10. Cross-Feature Validation Cases

### TC-X-001: Lookup values populate correctly

Check these dropdowns:

- OS type
- Device type
- Match mode
- Compliance action
- Rule condition operator
- Trust policy source type
- Remediation type
- Mapping source type
- Enforce mode

**Expected**

- each dropdown loads values
- no empty broken selector state

### TC-X-002: Page refresh persistence

For each module:

1. Create a record
2. Refresh the page
3. Search or locate the record

**Expected**

- saved values remain after refresh

### TC-X-003: Soft-delete visibility

For each module:

1. Delete a record
2. Refresh the page

**Expected**

- deleted record should not be visible in the main list page

## 11. Recommended Cleanup

After the test run:

1. Search all created records using your test suffix
2. Delete them from the UI
3. Refresh and verify they no longer appear in list views

## 12. Current UI Gaps to Record During Testing

While testing, note these current product behaviors:

- System Rule page exposes only a subset of backend rule fields
- Condition page does not expose `value_json`
- Remediation Rule page does not expose `instruction_json`
- Mapping page does not dynamically hide unused source-reference fields
- Policy management is currently available to any signed-in user, not only product admins
- overlapping score policies and decision ranges are possible in the current implementation

## 13. Test Completion Checklist

Mark the Policy Center UI test complete only when all are true:

- login succeeded
- all Policy Center pages loaded
- each page created at least one valid record
- each created record was edited successfully
- each created record was deleted successfully
- lookup-driven dropdowns loaded correctly
- mapping scenarios were tested for all four source types
- no unexpected redirects, JavaScript errors, or broken forms occurred

## Short Summary

If you follow this document from top to bottom, you will test every major Policy Center feature exposed in the current UI:

- page access,
- navigation,
- CRUD behavior,
- lookup behavior,
- source-type behavior,
- and the key current limitations of the UI.
