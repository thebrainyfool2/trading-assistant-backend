# Phase 13 — Live Data Foundation

Status: **implemented and locally verified; not deployed.**

Phase 13 makes the read-only live stream watchlist-driven and decision-ready. It
does not add recommendations, AI, scoring, automatic execution or an order
endpoint. The human user still executes every trade manually.

## Architecture

```text
configured instruments ─┐
                        ├─> LiveSubscriptionPlanner ─> desired InstrumentKey set
watchlists ─────────────┘             │
                                      ▼
                         LiveMarketRunner reconcile
                         │       │          │
                         │       │          └─> seed history for new instruments
                         │       └─> subscribe/unsubscribe pipeline channels
                         └─> open/close one SmartAPI feed when the set changes

SmartAPI feed ─> MarketProcessingPipeline ─> 16 indicators ─> existing engines
       │
       └─> MarketDataFreshnessService ─> GET /api/v1/market/freshness
```

The new foundation sits around the existing engines rather than replacing or
flattening them. `MarketProcessingPipeline`, the indicator registry, the
intelligence/options/strategy/qualification/risk engines, their immutable domain
models and their event contracts are unchanged.

### Subscription flow

1. `LiveSubscriptionPlanner` starts with `app.live.instruments`.
2. It resolves each entry through `LiveInstrumentResolver`.
3. It appends every item from every stored watchlist.
4. It rejects segments without a documented feed: `NSE_CASH`, `NSE_FO`,
   `BSE_CASH`, `BSE_FO` and `MCX_FO` are supported; CDS/NCDEX are reported and
   skipped.
5. It collapses duplicate segment/token pairs and deterministically caps the set
   at `app.live.max-instruments` (default 200).

Configured instruments are ordered before watchlist items; watchlist items keep
their stored order. The planner returns explicit warnings for unresolved
aliases, unsupported segments and truncation.

### Alias resolution

`LiveInstrumentResolver` accepts `SEGMENT:TOKEN_OR_SYMBOL`.

- A numeric token is passed through without loading the instrument master.
- Cash aliases match symbol, trading symbol or display name and prefer the index
  over an equity.
- Derivative aliases select the nearest non-expired future. Options are excluded
  so an alias cannot silently subscribe to an arbitrary strike.
- The instrument master is cached for `app.live.alias-cache-ttl` (default 10m).
  A failed load is cached briefly as empty so a broker outage does not hammer the
  endpoint on every reconciliation.

Configured examples:

```text
NSE_CASH:2885
NSE_CASH:NIFTY
NSE_CASH:BANKNIFTY
NSE_FO:NIFTY
```

### Supervisor lifecycle

`LiveMarketRunner` reconciles on application readiness, on every
`WatchlistChangedEvent`, and on a periodic safety-net interval. It diffs the
desired set against the current set:

- New instruments are subscribed and seeded with `app.live.history-days` of
  candles.
- Removed instruments are unsubscribed and their strategy, intelligence and
  options state is removed.
- The feed is closed and reopened only when the set changes or the existing feed
  is no longer open. Phase 13 deliberately uses reconnect rather than dynamic
  WebSocket subscribe/unsubscribe.
- A monotonically increasing feed generation ignores callbacks from a feed that
  has already been replaced.
- Shutdown closes the feed, expires sessions through the existing lifecycle
  manager and stops the supervisor executor.

`WatchlistService` publishes `WatchlistChangedEvent` only after successful
create, rename, delete, add-item and remove-item persistence. Failed mutations do
not trigger reconciliation.

### Indicators

`LiveIndicators` explicitly lists all sixteen indicators used by the decision
engines:

`SMA`, `EMA`, `WMA`, `RSI`, `MACD`, `STOCH_RSI`, `ATR`, `BOLLINGER`, `KELTNER`,
`DONCHIAN`, `ADX`, `DMI`, `VWAP`, `OBV`, `CMF`, `SUPERTREND`.

The list is explicit rather than derived from the registry so a future registry
addition does not silently change live subscriptions. A test compares it with
`IndicatorEngine.standard()`.

### Calendar and freshness

`MarketCalendarRegistry` now exposes `withHolidays(exchange, dates)` and
`parseHolidays(csv)`. `PipelineConfiguration` wires three configured lists:

```text
NSE_HOLIDAYS=2026-01-26,2026-03-04
BSE_HOLIDAYS=2026-01-26,2026-03-04
MCX_HOLIDAYS=2026-01-26
```

