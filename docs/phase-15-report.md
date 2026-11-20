# Phase 15 — Trade Monitoring Engine

Status: **implemented and locally verified; not deployed.**

> Phase 16 adds the application-level manual fill and premium-status APIs outside
> this read-only monitoring package. The monitoring package itself remains
> write-free; see [`phase-16-report.md`](phase-16-report.md).

Phase 15 adds continuous monitoring for trades the owner has already executed
outside the application. It answers one question from the latest immutable
engine outputs:

> Given everything the existing engines describe right now, should this trade
> continue to be held or should the owner prepare to exit?

The result is deterministic and explainable. The application never places,
modifies or closes an order. It never connects to broker execution, never moves
a stop, never scales a position and never exits automatically. The owner records
the trade manually after executing it, and exits it manually outside the
application.

There is no AI, ML, LLM, neural network, reinforcement learning, Bayesian model,
statistical prediction, external model service, randomness or hidden scoring in
the monitoring package.

## Architecture

```text
                    existing immutable engine outputs

 Recommendation ─────┐
 MarketAssessment ───┤
 IndicatorResults ───┤
 OptionsAssessment ──┤──> TradeMonitoringContext ──> TradeMonitoringEngine
 StrategyEvaluation ─┤                                      │
 TradeQualification ─┤                                      ▼
 RiskAssessment ─────┤                             MonitoringState
 Market Freshness ───┘                                      │
                                                            ▼
                         HOLD / WATCH_CLOSELY / PREPARE_EXIT / EXIT_NOW
                                                            │
                              ┌─────────────────────────────┼────────────────┐
                              ▼                             ▼                ▼
                       monitoring history            monitoring events   read-only API
```

Dependency direction is one-way:

```text
existing engines ──> monitoring
monitoring ──X──> execution / broker adapters / Android / HTTP clients
monitoring ──X──> indicator, options, risk or recommendation recalculation
```

No existing engine imports `com.tradingassistant.monitoring`. The sealed
`MarketEvent` hierarchy is unchanged; monitoring events are a separate sealed
hierarchy.

### Package layout

```text
com.tradingassistant.monitoring
├── api            MonitoringController, MonitoringDtos
├── config         MonitoringConfig, MonitoringProperties, MonitoringConfiguration
├── domain         ManualTrade, TradeMonitoringContext, TradeMonitoringResult,
│                  MonitoringState, ExitRecommendation, ComponentChange,
│                  MonitoringHistoryEntry, MonitoringAlert, MonitoringWarning,
│                  MonitoringAlertType, MonitoringInterval, ActiveTradeMonitoring
├── engine         TradeMonitoringEngine
├── event          MonitoringEvent hierarchy, bus and publisher
├── exception      MonitoringException, MonitoringValidationException
├── persistence    Mongo documents, repositories and MongoMonitoringStore
├── service        MonitoringService, MonitoringQueryService, MonitoringStore
└── validation     MonitoringValidator
```

`MonitoringStore` is a persistence port. Mongo is an adapter at the edge; the
pure engine and domain have no Spring, Mongo, clock or I/O dependency.

## Manual trade

`ManualTrade` is immutable and contains only what the owner recorded:

| Field | Meaning |
|---|---|
| `tradeId` | Stable local identity |
| `instrument` | Underlying instrument |
| `interval` | Decision interval |
| `recommendationType` | `BUY_CALL` or `BUY_PUT` only |
| `optionSymbol` | Executed contract symbol |
| `strike` | Executed strike |
| `expiry` | Executed expiry |
| `entryTimestamp` | Time the owner entered the trade |
| `entryPrice` | Owner-recorded entry price |
| `quantity` | Owner-recorded quantity |
| `notes` | Optional free text |
| `status` | `ACTIVE` or `CLOSED` |

There is deliberately no broker order id, fill id, execution state, stop
instruction, automation flag or account field. `ManualTrade.closed()` changes
only the local monitoring status. (Phase 16 adds optional recorded premium
target/stop boundaries to this record; they remain owner-facing alert levels,
not broker instructions.)

