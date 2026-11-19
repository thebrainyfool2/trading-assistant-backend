# Architecture Review — Trading Assistant

Review date: 18 November 2026  
Scope: Phases 1–12, backend and Android client  
Mode: documentation only. No code was changed as part of this review.

## 1. Executive Summary

The platform has a strong, unusually disciplined engine core. Market data,
indicators, market intelligence, options analytics, strategy evaluation, trade
qualification, risk sizing and execution are separated by explicit immutable
domain models and narrow service contracts. Phase 12 places an application shell
around that core without importing engine logic into controllers or recomputing
any engine output.

The result is coherent and test-heavy: 82 backend platform classes, 1,072
backend tests and a working Android client with 21 JVM tests. The most important
strengths are the one-way engine dependency, immutable records, account-scoped
JWT security, hashed/rotated refresh tokens and a strict no-recommendation
boundary.

The most important weaknesses are not in the calculation engines. They are
operational and multi-tenant concerns introduced by turning a single-account
engine into a service:

1. **Order submission is a non-atomic dual write.** The broker submission happens
   before the MongoDB order record is written. A database failure after a broker
   fill can leave an untracked live position.
2. **Portfolio data is global but exposed to every authenticated user.** Orders
   and watchlists are user-scoped, while the engine portfolio and portfolio
   snapshots belong to the house broker account. Any `USER` can currently read
   that account's capital and P&L.
3. **Application settings are stored but not enforced.** `maintenanceMode`,
   `maximumWatchlistItems` and `historyRetentionDays` do not affect runtime
   behavior; watchlists always use the constant limit of 100.
4. **The analysis read model is in-memory and rebuilt from zero.** It is a useful
   projection, not history, and disappears on restart or while the engine is
   idle.
5. **Android points the right way but is not production-hardened.** Tokens sit in
   ordinary private preferences, there is no certificate pinning, no push
   transport and no executed instrumented test run.

None of these invalidates the architecture. They define the gap between "verified
implementation" and "safe multi-user production service". A release should not
claim the latter until the first two findings are resolved and documented.

**Overall rating: adequate for a single-account, supervised deployment; not yet
ready for unsupervised multi-tenant production.**

## 2. System Context and Scope

The platform is an analysis and trade-monitoring system for an Indian markets
trader. A human makes every trading decision. The backend ingests broker data,
computes descriptive analytics, evaluates predefined rules, derives risk
boundaries, and can submit an explicit order intent to a broker. The Android
client displays the result.

```text
Angel One SmartAPI ──► broker adapter ──► market domain
                                      ├─► indicator engine
                                      ├─► pipeline / event bus
                                      ├─► intelligence
                                      ├─► options analytics
                                      ├─► strategy rules
                                      ├─► qualification rules
                                      └─► risk sizing
                                                    │
Android ◄── REST/JWT ◄── application layer ◄────────┘
                              │
                              ├─► execution / portfolio (optional)
                              └─► MongoDB (identity + records)
```

In scope for Phase 12: identity, persistence, REST, application jobs, Android UI.
Out of scope by design: AI/ML, recommendation, ranking, backtesting, automated
trading, portfolio optimisation, paper trading and broker account aggregation.

The system context is honest about its boundary: it describes and constrains; it
does not decide for the user. That is the correct product boundary for a tool
that can place orders.

## 3. Layered Architecture

The backend has five practical layers:

| Layer | Location | Responsibility |
|---|---|---|
| Transport | `platform.api` | HTTP mapping, validation, envelopes, pagination, OpenAPI |
| Application | `platform.executionapp`, `platform.history`, `platform.user`, `platform.watchlist`, `platform.*` services | Use cases, ownership checks, orchestration |
| Security | `platform.security` | JWT issue/verify, password hashing, refresh rotation, filter chain |
| Persistence | `platform.*` repositories/documents | MongoDB records and indexes |
| Engine | existing `broker`, `market`, `indicator`, `pipeline`, `intelligence`, `options`, `strategy`, `qualification`, `risk`, `execution` packages | Pure domain and calculation work |

The dependency direction is correct: controllers → application services →
engine/domain and repositories. Engine packages import **zero** platform classes.
The only engine change in Phase 12 is the addition of application error codes to
`ErrorCode`; no calculation file was modified.

