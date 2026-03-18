# Policy Ownership Model (P0 Execution)

Effective date: 2026-03-16

## Decision

Policy data supports two scopes:

- **Global policy**: `tenant_id IS NULL`
- **Tenant policy**: `tenant_id = <tenant_code>`

Role behavior:

- `PRODUCT_ADMIN`
  - Reads global by default.
  - Can read global + a specific tenant scope by sending `X-Tenant-Id`.
  - Creates global policy when `X-Tenant-Id` is absent.
  - Creates tenant-scoped policy when `X-Tenant-Id` is present.
  - Can update/delete global in default scope, and tenant-scoped records within the selected tenant scope.
- `TENANT_ADMIN`
  - Reads global + their own tenant policies.
  - Creates tenant-scoped policies for their own tenant only.
  - Can update/delete only their own tenant-scoped policies.
  - Cannot modify global policies.

## Scope

This applies to:

- system rules and conditions
- reject application policies
- trust score policies
- trust decision policies
- remediation rules
- rule-remediation mappings

## API Scope Selection

Policy endpoints under `/v1/policies/**` now accept optional `X-Tenant-Id`:

- For `PRODUCT_ADMIN`, this header chooses tenant scope (global + selected tenant).
- For `TENANT_ADMIN`, tenant scope is derived from authenticated principal; mismatched header is rejected.

## Runtime Evaluation Behavior

Evaluation/remediation policy loading is tenant-aware:

- Tenant-scoped evaluations can use both global and same-tenant policy records.
- Product/global context uses global policy records only.
- Decision policy lookup prefers tenant-scoped decision policy over global when both match.