The monitoring service exposes `register(ManualTrade)` and `close(tradeId,
reason)` for the application layer and future UI. Phase 15 intentionally exposed
no write endpoint: its REST surface was read-only, as specified. Phase 16 adds
the application-level write path outside the monitoring package.

## Context

`TradeMonitoringContext` freezes one evaluation:

- the active `ManualTrade`;
- the current Phase 14 `Recommendation`, including its component scores;
- `MarketAssessment`;
- the indicator snapshot;
- `OptionsAssessment`;
- the aligned `StrategyEvaluation`;
- the aligned `TradeQualification`;
- the aligned `RiskAssessment`;
- market freshness and session;
- the previous `TradeMonitoringResult`, when one exists.

The context is built from `RecommendationService.latestContext(...)`, which
reuses the already-staged Phase 14 inputs. Monitoring does not fetch candles,
recompute indicators, rebuild an option chain, recalculate Greeks or call the
recommendation engine. It consumes the current recommendation and its existing
twenty-one component scores.

## Continuous monitoring

`MonitoringService` is subscribed to the market event bus and the separate
recommendation event bus. It rescans active trades after:

- a completed candle;
- a market assessment update;
- an options assessment update;
- a strategy evaluation update;
- a qualification update;
- a risk assessment update;
- a generated or updated recommendation.

There is no polling loop and no duplicate indicator calculation. Events that
arrive before the recommendation has advanced reuse the latest aligned context;
the observable-result comparison prevents duplicate history and duplicate events
when repeated triggers produce the same result. The configuration enumerates
only `EVENT_DRIVEN`; polling is rejected at startup.

## States

| State | Deterministic meaning | Exit recommendation |
|---|---|---|
| `HEALTHY` | Original thesis remains supported; no configured deterioration | `HOLD` |
| `WEAKENING` | Confidence, edge, components, trend, structure, session or liquidity softened | `WATCH_CLOSELY` |
| `AT_RISK` | Multiple important components weakened, or deterioration is combined with low confidence | `PREPARE_EXIT` |
| `EXIT_RECOMMENDED` | A deterministic holding rule no longer holds: confidence at/below the exit floor, qualification lost or risk breached | `EXIT_NOW` |
| `INVALIDATED` | The current recommendation is `NO_TRADE`, changed side, edge reversed, or both trend and structure oppose the trade | `EXIT_NOW` |

These are recommendations only. There is no automatic exit, stop movement or
order path.

## Scoring and deltas

The engine reuses every Phase 14 `ComponentScore` exactly as produced. It does
not manufacture a second recommendation and does not combine scores into a new
hidden confidence.

For each pass it records:

- current confidence;
- current trade-aligned directional edge;
- previous confidence and edge, when a previous result exists;
- confidence delta;
- directional edge delta;
- every component's previous score, current score and delta;
- warnings with stable alert types and diagnostics.

The Phase 14 signed edge is positive for calls and negative for puts. Monitoring
multiplies it by the trade direction so `currentDirectionalEdge` is positive
when the market supports the active trade. A negative delta therefore always
means the active thesis weakened, irrespective of call or put.

The evaluation order is explicit:

1. Qualification lost or risk outside limits -> `EXIT_RECOMMENDED`.
2. Confidence at or below `exit-confidence` -> `EXIT_RECOMMENDED`.
3. Recommendation type lost, edge reversed, or both trend and structure against
   the trade -> `INVALIDATED`.
4. `at-risk-component-count` or more important components deteriorated by at
   least the configured component threshold -> `AT_RISK`.
5. `warning-component-count` or more important components deteriorated together
   with confidence or edge deterioration -> `AT_RISK`.
6. Confidence below the warning floor, any important component deterioration,
   confidence/edge deterioration, trend/structure opposition or a closed session
   -> `WEAKENING`.
7. Otherwise -> `HEALTHY`.

Important components are `TREND`, `MOMENTUM`, `LIQUIDITY`, `OPEN_INTEREST`,
`PCR`, `GREEKS`, `MARKET_STRUCTURE`, `TREND_ALIGNMENT`, `RISK_REWARD`,
`QUALIFICATION`, `STRATEGY_AGREEMENT` and `FRESHNESS`.

Warnings cover:

