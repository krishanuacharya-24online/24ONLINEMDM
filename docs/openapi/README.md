# OpenAPI Docs

Primary spec file:
- [mdm-openapi.json](C:/Users/krishanu/Downloads/NewCodes/New_Project_Spring-boot/24onlinemdm/docs/openapi/mdm-openapi.json)

## Integration Guides
- [Agent App API Usage Guide](C:/Users/krishanu/Downloads/NewCodes/New_Project_Spring-boot/24onlinemdm/docs/AGENT_APP_API_USAGE.md)
- [Auth API Usage Guide](C:/Users/krishanu/Downloads/NewCodes/New_Project_Spring-boot/24onlinemdm/docs/AUTH_API_USAGE.md)

## What It Covers
- Agent payload ingestion + decision acknowledgment
- Device trust profile, snapshot, installed-app, and score-event APIs
- Evaluation run APIs (run, matches, remediation)
- Decision response APIs (list/get/send/resend)
- Remediation task APIs (list/get/patch/complete/skip)
- OS lifecycle master APIs (catalog + per-device latest lifecycle status)
- Policy APIs for:
  - system rules + conditions
  - reject applications
  - trust score policies
  - trust decision policies
  - remediation rules
  - rule-remediation mappings
- Application catalog and lookup APIs (single lookup master: `lkp_master` with `lookup_type`)
- Admin job APIs (catalog rebuild and trust-score recomputation)

## How To View
1. Open [Swagger Editor](https://editor.swagger.io/).
2. Import `docs/openapi/mdm-openapi.json`.
3. Use generated docs/mock clients from the editor/tooling.

## Notes
- `mdm-openapi.json` is the canonical specification.
- `mdm-openapi.yaml` is retained for compatibility/transition.
- Data model details map to `docs/MDM_SCHEMA_LOGIC_REFERENCE.md`.
- Auth endpoint operational details map to `docs/AUTH_API_USAGE.md`.