One subtlety: the read model is not a separate layer in the request path. It is a
listener on the pipeline event bus (`PipelineSnapshotStore` implements
`MarketEventListener`, registered by `PlatformConfiguration.PlatformEventBridge`).
Analysis controllers query that store. This keeps the engine unaware of HTTP and
avoids adding query methods to engine services.

**Assessment: strong.** The layering is real rather than directory-deep.

## 4. Module Boundaries and Dependency Rules

The engine is organized around domain concepts, not technical layers:

- `broker` owns provider-specific authentication, REST and websocket details.
- `market` owns provider-neutral instruments, candles, quotes and sessions.
- `indicator` owns calculation contracts and sixteen calculators.
- `pipeline` owns aggregation, sessions, immutable events and lifecycle.
- `intelligence`, `options`, `strategy`, `qualification`, `risk` own one
  descriptive concern each.
- `execution` owns validation, order/position/portfolio lifecycle and the broker
  execution abstraction.
- `platform` owns accounts, security, persistence and transport.

Boundary strengths:

- SmartAPI DTOs do not leak into the market domain.
- Engine assessments are immutable records; API DTOs are separate projections.
- Execution is optional: `ObjectProvider` prevents the API from requiring broker
  beans.
- The platform's event bridge isolates projection failures so a broken read model
  cannot stop the engine.

Boundary weaknesses:

- `platform` necessarily imports engine domain types. A future extraction into a
  separate service would need an anti-corruption layer or shared contract module.
- `MarketDtos.OptionsAssessmentView.from` composes a summary string by
  concatenating chain and volatility summaries. That is presentation logic in a
  transport DTO, not a calculation error.
- The application layer knows the engine's `OrderIntent` and `ExecutionResult`
  directly. That is acceptable for a modular monolith but couples deployment
  units.

**Assessment: strong within the monolith; extraction would require work.**

## 5. Domain Model and Invariants

The domain model is the best part of the codebase. Records are immutable, money
uses `BigDecimal`, timestamps are `Instant`, instruments are keyed by
`(Segment, symbolToken)`, and assessments expose explicit status/warm-up rather
than pretending incomplete data is valid.

Important invariants:

- A market assessment is keyed by instrument and interval; a new assessment
  replaces the previous description.
- Option assessments are keyed by spot instrument and expose chain, Greeks,
  volatility, open interest, expected move and liquidity as separate components.
- Strategy, qualification and risk results carry per-rule evaluations, so a
  client can explain a status rather than only display it.
- An order intent contains direction and type but not quantity. Size and stop are
  engine outputs, not client inputs.
- A position has a lifecycle; only open positions are returned by the positions
  endpoint.

Gaps:

- There is no formal aggregate-boundary or transaction policy document. The
  engine's in-memory portfolio and MongoDB records have different lifetimes.
- The relationship between a "user" and the single engine portfolio is not
  modelled. It is implicitly a house account, which becomes a security concern in
  section 16.

**Assessment: strong domain modelling; missing tenancy aggregate.**

## 6. Market Data and Broker Integration

The broker adapter is conditional (`SMARTAPI_ENABLED`, default false), which lets
the application and tests run without credentials. Authentication uses TOTP,
session renewal handles expiry skew, and REST/websocket concerns stay inside
`broker.angelone`. Domain mapping converts provider instruments, quotes, candles,
depth and ticks into provider-neutral types.

Strengths:

- Credentials are externalized and never exposed in status endpoints.
- The adapter has bounded timeouts and retry policy.
- Provider errors are translated into safe platform categories.
- The instrument master is normalized before use.

Risks:

- The live adapter was not exercised in Phase 12 (no broker credentials in the
  test environment). The Phase 2 evidence remains the source of truth for live
  behavior.
- There is a single broker implementation. The abstraction is credible, but it has
  not been proven by a second adapter.
- Market-data correctness ultimately depends on provider timestamps and session
  handling; the reviewer did not revalidate those calculations.

**Assessment: adequate, with live-integration evidence outside this phase.**

## 7. Indicator Engine

The indicator engine provides sixteen calculators behind a registry/factory and
uses immutable series/results. Validation rejects bad candle ordering, invalid
OHLC, negative volume and gaps. Calculation utilities use decimal math and
rolling windows rather than `double` accumulation.

Strengths:

- Pure and stateless from the caller's perspective.
- Historical and incremental paths share the same contracts.
- Warm-up and insufficient-data states are explicit.
- Indicator output is not a signal; downstream layers interpret it.