- confidence deterioration;
- directional-edge deterioration;
- component deterioration;
- qualification lost;
- risk deterioration;
- trend reversal;
- structure reversal;
- liquidity deterioration;
- strategy disagreement;
- session closed;
- thesis invalidated;
- stale or invalid context.

Every warning carries its measured values in diagnostics. The result explanation
contains the state, exit recommendation, confidence and delta, edge and delta,
the number of important deteriorations and the warning texts.

## Persistence

MongoDB collections:

| Collection | Content |
|---|---|
| `manual_trades` | One immutable manual trade record per local trade id |
| `monitoring_results` | Latest result per trade, keyed by trade id |
| `monitoring_history` | Significant transitions with previous/current state, exit recommendation, confidence and edge |
| `monitoring_alerts` | Warnings and state/exit/lifecycle alerts exposed read-only |

History is written when the observable result changes. Repeated scans of the
same inputs do not create duplicate history. The history entry contains the
explanation and warning list needed for later replay and validation. Phase 15
does not implement replay itself.

Indexes are created by `MongoIndexInitializer`: manual trade status/entry time,
monitoring history trade/time, and alert time. Index creation remains
best-effort when MongoDB is temporarily unavailable.

## Events

The separate `MonitoringEvent` hierarchy permits:

- `MonitoringStartedEvent` — first result for an active trade;
- `MonitoringUpdatedEvent` — an observable result changed;
- `MonitoringStateChangedEvent` — the monitoring state changed;
- `MonitoringWarningEvent` — a warning or alert was persisted;
- `ExitRecommendedEvent` — the exit recommendation changed, including a
  non-`HOLD` first result;
- `MonitoringStoppedEvent` — the owner closed the trade locally.

Alerts are generated for state changes, exit recommendation changes, thesis
invalidation, qualification loss, risk deterioration, stale data, confidence
and edge deterioration, liquidity deterioration, strategy disagreement, trend
and structure reversal, and monitoring start/stop. No Android notification or
push transport is implemented; events are the integration surface for a later
phase.

## REST API

The API is read-only and exists only when the broker live pipeline is enabled,
like the existing engines.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/monitoring/active` | Active trades with latest result |
| `GET` | `/api/v1/monitoring/history?tradeId=&limit=` | History, newest first |
| `GET` | `/api/v1/monitoring/{tradeId}` | One trade and latest result |
| `GET` | `/api/v1/monitoring/alerts?limit=` | Recent alerts |

There is no POST, PUT, PATCH or DELETE endpoint. No endpoint records, modifies,
closes or executes a trade. Android is not implemented in Phase 15; Phase 17 can
consume these contracts.

## Configuration

`MonitoringProperties` binds `app.monitoring` and converts once to the immutable
`MonitoringConfig`:

```yaml
app:
  monitoring:
    enabled: true
    warning-confidence: 65
    exit-confidence: 45
    confidence-deterioration: 10
    edge-deterioration: 8
    component-deterioration: 10
    warning-component-count: 1
    at-risk-component-count: 3
    strategy-disagreement-score: 50
    maximum-stale-duration: 2m
    monitoring-interval: EVENT_DRIVEN
