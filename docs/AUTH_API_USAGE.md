# Auth API Usage Guide

This guide explains how web or API clients should use authentication APIs under `/auth/*` with cookie-based JWT and CSRF protection.

## 1) Base URL and Protocol

- Local default base URL: `http://localhost:8080`
- All bodies are JSON.
- Global JSON naming is snake_case, but map-based responses can still contain camelCase keys (for example `tenantId` in `/auth/me`).

## 2) Authentication Model

- Login issues 2 cookies:
  - `ACCESS_TOKEN`: short-lived JWT used for authenticated APIs.
  - `REFRESH_TOKEN`: long-lived JWT used only for refresh/logout token rotation.
- Cookie defaults in current implementation:
  - `httpOnly=true` for both auth cookies
  - `secure=false`
  - `path=/`
  - `sameSite=Lax` for `ACCESS_TOKEN`
  - `sameSite=Strict` for `REFRESH_TOKEN`
- Default TTL from `application.yaml`:
  - access token: `900` seconds (15 minutes)
  - refresh token: `604800` seconds (7 days)

## 3) CSRF Requirements

The API uses cookie CSRF tokens for protected POST endpoints.

- CSRF cookie name: `XSRF-TOKEN`
- CSRF header name: `X-XSRF-TOKEN`
- Endpoints requiring CSRF token:
  - `POST /auth/logout`
  - `POST /auth/change-password`
- Missing/invalid CSRF token on protected endpoints returns `403 Forbidden`.

Recommended pattern:

1. Call `GET /auth/csrf` before calling CSRF-protected POST endpoints.
2. Send `X-XSRF-TOKEN` header with the token value from response/cookie.
3. Include auth cookies and CSRF cookie in the same request context.

## 4) Endpoint Contracts

## 4.1 POST `/auth/login`

Purpose:

- Authenticate user credentials.
- Set `ACCESS_TOKEN` and `REFRESH_TOKEN` cookies.

Request body:

```json
{
  "username": "admin",
  "password": "YourStrongPassword!"
}
```

Responses:

- `200 OK` with body:

```json
{
  "username": "admin",
  "role": "PRODUCT_ADMIN",
  "tenant_id": 1
}
```

- `400 Bad Request`: missing/blank `username` or `password`
- `401 Unauthorized`: user not found, inactive user, or bad password

## 4.2 POST `/auth/refresh`

Purpose:

- Rotate refresh token and issue fresh `ACCESS_TOKEN` + `REFRESH_TOKEN` cookies.

Request:

- No request body.
- Requires valid `REFRESH_TOKEN` cookie.

Responses:

- `200 OK` with empty body
- `401 Unauthorized`: missing/invalid/expired/revoked refresh token, missing refresh token record, or inactive/deleted user

## 4.3 POST `/auth/logout`

Purpose:

- Revoke current refresh token (if present/valid) and clear auth cookies.

Request:

- No request body.
- Requires CSRF token (`X-XSRF-TOKEN`) because this endpoint is CSRF-protected.

Response:

- `204 No Content`
- `403 Forbidden`: missing/invalid CSRF token

Behavior:

- Clears both `ACCESS_TOKEN` and `REFRESH_TOKEN` cookies even if refresh cookie/token is absent or invalid.

## 4.4 GET `/auth/me`

Purpose:

- Return current authentication context derived from `ACCESS_TOKEN`.

Responses:

- `200 OK` authenticated shape:

```json
{
  "authenticated": true,
  "username": "admin",
  "role": "PRODUCT_ADMIN",
  "tenantId": 1
}
```

- `401 Unauthorized`: missing/invalid `ACCESS_TOKEN` cookie

Note:

- `tenantId` key is camelCase in this endpoint response.

## 4.5 GET `/auth/csrf`

Purpose:

- Generate/store a CSRF token and return it for subsequent protected POST calls.

Response:

```json
{
  "token": "<csrf_token>"
}
```

## 4.6 POST `/auth/change-password`

Purpose:

- Change password for the authenticated user.
- Revoke all refresh tokens for that user and clear auth cookies.

Request requirements:

- Authenticated user (`ACCESS_TOKEN` cookie).
- CSRF token (`X-XSRF-TOKEN` header).

Request body:

```json
{
  "current_password": "OldPassword123!",
  "new_password": "NewStrongPassword123!",
  "confirm_password": "NewStrongPassword123!"
}
```

Validation rules:

- all fields are required and non-blank
- `new_password` must equal `confirm_password`
- password strength for `new_password`:
  - minimum 12 characters
  - at least one uppercase letter
  - at least one lowercase letter
  - at least one digit
  - at least one special character

Responses:

- `200 OK`

```json
{
  "message": "Password updated successfully"
}
```

- `400 Bad Request` with `message` for:
  - missing fields
  - password mismatch
  - weak new password
  - incorrect current password
- `401 Unauthorized` with `message` for:
  - missing/invalid authentication context
  - inactive/deleted user
- `403 Forbidden`: missing/invalid CSRF token

## 5) Recommended Client Flow

1. Call `GET /auth/csrf` to initialize CSRF token cookie.
2. Call `POST /auth/login` with credentials.
3. Call authenticated APIs; browser/client automatically sends `ACCESS_TOKEN`.
4. On `401` from protected APIs, call `POST /auth/refresh` once, then retry original request.
5. For `POST /auth/logout` and `POST /auth/change-password`, include `X-XSRF-TOKEN` header.
6. After password change, require user to login again because all refresh tokens are revoked and auth cookies are cleared.
