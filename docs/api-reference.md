# Trading Assistant API Reference

Version `v1` of the application API. The backend is the system of record and the
only component that calculates market, options, strategy, qualification and risk
descriptions. The Android client renders these responses and never recomputes
them.

- Base URL (local): `http://localhost:8080`
- Interactive specification: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- Health: `/actuator/health`

## Single-owner security model

There is no registration, login, JWT, refresh token or role-based account flow.
A fixed local principal (`local-owner`) is installed for every request so
watchlists, preferences and notifications keep an owner boundary without
accounts. A request therefore cannot select another owner by changing a path,
body or header. Do not send an `Authorization` header; it is ignored.

The application is read-only with respect to markets and broker execution. It
exposes no order, portfolio, position, holding or funds endpoint. The only trade
write endpoints record a fill the owner already executed and close local
monitoring; neither contacts a broker. All trades are executed manually outside
the platform.

## Response envelope

Every JSON endpoint returns one of two envelopes.

Success:

```json
{
  "success": true,
  "data": { },
  "meta": {
    "timestamp": "2026-11-19T09:30:00Z",
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
      { "field": "name", "message": "must not be blank" }
    ]
  },
  "meta": {
    "timestamp": "2026-11-19T09:30:00Z",
    "requestId": "1f5c8f1e"
  }
}
```

Errors never include a stack trace, exception class or internal configuration.
`X-Request-Id` may be supplied by the caller and is echoed in the response.

## Limits and pagination

Analysis endpoints accept `limit`. The default is `25`; values are clamped to
`1..100`. They return the latest published descriptions, not history.

Notification and other paged endpoints use zero-based pages:

| Parameter | Default | Meaning |
|---|---:|---|
| `page` | `0` | Zero-based page number |
| `size` | `20` | Page size |

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
| `UNAUTHORIZED` | 401 | Missing credentials; not used by the local owner |
| `FORBIDDEN` | 403 | Role or ownership check failed |
| `RESOURCE_NOT_FOUND` | 404 | Unknown id or route |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method not supported |
| `CONFLICT` | 409 | Duplicate watchlist name |
| `ENGINE_UNAVAILABLE` | 503 | The broker-backed engine is not running |
| `DATABASE_UNAVAILABLE` | 503 | MongoDB could not be reached |
| `BROKER_*` | 429/502/503/500 | Safe broker failure category |
| `INTERNAL_ERROR` | 500 | Unexpected failure |