Risks:

- Mathematical correctness is covered by unit tests but not independently
  reconciled against a known external library in this review.
- Incremental and batch results can drift if a future calculator is added without
  testing both paths. The existing suite covers the current set.

**Assessment: strong.**

## 8. Market Processing Pipeline and Events

The pipeline is the runtime spine: tick-to-candle aggregation with bucket
alignment, exchange calendars, long-lived indicator sessions, and immutable
events published on an event bus. Session lifecycle is explicit, and the platform
subscribes without modifying the bus.

Strengths:

- Events are sealed immutable types with a clear publication contract.
- Session anchoring and bucket alignment are separated from indicator logic.
- The platform projections are additive listeners with failure isolation.
- The pipeline can be absent without preventing the API from starting.

Risks:

- Event delivery is in-process. There is no durable queue, replay or
  exactly-once guarantee. An application restart loses in-flight projections.
- Ordering guarantees are those of the bus implementation; the review did not
  find a documented cross-listener ordering contract.
- Projection failures are logged and swallowed by design, which protects the
  engine but can silently stale the read model.

**Assessment: appropriate for a monolith; not a durable event architecture.**

## 9. Market Intelligence

The intelligence engine describes trend, momentum, volatility, participation,
structure and session context. It returns one immutable assessment with
confidence and warm-up status. It deliberately does not score, rank or signal.

Strengths:

- Component analyzers have narrow responsibilities.
- Warm-up is first-class, avoiding false confidence from partial windows.
- Structure and session outputs are descriptive and explainable.
- API projection preserves component states and summaries.

Risks:

- Confidence levels are rule-derived; the review did not re-derive their
  calibration. They should be presented as descriptions, not probabilities, which
  the UI copy generally does.
- No historical assessment store exists; clients see only the latest per
  instrument/interval.

**Assessment: strong descriptive design.**

## 10. Options Analytics

The options engine is the most computationally ambitious component: multi-expiry
chains, Black-Scholes-Merton Greeks, implied volatility with rank/percentile,
smile/skew/surface preparation, open-interest and volume changes, concentration,
build-up, put/call ratios, max pain, expected move and liquidity.

Strengths:

- Chain, volatility, open interest, expected move, liquidity and max pain are
  separate immutable components.
- Diagnostics and summaries are preserved in the projection.
- Contract keys and expiries are explicit; no provider DTO leaks.
- The API sends numbers, not preformatted recommendations.

Risks:

- IV/Greeks depend on model assumptions and data quality. This review did not
  independently validate numerical edge cases.
- The API's `summary` concatenates two engine summaries; harmless, but it is the
  one place where transport adds wording.
- The projected chain is an assessment summary, not a full strike-by-strike
  chain. Android labels the screen "Option chain" but displays assessment
  analytics; this should be explained in UI copy or a future endpoint should
  expose the full chain.

**Assessment: strong engine; the client's "chain" label overpromises slightly.**

## 11. Strategy Evaluation

Strategies are immutable definitions with deterministic rules, evaluated by a
registry of fifteen built-ins. Evaluations state whether conditions currently
hold; there is no ranking or recommendation.

Strengths:

- Definitions, rule evaluation and registry are separated.
- Rule-level status/reason is preserved for explainability.
- Duplicate strategy ids are rejected by the registry.
- Output is independent of execution.

Risks:

- Built-in strategies are samples, not validated trading edges. The documentation
  is careful about this, but product copy must remain equally careful.
- Strategy definitions are code-owned; there is no user-authored strategy model,
  which is appropriate for this phase but limits extensibility.
- Evaluation freshness depends on the pipeline; clients see the last event, not a
  guaranteed live recomputation at request time.

**Assessment: strong rule engine, appropriately constrained.**

## 12. Trade Qualification

Qualification sits between strategy evaluation and risk sizing. It applies
session, readiness, option-data, liquidity, strategy-status, confidence and
participation rules, suppresses duplicates, and returns an immutable eligibility
description. It does not approve or size.

Strengths:

- Rule registry and duplicate suppression are explicit.
- Qualification status and per-rule reasons are projected to clients.
- The layer prevents downstream risk work on incomplete or illiquid contexts.

Risks:

- Qualification is a gate, not a guarantee; naming and UI should avoid implying
  "approved".
