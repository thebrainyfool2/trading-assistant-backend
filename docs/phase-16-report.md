# Phase 16 — Actionable Recommendation and Trade Lifecycle

Status: **implemented and locally verified; not deployed.**

Phase 16 closes the gap between the analytical engines and the owner's manual
workflow:

1. search for a listed instrument;
2. read the current actionable recommendation with an option contract and a
   reference entry/target/stop;
3. execute manually outside the application;
4. record the fill and quantity;
5. continue monitoring the underlying thesis and the option premium;
6. receive an in-app alert when a premium target, stop or expiry boundary is
   reached;
7. close the local monitoring record after exiting manually.

The application still never places, modifies or cancels an order. It never
contacts broker execution, moves a stop, changes a position or exits a trade.

## Package layout

```text
com.tradingassistant.platform.instruments
├── InstrumentCatalog          cached read-only instrument master
├── InstrumentSearchService    deterministic name/symbol search
├── InstrumentReferences       SEGMENT:TOKEN parsing/formatting
├── InstrumentController       GET /api/v1/instruments/search
└── InstrumentDtos

com.tradingassistant.platform.trade
├── TradePlan                  immutable reference plan
├── TradePlanConfig            validated policy
├── TradePlanProperties        app.trade-plan binding
├── TradePlanService           ATR + ATM-delta plan derivation
├── RecommendationQueryService current recommendation + plan
├── RecommendationController   GET /api/v1/recommendations
├── ManualTradeCommandService  register/close through MonitoringService
├── ManualTradeController      POST manual fill and local close
├── OptionPremiumService       scheduled premium boundary monitor
├── OptionPremiumController    read-only premium status
└── DTOs
```

The Android client now consumes these endpoints: instrument search, the
recommendation with its plan, manual fill entry, and the active premium status
with a local-monitoring close action. It renders backend values and never
computes a signal, plan, size or boundary.

The pure recommendation and monitoring cores remain unchanged in dependency
direction. The new layers read their existing immutable outputs and application
services; no engine imports the platform.

## Search

```http
GET /api/v1/instruments/search?q=reliance
GET /api/v1/instruments/search?q=NIFTY&segment=NSE_CASH&type=INDEX
GET /api/v1/instruments/search?q=RELIANCE&type=OPTION&limit=50
```

Default results are cash equity, index and other non-derivative instruments, so
a company search is not buried under option contracts. Exact symbol/name matches
come first, then prefix, then contains; ties are alphabetical. The instrument
master is cached for ten minutes, so keystrokes do not trigger a provider
download. The response includes the stable `SEGMENT:TOKEN` used by the other
endpoints, plus symbol, display name, lot size, tick size and derivative fields.

## Recommendation and reference plan

```http
GET /api/v1/recommendations
GET /api/v1/recommendations?instrument=NSE_CASH:2885&interval=FIVE_MINUTE
```

The response contains the current `BUY_CALL`, `BUY_PUT` or `NO_TRADE`, all
component scores and explanations, the selected expiry/strike/type, and an
optional `plan`.

The plan is derived from existing evidence only:

- entry reference: the selected chain leg's last premium;
- underlying stop: the risk engine's ATR stop distance;
- underlying target: stop distance multiplied by the configured minimum
  reward/risk ratio;
- option-premium distances: underlying distances multiplied by the absolute ATM
  delta for the selected option type;
- quantity: the configured risk budget divided by premium risk per lot, rounded
  down to whole lots;
- target and stop premium: entry plus/minus the premium distances, rounded to
  the contract tick.

The delta is an ATM approximation, not a contract-specific Greek. When the ATM
Greek is unavailable, the configured fallback delta is used and marked in
diagnostics. The plan is a reference, not an order or a promise. The owner
records the actual fill price, which becomes the entry for monitoring.

`NO_TRADE` has no plan.

## Recording the fill

```http
POST /api/v1/monitoring/trades
Content-Type: application/json

{
  "instrument": "NSE_CASH:2885",
  "recommendationType": "BUY_CALL",
  "interval": "FIVE_MINUTE",
  "recommendationTimestamp": "2026-11-18T03:45:00Z",
  "optionSymbol": "SBIN26NOV500CE",
  "strike": 500,
  "expiry": "2026-11-26",
  "entryTimestamp": "2026-11-18T03:50:00Z",
  "entryPrice": 12.50,
  "quantity": 75,
  "notes": "executed manually",
  "targetPremium": 18.00,
  "stopPremium": 8.00
}
```

`recommendationTimestamp` is required and is the identity the fill is pinned to:
the market timestamp of the recommendation whose plan the owner acted on. The
server resolves that exact recommendation and its chain snapshot instead of the
newest one, so a recommendation published while the owner was filling cannot
change the derived boundaries. If the pinned recommendation has already been
superseded or evicted, the endpoint returns `409 CONFLICT` and records nothing.
The value is stored on the trade and returned as `recommendationTimestamp`.

