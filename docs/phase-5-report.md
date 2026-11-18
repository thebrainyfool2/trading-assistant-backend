# Phase 5 — Market Processing Infrastructure

**Status: implemented, tested and documented. Awaiting architectural review.**
No Market Intelligence, strategy, signal, risk, option, Greek, persistence, database, notification, backtesting, trade-monitoring, AI or machine-learning functionality was started.

---

## 1. Executive Summary

Phases 2–4 delivered a broker adapter, a broker-independent market domain and a reusable technical-indicator engine. Each was a library: nothing owned the long-lived runtime state required to keep processing a live feed, and nothing connected ticks to candles to indicator instances.

Phase 5 adds that missing runtime layer without adding any interpretation of the market:

```text
Tick
 │
 ▼
CandleAggregator            (session-anchored OHLCV)
 │
 ▼
Completed Candle
 │
 ▼
IndicatorSessionRegistry    (one long-lived indicator instance per key)
 │
 ▼
Updated Indicator Results
 │
 ▼
MarketEventBus              (immutable domain events)
```

Six objectives were delivered:

1. an **indicator session registry** keyed by instrument + interval + configuration;
2. **tick-to-candle aggregation** for one through sixty minutes and one day;
3. a **market processing pipeline** that runs the tick → candle → session → event flow;
4. **immutable event contracts** carrying only domain objects;
5. **session lifecycle management** (start, drain, idle sweep, shutdown, recovery hook);
6. a **market calendar abstraction** with trading day, trading time, current session and next-session discovery.

The pipeline observes and publishes. It does not score, rank, compare, interpret or recommend, and it never places, modifies or cancels an order. The human user continues to execute every trade manually.

Verification: `mvn -B -ntp verify` → **212 tests, 0 failures, 0 errors, 2 skipped, BUILD SUCCESS**, of which **71 tests are new in Phase 5**. The two skipped tests are the opt-in live broker tests from Phases 2 and 3.

---

## 2. Scope and Boundaries

Implemented under `com.tradingassistant.pipeline` (45 main classes, 9 test classes).

Explicitly **not** implemented: Market Intelligence, strategies, signals, market regimes, scoring, risk, position sizing, options, Greeks, option-chain analysis, persistence, database, cache, scheduler, notifications, backtesting, paper trading, trade monitoring, AI or ML. `grep` over the new package finds no broker import and no strategy, risk, Greek or signal type; the word "strategy" appears only in the javadoc for the alignment *mechanism*.

Architectural constraints preserved from Phases 1–4:

- the broker adapter remains the only place a SmartAPI DTO exists;
- domain timestamps remain UTC `Instant`s, with `Asia/Kolkata` centralized in `MarketStatusResolver.MARKET_ZONE`;
- prices remain `BigDecimal`;
- every value crossing a boundary is immutable;
- no previously approved class was modified to make this phase work.

The pipeline is wired only when `app.broker.angel-one.enabled=true`, matching Phase 3: without a feed there is nothing to process, and the Phase 1 endpoints must still start with an empty configuration.

---

## 3. Infrastructure Architecture

```text
                 ┌──────────────────────────────┐
   Tick ────────▶│ MarketProcessingPipeline      │
                 │  per-instrument channel group │
                 └───────────┬──────────────────┘
                             │ 1. TickReceivedEvent
                             ▼
                 ┌──────────────────────────────┐
                 │ CandleAggregator              │  one per (instrument, interval)
                 │  CandleBucketAligner          │  session-anchored / market-date grid
                 └───────────┬──────────────────┘
                             │ 2. CandleCompletedEvent
                             ▼
                 ┌──────────────────────────────┐
                 │ IndicatorSessionRegistry      │
                 │  IndicatorSession             │  one Indicator instance per type
                 └───────────┬──────────────────┘
                             │ 3. SessionStartedEvent (first candle only)
                             │ 4. IndicatorUpdatedEvent
                             ▼
                 ┌──────────────────────────────┐
                 │ MarketEventBus                │  synchronous, ordered delivery
                 └──────────────────────────────┘

   MarketCalendarRegistry ──▶ MarketCalendar ──▶ TradingSession
         (per exchange)         (trading days, holidays, special sessions)

   MarketSessionLifecycleManager ──▶ start / drain / sweep / shutdown / snapshot
```