- The duplicate-suppression window is in-memory and resets on restart. That is
  acceptable for a descriptive gate but should be understood operationally.

**Assessment: strong; semantics must stay descriptive.**

## 13. Risk and Position Sizing

Risk derives stops from ATR with volatility adjustment, sizes from reference
capital and risk percentage, and returns risk per trade, maximum loss, stop
distance, quantity, notional, leverage, reward-to-risk, daily-loss and exposure
boundaries. It does not approve or place.

Strengths:

- Pure deterministic inputs; no broker dependency.
- Every boundary is exposed, not just a final quantity.
- Rule-level evaluations explain failures.
- Order placement consumes the assessment rather than accepting a client size.

Risks:

- The capital base is configured, not reconciled with the broker's live funds.
  Real account equity can diverge.
- Risk policy is global, not per user or per strategy allocation. Multi-user
  operation would need explicit capital ownership.
- There is no portfolio-level correlation or concentration model; exposure limits
  are boundary checks, not portfolio optimisation.

**Assessment: strong single-account risk engine.**

## 14. Execution, Orders and Portfolio

Execution validates intents, submits through a broker abstraction, tracks order
and position lifecycles and maintains an in-memory portfolio. Phase 12 adds an
application order record, history listeners, portfolio snapshots and REST.

Strengths:

- OrderIntent is explicit and contains no client quantity.
- Cancellation checks ownership, engine id and terminal state.
- Portfolio responses state `LIVE`, `SNAPSHOT` or `UNAVAILABLE`.
- Execution history and notification listeners observe engine events without
  coupling the engine to persistence.
- Broker absence is a documented `503`, not a startup failure.

**High-severity findings:**

1. **Dual write without recovery.** `OrderApplicationService.place` calls
   `gateway.submit(intent)` and only then writes the order record via
   `history.recordSubmission`. If the broker accepts/fills and MongoDB fails, the
   client receives `503 DATABASE_UNAVAILABLE` while a live position exists. There
   is no outbox, idempotency key, reconciliation job or startup recovery scan.
2. **In-memory portfolio is not restored.** Portfolio snapshots are persisted,
   but the engine portfolio starts empty after restart. Snapshot fallback is
   reporting, not execution state. Open positions must be reconciled with the
   broker before trading resumes.
3. **Global portfolio visibility.** `/api/v1/portfolio`, `/api/v1/positions` and
   `/api/v1/history/portfolio` return the house portfolio/snapshots to any
   authenticated `USER`. Orders are user-scoped; portfolio is not. In a
   multi-tenant deployment this leaks capital, P&L and exposure.
4. **Cancellation is not a state transition guarantee.** The engine response is
   applied, but there is no persisted cancellation intent/outbox if the process
   dies between broker cancel and record update.

**Assessment: adequate for a single-operator, supervised system; high risk for
unattended multi-user operation.**

## 15. Application Layer and REST API

The application layer is broad but consistent: 82 classes, 20+ controllers/DTO
groups, account-scoped repositories, central error advice, paged envelopes and
OpenAPI generation. API paths are versioned under `/api/v1`.

Strengths:

- Controllers are thin; business decisions remain in services/engine.
- DTOs are separate from documents and engine types.
- Stable error codes and validation details are exposed without internals.
- Pagination is bounded to 100.
- History, preferences, watchlists, notifications and settings are persistent.
- Android consumes the same contract that integration tests exercise.

Weaknesses:

- **Settings are not wired to behavior.** `maintenanceMode` is never checked by a
  filter; `maximumWatchlistItems` is not consulted by `WatchlistService`;
  `historyRetentionDays` is not used by a cleanup job. The admin API can store
  values that have no effect. This is misleading and should be fixed or the
  fields should be documented as reserved.
- **No idempotency key** on order placement. A network retry can create a second
  order.
- **No rate limiting** on auth or order endpoints.
- **No API deprecation policy** beyond the path version.
- `PortfolioController` exposes both `/portfolio` and `/portfolio/summary` for
  the same data; harmless, but redundant.
- Admin history is read-only and cross-account; correct for an operator, but its
  authorization depends on a single `ADMIN` role with no audit trail of reads.

**Assessment: good implementation; several admin controls are cosmetic.**

## 16. Security and Identity

Strengths:

- Stateless JWT with HMAC-SHA256 and explicit issuer/expiry validation.
- Refresh tokens are opaque, hashed at rest, rotated atomically, and revoked on
  logout/password change.
- BCrypt cost 12.
- Account queries use the JWT subject; ownership checks exist for orders and
  watchlists.
- Production refuses to start without a sufficiently large signing secret.
- Error responses do not reveal internals.
- CORS is opt-in and credentials are disabled.
- Android permits cleartext only for emulator loopback hosts.

Risks and gaps:

- **No MFA, email verification, password reset, account lockout or rate limiting.**
  A public deployment is exposed to credential stuffing.
- **No key rotation/JWKS.** Rotating `JWT_SECRET` invalidates all access tokens
  immediately; there is no overlap window.
- **No audit log** for admin reads/role changes or order actions. Application logs
  are not a tamper-evident audit trail.
- **Global portfolio access** (section 14) is a cross-tenant data exposure if more
  than one human uses the deployment.
- **Bootstrap admin** is created from environment variables; password rotation is
  an operational manual step.
- **Token storage on Android** is ordinary private `SharedPreferences`. It is
  sandboxed but not hardware-backed; a rooted device or backup extraction can
  read it. `android:allowBackup="false"` reduces the backup path.
- **No certificate pinning**; HTTPS trust relies on the system store.
- **CORS empty by default** is safe for Android, but a browser client will fail
  until configured.
- **Logging** uses BASIC OkHttp logging in debug; no token/body logging was found,
  but debug builds on a shared device can still expose URLs.

**Assessment: solid authentication core; incomplete account-security lifecycle.**

## 17. Persistence and Consistency

MongoDB is used appropriately for identity and application records: nine
collections, immutable document records, idempotent indexes, hashed tokens and
TTL cleanup. `BigDecimal` is stored as a string representation, avoiding binary
floating-point loss.

Strengths:

- Unique indexes enforce email, refresh-token hash, watchlist owner+name and trade
  key.
- Account scoping is applied in repositories/services for user-owned data.
- Portfolio snapshots provide a durable reporting fallback.
- Database timeouts are bounded; outage becomes a typed 503.

Risks:

- **No multi-document transactions.** The order dual write is the clearest case.
  Watchlist item updates also read-modify-write an embedded array without an
  optimistic version, so concurrent edits can lose one item.
- **Application settings are not enforced** (section 15).
- **`historyRetentionDays` is not applied.** Orders/trades are never pruned by the
  configured value.
- **Portfolio snapshots are global**, not owner-scoped, mirroring the portfolio
  visibility issue.
- **No migration framework.** Additive index creation is not a schema migration
  strategy.
- **No backup restore test** in the suite.
- Integration tests use `mongo-java-server`, which is valuable but not a full
  MongoDB/Atlas fidelity test (transactions, TTL behavior, index semantics).

**Assessment: adequate persistence for the current single-account model; needs
transaction/reconciliation design for live trading.**

## 18. Android Client Architecture

The client is a clean Compose application with Hilt, Retrofit, repository
interfaces, ViewModels and StateFlow. Screens are stateless composables with
route wrappers, which makes UI tests possible without Hilt.

Strengths:

- Dark-only Material 3 theme with a coherent visual language.
- Fifteen screens cover every required flow.
- All data comes from the backend; no calculations or recommendations in Kotlin.
- Bearer interceptor and single-flight refresh authenticator handle token expiry.
- Envelopes and structured errors are translated into one result type.
- Loading, empty, error and content states exist on data screens.
- API base URL is build-configurable; local cleartext is restricted.
- Unit tests cover API contract and key view models; Compose tests compile.

Weaknesses:

- **Instrumented UI tests were not executed.** No emulator/system image was
  available; compiling them is not the same as running them.
- **Token storage is not Keystore-backed.**
- **No offline cache, pagination beyond "next page", pull-to-refresh gesture,
  push notifications or background sync.** Data is fetched on screen entry and
  explicit refresh.
- **No certificate pinning or network security beyond HTTPS/cleartext policy.**
- **Option chain screen shows assessment analytics, not a strike ladder.** The
  label should be clarified or a full-chain endpoint added.
- **Coil is included but unused** because no image URLs exist in the contract.
  This is harmless but should be removed if it remains unused.
