# Trading Assistant API Reference

Version `v1` of the application API. The backend is the system of record and the
only component that calculates market, strategy, qualification and risk data.
The Android client renders these responses and never recomputes them.

- Base URL (local): `http://localhost:8080`
- Base URL (deployed example): `https://trading-assistant-api-x856.onrender.com`
- Interactive specification: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- Health: `/actuator/health`

## Response envelope

Every JSON endpoint returns one of two envelopes.

Success:

```json
{
  "success": true,
  "data": { },
  "meta": {
    "timestamp": "2026-11-18T09:30:00Z",
    "requestId": "1f5c8f1e"
  }
}
```

Error:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed.",
    "details": [
      { "field": "email", "message": "must be a well-formed email address" }
    ]
  },
  "meta": {
    "timestamp": "2026-11-18T09:30:00Z",
    "requestId": "1f5c8f1e"
  }
}
```

Errors never include a stack trace, exception class or internal configuration.
`X-Request-Id` may be supplied by the caller and is echoed in the response.

## Authentication

- Access token: signed JWT, HMAC-SHA256, 15 minutes by default.
- Refresh token: opaque random value, 30 days by default. Only its SHA-256 hash
  is stored. It is rotated on every refresh and revoked on logout.
- Send the access token on every protected request:

```http
Authorization: Bearer <access-token>
```

- The client refreshes once on `401` with the current refresh token. A replayed
  refresh token is rejected because the previous token is consumed atomically.
- Roles: `USER` and `ADMIN`. Administrative routes require `ADMIN`.

### Public routes

`/api/v1/auth/**`, `/api/v1/system/**`, `/actuator/health/**`,
`/actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`,
`/error`.

Every other route requires a valid access token.

## Pagination

Collection endpoints use zero-based pages.

Query parameters:

| Parameter | Default | Meaning |
|---|---:|---|
| `page` | `0` | Zero-based page number |
| `size` | `20` | Page size; capped by the service, 100 maximum |

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

## Error codes

| Code | HTTP | Meaning |
|---|---:|---|
| `VALIDATION_FAILED` | 400 | Request body or parameter validation failed |
| `MALFORMED_REQUEST` | 400 | Body could not be parsed |
| `UNAUTHORIZED` | 401 | Missing, invalid or expired credentials |
| `FORBIDDEN` | 403 | Authenticated account lacks the role or ownership |
| `RESOURCE_NOT_FOUND` | 404 | Unknown id or route after authentication |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method not supported |
| `CONFLICT` | 409 | Duplicate email, watchlist name or trade key |
| `EXECUTION_REJECTED` | 422 | Order intent rejected by an execution rule |
| `ENGINE_UNAVAILABLE` | 503 | Broker/execution engine is not enabled |
| `DATABASE_UNAVAILABLE` | 503 | MongoDB could not be reached |
| `BROKER_*` | 429/502/503/500 | Safe broker failure category |
| `INTERNAL_ERROR` | 500 | Unexpected failure |

---

## Authentication endpoints

### `POST /api/v1/auth/register`

Creates an account and returns a full token set.

```json
{
  "email": "trader@example.com",
  "displayName": "Trader",
  "password": "at-least-8-characters"
}
```

`201 Created` with the auth response below. Duplicate email returns `409`.

### `POST /api/v1/auth/login`

```json
{
  "email": "trader@example.com",
  "password": "at-least-8-characters"
}
```

`200 OK`:

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresInSeconds": 900,
    "accessExpiresAt": "2026-11-18T09:45:00Z",
    "refreshToken": "opaque-random-value",
    "refreshExpiresAt": "2026-12-18T09:30:00Z",
    "user": {
      "id": "66f0c0f0c0f0c0f0c0f0c0f0",
      "email": "trader@example.com",
      "displayName": "Trader",
      "roles": ["USER"],
      "status": "ACTIVE",
      "createdAt": "2026-11-01T10:00:00Z",
      "lastLoginAt": "2026-11-18T09:30:00Z"
    }
  }
}
```

### `POST /api/v1/auth/refresh`

```json
{ "refreshToken": "opaque-random-value" }
```

Returns the same auth response with a new access token and a new refresh token.
The submitted refresh token is consumed even if the caller does not store the
replacement; clients must persist the returned value.

### `POST /api/v1/auth/logout`

```json
{ "refreshToken": "opaque-random-value" }
```

Revokes the refresh token. The access token remains valid until it expires.
Response data: `{ "revoked": true, "message": "Signed out." }`.

---

## Profile and users

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/users/me` | USER | Current profile |
| `PUT` | `/api/v1/users/me` | USER | Update `displayName` |
| `PUT` | `/api/v1/users/me/password` | USER | Change password and revoke all sessions |
| `GET` | `/api/v1/users/me/sessions` | USER | Count active refresh tokens |
| `PUT` | `/api/v1/users/me/logout-all` | USER | Revoke every refresh token |
| `GET` | `/api/v1/users` | ADMIN | Paged account list |
| `GET` | `/api/v1/users/{id}` | ADMIN | One account |
| `PUT` | `/api/v1/users/{id}/roles` | ADMIN | Replace roles |
| `PUT` | `/api/v1/users/{id}/status` | ADMIN | Set `ACTIVE` or `DISABLED` |

Update profile:

```json
{ "displayName": "New name" }
```

Change password:

```json
{
  "currentPassword": "old-password",
  "newPassword": "new-password-at-least-8"
}
```

Role update:

```json
{ "roles": ["USER", "ADMIN"] }
```

Status update:

```json
{ "status": "ACTIVE" }
```

`GET /api/v1/users/me` data:

```json
{
  "id": "66f0c0f0c0f0c0f0c0f0c0f0",
  "email": "trader@example.com",
  "displayName": "Trader",
  "roles": ["USER"],
  "status": "ACTIVE",
  "createdAt": "2026-11-01T10:00:00Z",
  "lastLoginAt": "2026-11-18T09:30:00Z"
}
```

---

## Watchlists

All routes are account-scoped. A watchlist owned by another account behaves as
not found.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/watchlists` | List all own watchlists |
| `GET` | `/api/v1/watchlists/{id}` | One watchlist |
| `POST` | `/api/v1/watchlists` | Create `{ "name": "Favourites" }` |
| `PUT` | `/api/v1/watchlists/{id}` | Rename `{ "name": "Long term" }` |
| `DELETE` | `/api/v1/watchlists/{id}` | Delete the list |
| `POST` | `/api/v1/watchlists/{id}/items` | Add an instrument |
| `DELETE` | `/api/v1/watchlists/{id}/items/{itemId}` | Remove an instrument |

Add item:

```json
{
  "symbolToken": "2885",
  "symbol": "SBIN",
  "segment": "NSE_CASH"
}
```

The service normalises the name for uniqueness and enforces the maximum item
count from application settings (100 by default).

Watchlist data:

```json
{
  "id": "66f0c0f0c0f0c0f0c0f0c0f1",
  "name": "Favourites",
  "items": [
    {
      "id": "66f0c0f0c0f0c0f0c0f0c0f2",
      "symbolToken": "2885",
      "symbol": "SBIN",
      "segment": "NSE_CASH",
      "addedAt": "2026-11-18T09:00:00Z"
    }
  ],
  "createdAt": "2026-11-01T10:00:00Z",
  "updatedAt": "2026-11-18T09:00:00Z"
}
```

Segments: `NSE_CASH`, `NSE_FO`, `BSE_CASH`, `BSE_FO`, `MCX_FO`, `NSE_COM`,
`CDS`, `NCDEX`.

---

## Orders

Orders are placed against the running engine's risk assessment. The request
never contains a quantity: the engine's assessed size and stop are used.

### `POST /api/v1/orders`

```json
{
  "strategyId": "EMA_TREND",
  "segment": "NSE_CASH",
  "symbolToken": "2885",
  "side": "BUY",
  "type": "MARKET",
  "limitPrice": null,
  "triggerPrice": null,
  "targetPrice": null,
  "timeInForce": "DAY"
}
```

- `side`: `BUY` or `SELL`.
- `type`: `MARKET`, `LIMIT`, `STOP`, `STOP_LIMIT` (engine-supported values).
- `timeInForce`: `DAY`, `IOC` or `GTC`; defaults to `DAY`.
- Price fields are optional, positive when present.
- `201 Created` on acceptance. `422` when an execution rule rejects the intent.
- `503 ENGINE_UNAVAILABLE` when the broker/engine is not enabled.
- If the client omits a required risk assessment, the engine rejects the order;
  it does not invent a quantity.

Order data:

```json
{
  "id": "66f0c0f0c0f0c0f0c0f0c0a1",
  "engineOrderId": "ENG-0001",
  "strategyId": "EMA_TREND",
  "segment": "NSE_CASH",
  "symbolToken": "2885",
  "side": "BUY",
  "type": "MARKET",
  "quantity": 100,
  "filledQuantity": 100,
  "limitPrice": null,
  "triggerPrice": null,
  "stopPrice": 780.50,
  "targetPrice": 830.00,
  "timeInForce": "DAY",
  "status": "FILLED",
  "averageFillPrice": 800.25,
  "executionStatus": "COMPLETED",
  "reason": null,
  "positionId": "POS-0001",
  "createdAt": "2026-11-18T09:30:00Z",
  "updatedAt": "2026-11-18T09:30:01Z"
}
```

Statuses: `CREATED`, `VALIDATED`, `SUBMITTED`, `PENDING`, `PARTIALLY_FILLED`,
`FILLED`, `CANCELLED`, `REJECTED`, `FAILED`, `EXPIRED`.

### Other order routes

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/orders?page=0&size=20` | Paged own orders, newest first |
| `GET` | `/api/v1/orders/{id}` | One own order |
| `POST` | `/api/v1/orders/{id}/cancel` | Ask the engine to cancel |

A terminal order cannot be cancelled. The returned record reflects the engine's
response; the Android client does not assume a cancellation succeeded.

---

## Portfolio and positions

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/portfolio` | Normalised portfolio |
| `GET` | `/api/v1/portfolio/summary` | Alias of the same view |
| `GET` | `/api/v1/portfolio/positions` | Open positions |
| `GET` | `/api/v1/positions` | Open positions |

The portfolio states its source:

- `LIVE`: the engine is running and returned its current snapshot.
- `SNAPSHOT`: the latest persisted scheduled snapshot is being served.
- `UNAVAILABLE`: no engine and no stored snapshot; monetary values are zero.

```json
{
  "source": "LIVE",
  "capturedAt": null,
  "capital": 100000,
  "availableCapital": 90000,
  "blockedMargin": 0,
  "usedMargin": 10000,
  "realisedPnl": 500,
  "unrealisedPnl": 250,
  "exposure": 80000,
  "openPositions": 1,
  "closedPositions": 0,
  "positions": [
    {
      "positionId": "POS-0001",
      "strategyId": "EMA_TREND",
      "segment": "NSE_CASH",
      "symbolToken": "2885",
      "direction": "LONG",
      "quantity": 100,
      "closedQuantity": 0,
      "remainingQuantity": 100,
      "averageEntryPrice": 800.25,
      "stopPrice": 780.50,
      "targetPrice": 830.00,
      "lastPrice": 802.75,
      "realisedPnl": 0,
      "status": "OPEN",
      "openedAt": "2026-11-18T09:30:01Z",
      "updatedAt": "2026-11-18T10:00:00Z",
      "closedAt": null
    }
  ]
}
```

---

## History

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/history/orders` | USER | Paged own order history |
| `GET` | `/api/v1/history/trades` | USER | Paged own closed trades |
| `GET` | `/api/v1/history/portfolio` | USER | Paged portfolio snapshots |
| `GET` | `/api/v1/admin/orders` | ADMIN | All orders |
| `GET` | `/api/v1/admin/history/trades` | ADMIN | All trades |
| `GET` | `/api/v1/admin/history/portfolio` | ADMIN | All portfolio snapshots |

Trade data includes `tradeKey`, `orderId`, `positionId`, strategy, direction,
filled quantity, average entry, closed quantity, realised P&L, and open/close
timestamps. History is durable in MongoDB; engine portfolio state itself is not
restored from it.

---

## Analysis read model

These endpoints project the latest engine assessments. They are read-only and
never trigger analysis. If the engine has not produced an assessment, the lists
are empty; `engineAvailable` states whether the execution engine is registered.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/market/overview?limit=25` | All five latest collections |
| `GET` | `/api/v1/market/assessments?limit=25` | Market intelligence |
| `GET` | `/api/v1/options/assessments?limit=25` | Option chain analytics |
| `GET` | `/api/v1/strategy/evaluations?limit=25` | Strategy evaluations |
| `GET` | `/api/v1/strategy/definitions` | Registered strategy metadata |
| `GET` | `/api/v1/risk/assessments?limit=25` | Risk assessments |
| `GET` | `/api/v1/risk/qualifications?limit=25` | Trade qualifications |

`limit` defaults to 25 and is clamped to 100 by the API. The in-memory store
keeps up to 128 entries per assessment type. The overview response:

```json
{
  "generatedAt": "2026-11-18T09:30:00Z",
  "engineAvailable": true,
  "readModelEntries": 12,
  "markets": [],
  "options": [],
  "strategies": [],
  "risks": [],
  "qualifications": []
}
```

Each item is a direct projection of an immutable engine assessment. No API layer
recalculates trend, Greeks, sizing, liquidity, qualification or risk.

---

## Settings

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/settings/preferences` | USER | Read own preferences |
| `PUT` | `/api/v1/settings/preferences` | USER | Replace own preferences |
| `GET` | `/api/v1/settings/application` | USER | Read application settings |
| `PUT` | `/api/v1/settings/application` | ADMIN | Update application settings |

Preferences:

```json
{
  "theme": "DARK",
  "defaultInterval": "FIFTEEN_MINUTE",
  "defaultStrategyId": "EMA_TREND",
  "notificationsEnabled": true,
  "orderNotifications": true,
  "positionNotifications": true,
  "loginAlerts": true,
  "systemMessages": true
}
```

Application settings update:

```json
{
  "maintenanceMode": false,
  "maximumWatchlistItems": 100,
  "historyRetentionDays": 365
}
```

`maximumWatchlistItems` is 1–100. `historyRetentionDays` is 30–3650.

---

## Notifications

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/notifications?page=0&size=20` | Paged own notifications |
| `GET` | `/api/v1/notifications/unread` | `{ "unread": 3 }` |
| `PUT` | `/api/v1/notifications/{id}/read` | Mark one notification read |
| `PUT` | `/api/v1/notifications/read-all` | Mark all read; returns unread count |

Notifications are records persisted by the backend. Android creates local
notification channels but the backend is the source of the notification feed;
there is no push transport in Phase 12.

---

## System and operations

### `GET /api/v1/system/status` (public)

```json
{
  "timestamp": "2026-11-18T09:30:00Z",
  "status": "UP",
  "database": "UP",
  "engine": "AVAILABLE",
  "version": "0.1.0",
  "readModelEntries": 12,
  "uptimeSeconds": 3600
}
```

`status` is `DEGRADED` when MongoDB cannot be pinged. `engine` is `DISABLED`
when broker credentials are absent. No credentials or connection details are
returned.

### Operational endpoints

| Path | Exposure | Purpose |
|---|---|---|
| `/actuator/health` | public | Liveness/readiness aggregate |
| `/actuator/info` | public | Build and application info |
| `/actuator/metrics` | authenticated | Metrics |
| `/actuator/prometheus` | prod, authenticated | Prometheus scrape |

The base profile does not enable the MongoDB health indicator so the API can
start without a database during development. Production enables it.

---

## Example session

```bash
BASE=http://localhost:8080

curl -s -X POST "$BASE/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"trader@example.com","displayName":"Trader","password":"strong-password"}'

TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"trader@example.com","password":"strong-password"}' \
  | python -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])')

curl -s "$BASE/api/v1/portfolio" -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/api/v1/market/overview" -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/api/v1/orders?page=0&size=20" -H "Authorization: Bearer $TOKEN"
```

## Contract rules

- JSON field names are camelCase.
- Timestamps are ISO-8601 UTC instants.
- Money and quantities are JSON numbers serialised from `BigDecimal`; clients
  must not assume binary floating-point precision.
- All account resources are scoped by the authenticated user id taken from the
  JWT subject. A client-supplied id is never trusted for ownership.
- Lists are ordered newest first unless the endpoint documents otherwise.
