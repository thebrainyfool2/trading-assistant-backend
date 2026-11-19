# Phase 12 — Application Layer and Android Client

Status: **implementation complete, locally verified; not deployed.**

Phase 12 turns the Phase 1–11 engine into a multi-user application. It adds JWT
authentication, MongoDB persistence, account-scoped REST APIs, maintenance jobs,
observability, API documentation, and a native Android client. It does not change
the market, intelligence, options, strategy, qualification, risk, execution or
portfolio calculations delivered in earlier phases.

## 1. Scope and constraints honored

| Constraint | Result |
|---|---|
| Do not change Phase 1–11 architecture or calculations | Engine packages are unchanged except new app error codes. Controllers call services; services call existing engine components. |
| Database `trading_assistant` on the existing Atlas cluster | Configured only through `MONGODB_URI`; local fallback is `mongodb://localhost:27017/trading_assistant`; `boom` is never used. |
| No hardcoded credentials | Secrets come from environment variables; `.env.example` contains placeholders only. |
| Persist users, watchlists, preferences, orders, trades, portfolio snapshots, settings | Done through nine MongoDB collections. |
| Do not persist engine caches | The read model is in memory; only application records are stored. |
| Background jobs only | Token cleanup, notification retention, portfolio snapshots and read-model trim; no trade scheduler. |
| Android: Kotlin, Compose, Material 3, Navigation Compose, Retrofit, ViewModel, StateFlow, Hilt, Coil, API 29+, dark only | Implemented in `frontend/` with 15 screens. |
| No AI, ML, backtesting, strategy generation or recommendations | Nothing in this phase analyses, ranks or recommends a trade. |
| All tests pass | Backend 1,072 tests pass, 2 skipped. Android 21 JVM tests pass. |

## 2. Backend architecture

Phase 12 code lives entirely under `com.tradingassistant.platform`, plus six new
`ErrorCode` values and configuration yml changes. Existing engine packages do not
import the platform layer.

```text
HTTP / JWT
   │
   ├── platform.api             controllers, DTO projections, pages, OpenAPI
   ├── platform.security        JWT, refresh tokens, BCrypt, filter, error handler
   ├── platform.user            accounts, roles, status, bootstrap admin
   ├── platform.watchlist       per-account watchlists
   ├── platform.preferences     per-account settings
   ├── platform.settings        application-wide settings
   ├── platform.notification    in-app records and unread counts
   ├── platform.history         order/trade/portfolio documents and queries
   ├── platform.executionapp    order use case, portfolio query, engine gateway
   ├── platform.readmodel       event bridge and bounded analysis snapshots
   └── platform.jobs            maintenance jobs
             │
             ▼
   Phase 1–11 engine (optional, broker-conditional)
```

The boundary is deliberately narrow:

- `ExecutionGateway` resolves `ExecutionService` and `PortfolioService` through
  `ObjectProvider`. When broker credentials are absent the beans do not exist;
  order routes answer `503 ENGINE_UNAVAILABLE` and portfolio falls back to a
  stored snapshot or `UNAVAILABLE`.
- `PlatformEventBridge` subscribes to the existing `MarketEventBus`; it observes
  events and fills `PipelineSnapshotStore`. No engine class knows about it.
- `AnalysisQueryService` projects existing immutable assessments into API views;
  it does not recalculate any value.
- `OrderApplicationService` converts an explicit side/type intent into the
  engine's existing order flow. The client cannot submit a quantity; size and
  stop come from the engine's `RiskAssessment`.

## 3. Authentication and authorization

- Access token: HMAC-SHA256 JWT via Spring Security 7 `NimbusJwtEncoder`.
  Subject is the user id; roles and email are claims; 15-minute default TTL.
- Refresh token: 32 random bytes, Base64 URL encoded, stored only as a SHA-256
  hash, 30-day default TTL, rotated atomically on refresh and revoked on logout.
- Passwords: BCrypt, cost 12 by default.
- Security filter chain is stateless, CSRF disabled, CORS configured from
  `CORS_ALLOWED_ORIGINS`.
- Public routes are limited to auth, system status, health, API docs and error.
- `/api/v1/admin/**`, user listing/role/status updates and application-settings
  updates require `ADMIN`.
- Every account query uses the authenticated principal's user id, never a
  client-supplied owner id.
- Login and registration create notification records; no password or token is
  logged.

Development profiles generate an ephemeral signing key when `JWT_SECRET` is
blank. The `prod` profile refuses to start without a secret, and any configured
secret must decode to at least 256 bits.