`targetPremium` and `stopPremium` are optional. When omitted, the server applies
the pinned plan's distances to the recorded entry, or falls back to the
configured percentages if the pinned plan is unavailable. The Android fill form
leaves both fields empty and shows the plan's own boundaries as hints, so a
different executed premium still gets the pinned plan's risk distances; typing a
value there overrides that and sends it explicitly. The endpoint never invents a
trade.

```http
POST /api/v1/monitoring/trades/{tradeId}/close
Content-Type: application/json

{ "reason": "owner exited manually" }
```

Closing changes local monitoring state only. It never contacts a broker.

## Premium monitoring

`OptionPremiumService` runs every `app.trade-plan.premium-refresh-ms`
(default 30 seconds) and suppresses alerts outside market hours. It:

- resolves the listed option contract from the cached instrument master;
- polls LTP in batches of at most 50;
- compares the premium with the recorded target and stop;
- reports `NO_QUOTE`, `HOLDING`, `TARGET_HIT`, `STOP_HIT`, `EXPIRED` or
  `CLOSED`;
- records an in-app notification once when a target, stop or expiry boundary is
  first reached;
- does not alert outside market hours and does not automatically close anything.

```http
GET /api/v1/monitoring/premium
GET /api/v1/monitoring/premium/{tradeId}
```

Each snapshot includes entry premium, current premium, P&L, P&L percentage,
target, stop, session state and timestamp.

## Configuration

```yaml
app:
  trade-plan:
    delta-fallback: 0.50
    fallback-target-percent: 40
    fallback-stop-percent: 20
    premium-refresh-ms: 30000
    premium-initial-delay-ms: 15000
    premium-alerts-enabled: true
```

The target/stop plan uses `app.risk.default-atr-multiplier`,
`minimum-reward-risk-ratio`, reference capital and maximum risk percent from the
existing risk policy. Premium alerts use the existing notification API and
`TRADE_ALERT` category.

## Validation and invariants

- Search requires a non-blank query and clamps results to 1–50.
- Segment and instrument type values are validated; unknown values return the
  standard validation error.
- A manual trade requires the exact `recommendationTimestamp` the owner acted
  on; the recorded direction and interval must match that pinned recommendation.
  A missing, superseded or evicted pin is rejected rather than replaced by the
  latest recommendation.
- Entry, quantity, strike, target and stop are positive.
- Target must be above entry; stop must be below entry.
- Target/stop are persisted with the manual trade, so a later recommendation
  cannot silently move them.
- A closed trade is excluded from scheduled premium monitoring.
- An expired contract reports `EXPIRED`; it is not treated as a live quote.
- Monitoring cannot read execution, broker order or Android packages.
- No new endpoint submits an order.

## Tests

Phase 16 adds:

- `InstrumentCatalogTest` — master caching, default cash/index search and
  option-contract resolution.
- `TradePlanServiceTest` — ATR target/stop, ATM delta, tick rounding, quantity,
  reward/risk and NO_TRADE behavior.
- `ManualTradeCommandServiceTest` — explicit and plan-derived boundaries, the
  pinned timestamp, a replaced pin, a missing pin, direction mismatch and local
  close.
- `RecommendationServiceTest` — exact-timestamp recommendation and context
  lookup, so a pin cannot silently resolve to a newer recommendation.
- `OptionPremiumServiceTest` — target alert, stop alert, duplicate suppression
  and closed-session suppression.
- `TradePlanConfigurationTest` — property binding and broker condition.
- `ActionableControllerTest` — search, recommendation, manual-fill and premium
  route contracts.
- Extended `ArchitectureGuardrailTest` — the actionable workflow can not import
  execution, broker adapter, order submission or Android code.

Final backend verification: **1,149 tests, 0 failures, 0 errors, 2 skipped** (the
opt-in SmartAPI live tests). Android verification: **14 JVM tests, 0 failures**,
plus a successful `compileDebugKotlin`. No deployment, broker order or external
AI service was used.

## Limitations

- **Android screens are not device-verified.** Search, recommendation/plan,
  fill entry and active premium/close screens compile and their view models are
  unit tested, but no emulator or device was available for a UI run, and the
  Compose UI tests were not executed.
- **No live broker verification of the new screens.** With the broker disabled
  the endpoints are absent, so the screens show the backend's error state; a
  live search → recommendation → fill → premium loop still needs SmartAPI
  credentials and market hours.
- **Delta approximation.** The plan uses the ATM Greek, not a per-contract
  Greek; this is explicitly labelled and configurable.
- **Premium polling, not tick streaming.** Option contracts are not added to the
  live WebSocket subscription; LTP is polled every 30 seconds by default.
- **Nearest-expiry chain subset.** The live chain loader still stages the nearest
  expiry and closest strikes, not every expiry and strike.
- **No automatic exit.** Alerts are notifications only. The owner exits
  manually and closes the local record.
- **MongoDB required for persistence.** Manual trades, results, history, alerts
  and notifications use the existing Mongo adapters; the local test context
  tolerates Mongo being unavailable, but the API cannot persist trades without
  it.