- **No accessibility or screen-size audit was performed.**
- **No localization**; all copy is English.
- **Session expiry** is handled reactively on a 401; the UI does not proactively
  navigate to login when a refresh fails unless the next request fails.
- **No deep links or notification navigation** beyond opening MainActivity.

**Assessment: good client foundation; not yet field-hardened.**

## 19. Testing and Verification

Backend:

- 1,072 tests, 0 failures, 2 skipped. 166 Phase 12 tests: 108 unit, 40 controller,
  18 integration.
- Integration tests exercise the real Spring security chain, JWT handling,
  repositories and Mongo-compatible persistence.
- Controller tests isolate transport concerns with standalone MockMvc and an
  authenticated-principal resolver.
- Repository tests use an in-process Mongo-compatible server, avoiding Docker and
  Atlas credentials.

Android:

- 21 JVM tests: API envelopes/endpoints, login/register, dashboard, orders,
  watchlist, settings.
- Three Compose UI tests compile.
- `compileDebugKotlin`, `testDebugUnitTest` and
  `compileDebugAndroidTestKotlin` pass.

Weaknesses:

- **No live Atlas test.** Connection-string format, Atlas network rules, TTL and
  index behavior on Atlas are unverified.
- **No broker/live execution test** in this phase.
- **No load, soak, concurrency or failure-injection tests.** The dual-write and
  cancellation race are not covered.
- **No Android instrumented execution**, no screenshot/visual regression, no
  accessibility tests.
- **No contract test against a running backend**; MockWebServer verifies the
  client's expectations, not the server's actual current output.
- Two skipped backend tests are pre-existing/conditional; they should be named in
  CI output so they do not hide a regression.

**Assessment: strong unit/integration coverage for logic; weak operational and
device coverage.**

## 20. Production Readiness, Risks and Recommendations

### Readiness by dimension

| Dimension | Rating | Reason |
|---|---|---|
| Domain correctness | Strong | Immutable models, explicit states, extensive unit tests |
| API design | Good | Versioned, enveloped, documented, bounded pages |
| Authentication | Good | JWT, BCrypt 12, hashed rotating refresh tokens |
| Account security lifecycle | Weak | No MFA, reset, verification, lockout, rate limits |
| Multi-tenancy | Weak | Global portfolio exposed to every user |
| Trading consistency | High risk | Dual write, no idempotency, no reconciliation |
| Persistence | Adequate | Good records/indexes, no transactions/migrations |
| Observability | Adequate | Health/metrics/ECS logs; no tracing/alerting/audit |
| Android | Good foundation | Compiles/tests, but no device run or offline/push |
| Deployment | Documented, unexecuted | Render/Atlas not contacted; no production verification |

### Prioritized recommendations

**Before any real-money, unattended, or multi-user deployment**

1. Add an order idempotency key and persist the submission intent before calling
   the broker (outbox/state machine). On startup, reconcile pending intents and
   broker positions.
2. Make portfolio and portfolio-snapshot access `ADMIN`-only, or introduce an
   explicit account/tenant model and scope every query to it.
3. Enforce or remove `maintenanceMode`, `maximumWatchlistItems` and
   `historyRetentionDays`; do not expose controls that do nothing.
4. Add broker-position reconciliation and a documented restart procedure before
   the engine is allowed to resume trading.

**Before public exposure**

5. Rate-limit auth and order routes; add login lockout/backoff.
6. Add password reset and email verification (or explicitly scope the service to
   invited users).
7. Add an audit trail for admin actions, role changes and order/cancel requests.
8. Add request-level tracing and alerting on database/broker failures.

**Engineering quality**

9. Add Testcontainers/Atlas-compatible integration tests for indexes and TTL.
10. Add concurrency tests for watchlist item updates and cancellation races.
11. Run Android instrumented tests on a pinned emulator image in CI; add a
    screenshot/smoke lane.
12. Clarify the option-chain screen or expose a full strike ladder.
13. Remove unused Coil or use it for a real image surface.
14. Document the API error/idempotency contract and a deprecation policy.

### Release gate

The implementation is suitable for a **single trusted operator, supervised
paper/small-capital use**, with broker credentials guarded and the engine
portfolio treated as the sole account. It is **not approved** for public
multi-user or unattended live trading until findings 1–4 are resolved and
verified in an environment connected to the real Atlas cluster and broker.

No code was changed during this review. The findings above are architectural
observations and recommendations for a later phase.