Package layout:

| Package | Contents |
|---|---|
| `pipeline` | `MarketProcessingPipeline`, `PipelineSubscription`, `PipelineOutcome`, `MarketSessionLifecycleManager`, `PipelineConfiguration` |
| `pipeline.aggregation` | `CandleAggregator`, `AggregationPolicy`, `AggregationResult`, `AggregationStatus`, bucket aligners |
| `pipeline.calendar` | `MarketCalendar`, `WeeklyMarketCalendar`, `TradingSession`, `SessionWindow`, `MarketCalendarRegistry` |
| `pipeline.event` | sealed `MarketEvent` and five records, `MarketEventBus`, publisher and listener contracts, session-event adapter |
| `pipeline.session` | `IndicatorSession`, `IndicatorSessionKey`, `IndicatorSessionRegistry`, `IndicatorSessionSnapshot`, `SessionRegistryPolicy`, lifecycle listener |
| `pipeline.exception` | safe pipeline, session and delivery failures |

Dependency direction is one-way: `event` and `pipeline` depend on `session`, `aggregation` and `calendar`; `session` depends on the Phase 4 indicator engine; `aggregation` and `calendar` depend only on the Phase 3 domain. `session` does not depend on `event`, which is why the registry reports lifecycle transitions through a listener instead of publishing events itself.

---

## 4. Session Registry Design

### 4.1 Identity

`IndicatorSessionKey(instrument, interval, indicatorConfig)`.

Configuration values are canonicalized (`2` and `2.00` address the same session) because otherwise two callers supplying the same deviation with different scales would silently create two sessions for one candle stream — and each would see only half the candles.

### 4.2 Session

One `IndicatorSession` owns one `Indicator` instance per configured type. Indicators are stateful, so instances are never shared: two instruments, or two timeframes of one instrument, cannot corrupt each other's recursive state.

`update(candle, accessedAt)` validates that the candle belongs to the session's instrument and interval, advances every indicator, and returns an immutable `IndicatorSessionUpdate` with a named result map. A candle from another instrument or interval is a programming error and is rejected, not absorbed.

`reset(accessedAt)` clears indicator state while keeping the session registered.

### 4.3 Registry responsibilities

| Operation | Behaviour |
|---|---|
| `getOrCreate(key, types)` | Lazy creation, atomic per key; concurrent callers observe the same instance |
| `find(key)` | Read-only lookup; deliberately **does not** extend the idle deadline |
| `require(key)` | Lookup that fails fast with `SessionNotFoundException` |
| `reset(key)` | Clears recursive state, keeps registration |
| `remove(key, reason)` | Terminal expiry and unregistration |
| `expireIdle()` | Expires sessions untouched beyond the policy timeout |
| `expireAll(reason)` | Shutdown sweep |
| `snapshots()` / `restore(snapshot)` | Recovery hook |
| `requireTypes(types)` | Validates indicator types against the engine before a session exists |

Sessions are created lazily on the first completed candle, so an instrument that never trades never allocates indicator state.

### 4.4 Lifecycle and policy

`SessionRegistryPolicy(idleTimeout, maxSessions)` defaults to **30 idle minutes** and **2 000 sessions**, which covers roughly a thousand instruments on two timeframes with headroom.

- `idleTimeout = ZERO` disables idle expiry.
- `maxSessions` is a guardrail, not an eviction policy: exceeding it raises `SessionLimitExceededException` rather than silently dropping another instrument's state.
- Expiry re-checks idleness under the session lock, so a sweep can never kill a session that just received a candle. A session that expires concurrently with an update fails that update with `SessionExpiredException` instead of resurrecting a dead instance.
- Expiry is terminal: `ACTIVE → EXPIRED`, never back.

### 4.5 Recovery hook

`IndicatorSessionSnapshot` records key, indicator types, creation and update times, candle count and state. It deliberately carries **no indicator arithmetic state**: restoring recreates the binding, and a candle replay rebuilds the recursive state. Persisting a serialization format that no approved phase has specified would have been a premature architectural decision.

---

## 5. Aggregation Design

### 5.1 Alignment

A bucket grid must be explicit, because the session open is not a wall-clock multiple: `09:15` IST is not aligned to any UTC or even IST hour boundary. Three strategies implement `CandleBucketAligner`:

| Aligner | Used for | Rule |
|---|---|---|
| `TradingSessionAligner` | 1/3/5/10/15/30 min, 1 hour | Floor from the trading day's session open; session-aware |
| `MarketDateAligner` | 1 day | Midnight in `Asia/Kolkata`, matching provider daily candles |
| `FixedStepAligner` | Explicit anchors (backfill) | Floor from a supplied anchor instant |

`CandleBucketAligners.forInterval(interval, calendar)` chooses between them, so callers cannot accidentally pick a UTC-midnight grid.

Consequences, both intentional:

- a five-minute series starts at `09:15`, not `09:10`;
- a one-hour series produces `09:15`, `10:15`, …, with a final partial bucket before the close;
- a daily candle timestamp matches the provider's daily convention, so live and historical daily candles describe the same date.

### 5.2 OHLCV

| Field | Source |
|---|---|
| Open | First accepted tick of the bucket |
| High / Low | Extremes of the accepted prices in arrival order |
| Close | Last accepted tick of the bucket |
| Volume | Sum of each accepted tick's last traded quantity |
| Timestamp | Bucket start |

OHLC follows **arrival order**, which is the only ordering a live system observes. Volume accumulates `lastTradedQuantity`; ticks without a quantity contribute zero, and rejected ticks never contribute.

### 5.3 Rejection statuses

Every offered tick yields exactly one status:

| Status | Meaning | Policy |
|---|---|---|
| `ACCEPTED` | Merged into the forming candle | — |
| `IGNORED_WRONG_INSTRUMENT` | Belongs to another instrument | One aggregator owns one instrument |
| `IGNORED_MISSING_PRICE` | No last traded price | No OHLCV can be derived |
| `IGNORED_SEQUENCE` | Sequence not greater than the last accepted | Duplicate / out-of-order protection |
| `IGNORED_LATE_TIMESTAMP` | Bucket earlier than the forming candle | Closed candles are never restated |
| `IGNORED_FUTURE_TIMESTAMP` | Beyond `futureSkewTolerance` (default 5 s) | Protects the forming candle from clock skew |
| `IGNORED_STALE_TIMESTAMP` | Older than `staleTolerance` | **Opt-in**; disabled by default so replay uses the live code path |
| `IGNORED_OUTSIDE_SESSION` | Outside the calendar session | Pre-open snapshots and after-hours prints cannot extend the day's candles |

A feed whose sequence numbers are always `0` (some providers) cannot be deduplicated here; the aggregator documents that limitation and relies on arrival ordering instead of guessing.

`flush()` closes the forming candle (session end, shutdown). `reset()` discards state **and** the sequence watermark, which a reconnecting feed must call so a restarted provider sequence does not look duplicate.

Empty intervals produce **no** candle: when a tick arrives in a later bucket, the forming candle closes and the new bucket opens. No synthetic candles are ever generated, so indicators see the genuine gaps that `CandleSeriesValidator.findGaps` reports.

---

## 6. Pipeline Design

`MarketProcessingPipeline` owns:

- one `CandleAggregator` per subscribed (instrument, interval);
- the indicator-session key and indicator types for each channel;
- one channel group per instrument, which is also the monitor that serializes that instrument's processing.

### 6.1 Event order (identical for live and replay)

1. `TickReceivedEvent` — the raw observation;
2. `SessionStartedEvent` — only when this candle creates the session;
3. `CandleCompletedEvent` — the closed candle;
4. `IndicatorUpdatedEvent` — after every indicator advanced.

### 6.2 Operations

| Operation | Behaviour |
|---|---|
| `subscribe(PipelineSubscription)` | Validates indicator types and resolves the exchange calendar; idempotent for an existing session key so duplicate wiring cannot double-count volume |
| `unsubscribe(instrument)` | Removes channels and discards forming candles |
| `process(tick)` | Runs the full flow and returns `PipelineOutcome` counters |
| `flush(instrument)` / `flushAll()` | Publishes forming candles as completed candles |
| `close()` | Flushes and stops accepting ticks |
| `aggregator(instrument, interval)` | Diagnostic access to one aggregator |

Ticks for an unsubscribed instrument publish only `TickReceivedEvent`, keeping the raw feed observable without inventing state.