```

All thresholds and component-count rules are configurable. Validation rejects missing values,
out-of-range percentages, negative deterioration thresholds, an exit floor above
the warning floor, a non-positive stale duration and an unsupported monitoring
interval. The configuration is conditional on the broker live pipeline being
enabled.

## Validation

`MonitoringValidator` rejects:

| Condition | Behaviour |
|---|---|
| Closed trade | Rejected before scoring |
| Duplicate active trade id | Rejected by `MonitoringService.register` |
| Missing recommendation | Rejected at registration and skipped at runtime |
| Missing qualification, risk, options or strategy | Context cannot be built; no result |
| Stale, unknown or over-age freshness | Rejected; an alert is recorded and the last result is retained |
| Mismatched instrument or interval | Rejected |
| Mismatched market, options, strategy, qualification or risk timestamp | Rejected |
| Mismatched strategy/qualification/risk identity | Rejected |
| Recommendation expiry not equal to the trade expiry | Rejected |
| Previous result belongs to another trade or is newer | Rejected |
| Invalid configuration | Rejected at startup |

A rejected runtime context never overwrites the last valid result. The service
publishes a `STALE_DATA` or `INVALID_CONTEXT` alert once per distinct problem.

## Tests

Phase 15 adds:

- `TradeMonitoringEngineTest` — healthy hold, confidence weakening, multiple
  component deterioration, exit floor, qualification loss, risk breach, trend
  reversal, structure reversal, combined invalidation, liquidity deterioration,
  edge deterioration, session close, strengthening, changed recommendation side,
  no-trade invalidation and deterministic equality.
- `MonitoringValidatorTest` — aligned context, closed trades, missing
  recommendation, stale and aged data, mismatched instrument, mismatched expiry
  and mismatched previous trade.
- `MonitoringServiceTest` — registration, duplicate registration, missing
  recommendation, duplicate-scan suppression, recommendation update transitions,
  history, state/exit events, stale-context alerts without overwriting the last
  result, and local close/stop lifecycle.
- `MonitoringControllerTest` — active, history, single-trade, unknown-trade and
  alerts endpoints.
- `MonitoringConfigTest` — documented defaults, invalid floors, invalid
  thresholds, property conversion and unsupported intervals.
- `MonitoringConfigurationTest` — broker condition, all beans and bus
  subscriptions, plus shutdown cleanup.
- `ManualTradeTest` — immutable manual record, local close, and rejected
  direction/economics.
- `MonitoringPersistenceTest` — manual trade, result, history and alert document
  round-trips.
- `RecommendationServiceTest` — latest recommendation and reusable context
  accessors used by monitoring.
- `ArchitectureGuardrailTest` — extended for monitoring dependencies and
  deterministic-core constraints.

## Guardrails

The build fails if monitoring:

- imports execution or broker packages;
- references order placement, submission or an order repository;
- imports Android;
- imports an HTTP client;
- imports indicators, options analytics, risk calculation or the recommendation
  engine;
- exposes a write endpoint;
- depends on AI/ML/LLM libraries or markers;
- uses randomness; or
- uses a clock or wall-clock call in its engine, domain or validation core.

The guardrail is source-level review protection, not a formal proof.

## Performance

- Active trades are queried from `manual_trades` by status.
- Each trigger reuses `RecommendationService` caches and the latest recommendation;
  no indicator, options or recommendation calculation is repeated.
- A result is persisted only when state, exit recommendation, confidence, edge,
  component scores or warnings change.
- History and alert reads are bounded by a maximum page size of 500.
- The engine is pure and side-effect free; hundreds of active trades can be
  rescored without new analytics work.

## Limitations

- **No write API in Phase 15.** The read-only REST requirement is honoured.
  Manual trades are recorded through the application service. Phase 16 adds the
  application-level write path and premium monitor outside this package.
- **No live session verification.** Automated tests use deterministic immutable
  fixtures. Broker credentials and a live market session were unavailable; the
  opt-in SmartAPI live tests remain skipped.
- **MongoDB was unavailable during the local application-context startup test.**
  Startup reported the documented connection warning and index creation was
  skipped; persistence is exercised through the store port with an in-memory
  adapter in tests.
- **No replay.** History is persisted to support later replay, but Phase 15 does
  not implement it.
- **No retention job.** Monitoring history and alerts currently grow until a
  later retention policy is added.
- **No automatic action.** Exit recommendations never move a stop, close a
  position or contact a broker.
- **Latest chain limitation inherited from Phase 14.** If the live option chain
  is not captured for the recommendation timestamp, the recommendation service
  does not publish and monitoring waits rather than inventing a contract.
- **Session-close handling.** A closed session is surfaced as `WEAKENING` and
  `WATCH_CLOSELY`; monitoring does not fabricate fresh evidence or force an
  exit after the market closes.

## Verification

```bash
cd backend
mvn -B -ntp test
```

The final full run is **1,126 tests, 0 failures, 0 errors and 2 skipped**
(BUILD SUCCESS). The two skipped tests are the opt-in SmartAPI live integration
tests, which require broker credentials and a live session. No deployment,
order submission or external AI service was used.