## Endpoint summary

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/system/info` | Build, runtime and configuration summary |
| GET | `/api/v1/system/status` | Database, engine and read-model status |
| GET | `/api/v1/market/overview` | Combined market, options, strategy, risk and qualification view |
| GET | `/api/v1/market/assessments` | Market intelligence assessments |
| GET | `/api/v1/market/freshness` | Tick/candle freshness and session state per observed instrument |
| GET | `/api/v1/options/assessments` | Options analytics assessments |
| GET | `/api/v1/instruments/search` | Search the instrument master by name or symbol |
| GET | `/api/v1/recommendations` | Current recommendations and optional reference plans |
| GET | `/api/v1/strategy/definitions` | Registered strategy definitions |
| GET | `/api/v1/strategy/evaluations` | Latest strategy evaluations |
| GET | `/api/v1/risk/assessments` | Latest risk assessments |
| GET | `/api/v1/risk/qualifications` | Latest trade qualifications |
| GET | `/api/v1/monitoring/active` | Active manual trades with latest monitoring result |
| GET | `/api/v1/monitoring/history` | Recent monitoring history, optionally by trade id |
| GET | `/api/v1/monitoring/{tradeId}` | One manual trade and its latest result |
| GET | `/api/v1/monitoring/alerts` | Recent monitoring alerts |
| GET | `/api/v1/monitoring/premium` | Live premium/P&L status for active option trades |
| GET | `/api/v1/monitoring/premium/{tradeId}` | Premium status for one trade |
| POST | `/api/v1/monitoring/trades` | Record a manually executed option fill |
| POST | `/api/v1/monitoring/trades/{tradeId}/close` | Close local monitoring only |
| GET | `/api/v1/watchlists` | List watchlists |
| GET | `/api/v1/watchlists/{id}` | Read one watchlist |
| POST | `/api/v1/watchlists` | Create a watchlist |
| PUT | `/api/v1/watchlists/{id}` | Rename a watchlist |
| DELETE | `/api/v1/watchlists/{id}` | Delete a watchlist |
| POST | `/api/v1/watchlists/{id}/items` | Add an instrument |
| DELETE | `/api/v1/watchlists/{id}/items/{itemId}` | Remove an instrument |
| GET | `/api/v1/settings/preferences` | Read client preferences |
| PUT | `/api/v1/settings/preferences` | Replace client preferences |
| GET | `/api/v1/settings/application` | Read application settings |
| PUT | `/api/v1/settings/application` | Update application settings; requires `ADMIN` |
| GET | `/api/v1/notifications` | Paged notifications |
| GET | `/api/v1/notifications/unread` | Unread count |
| PUT | `/api/v1/notifications/{id}/read` | Mark one notification read |
| PUT | `/api/v1/notifications/read-all` | Mark all notifications read |

The fixed local principal has role `USER`, so the administrative settings update
returns `403 FORBIDDEN` in the single-owner deployment.

## Actionable workflow endpoints

```http
GET /api/v1/instruments/search?q=reliance
GET /api/v1/instruments/search?q=NIFTY&segment=NSE_CASH&type=INDEX
GET /api/v1/recommendations
GET /api/v1/recommendations?instrument=NSE_CASH:2885&interval=FIVE_MINUTE
POST /api/v1/monitoring/trades
POST /api/v1/monitoring/trades/{tradeId}/close
GET /api/v1/monitoring/premium
GET /api/v1/monitoring/premium/{tradeId}
```

Search returns the stable `SEGMENT:TOKEN` needed by the recommendation and trade
endpoints. By default it returns cash equity, index and other non-derivative
rows; pass `type=OPTION` or `type=FUTURE` to search derivatives.

A recommendation response includes the decision, component evidence, selected
contract and an optional `plan`. The plan contains `suggestedEntry`,
`targetPremium`, `stopPremium`, `suggestedQuantity`, `maximumLoss`,
`potentialReward` and its ATR/delta diagnostics. It is a reference only; it is not
an order.

The manual trade request requires `instrument`, `recommendationType`,
`recommendationTimestamp`, `optionSymbol`, `strike`, `expiry`, `entryPrice` and
`quantity`:

```json
{
  "instrument": "NSE_CASH:2885",
  "recommendationType": "BUY_CALL",
  "interval": "FIVE_MINUTE",
  "recommendationTimestamp": "2026-11-18T03:45:00Z",
  "optionSymbol": "SBIN26NOV500CE",
  "strike": 500,
  "expiry": "2026-11-26",
  "entryPrice": 12.50,
  "quantity": 75,
  "notes": "executed manually",
  "targetPremium": 18.00,
  "stopPremium": 8.00
}
```

`recommendationTimestamp` pins the exact recommendation the owner acted on, so
the server resolves that recommendation and its chain snapshot instead of the
newest one. A superseded or evicted pin returns `409 CONFLICT` and records
nothing. `targetPremium`/`stopPremium` are optional overrides; if omitted, the
pinned plan's distances (or configured fallbacks) are applied. The recorded
trade view includes `recommendationTimestamp` for audit. Closing changes local
monitoring state only.

Premium status compares the live option LTP with the recorded target and stop.
The scheduled monitor raises a persisted `TRADE_ALERT` notification once when a
boundary is reached and never exits a position automatically.

## Analysis endpoints

### `GET /api/v1/market/overview`

Returns `{ now, engineAvailable, readModelEntries, markets, options,
strategies, risks, qualifications }`. Each list honours `limit`.

### `GET /api/v1/market/assessments`

Market assessment fields include instrument, interval, timestamp, assessment
status, warm-up flag, confidence level and basis, trend/momentum/volatility/
participation components, structure bias and signals, and session phase.

### `GET /api/v1/market/freshness`

Projects the live pipeline's latest observations. It does not trigger a broker
request and returns an empty list when no live data has been observed.

```json
{
  "success": true,
  "data": [
    {
      "segment": "NSE_CASH",
      "symbolToken": "2885",
      "status": "FRESH",
      "session": "OPEN",
      "lastTickAt": "2026-11-19T09:29:55Z",
      "lastCandleAt": "2026-11-19T09:25:00Z",
      "candleInterval": "FIVE_MINUTE",
      "tickAgeSeconds": 5,
      "candleAgeSeconds": 300,
      "latestPrice": 2500.50
    }
  ]
}
```

`status` is `FRESH`, `STALE` or `UNKNOWN`. `session` is `OPEN`, `CLOSED` or
`UNKNOWN`; it uses the configured exchange calendar, including holidays and
special sessions.

### `GET /api/v1/options/assessments`

Options assessment fields include spot instrument and underlying, chain counts,
ATM implied volatility and its rank/percentile, skew, net Greeks, open interest
and volume totals, concentration strikes, support/resistance zones, put/call
ratios, expected move, liquidity, max pain, session phase, diagnostics and a
summary.

### `GET /api/v1/strategy/definitions`

Returns immutable strategy definitions. The list is empty when the broker-backed
pipeline is not running.

### `GET /api/v1/strategy/evaluations`

Returns each strategy's status, summary and rule outcomes. Evaluations describe
whether conditions hold; they are not buy/sell recommendations.

### `GET /api/v1/risk/assessments` and `/api/v1/risk/qualifications`

Risk assessments describe stop distance, risk budget, size, exposure, leverage,
reward-to-risk and policy outcomes. Qualifications describe whether a strategy
evaluation is eligible for downstream consideration. Neither approves or places
a trade.

## Monitoring (read-only)

Monitoring endpoints exist only when the broker live pipeline is enabled. They
expose trades recorded manually through the application service and never place,
modify or close an order.

### `GET /api/v1/monitoring/active`

Returns active manual trades with their latest monitoring result. Each item has
`trade` and `latestResult`. A trade recorded before its first evaluation has an
empty `latestResult`.

### `GET /api/v1/monitoring/history?tradeId=&limit=`

Returns newest-first significant monitoring transitions. `tradeId` is optional;
`limit` defaults to `50` and is clamped by the store. Each entry contains previous
and current state, previous and current exit recommendation, previous and current
confidence, previous and current directional edge, the explanation and warnings.

### `GET /api/v1/monitoring/{tradeId}`

Returns one manual trade and its latest result. An unknown trade returns the
standard `RESOURCE_NOT_FOUND` envelope.

### `GET /api/v1/monitoring/alerts?limit=`

Returns newest-first persisted monitoring alerts, including state changes, exit
recommendation changes, stale data, qualification loss, confidence/edge
deterioration and lifecycle alerts.

`monitoringState` is one of `HEALTHY`, `WEAKENING`, `AT_RISK`,
`EXIT_RECOMMENDED` or `INVALIDATED`. `exitRecommendation` is one of `HOLD`,
`WATCH_CLOSELY`, `PREPARE_EXIT` or `EXIT_NOW`. These are recommendations only;
there is no execution endpoint and no automatic exit.

## Watchlists

### `POST /api/v1/watchlists`

```json
{ "name": "Momentum" }
```

`201 Created` with the watchlist. Duplicate names return `409`.

### `POST /api/v1/watchlists/{id}/items`

```json
{
  "symbolToken": "2885",
  "symbol": "RELIANCE",
  "segment": "NSE_CASH"
}
```

A watchlist item change schedules the live subscription supervisor to reconcile
its instrument set. Supported feed segments are `NSE_CASH`, `NSE_FO`, `BSE_CASH`,
`BSE_FO` and `MCX_FO`. The server caps the total monitored set.

## Settings and notifications

`PUT /api/v1/settings/preferences` accepts theme, default interval, default
strategy id and notification switches. `PUT /api/v1/settings/application` accepts
maintenance mode, maximum watchlist items (`1..100`) and history retention days
(`30..3650`).

Notifications are in-app records owned by the local principal. There is no push
transport, WebSocket or background polling in the client.

## Live data configuration

The live supervisor is enabled only with `SMARTAPI_ENABLED=true` and valid
read-only broker credentials. Its configured baseline and every watchlist item
form the monitored set. Readable aliases such as `NSE_CASH:NIFTY`,
`NSE_CASH:BANKNIFTY` and `NSE_FO:NIFTY` are resolved from the instrument master;
numeric tokens pass through directly. Alias resolution never selects an option
contract.

Relevant environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `LIVE_MARKET_INSTRUMENTS` | `NSE_CASH:2885,NSE_CASH:NIFTY,NSE_CASH:BANKNIFTY,NSE_FO:NIFTY` | Baseline subscriptions |
| `LIVE_MARKET_INCLUDE_WATCHLISTS` | `true` | Include watchlist instruments |
| `LIVE_MARKET_MAX_INSTRUMENTS` | `200` | Hard cap |
| `LIVE_MARKET_RECONCILE_INTERVAL_MS` | `15000` | Safety-net reconciliation |
| `LIVE_TICK_MAX_AGE` | `2m` | Tick freshness threshold |
| `LIVE_CANDLE_MAX_AGE` | `10m` | Candle freshness threshold |
| `NSE_HOLIDAYS`, `BSE_HOLIDAYS`, `MCX_HOLIDAYS` | empty | Comma-separated ISO holiday dates |

Freshness thresholds and the market calendar are server-side policy. The API
exposes outcomes, not thresholds.