Blank input is an empty holiday set. A malformed date fails startup explicitly
rather than silently treating a holiday as a trading day. Special sessions
continue to override holidays, matching the existing `WeeklyMarketCalendar`
semantics. No holiday list is hardcoded or fetched automatically.

`MarketDataFreshnessService` listens to `TickReceivedEvent` and
`CandleCompletedEvent` and keeps only the latest observation per instrument:

- `FRESH`: both present timestamps are within policy, or the only present
  timestamp is within its policy.
- `STALE`: a present timestamp exceeds its configured maximum age.
- `UNKNOWN`: no tick or candle has been observed.

It also reports the calendar session state (`OPEN`, `CLOSED`, `UNKNOWN`) for the
instrument's exchange. Policies are:

```text
app.live.freshness.max-tick-age=2m
app.live.freshness.max-candle-age=10m
```

`GET /api/v1/market/freshness` projects only instrument, status, session, ages,
timestamps, interval and latest price. Thresholds are not exposed.

### Live option-chain staging

SmartAPI has no option-chain endpoint. `LiveOptionChainService` therefore builds
the read-only chain from the instrument master and quote API for every monitored
cash/index instrument:

- the nearest non-expired expiry is selected;
- the strikes closest to spot are retained;
- CE/PE quotes are fetched in batches of at most 50 with `FULL` mode so open
  interest, volume and depth are available to the existing analytics;
- the contracts, quotes and spot price are handed to
  `InMemoryOptionChainProvider`, which the existing options engine already
  consumes.

It never recalculates analytics and has no order path. Defaults:

```text
app.live.option-chain.enabled=true
app.live.option-chain.strikes-each-side=10
app.live.option-chain.refresh-interval-ms=30000
app.live.option-chain.quote-batch-size=50
```

Refresh is throttled independently of the 15-second live reconciliation loop.
The instrument master is cached for ten minutes. A missing chain is skipped; it
is never fabricated.

`AngelOneHttpClient` also treats a plain-text 403 containing “access denied
because of exceeding access rate” (or equivalent rate-limit wording) as a
retryable rate limit instead of an authentication failure. Historical warm-up
uses a slower bounded retry policy so market-hours throttling does not discard
the whole seed.

## Domain model

No existing immutable engine model was changed.

| Type | Kind | Purpose |
|---|---|---|
| `LiveIndicators` | static utility | Explicit 16-indicator decision set |
| `LiveInstrumentSpec` | immutable record | Parsed `SEGMENT:REFERENCE` |
| `LiveInstrumentResolver` | service | Numeric passthrough and alias/future resolution |
| `LiveSubscriptionPlanner.Plan` | immutable record | Ordered instruments plus warnings |
| `WatchlistChangedEvent` | immutable event | Successful watchlist mutation signal |
| `MarketDataFreshnessService.Freshness` | immutable record | One instrument's operational freshness |
| `LiveOptionChainService` | service | Read-only nearest-expiry option-chain staging |

## Validation

- Invalid segment/reference syntax is rejected by `LiveInstrumentSpec`.
- Unresolved aliases are skipped with a warning; they do not abort the whole set.
- Options are excluded from live subscription alias resolution. Option chains
  are staged separately by `LiveOptionChainService`.
- Unsupported segments are skipped and reported.
- Duplicate instruments are collapsed.
- The subscription cap is positive and enforced.
- Holiday dates must be ISO `yyyy-MM-dd`; invalid input fails startup.
- Freshness thresholds must be positive durations.
- `WatchlistChangedEvent` requires a non-blank owner.
- No new randomness or wall-clock dependency was added to engine packages.

## Tests

New and extended tests:

- `LiveIndicatorsTest` — all 16 indicators and stable deduplicated order.
- `LiveInstrumentResolverTest` — numeric passthrough, index preference, nearest
  non-expired future, option exclusion, unknown alias.
- `LiveSubscriptionPlannerTest` — configured/watchlist merge, duplicate
  collapse, unsupported segment warning, deterministic cap.
- `LiveMarketRunnerTest` — first reconcile subscribes both sources and opens the
  feed; removing a watchlist item unsubscribes and clears engine state.
- `MarketDataFreshnessServiceTest` — fresh, stale, unknown and closed-session
  outcomes.
