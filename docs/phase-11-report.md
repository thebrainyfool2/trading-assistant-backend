# Phase 11 — Order Management, Portfolio & Execution

**Status: implemented, tested and documented. Awaiting architectural review.**
No AI, machine learning, optimization, backtesting, historical optimization, recommendation change or strategy change was started. Phase 11 ends at a working order lifecycle, a portfolio view and an abstracted broker boundary.

---

## 1. Executive Summary

Phase 10 described what a qualified trade may risk: quantity, stop distance, maximum loss. It deliberately produced no direction, no order, no fill and no position.

Phase 11 turns those boundaries into an executable lifecycle **only when a caller explicitly asks for one**:

```text
RiskAssessmentGeneratedEvent ─────┐
TradeQualifiedEvent ──────────────┤
MarketAssessmentGeneratedEvent ───┤   ExecutionService (bounded caches)
StrategyEvaluatedEvent ───────────┤        │
OptionsAssessmentGeneratedEvent ──┘        │  caller supplies OrderIntent (side + type)
                                           ▼
                                   OrderRequestFactory
                                           │
                                           ▼
                              ExecutionEngine — create → validate → submit
                                           │
                              ┌────────────┼─────────────┐
                              ▼            ▼             ▼
                        ExecutionRules  BrokerAdapter  PortfolioService
                              │            │             │
                              └────────────┼─────────────┘
                                           ▼
                     Order*, Position*, PortfolioUpdated, TradeCompleted events
```

The engine creates an order from an intent and a risk assessment, validates it against thirteen deterministic rules, submits it through a broker adapter, interprets the broker report, updates positions and the portfolio, and publishes the resulting events. It never decides that a trade should be taken, never infers a side, never sizes beyond the assessment and never recommends anything.

Verification: `mvn -B -ntp verify` → **905 tests, 0 failures, 0 errors, 2 skipped, BUILD SUCCESS**, of which **228 tests are new in Phase 11**. The two skipped tests are the opt-in live broker tests from Phases 2 and 3, unchanged.

---

## 2. Architecture

`com.tradingassistant.execution` — 49 main classes, 18 test classes and fixtures:

| Package | Contents |
|---|---|
| `execution.api` | `ExecutionRule` contract |
| `execution.domain` | 23 immutable records/enums plus `ExecutionValue` precision helper |
| `execution.order` | `OrderStore`, `OrderStateMachine`, `OrderRequestFactory` |
| `execution.position` | `PositionCalculator` (pure open/add/close arithmetic) |
| `execution.portfolio` | `PortfolioService`, `PortfolioChange` |
| `execution.broker` | `BrokerAdapter`, `BrokerOrderCommand`, `PaperBrokerAdapter`, `BrokerAdapterRegistry` |
| `execution.rule` | `ExecutionRules` — 13 immutable rule records |
| `execution.registry` | `ExecutionRuleRegistry`, `BuiltInExecutionRules` |
| `execution.engine` | `ExecutionEngine` |
| `execution.validation` | `ExecutionValidator` |
| `execution.config` | `ExecutionConfig`, `ExecutionMode` |
| `execution.exception` | `ExecutionException`, `ExecutionValidationException`, `ExecutionBrokerException` |
| `execution.event` | `ExecutionEventSink` |
| `execution.service` | `ExecutionService`, `BoundedCache`, `ExecutionMarketDataProvider` |
| `execution` | `ExecutionConfiguration` Spring wiring |

The 13 output events live in `com.tradingassistant.pipeline.event`, because `MarketEvent` is a sealed interface and its permitted subtypes must share the package.

Dependency direction:

- domain records depend on nothing but the market identity types;
- rules depend on the rule context and the immutable policy only;
- the engine depends on the rule registry, the broker registry, the portfolio, the order store, an event sink and a clock;
- adapters depend on the broker command and report records, and on nothing else;
- the service depends on pipeline events, the engine, the portfolio and a mark-price provider;
- no execution class reads HTTP objects, repositories, strategy internals, indicators or recommendations.

---

## 3. Domain Model

Immutable models, all validated in compact constructors:

| Model | Purpose |
|---|---|
| `OrderIntent` | What a caller wants: side, type, prices, target, time in force. Carries no quantity — the assessment supplies it. |
| `OrderRequest` | Intent plus the assessed quantity and protective stop. |
| `TradeOrder` | Order state machine representation with `withStatus`, `withReason`, `withFill`, `remainingQuantity`. |
| `OrderExecution` | One fill: identity, quantity, price, instant, notional. |
| `ExecutionReport` | Broker view: status, cumulative fill, average price, message, fills, timestamp. |
| `ExecutionRequest` | Order + risk assessment + market assessment of one instant, cross-validated. |
| `ExecutionResult` | Outcome: status, order, position, portfolio, rule evaluations, report, reason, diagnostics. |
| `ExecutionDiagnostics` | Named values, warnings and broker attempts. |
| `Position` | Direction, opened/closed quantity, average entry, stop, target, realised P&L, last price, status. |
| `PortfolioSnapshot` / `PortfolioSummary` | Immutable portfolio view and compact derived summary. |
| `TradeLifecycle` | Order and position facts for a completed or open trade. |

Enumerations:

- `OrderType`: `MARKET`, `LIMIT`, `STOP`, `STOP_LIMIT`.
- `OrderSide`: `BUY`, `SELL`; `PositionDirection`: `LONG`, `SHORT` derived from the side.
- `TimeInForce`: `DAY`, `IOC`, `GTC`.
- `OrderStatus`: `CREATED`, `VALIDATED`, `SUBMITTED`, `PENDING`, `PARTIALLY_FILLED`, `FILLED`, `CANCELLED`, `REJECTED`, `FAILED`, `EXPIRED`, with `active()` and `terminal()` partitioning the ten states.
- `PositionStatus`: `OPENING`, `OPEN`, `PARTIALLY_CLOSED`, `CLOSED`.
- `ExecutionStatus`: `COMPLETED`, `PARTIAL`, `PENDING`, `REJECTED`, `FAILED`, `CANCELLED`.
- `ExecutionRuleStatus`: `PASSED`, `FAILED`, `INSUFFICIENT_DATA`, `UNKNOWN`.

**Direction decision (unchanged from earlier phases):** Phases 8–10 never produce a side, and Phase 11 does not invent one. The caller supplies it in `OrderIntent`. The risk assessment supplies quantity and stop distance, so execution can never size a trade the risk layer refused to size.

---

## 4. Order Engine

`ExecutionEngine.execute(ExecutionRequest)` is the single stage boundary:

1. Reject a request that cannot be attempted at all: execution disabled, no adapter with the configured name, or `LIVE` mode wired to the paper adapter.
2. Create the order (`ORD-<n>` from a per-engine atomic sequence) and publish `OrderCreatedEvent`.
3. Compute the reference price (limit, else trigger, else assessed reference), the notional and the required margin.
4. Evaluate every rule in registry order against an immutable `ExecutionRuleContext` that includes the duplicate flag and the adapter's connection state.
5. If any rule did not pass, mark the order `REJECTED`, publish `OrderRejectedEvent` with the concatenated reasons and stop.
6. If the risk assessment is not `WITHIN_LIMITS`, reject for the same reason.
7. Transition to `VALIDATED`, publish `OrderValidatedEvent`, reserve margin for the order.
8. Transition to `SUBMITTED`, publish `OrderSubmittedEvent`, submit through the adapter.
9. Retry only retryable `ExecutionBrokerException`s, up to `maxSubmitAttempts` (1–5), without sleeps.
10. Interpret the report: fill, partial fill, acceptance, rejection, cancellation/expiry or failure; update the order, position and portfolio; publish the matching events.

`cancel(orderId)` cancels a working order through the adapter, releases its margin, transitions it to `CANCELLED` and publishes `OrderCancelledEvent`. Unknown or terminal orders are refused.

`OrderStateMachine` owns the legal transition table. An order moves forward only; `PARTIALLY_FILLED → PARTIALLY_FILLED` is the single self-transition, for successive fills; every terminal state is final.

`OrderStore` is a bounded LRU map of recent orders plus their broker identities (default 256, configurable). `hasActiveOrder` provides duplicate detection, `activeOrders` the risk-consuming subset.

`OrderRequestFactory` refuses an intent whose strategy or instrument differs from the assessment, and refuses an assessment without a usable quantity or reference price.

---

## 5. Broker Integration

`BrokerAdapter` has exactly five operations: `placeOrder`, `cancelOrder`, `modifyOrder`, `orderStatus`, `positions`, plus `brokerName()` and `connected()`. It performs no validation and holds no business logic.

`BrokerOrderCommand` is the adapter-facing instruction. `ExecutionReport` and `OrderExecution` are the adapter-facing responses. Broker-specific mapping stays entirely behind the interface, so the engine never sees a broker payload.

`PaperBrokerAdapter` is the deterministic adapter shipped with the application:

