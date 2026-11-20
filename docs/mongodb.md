# MongoDB Guide

Phase 12 makes MongoDB the durable store for single-owner application records.
The engine's in-memory caches and assessments remain in memory; only the records
listed here are persisted.

## Database and connection

- Database name: **`tradingassistant`**
- Configuration key: `spring.mongodb.uri` (Spring Boot 4; the old
  `spring.data.mongodb.uri` key is ignored)
- Environment variable: **`MONGODB_URI`** (the only production source)
- Never use the `boom` database.
- Never commit a connection string or credentials.

```text
mongodb+srv://<user>:<password>@<cluster>/tradingassistant?retryWrites=true&w=majority
```

The URI is required in the shared and production profiles. Only the `local`
profile has a fallback: `mongodb://localhost:27017/tradingassistant`. Always
include the database path; otherwise the driver selects `test`.

Spring Boot 4 uses `spring.mongodb.uri` for the client connection. The former
`spring.data.mongodb.uri` key is ignored by the auto-configuration; the
remaining Spring Data settings (`auto-index-creation` and
`representation.big-decimal`) stay under `spring.data.mongodb`.

The database is dedicated to this application. The related `boom` database is
not read or modified.

MongoDB is treated as an external dependency. `MongoClientConfiguration` sets a
5-second server-selection timeout, 5-second connect timeout and 10-second socket
read timeout. `MongoIndexInitializer` pings once at startup and skips index
creation with a warning if the database is unreachable, so the operational
endpoints can still start. `PlatformExceptionHandler` maps database connection
failures to `503 DATABASE_UNAVAILABLE` without exposing driver details.

## Collections

| Collection | Document | Owner scoping | Purpose |
|---|---|---|---|
| `users` | `UserDocument` | `_id` is the user id | Legacy owner record; no login API |
| `refresh_tokens` | `RefreshTokenDocument` | `userId` | Legacy token records; no login API |
| `watchlists` | `WatchlistDocument` | `userId` | Named instrument lists |
| `orders` | `OrderRecordDocument` | `userId` | Dormant execution records; no API |
| `trades` | `TradeRecordDocument` | `userId` | Dormant execution records; no API |
| `portfolio_snapshots` | `PortfolioSnapshotDocument` | global portfolio id | Dormant execution records; no API |
| `notifications` | `NotificationDocument` | `userId` | In-app notification records |
| `user_preferences` | `UserPreferencesDocument` | `userId` | Theme, interval, notification switches |
| `application_settings` | `ApplicationSettingsDocument` | singleton | Maintenance flag, limits |
| `manual_trades` | `ManualTradeDocument` | local trade id | Manually recorded, already-executed option trades |
| `monitoring_results` | `MonitoringResultDocument` | trade id | Latest deterministic monitoring result per trade |
| `monitoring_history` | `MonitoringHistoryDocument` | trade id | Significant monitoring transitions for replay |
| `monitoring_alerts` | `MonitoringAlertDocument` | trade id | Monitoring warnings and lifecycle alerts |

No candle series, indicator output, option chain, strategy evaluation or risk
assessment is stored. Those are recomputed by the engine. Monitoring stores only
the current result and significant transition history; it never stores a new
indicator or recommendation calculation.
Orders, trades and portfolio snapshots are retained only because the dormant
Phase 11 model still defines them; no HTTP route or live-supervisor path writes
or reads them.

## Indexes

Created idempotently by `MongoIndexInitializer`:

| Collection | Index | Options |
|---|---|---|
| `users` | `{ email: 1 }` | unique |
| `refresh_tokens` | `{ tokenHash: 1 }` | unique |
| `refresh_tokens` | `{ expiresAt: 1 }` | TTL, expire after 0 seconds |
| `watchlists` | `{ userId: 1, name: 1 }` | unique |
| `orders` | `{ engineOrderId: 1 }` | — |
| `trades` | `{ tradeKey: 1 }` | unique |
| `portfolio_snapshots` | `{ portfolioId: 1, capturedAt: -1 }` | — |
| `notifications` | `{ userId: 1, createdAt: -1 }` | — |
| `application_settings` | `{ updatedAt: -1 }` | — |
| `manual_trades` | `{ status: 1, entryTimestamp: 1 }` | — |
| `monitoring_history` | `{ tradeId: 1, timestamp: -1 }` | — |
| `monitoring_alerts` | `{ timestamp: -1 }` | — |

`orders` and `trades` are dormant; do not add indexes for them speculatively.
Do not add production indexes blindly; review the actual query patterns first.

## Document outlines

### watchlists

```json
{
  "_id": "66f0...",
  "userId": "local-owner",
  "name": "Favourites",
  "items": [
    {
      "itemId": "66f0...",
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

Maximum 100 items by default; the limit is also stored in `application_settings`.
A watchlist item change publishes `WatchlistChangedEvent`, which schedules the
live subscription supervisor to add or remove the instrument.

### notifications

Notifications contain the owner id, category, title, message, read state and
timestamps. The client lists them, reads an unread count, marks one read or marks
all read. There is no push transport.

### manual_trades and monitoring

`manual_trades` stores the owner-recorded contract, entry economics, optional
notes and `ACTIVE`/`CLOSED` status. It has no broker order id or execution state.
`monitoring_results` stores the latest state, exit recommendation, confidence,
trade-aligned edge, component deltas, warnings and explanation for one trade.
`monitoring_history` stores significant transitions; `monitoring_alerts` stores
the alert projection used by the read-only API. No monitoring document contains
an order instruction, stop, target, quantity change or broker identifier.

### user_preferences and application_settings

Preferences store theme, default interval, default strategy id and notification
switches for the fixed local owner. Application settings store maintenance mode,
maximum watchlist items and history retention days. The administrative update
route requires role `ADMIN`, but the fixed local principal has role `USER`, so
the route currently returns `403 FORBIDDEN`; this is an explicit limitation of
the single-owner correction.

## Test database

Integration tests use a dedicated test database or an in-process Mongo-compatible
server. Never point tests at production. Unit tests use fakes and do not require
MongoDB.

## Operational rules

- Never use the `boom` database.
- Never log or return the URI.
- Back up the `tradingassistant` database before a destructive migration.
- If a collection must be dropped, do it explicitly after a backup; no startup
  component drops collections.