The pipeline never expires sessions: shutdown ordering belongs to `MarketSessionLifecycleManager`, so a candle cannot be lost between "stop processing" and "release state".

### 6.3 Session lifecycle manager

`MarketSessionLifecycleManager` is **synchronous and thread-free**. No scheduler was approved, so it exposes `start()`, `sweep()`, `drain()`, `closeSession(key)`, `shutdown()`, `snapshots()` and `restore(snapshots)`. A future phase's scheduler simply calls `sweep()` at its own cadence. Shutdown order is fixed and testable: flush forming candles, then expire every session.

---

## 7. Event Model

`MarketEvent` is a sealed interface with five records:

| Event | Payload |
|---|---|
| `TickReceivedEvent` | `Tick`, `occurredAt` |
| `CandleCompletedEvent` | `Candle`, `occurredAt` |
| `IndicatorUpdatedEvent` | session key, `Candle`, indicator type → `IndicatorResult` map, `occurredAt` |
| `SessionStartedEvent` | session key, indicator types, `occurredAt` |
| `SessionExpiredEvent` | session key, reason, `occurredAt` |

All payloads are domain objects; no broker DTO, provider payload, HTTP detail or mutable collection crosses the boundary. Maps are defensively copied to immutable copies, and `occurredAt` is application time, distinct from the market timestamp inside the payload.

`MarketEventBus` is a synchronous `CopyOnWriteArrayList` dispatcher:

- every active subscriber receives the event, in subscription order;
- one failing subscriber never prevents delivery to the others;
- the first failure is rethrown as `EventDeliveryException` with the others suppressed, so a broken consumer is visible rather than swallowed;
- a `Subscription` handle unsubscribes itself and is idempotent;
- publishing or subscribing after `close()` is a programming error.

`MarketEventPublisher.discarding()` supports wiring a pipeline with no consumers.

---

## 8. Calendar Design

`MarketCalendar` exposes exactly what later engines need:

```java
String code();
ZoneId zone();
SessionWindow regularWindow();
boolean isTradingDay(LocalDate date);
boolean isTradingTime(Instant instant);
Optional<TradingSession> sessionOn(LocalDate date);
Optional<TradingSession> currentSession(Instant instant);
Optional<TradingSession> nextTradingSession(Instant instant);
LocalDate marketDate(Instant instant);
```

`WeeklyMarketCalendar` is data-driven. Trading weekdays, holidays and special sessions are constructor data, not `if (day == SATURDAY)` statements, so a holiday feed or a Muhurat session changes configuration rather than code. Precedence is explicit:

1. **special session** for the date (Muhurat trading, half day);
2. **holiday** (no session even on a weekday);
3. **regular window** on a configured trading weekday.

Defaults in `MarketCalendarRegistry`:

| Exchange | Window (IST) |
|---|---|
| NSE / BSE | 09:15 – 15:30 |
| MCX | 09:00 – 23:30 |
| CDS / NCDEX | 09:00 – 17:00 |

Sessions are **half-open** (`open` inclusive, `close` exclusive), so `15:30:00` is outside the session and a tick exactly at the open belongs to it. The post-close auction window that Phase 3's `MarketStatusResolver` reports is deliberately *not* part of the trading session; a feed that must capture auction prints subscribes with a calendar whose window includes them.

`nextTradingSession` searches a bounded number of days (366 by default), so a misconfigured calendar returns empty deterministically instead of looping. No holiday list is hardcoded: Phase 5 ships empty holiday data and an extension method, because inventing an exchange holiday calendar without an approved data source would be guesswork.

---

## 9. Validation Strategy

| Condition | Behaviour |
|---|---|
| Null tick / event / subscription | `PipelineValidationException` with a fixed message |
| Duplicate tick | Sequence guard → `IGNORED_SEQUENCE`; sequence `0` falls back to arrival order |
| Out-of-order tick | Rejected by the sequence guard or as `IGNORED_LATE_TIMESTAMP` |
| Late tick | Never restates a closed candle; the forming candle is unchanged |
| Future timestamp | Beyond the skew tolerance → `IGNORED_FUTURE_TIMESTAMP` |
| Clock drift | Bounded on both sides: skew tolerance plus opt-in staleness, evaluated against the injected `Clock` |
| Missing candles / empty intervals | No synthetic candle; the next tick closes the forming one |
| Outside session | `IGNORED_OUTSIDE_SESSION` (session-aware alignment only) |
| Unknown indicator type | Rejected before any session exists |
| Empty indicator set | Rejected by both `PipelineSubscription` and the registry |
| Invalid interval | Impossible by construction: `Interval` is an enum and the aggregator rejects null |
| Expired session used | `SessionExpiredException`; the instance cannot be revived |
| Session limit exceeded | `SessionLimitExceededException`; no silent eviction |
| Wrong candle for a session | `PipelineValidationException` (instrument or interval mismatch) |
| Unknown session | `SessionNotFoundException` |