- fills at the limit price, or at the reference price for market orders;
- configurable fill ratio for deterministic partial fills;
- configurable rejection of everything;
- configurable transient failures (`failNext(n)`), each a retryable `ExecutionBrokerException`;
- connect/disconnect, cancellation, modification accepted as `PENDING`, bounded report memory (1024);
- no clock, no randomness, no latency, no market simulation.

`BrokerAdapterRegistry` is a thread-safe name registry with duplicate detection, replacement, removal, immutable sorted snapshots and `select(name)`.

**Mode decision:** only the paper adapter exists, because Phase 2 delivered a read-only broker integration. `LIVE` mode therefore refuses to start an attempt (`ExecutionValidator.validateMode`), so a live configuration can never silently simulate fills. This is recorded as a known limitation.

---

## 6. Portfolio Management

`PortfolioService` is the single writer of portfolio state and synchronizes every public method:

- open and closed positions, keyed by position identity, with at most one open position per strategy and instrument;
- realised P&L, accumulated from closing fills;
- unrealised P&L, marked from the latest price per instrument;
- blocked margin, reserved per working order;
- used margin, derived as `marginPercent × open notional`;
- exposure, the sum of open position notionals;
- available capital = `referenceCapital + realisedPnl − usedMargin − blockedMargin`.

Fills are applied through `applyFill(order, execution)`:

- no open position → open a new one;
- same direction → add quantity and recompute the weighted average entry;
- opposite direction → close up to the remaining quantity, realise P&L signed by direction and mark the position `PARTIALLY_CLOSED` or `CLOSED`.

A partial fill releases the order's block and immediately re-blocks the margin of the remaining quantity, so a working remainder still consumes risk. A full fill removes the block permanently.

Marks arrive from `MarketAssessmentGeneratedEvent` through `ExecutionMarketDataProvider`; a missing market price simply leaves the last price in place.

`PortfolioChange` reports the affected position, its status and the `opened/modified/closed` flags, which is exactly what the engine needs to choose the position event.

---

## 7. Position Lifecycle

`PositionCalculator` is pure arithmetic with no state:

- `open` creates an `OPEN` position from a fill;
- `add` computes the new quantity and weighted average;
- `close` caps the close at the remaining quantity, computes realised P&L from the average entry and the close price signed by direction, sets `PARTIALLY_CLOSED` or `CLOSED`, and sets `closedAt` only on a full close;
- `direction` maps `BUY → LONG` and `SELL → SHORT`.

Status transitions in the service: `OPEN → PARTIALLY_CLOSED → CLOSED`. `OPENING` exists in the model for a position whose opening fill has not completed; the current single-report engine creates positions directly in `OPEN`, which is recorded as a limitation rather than hidden.

An exit is an explicit opposite-side intent. The engine does not generate exits, because generating them would require a strategy or recommendation decision that Phase 11 must not make.

---

## 8. Event Integration

Consumed:

- `RiskAssessmentGeneratedEvent` — authoritative quantity, stop distance and policy status;
- `TradeQualifiedEvent` — cached qualification; a cached non-qualified outcome blocks submission;
- `MarketAssessmentGeneratedEvent` — the session context for `MARKET_OPEN`, and the mark trigger;
- `StrategyEvaluatedEvent` — cached for diagnostics;
- `OptionsAssessmentGeneratedEvent` — cached for diagnostics.

Published, in lifecycle order:

```text
OrderCreated, OrderValidated, OrderSubmitted,
OrderAccepted, OrderRejected, OrderPartiallyFilled, OrderFilled, OrderCancelled,
PositionOpened, PositionModified, PositionClosed,
PortfolioUpdated, TradeCompleted
```

`ExecutionService` keeps five bounded LRU caches (64 entries by default) and stages inputs by `(strategyId, instrument, timestamp)`. Risk and market assessments are joined by identity and instant, so an order can never combine the size of one candle with the session of another. The engine emits through `ExecutionEventSink`; the Spring wiring supplies `publisher::publish`, and tests supply a recording sink.

`FAILED` submissions and broker-reported failures have no dedicated event in the required list and are published as `OrderRejectedEvent` with the deterministic reason. That mapping is explicit and covered by tests.

---

## 9. Validation

Boundary validation:

- records reject blank identities, non-positive quantities and prices, negative fills, overfills, and closed positions without a close instant;
- `ExecutionRequest` cross-checks strategy, instrument and timestamp across its three inputs;
- `ExecutionConfig` rejects non-positive capital, impossible position counts, leverage and margin percentages, empty allowed sets, retry counts outside 1–5, timeouts outside 100–60 000 ms, retention outside 1–100 000 and blank broker names;
- `ExecutionValidator` rejects null requests, blank order ids, null/empty/duplicate rule batches, and a live configuration wired to paper;
- `OrderStateMachine` rejects illegal transitions;
- `OrderRequestFactory` rejects mismatched assessments and unusable sizes.

The thirteen execution rules:

| Rule | Boundary |
|---|---|
| `VALID_QUANTITY` | Positive quantity, within `maxOrderQuantity` |
| `VALID_PRICE` | Reference available; limit present and positive for limit/stop-limit |
| `VALID_STOP` | Stop present and protective for the side |
| `VALID_TARGET` | Optional target on the profitable side |
| `MARKET_OPEN` | Ready session in `OPENING`/`REGULAR`/`CLOSING` when policy requires it |
| `BROKER_CONNECTED` | Adapter reports a connection |
| `ACCOUNT_READY` | Positive capital and a ready account flag |
| `SUFFICIENT_MARGIN` | Required margin ≤ available capital |
| `ORDER_NOT_DUPLICATE` | No active order for the same strategy, instrument and side |
| `POSITION_LIMIT` | Opening stays below `maxOpenPositions`; closing always passes |
| `LEVERAGE_LIMIT` | Notional ÷ capital ≤ `maxLeverage` |
| `VALID_PRODUCT` | Segment allowed by policy |
| `VALID_ORDER_TYPE` | Type allowed; required trigger/limit present |

Rules return `INSUFFICIENT_DATA` rather than guessing when a precondition cannot be decided, and an undecidable rule blocks submission exactly like a failed one.

---

## 10. Configuration

`ExecutionConfig` is an immutable record with a builder and no runtime mutation:

| Setting | Default |
|---|---|
| `enabled` | `true` |
| `mode` | `PAPER` |
| `brokerName` | `paper` |
| `referenceCapital` | 100 000 |
| `maxOpenPositions` | 5 |
| `maxLeverage` | 5 |
| `marginPercent` | 20 |
| `maxOrderQuantity` | 10 000 |
| `allowedOrderTypes` | all four |
| `allowedSegments` | `NSE_CASH`, `NSE_FO` |
| `defaultTimeInForce` | `DAY` |
| `maxSubmitAttempts` | 2 |
| `timeoutMillis` | 2 000 |
| `maxRetainedOrders` | 256 |
| `requireMarketOpen` | `true` |

Spring exposes `app.execution.enabled`, `app.execution.mode` and `app.execution.broker-name`. `ExecutionConfiguration` is conditional on `app.broker.angel-one.enabled=true`, exactly like the evaluation stages, and wires the registry, paper adapter, portfolio, order store, engine, service and the bus subscription. The subscription is removed on shutdown.

---

## 11. Performance

- No mutable global state: the only mutable values are instance fields of the engine's collaborators.
- `PortfolioService` synchronizes every read and write on the instance; all returned views are immutable copies.
- `OrderStore`, `BrokerAdapterRegistry`, `ExecutionRuleRegistry` and `BoundedCache` are thread-safe; stores and caches evict by LRU at a fixed bound.
- The engine holds one `AtomicLong` order sequence; identities are unique within a process without a static counter.
- Rule evaluation iterates an immutable snapshot in deterministic identifier order.
- No sleeps, no timers, no thread pools are created by the layer; retries are immediate and bounded.
- Broker calls are synchronous; the paper adapter does no I/O.

---

## 12. Testing Summary

228 new deterministic tests, all passing:

| Test class | Tests |
|---|---|
| `ExecutionRulesTest` | 29 |
| `ExecutionDomainTest` | 25 |
| `ExecutionEngineTest` | 24 |
| `PortfolioServiceTest` | 18 |
| `ExecutionConfigTest` | 16 |
| `PaperBrokerAdapterTest` | 14 |
| `ExecutionServiceTest` | 14 |
| `OrderStateMachineTest` | 13 |
| `PositionCalculatorTest` | 12 |
| `OrderStoreTest` | 11 |
| `OrderRequestFactoryTest` | 9 |
| `ExecutionRuleRegistryTest` | 9 |
| `BrokerAdapterRegistryTest` | 9 |
| `ExecutionValidatorTest` | 9 |
| `BoundedCacheTest` | 7 |
| `ExecutionConfigurationTest` | 5 |
| `ExecutionConcurrencyTest` | 4 |