## 4. Persistence

Nine collections on the `trading_assistant` database:

| Collection | Purpose |
|---|---|
| `users` | Account, BCrypt hash, roles, status, timestamps |
| `refresh_tokens` | Hashed refresh tokens, expiry and revocation |
| `watchlists` | Named instrument lists with embedded items |
| `orders` | Application order records and engine execution state |
| `trades` | Closed-position/trade history |
| `portfolio_snapshots` | Scheduled portfolio captures |
| `notifications` | In-app records and read state |
| `user_preferences` | Theme, interval, strategy and notification switches |
| `application_settings` | Singleton maintenance/limits document |

Indexes are created idempotently at startup: unique email, unique refresh-token
hash, TTL on token expiry, unique owner+name watchlists, unique trade key,
order engine id, snapshot/notification query indexes and settings update index.
If MongoDB is unreachable at boot, index creation is skipped with a warning and
the application still starts; `/api/v1/system/status` reports `DEGRADED`.

`MongoClientConfiguration` bounds waits to 5 seconds for server selection and
connection and 10 seconds for reads. Database failures map to
`503 DATABASE_UNAVAILABLE` without driver detail.

## 5. REST API

Fifty routes across thirteen groups:

- `/api/v1/auth` — register, login, refresh, logout.
- `/api/v1/users` — profile, password, sessions, logout-all, admin user
  management.
- `/api/v1/watchlists` — list, get, create, rename, delete, add/remove item.
- `/api/v1/orders` — place, list, get, cancel.
- `/api/v1/portfolio` and `/api/v1/positions` — current portfolio and open book.
- `/api/v1/history` — own orders, trades and portfolio snapshots.
- `/api/v1/market` — overview and market assessments.
- `/api/v1/options` — option-chain assessments.
- `/api/v1/strategy` — evaluations and definitions.
- `/api/v1/risk` — risk assessments and qualifications.
- `/api/v1/settings` — preferences and application settings.
- `/api/v1/notifications` — list, unread count, mark one/all read.
- `/api/v1/system` and `/api/v1/admin` — status and cross-account history.

Every response uses the existing `ApiResponse` envelope. The full contract,
examples and error catalog are in [api-reference.md](api-reference.md). OpenAPI
is generated at `/v3/api-docs` with Swagger UI at `/swagger-ui.html`.

## 6. Read model and execution flow

`PlatformEventBridge` subscribes to the engine event bus and extracts the latest
market, options, strategy, qualification and risk assessments into
`PipelineSnapshotStore` (capacity 128 per kind). The store keeps insertion order,
deduplicates by instrument/strategy identity and trims stale entries. Analysis
endpoints read this store; they never invoke the engine.

Order flow:

1. Authenticated request arrives with strategy, segment, token, side, type and
   optional prices. Quantity is absent by design.
2. `OrderApplicationService` resolves the latest risk/strategy context and builds
   the engine intent.
3. `ExecutionGateway` submits to the existing execution service.
4. The application record is written to `orders`; execution events update it and
   write trade records when a position closes.
5. The client receives the engine-derived order view, including status, fill and
   reason. It does not compute any of these.

Portfolio flow: live engine snapshot when available; latest stored snapshot
otherwise. The response always includes `source` so stale data is visible.

## 7. Background jobs

All jobs are maintenance tasks under `app.platform.scheduling` and run in UTC:

- Refresh-token cleanup, hourly at `:15` (plus the TTL index).
- Portfolio snapshot every 15 minutes while the engine is running.
- Read notification purge daily at 02:30.
- Read-model trim daily at 02:45.

Scheduling can be disabled with `PLATFORM_JOBS_ENABLED=false`. No job submits,
modifies or cancels an order.

## 8. Production readiness

- Health: `/actuator/health` with liveness/readiness probes; MongoDB health is
  disabled by default locally and enabled in production.
- Metrics: `/actuator/metrics`; Prometheus exposed in `prod`.
- Structured logging: ECS JSON console output in production, request id in MDC.
- Errors: centralized platform advice and existing global advice; no internal
  details leak.
- Configuration: typed `SecurityProperties` and `SchedulingProperties`, with
  production JWT validation.
- Docker: existing multi-stage, non-root image.
- Render: `render.yaml` now declares MongoDB, JWT, admin and CORS secrets as
  `sync: false`.