Every rule is deterministic: the same tick, state and clock produce the same status and the same message.

---

## 10. Concurrency Strategy

| Structure | Mechanism | Why |
|---|---|---|
| Per-instrument processing | Monitor on the immutable `ChannelGroup` | A candle and its indicator update cannot interleave with another tick's, so published candle timestamps stay strictly ascending |
| One aggregator | `synchronized` methods | Feed thread and shutdown thread can both touch it safely |
| One session | `synchronized` mutation, volatile lifecycle fields | Indicator recursion is never observed half-applied |
| Registry | `ConcurrentHashMap` + atomic `computeIfAbsent`, compare-and-remove expiry | Concurrent creation yields one instance; a lost expiry race cannot remove a live session |
| Session limit | Approximate `mappingCount` guardrail inside creation | Documented as a guardrail, not a hard real-time bound |
| Event bus | `CopyOnWriteArrayList` | Subscriptions change rarely; publication is lock-free |
| Cross-thread values | Immutable records and defensive copies | No shared mutable payload leaves a stage |
| Registration | Single registration lock, atomic map replacement | A tick in flight keeps the channel group it started with |

Instruments are independent, so a future executor can process different instruments in parallel without touching this design; the concurrency tests already exercise four instruments in parallel.

---

## 11. Performance Notes

- Aggregation allocates nothing per tick beyond the returned record; the forming candle is a single mutable bucket.
- Each completed candle allocates one `Candle`, one event, one result map and one `IndicatorResult` per indicator — proportional to configured indicators, not to tick count.
- Sessions are created lazily, so idle instruments hold no state.
- Replay and live input use the same path; there is no separate "fast path" that could diverge.
- The 3 000-tick, 10-instrument replay test runs well inside its 20-second bound; the full suite runs in roughly 15 seconds on the development machine.
- Actual throughput targets are deliberately not asserted: Phase 5 has no approved latency budget, and a fabricated threshold would be a made-up requirement.

---

## 12. Testing Summary

| Test class | Tests | Coverage |
|---|---|---|
| `CandleAggregatorTest` | 14 | OHLCV, every interval's grid, daily rollover, duplicate/out-of-order sequence, late ticks, future and stale tolerances, wrong instrument, missing price, empty intervals, session gating, reset, sequence-less feeds |
| `MarketCalendarTest` | 12 | Weekday sessions, half-open window, weekends, holidays, Muhurat special sessions, `isTradingTime`, `currentSession`, `nextTradingSession` across weekends and holidays, bounded lookahead, market-date boundaries, exchange defaults, custom calendar registration, invalid value objects |
| `IndicatorSessionRegistryTest` | 15 | Creation and reuse, key identity, configuration canonicalization, type validation, warm-up advancement, reset, idle expiry, access semantics, disabled expiry, session limit, listener notifications, snapshots/restore, stream mismatch, unknown keys, concurrent creation |
| `MarketEventBusTest` | 8 | Ordered delivery, unsubscription, failure isolation, retained suppressed failures, post-close rejection, null rejection, immutability and discriminators, lifecycle adapter |
| `MarketProcessingPipelineTest` | 12 | Documented event order, indicator values from aggregated candles, multiple intervals, unsubscribed ticks, idempotent subscription, invalid subscriptions, flush, unsubscribe, close, instrument isolation, 3 000-tick replay, null rejection |
| `MarketProcessingPipelineConcurrencyTest` | 2 | Four instruments in parallel without losing candles; one instrument under concurrent ticks stays consistent and ordered |
| `MarketSessionLifecycleManagerTest` | 5 | Start, drain, sweep, shutdown ordering, close-session reason, snapshots/restore, idempotent close |
| `PipelineConfigurationTest` | 3 | Absent beans when the broker is disabled, complete wiring when enabled, empty initial state |

