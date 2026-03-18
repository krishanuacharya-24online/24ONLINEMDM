# Superset Reporting Setup

This project includes an open-source reporting stack based on Apache Superset.

## 1. Start Superset Stack

Run:

```bash
docker compose -f docker-compose.reports.yml up -d
```

Services started:

- `superset` (UI/API on `http://localhost:8088`)
- `superset-db` (metadata PostgreSQL)
- `superset-redis` (cache/broker)
- `superset-worker` (async tasks)
- `superset-beat` (scheduler)

## 2. Login to Superset

Default admin credentials (change immediately):

- Username: `admin`
- Password: `admin`

## 3. Configure Dashboard for MDM UI Embed

Create or choose a Superset dashboard and copy the path.

Example:

- `/superset/dashboard/3/?standalone=1`

Set application env/config:

```yaml
reports:
  superset:
    enabled: true
    base-url: http://localhost:8088
    dashboard-path: /superset/dashboard/3/?standalone=1
```

Then open `http://localhost:8080/ui/reports`.

## 4. Optional: Guest Token Embed

Enable guest token mode in app config:

```yaml
reports:
  superset:
    guest-token-enabled: true
    resource-type: dashboard
    resource-id: <embedded-dashboard-id>
    username: <superset-service-user>
    password: <superset-service-password>
    auth-provider: db
    tenant-rls-clause-template: "tenant_id = '{{tenantId}}'"
```

Notes:

- `resource-id` should match your Superset embedded resource id.
- Placeholders supported in `tenant-rls-clause-template`:
  - `{{tenantId}}` -> resolved tenant code (string)
  - `{{tenantPk}}` -> tenant master id from authenticated principal (number)

## 5. Production Hardening

- Rotate `SUPERSET_SECRET_KEY` and `SUPERSET_GUEST_TOKEN_JWT_SECRET`.
- Use HTTPS and private networking.
- Create a read-only reporting DB user/schema.
- Enforce tenant filters with Superset row-level security.
- Restrict external access to Superset admin endpoints.