- `LiveOptionChainServiceTest` — nearest expiry, closest strikes, quote staging,
  refresh throttling and missing-chain handling.
- `AngelOneHttpClientTest` — plain-text and JSON 403 rate-limit classification
  and bounded retry.
- `MarketCalendarTest` — configured holidays, blank input, invalid date, and
  exchange isolation.
- `WatchlistServiceTest` — events published after successful mutations.
- `MarketFreshnessControllerTest` — API projection and authentication shape.
- `ArchitectureGuardrailTest` — AI/ML markers, engine randomness/wall-clock,
  execution imports in API/live packages, and order/portfolio endpoints.

The full backend suite result: **1,022 tests, 0 failures, 0 errors, 2 skipped**. The two skipped tests are the opt-in SmartAPI live integration tests; they were not run without broker credentials.

## Performance considerations

- Alias resolution loads the instrument master once per TTL, not once per
  instrument per tick.
- Reconciliation is O(instruments) plus one instrument-master lookup; the
  subscription set is capped at 200 by default.
- Freshness storage is O(observed instruments), with only the latest tick/candle
  per instrument and no historical series.
- Freshness snapshot sorting is O(n log n) for at most the subscription cap.
- One feed is open at a time. Set changes pay a reconnect cost, but ticks do not
  pay any subscription-management cost.
- History seeding uses the existing historical REST path per added instrument;
  it is not a bulk download. A large initial watchlist therefore produces
  several sequential REST calls, bounded by the cap.
- Option-chain staging loads the instrument master at most once per ten minutes
  and reuses it for every monitored underlying. Quotes are capped at 50 per
  request and refreshed no more often than the configured interval.

## Limitations

- **No dynamic WebSocket subscription.** Adding/removing an instrument closes and
  reopens the feed. This is deliberate for Phase 13 but can miss ticks during the
  reconnect window.
- **Alias rollover is reconciliation-driven.** A futures alias resolves the
  nearest non-expired contract from the cached master. It does not switch
  contracts at expiry until the cache expires and a reconciliation runs.
- **Holiday lists are operator-supplied.** No holiday data is shipped or fetched.
  An empty list means weekends only, as before.
- **Freshness is in-memory.** It is rebuilt after restart and is not persisted or
  exposed as history.
- **No UI surface yet.** The Android client does not display `/market/freshness`
  in Phase 13; this phase is the backend data foundation only.
- **No live integration test.** The automated tests use mocks and fixtures; the
  opt-in SmartAPI live tests remain skipped without credentials.
- **Option chains are nearest-expiry only.** The live loader stages one expiry
  per underlying to keep quote requests bounded; later expiries are not staged
  by the live path.
- **Dormant execution remains.** Phase 11 code is still present, but no bean,
  controller, live-supervisor call site or broker order adapter wires it in. The
  source guardrails enforce that boundary.

## Database and documentation correction

- The local fallback, environment example, deployment guide, MongoDB guide,
  current READMEs and API reference now use `tradingassistant`.
- The unrelated `boom` database is explicitly excluded.
- The historical architecture review is marked as historical because it
  described a JWT/order/15-screen application that no longer matches the
  single-owner, read-only, seven-screen client.
- The API reference now describes the actual single-owner endpoints and states
  that no order, portfolio, position, holding or funds route exists.

## Architecture guardrails

`ArchitectureGuardrailTest` fails the build when:

- an AI/ML marker or dependency appears in `pom.xml` or main source;
- `java.util.Random`, `ThreadLocalRandom`, `Math.random`, `Instant.now`,
  `LocalDate.now`, `LocalDateTime.now` or `System.currentTimeMillis` appears in
  an engine package;
- `platform/api` imports an execution type or exposes an order/portfolio/position
  route;
- `platform/live` depends on execution, `BrokerAdapter`, `ExecutionService` or
  `ExecutionEngine`;
- `platform/config` wires an execution bean.

This is a source-level guardrail, not a full architectural proof. It is
intentionally narrow and reviewable.

## Verification

Commands used:

```bash
cd backend
mvn -B -ntp test
```

`mvn -B -ntp test` ran **1,022 tests with 0 failures, 0 errors and 2 skipped**
(BUILD SUCCESS). The two skipped tests are the opt-in SmartAPI live integration
tests; they were not run without broker credentials. The test run used fakes and
fixtures, not a live market session.