All 71 Phase 5 tests pass. The complete suite is **212 tests, 0 failures, 0 errors, 2 skipped**.

---

## 13. Problems Encountered

| Problem | Resolution |
|---|---|
| Post-close ticks were initially clamped into the last session bucket, which meant an entire evening of prints extended the 15:25 candle | Replaced clamping with an `isTradable` gate on the aligner: out-of-session ticks are rejected as `IGNORED_OUTSIDE_SESSION`. The initial design was wrong, not the calendar. |
| A concurrency test exposed out-of-order indicator updates: the aggregator lock covered rollover, but candle publication and session updates ran outside it | Added a per-instrument `ChannelGroup` monitor covering tick event, aggregation, candle publication and session update, so published candles are strictly ascending |
| Bucket alignment for intraday candles cannot be a wall-clock multiple, because the NSE open at 09:15 is not aligned to any minute-of-hour grid | Introduced explicit alignment strategies; daily candles use market-date midnight so they match provider history |
| `2` and `2.00` produced two sessions for one candle stream | Canonicalized configuration values inside `IndicatorSessionKey` |
| Idle expiry could race with an incoming candle and resurrect a dead session | Expiry re-checks idleness under the session lock; a concurrent update fails deterministically instead of reviving the instance |
| A record compact constructor assigned `this.indicatorTypes`, which does not compile for records | Assigned the constructor parameter instead |
| `AggregationResult` referenced `Candle` without the import | Added the import; caught by the first compile |
| Tests initially used `withHolidays` on the `MarketCalendar` interface, which is intentionally implementation-specific data | Tests use the concrete `WeeklyMarketCalendar` type; the interface stays minimal |
| The "large replay" test ran past the session close, so gating reduced the expected candle count | Test data kept inside the session; the mismatch is exactly what the session gate is supposed to cause |

---

## 14. Known Limitations

- **Holiday data is not bundled.** Calendars ship weekday rules and an empty holiday set; an approved holiday source would populate it.
- **No scheduler.** Idle sweeps and shutdown are explicit calls; nothing runs on a timer.
- **Session expiry drops arithmetic state.** Recovery recreates the binding and requires a candle replay; there is no persisted indicator state.
- **Duplicate detection needs sequence numbers.** A feed that always sends `0` cannot be deduplicated at this layer.
- **One aggregator per instrument and interval.** Multi-timeframe aggregation recomputes from the same tick stream; sharing a 1-minute backbone is an optimization for a later phase with measured need.
- **Events are synchronous.** A slow subscriber delays the feed thread; consumers are expected to hand work to their own executor.
- **Pre-open and auction windows are outside the trading session**, so those ticks are rejected unless a calendar includes them.
- **No backpressure policy.** Bounded queues and overflow handling belong to the transport phase that introduces them.

---

## 15. Recommendations for the Next Phase (Market Intelligence)

Do not implement these here:

1. Define the Market Intelligence contract as a pure function of immutable inputs: completed candles, indicator results and calendar session context → immutable assessment.
2. Keep it synchronous and stateless per evaluation so it can be replayed against history without a second code path.
3. Decide explicitly whether intelligence is pull-based (request an assessment) or push-based (subscribe to `IndicatorUpdatedEvent`); the event bus supports the latter without change.
4. Add a deliberate warm-up policy: indicators publish warm-up rows, and intelligence must state how it treats them.
5. Introduce multi-timeframe alignment as an explicit input, not an implicit lookup, once candle aggregation for several intervals is in use.
6. Keep scores and regimes out of the event model until the review approves a contract; Phase 5 events publish observations only.
7. Add a holiday calendar source and a scheduler invocation for `sweep()`/`drain()` when a phase owns operational scheduling.
8. Measure throughput before optimizing: reuse one aggregator across timeframes only if profiling shows the recomputation matters.

---

## 16. Completion Gate

Phase 5 is implemented, tested and documented. The broker adapter, market domain and indicator engine were not modified. No Market Intelligence, strategy, signal, risk, option, Greek, persistence, scheduler, notification, backtesting or trade-monitoring functionality was started, and nothing was pushed or deployed.

**STOP — awaiting architectural review. The Market Intelligence Engine has not been started.**
