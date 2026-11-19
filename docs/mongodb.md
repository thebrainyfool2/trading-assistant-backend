# MongoDB Guide

Phase 12 makes MongoDB the durable store for identity and application records.
The engine's in-memory caches and assessments remain in memory; only the records
listed here are persisted.

## Database and connection

- Database name: **`trading_assistant`**
- Configuration key: `spring.mongodb.uri` (Spring Boot 4; the old
  `spring.data.mongodb.uri` key is ignored)
- Environment variable: **`MONGODB_URI`** (the only production source)
- Never use the `boom` database.
- Never commit a connection string or credentials.

```text
mongodb+srv://<user>:<password>@<cluster>/trading_assistant?retryWrites=true&w=majority
```

The URI is required in the shared and production profiles. Only the `local`
profile has a fallback: `mongodb://localhost:27017/trading_assistant`. Always
include the database path; otherwise the driver selects `test`.

Spring Boot 4 uses `spring.mongodb.uri` for the client connection. The former
`spring.data.mongodb.uri` key is ignored by the auto-configuration; the
remaining Spring Data settings (`auto-index-creation` and
`representation.big-decimal`) stay under `spring.data.mongodb`.

A dedicated `trading_assistant` database has been created on the existing Atlas
cluster. It contains the nine application collections and the indexes below;
the unrelated `boom` database was not modified.

MongoDB is treated as an external dependency. `MongoClientConfiguration` sets a
5-second server-selection timeout, 5-second connect timeout and 10-second socket
read timeout. `MongoIndexInitializer` pings once at startup and skips index
creation with a warning if the database is unreachable, so the operational
endpoints can still start. `PlatformExceptionHandler` maps database connection
failures to `503 DATABASE_UNAVAILABLE` without exposing driver details.

## Collections

| Collection | Document | Owner scoping | Purpose |
|---|---|---|---|
| `users` | `UserDocument` | `_id` is the user id | Account, BCrypt hash, roles, status |
| `refresh_tokens` | `RefreshTokenDocument` | `userId` | Hashed refresh tokens, expiry, revocation |
| `watchlists` | `WatchlistDocument` | `userId` | Named instrument lists |
| `orders` | `OrderRecordDocument` | `userId` | Submitted order and execution state |
| `trades` | `TradeRecordDocument` | `userId` | Closed position history |
| `portfolio_snapshots` | `PortfolioSnapshotDocument` | global portfolio id | Scheduled engine portfolio captures |
| `notifications` | `NotificationDocument` | `userId` | In-app notification records |
| `user_preferences` | `UserPreferencesDocument` | `userId` | Theme, interval, notification switches |
| `application_settings` | `ApplicationSettingsDocument` | singleton | Maintenance flag, limits |

No engine cache, candle series, indicator output, option chain, strategy
evaluation or risk assessment is stored. Those are recomputed by the engine.

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

`orders` and `trades` queries by user rely on the user id stored in each document;
add a compound index such as `{ userId: 1, createdAt: -1 }` if account history
grows large. Do not add indexes blindly in production; review the query patterns
first.

## Document outlines

### users

```json
{
  "_id": "66f0c0f0c0f0c0f0c0f0c0f0",
  "email": "trader@example.com",
  "displayName": "Trader",
  "passwordHash": "$2a$12$...",
  "roles": ["USER"],
  "status": "ACTIVE",
  "createdAt": "2026-11-01T10:00:00Z",
  "updatedAt": "2026-11-18T09:30:00Z",
  "lastLoginAt": "2026-11-18T09:30:00Z"
}
```

The password hash is never returned. Email uniqueness is enforced by both the
service and the unique index.

### refresh_tokens

```json
{
  "_id": "66f0...",
  "userId": "66f0c0f0c0f0c0f0c0f0c0f0",
  "tokenHash": "SHA-256 hex digest",
  "issuedAt": "2026-11-18T09:30:00Z",
  "expiresAt": "2026-12-18T09:30:00Z",
  "revokedAt": null
}
```

Only the hash is stored. Rotation consumes the presented token atomically with
`findAndModify`; a replayed token cannot create a second session.

### watchlists

```json
{
  "_id": "66f0...",
  "userId": "66f0...",
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

### orders and trades

`orders` stores the engine order id, strategy, segment, token, side, type,
quantity, fill fields, stop/target, status, execution status, reason, position id
and timestamps. `trades` stores a unique `tradeKey`, the linked order/position,
direction, filled/closed quantity, average entry, realised P&L and open/close
times. Account APIs always filter by the authenticated `userId`; fields are never
used to infer ownership.

### portfolio_snapshots

A snapshot stores capital, available/blocked/used margin, realised/unrealised
P&L, exposure, open/closed counts, and a list of open and closed positions. The
scheduler captures one every 15 minutes while the engine is running. The current
portfolio endpoint prefers live engine state and falls back to the latest stored
snapshot (`source: "SNAPSHOT"`), so clients can see that the data is not live.

### notifications, preferences and settings

Notifications store type, title, message, read flag and timestamps. Preferences
store the dark theme, default interval/strategy and five notification switches.
Application settings are a singleton document with maintenance mode, watchlist
limit, history retention and last updater.

## Retention

`MaintenanceJobs` runs four maintenance tasks in UTC:

| Job | Default | Effect |
|---|---|---|
| Token cleanup | hourly at `:15` | Removes expired/revoked refresh tokens older than `TOKEN_RETENTION_DAYS` (7) |
| Portfolio snapshot | every 15 minutes | Captures the live engine portfolio |
| Notification purge | daily 02:30 | Removes read notifications older than 30 days |
| Read-model trim | daily 02:45 | Trims stale in-memory assessment entries |

The TTL index on `refresh_tokens.expiresAt` provides a second, database-level
cleanup. No job places, modifies or cancels a trade.

## Local development

Option A — local MongoDB:

```bash
# .env is not read automatically; export variables.
export MONGODB_URI='mongodb://localhost:27017/trading_assistant'
cd backend
mvn spring-boot:run
```

Option B — Atlas with a dedicated development database/user. Keep the database
name `trading_assistant`; do not point local tests at production.

The test suite does not need MongoDB. Repository and HTTP integration tests use
`de.bwaldvogel:mongo-java-server` with a database named
`trading_assistant_test`. Unit tests use fakes.

## Backup and recovery

- Enable Atlas continuous backups or scheduled snapshots.
- Test a restore into a separate database before relying on it.
- Losing `users` and `refresh_tokens` signs everyone out; losing `orders`/`trades`
  loses application history but not engine state.
- Losing `portfolio_snapshots` removes the fallback portfolio; the live engine
  portfolio still serves while running.
- The engine portfolio is in memory and is **not restored** from snapshots on
  restart. Treat snapshots as reporting history, not as execution state.

## Operational checks

```javascript
// Atlas / mongosh
use trading_assistant
db.users.countDocuments({})
db.orders.find().sort({ createdAt: -1 }).limit(5)
db.portfolio_snapshots.find().sort({ capturedAt: -1 }).limit(1)
db.refresh_tokens.getIndexes()
```

Never print a user document's `passwordHash` or a token hash in logs, tickets or
chat. Do not run write commands against production while diagnosing; prefer
read-only checks and application metrics.

## Known gaps

- No multi-document transactions are used. Order recording and engine execution
  are separate operations; see the architecture review's consistency finding.
- No schema migration framework. Changes must be additive or explicitly planned.
- No field-level encryption at rest beyond Atlas disk encryption.
- No automated backup verification in the test suite.