Coverage: order validation and state transitions, engine happy path and every rejection path, broker abstraction and paper behaviour, portfolio arithmetic and margins, position lifecycle, registries, configuration, Spring wiring and shutdown, concurrency (fills, store writes, parallel executions, marks), failure handling, retries, partial fills and cancellations.

Full run: **905 tests, 0 failures, 0 errors, 2 skipped**. The execution tests use a fixed `Clock`, the real risk engine for their baseline assessment and a recording sink, so they assert exact event sequences.

---

## 13. Problems Encountered

- **Records are final.** An early rule test tried to override `accountReady()` with an anonymous subclass of `ExecutionRuleContext`; the test was rewritten to construct the context with the flag instead.
- **AssertJ `Optional.contains` is exact equality, not a substring match.** Reason assertions were changed to inspect the unwrapped string; the engine's reason text itself was already correct.
- **Snapshot timing after a partial fill.** The engine re-blocks the remaining margin after applying the fill, but the result initially carried the snapshot captured before the block. The result now reads a fresh portfolio snapshot after all state changes.
- **`InstrumentKey` exposes `symbolToken`**, not `symbol`; diagnostics and messages were corrected.
- **`QualificationStatus` has `DISQUALIFIED`**, not `REJECTED`; the service gating test was corrected.
- **`ExecutionRuleEvaluation.failed` requires metadata**; one stop-rule branch was updated to supply it.

---

## 14. Known Limitations

1. No live broker adapter exists, so `LIVE` mode is refused. Phase 2 was deliberately read-only.
2. State is in memory only: a restart loses orders, positions, margins and realised P&L. There is no persistence and no recovery.
3. Only the submission report is applied. There is no polling loop, no push stream and no reconciliation with broker positions.
4. Exit orders are explicit caller intents. The layer never generates a stop-loss or target exit, because that would be a strategy decision.
5. A closing order larger than the remaining position closes the position and ignores the excess; reversal is not modelled.
6. A partial close followed by further fills can only reduce, never flip, the position.
7. `OrderStatus.OPENING`/`PositionStatus.OPENING` exist in the model but the single-report flow creates positions directly in `OPEN`.
8. The order store evicts at capacity, so duplicate detection only covers retained orders.
9. `timeoutMillis` is policy metadata; adapter calls are synchronous and not wrapped in a timeout or executor.
10. Order identities are unique within a process, not across restarts or instances.
11. P&L is gross: no brokerage, taxes, slippage or financing.
12. The paper adapter always fills at one price with no queue, latency or market impact, and a single report carries the whole fill.
13. Options and strategy assessments are cached for diagnostics; they do not gate or size execution.
14. Broker-reported failures are published as `OrderRejectedEvent` because the required event list has no failure event.
15. GTC orders expire only if the broker reports expiry; no scheduler exists.

---

## 15. Recommendations for Phase 12

1. Add a live `BrokerAdapter` and an explicit capability check, so `LIVE` mode becomes possible without weakening the paper guard.
2. Add order/position persistence and startup reconciliation before any live use.
3. Introduce a status-polling or push reconciliation loop that applies subsequent fills, partial fills and expiries idempotently.
4. Wrap adapter calls in the configured timeout and record timeout outcomes distinctly.
5. Model commission, taxes and slippage explicitly before reporting net P&L.
6. Add a shared risk budget across strategies, so margin and exposure are enforced portfolio-wide rather than per order.
7. Add a scheduler for GTC expiry and end-of-day cancellation.
8. Expose lifecycle metrics (orders by status, fill ratio, rejection reasons, attempts) for observability.
9. Decide explicitly whether Phase 12 may generate exit intents; if it does, that belongs to strategy/risk, not to execution.
10. Extend duplicate detection with a client order key that survives store eviction.

---

## 16. Completion Gate

- [x] Orders can be created, validated, submitted, tracked, cancelled and completed.
- [x] Positions and portfolio state are maintained (open, partial close, close, realised, unrealised, margins, exposure).
- [x] Broker communication is abstracted behind `BrokerAdapter` with no business logic.
- [x] No AI, machine learning, optimization, recommendation change or backtesting was implemented.
- [x] Nothing beyond Phase 11 was started.
- [x] `mvn -B -ntp verify` passes: 905 tests, 0 failures, 0 errors, 2 skipped.
- [x] Backend and root READMEs updated.
- [x] Committed locally only; nothing pushed or deployed.

**STOP — awaiting architectural review before beginning Phase 12 (AI, learning, optimization and final system integration).**