- Android base URL is build-time configurable (`-PapiBaseUrl=...`).
- API docs and deployment/Mongo guides included.

## 9. Android client

Location: `frontend/`, package `com.tradingassistant.android`.

Stack: Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, Jetpack Compose with Material 3,
Navigation Compose, Retrofit/Gson, OkHttp, Hilt, StateFlow, Coil, min SDK 29,
target/compile SDK 35, dark theme only.

Fifteen screens:

1. Login
2. Register
3. Dashboard
4. Portfolio
5. Positions
6. Orders
7. Order detail
8. Watchlist
9. Option chain
10. Market overview
11. Strategy evaluations
12. Risk assessments
13. Trade history
14. Settings
15. Profile

Supporting structure:

- `data/remote` — Retrofit contracts, DTOs, bearer interceptor, refresh
  authenticator, envelope/error translation.
- `data/repository` — six repository interfaces and implementations.
- `data/local` — private token storage.
- `di` — Hilt modules for network, repositories and API clients.
- `ui/**` — stateless screens plus Hilt view models; StateFlow drives loading,
  error, empty and content states.
- `ui/common` — shared cards, metrics, state views, formatting.
- `notifications` — Android notification channels and local notification helper.
  The backend remains the notification record source; there is no push transport.
- Coil is available for image loading; the current data set contains no image
  URLs.

The client never calculates a signal, size, stop, target, recommendation or P&L.
It renders backend values, formats them for display and sends explicit user
actions (login, watchlist edits, order cancellation, profile/preferences).

## 10. Verification

Backend, from `backend/`:

```bash
mvn -B -ntp verify
```

Result: **1,072 tests, 0 failures, 0 errors, 2 skipped.**

Phase 12 contributes 166 tests under `com.tradingassistant.platform`:

- 108 service/unit tests: order application, portfolio query, history listeners
  and services, notifications, preferences, read model, JWT/auth, settings,
  users, watchlists.
- 40 controller tests across auth, users, watchlists, orders, portfolio,
  settings, notifications, market/options/strategy/risk.
- 18 `PlatformIntegrationTest` cases using the real security chain and
  repositories against an in-process Mongo-compatible server.

The existing 906 engine/system tests remain green.

Android, from `frontend/`:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
```

Result: **21 JVM tests pass** across API contract, login/register, dashboard,
orders, watchlist and settings view models. The three Compose UI tests compile.

The debug APK was assembled (`assembleDebug`) and Android lint passed with zero
errors. Remaining lint warnings are dependency-update notices and an unavoidable
adaptive-icon folder qualifier notice.

Not run: Android instrumented UI tests. No emulator/device or system image was
available; only the Android SDK platform/build tools were installed locally to
compile the app and run JVM tests. Compiling the instrumented tests is not the
same as executing them.

Backend integration tests use `de.bwaldvogel:mongo-java-server`, not Atlas. A
separate manual check connected to the Atlas cluster using the dedicated
`trading_assistant` database, created its nine collections and let the
application initialize its indexes; `/api/v1/system/status` reported
`database: UP`. The automated suite still does not exercise Atlas, and no
broker or Render deployment was performed.

## 11. Deliberately excluded

- Deployment, publishing, Atlas cluster provisioning/network-policy changes,
  DNS and CI/CD changes.
- Live broker execution validation; broker remains disabled by default.
- Push notifications, websockets or background sync on Android.
- Rate limiting, MFA, email verification, password reset, key rotation/JWKS.
- Automated trading, strategy generation, recommendations, AI/ML, backtesting,
  optimisation and paper trading.
- Any change to Phase 1–11 business logic.

## 12. Follow-up work

1. Validate production startup with real Render secrets and network policy.
2. Run instrumented Android tests on an emulator/device and smoke-test against a
   deployed HTTPS API.
3. Decide whether analysis assessments should be persisted as history; today
   the read model is bounded and in memory.
4. Address the dual-write consistency gap between engine execution and the order
   record, and document recovery for an interrupted submission.
5. Add rate limiting and account recovery before exposing the API publicly.
6. Replace plain private-storage token persistence with a Keystore-backed store
   if the threat model requires it.
7. Enforce or remove the stored `maintenanceMode`, `maximumWatchlistItems` and
   `historyRetentionDays` settings; they are currently persisted but not applied
   by runtime behavior.
8. Decide whether the house portfolio should be visible to every `USER`; current
   endpoints expose it to all authenticated accounts.

The detailed architectural assessment is in
[architecture-review.md](architecture-review.md).
