# Phase 10 — Risk Management & Position Sizing

**Status: implemented, tested and documented. Awaiting architectural review.**
No order placement, broker execution, portfolio management, capital allocation, account synchronization, P&L tracking, trade lifecycle, trailing stop, notification, scheduler, persistence, backtesting, optimization, AI/ML, recommendation or strategy change was started.

---

## 1. Executive Summary

Phase 9 decided whether a strategy evaluation is eligible for downstream consideration. It says nothing about how much could be lost, how wide a protective distance is reasonable or how large a position the policy would fund.

Phase 10 adds those numbers and only those numbers. Given a qualified trade, it derives a stop distance from volatility, converts a configured risk budget into a quantity, and checks every configured boundary:

```text
TradeQualifiedEvent ────────────────┐
MarketAssessmentGeneratedEvent ─────┤
OptionsAssessmentGeneratedEvent ────┤   RiskAssessmentService
StrategyEvaluatedEvent ─────────────┘   (aligned staging, bounded, in-memory)
                                                │
                                                ▼
                                     RiskCalculator  (stop + size, pure)
                                                │
                                                ▼
                                    RiskEngine (stateless, rules)
                                                │
                        ┌───────────────────────┼───────────────────────┐
                        ▼                       ▼                       ▼
                    Rule A                  Rule B                  Rule N
                        └───────────────────────┼───────────────────────┘
                                                ▼
                                         RiskAssessment
                                                │
                                                ▼
                                     RiskAssessmentGeneratedEvent
```

The output answers "if this qualified trade were considered, what are the allowable risk parameters according to the configured policies?" It never answers whether the trade should be taken, never approves anything, never sizes capital across strategies and never talks to a broker.

Verification: `mvn -B -ntp verify` → **677 tests, 0 failures, 0 errors, 2 skipped, BUILD SUCCESS**, of which **99 tests are new in Phase 10**. The two skipped tests are the opt-in live broker tests from Phases 2 and 3.

A pre-existing Phase 5 pipeline concurrency test failed once during a full run and passed on immediate repetition and in isolation; the risk layer does not touch that code path, and the failure is reported here rather than hidden.

---

## 2. Architecture

`com.tradingassistant.risk` (29 main classes, 8 test classes plus fixtures):

| Package | Contents |
|---|---|
| `risk.api` | `RiskRule` contract: `id()` and `evaluate(ruleContext)` |
| `risk.domain` | `RiskAssessment`, `RiskContext`, `RiskPlan`, `RiskRuleContext`, `RiskRuleEvaluation`, `StopCalculation`, `PositionSizing`, the three validation records, `RiskStatus`, `RiskRuleStatus`, `StopMethodology` |
| `risk.math` | `RiskCalculator` (pure derivation) and `RiskNumbers` (precision, formatting, metadata) |
| `risk.rule` | `RiskRules` — 15 immutable rule records |
| `risk.engine` | `RiskEngine` |
| `risk.registry` | `RiskRuleRegistry`, `BuiltInRiskRules` |
| `risk.config` | `RiskEngineConfig` |
| `risk.validation` | `RiskValidator` |
| `risk.exception` | `RiskException`, `RiskValidationException`, `RiskEvaluationException` |
| `risk.service` | `RiskAssessmentService`, `RiskMarketDataProvider` |
| `risk` | `RiskConfiguration` Spring wiring |

Dependency direction:

- the calculator depends only on the context and the configuration;
- rules depend on the rule context (inputs, plan, policy) and never on the calculator, registry or engine;
- the engine depends on the registry, the calculator and an injected executor;
- the service depends on the pipeline events, a market-data provider contract and the engine;
- the risk layer never reads broker adapters, HTTP objects, persistence, the strategy engine internals or a portfolio.

Two boundaries keep the layer honest:

1. **Derivation is separated from checking.** The calculator produces one `RiskPlan`; every rule validates the same numbers. A rule can never compute a different stop than the published assessment reports.
2. **The context is closed and aligned.** Every part must describe the same instrument, interval and instant, so a boundary can never mix a fresh ATR with a stale chain.

---

## 3. Domain Model

**`RiskContext`** — the only inputs a rule can read: `StrategyEvaluation`, `TradeQualification`, `MarketAssessment`, optional `OptionsAssessment`, optional `Candle` and a canonical indicator map (uppercase type keys). It validates strategy id, instrument, interval and timestamp across all parts, rejects a foreign or stale option assessment and a foreign candle, rejects blank indicator keys and a negative ATR. It exposes `atr()`, `price()` (candle close, else chain spot) and `indicatorValue(type, key)`.

**`RiskPlan`** — the derived numbers: reference capital, applied risk percent, risk amount, ATR, volatility adjustment, effective ATR multiple, `StopCalculation`, optional `PositionSizing` and optional available reward.

**`RiskAssessment`** — the published result: strategy identity, instrument, interval, timestamp, `QualificationKey` reference, qualification status, aggregate `RiskStatus`, `riskPerTrade`, `maximumPermittedLoss`, stop, ATR multiple, volatility adjustment, sizing, maximum leverage allowed, the three validations, all rule evaluations, a deterministic summary and a diagnostics map. It contains no approval, recommendation, ranking, order, fill, account balance or profit projection.

| Model | Purpose |
|---|---|
| `StopCalculation` | methodology, ATR, effective multiple, adjustment, absolute and percentage distance |
| `PositionSizing` | quantity, position value, required leverage, reference price, fractional flag |
| `RiskRewardValidation` | configured floor, required reward, available reward, achieved ratio |
| `DailyLossValidation` | daily limit, planned risk, utilisation percentage |
| `ExposureValidation` | maximum exposure, planned exposure, utilisation percentage |

`RiskStatus` values: `WITHIN_LIMITS`, `LIMIT_BREACHED`, `PARTIAL`, `INSUFFICIENT_DATA`, `UNKNOWN`. `RiskRuleStatus` is separate (`PASSED`, `FAILED`, `INSUFFICIENT_DATA`, `UNKNOWN`) so a rule that cannot decide never pretends a limit was breached.

`StopMethodology` is `ATR` or `NONE`: the model names a calculation, never an exit instruction.

---

## 4. Rule System

Fifteen deterministic rules, each an immutable record:

| Id | Boundary | Passes | Fails | Insufficient |
|---|---|---|---|---|
| `QUALIFICATION_ELIGIBLE` | only a qualified trade is assessed | `QUALIFIED` | `DISQUALIFIED`, `PARTIAL` | `INSUFFICIENT_DATA`, `UNKNOWN` |
| `ATR_STOP_VALIDATION` | a positive ATR produced the stop | ATR present and stop derived | ATR ≤ 0 | ATR or stop missing |
| `MINIMUM_STOP_DISTANCE` | stop not tighter than the floor | distance ≥ minimum | distance < minimum | no stop |
| `MAXIMUM_STOP_DISTANCE` | stop not wider than the ceiling | distance ≤ maximum | distance > maximum | no stop |
| `VOLATILITY_MULTIPLIER` | adjustment resolved and bounded | factor in [0.5, 2.0] | outside the band | volatility state `UNKNOWN` |
| `MAXIMUM_RISK_PERCENT` | risk budget inside the policy | amount ≤ capital × percent | amount or percent above policy | amount ≤ 0 |
| `DAILY_RISK_LIMIT` | risk inside the daily limit | risk ≤ capital × daily % | risk above the limit | risk unavailable |
| `SESSION_LOSS_CAP` | risk inside the session cap | risk ≤ capital × session % | risk above the cap | risk unavailable |
| `MAXIMUM_OPEN_EXPOSURE` | notional inside the exposure limit | exposure ≤ capital × exposure % | exposure above the limit | no size |
| `MAXIMUM_LEVERAGE` | implied leverage inside the ceiling | leverage ≤ maximum | leverage above the ceiling | no size |
| `MAXIMUM_QUANTITY` | quantity inside the ceiling | quantity ≤ maximum | quantity above the ceiling | no size |
| `MINIMUM_REWARD_RISK` | expected move covers the floor | ratio ≥ minimum | ratio < minimum | no expected move or no stop |
| `GAP_PROTECTION` | stop clears half an ATR | distance ≥ ATR × 0.5 | distance < floor | no stop |
| `OPTION_PREMIUM_RISK` | planned risk inside the option risk budget | risk ≤ quantity × expected move | above the budget | no size, no expected move |
| `INSTRUMENT_OVERRIDE` | the configured per-instrument percent is what was applied | override absent or applied | applied percent differs | — |

Design properties:

- every rule holds only its identifier and reads policy from the rule context;
- rules never see the registry, the engine, the calculator or each other;
- missing data is `INSUFFICIENT_DATA` with a field-specific reason, never a default;
- thresholds come from the configuration, so a change of policy changes behaviour without code edits;
- the reward-to-risk rule uses the option market's expected move as the reward reference, because Phase 8 deliberately produces no target price.

---

## 5. Engine Design

`RiskEngine` holds configuration, a registry, a calculator and an injected `Executor`. It is stateless.

Flow:

1. validate the context and the rule batch (non-null, non-empty, no duplicate ids);
2. derive the plan once with `RiskCalculator`;
3. with `strictValidation`, short-circuit when no stop could be derived: every rule reports `INSUFFICIENT_DATA` and the summary names the cause;
4. otherwise evaluate every rule against the same rule context, sequentially or through the executor;
5. aggregate, build the three validation records and publish an immutable assessment.

Aggregation:

| Situation | Status |
|---|---|
| any rule failed | `LIMIT_BREACHED` |
| every rule passed | `WITHIN_LIMITS` |
| some passed, some undecided | `PARTIAL` |
| nothing passed, at least one `UNKNOWN` | `UNKNOWN` |
| nothing passed, only missing data | `INSUFFICIENT_DATA` |

Failure policy: an unexpected rule exception becomes an `UNKNOWN` rule when `failFast=false` and a `RiskEvaluationException` naming the rule when `failFast=true`.

Parallel evaluation submits each rule as a `FutureTask` and reassembles results in registry order, so output is identical to sequential evaluation. The application wires a direct executor; the layer never creates or owns a pool.

Worked example of the baseline policy: capital 100 000, 1 % risk → 1 000 budget; ATR 5.00, stable volatility, multiplier 2.0 → 10.00 stop (2 % of a 500.00 price); 1 000 / 10.00 → 100 units; notional 50 000; required leverage 0.5; expected move 25.00 → reward-to-risk 2.5.

---

## 6. Configuration

`RiskEngineConfig` is an immutable record with a builder and construction-time validation:

| Knob | Default | Purpose |
|---|---|---|
| `enabled` | true | whether risk assessment runs |
| `referenceCapital` | 100 000 | sizing reference; not account state, never synchronized |
| `maximumRiskPercent` | 1.0 | risk budget per assessment |
| `maximumDailyLossPercent` | 3.0 | daily loss ceiling |
| `maximumSessionLossPercent` | 2.0 | session loss ceiling (≤ daily) |
| `maximumOpenExposurePercent` | 100.0 | exposure ceiling |
| `defaultATRMultiplier` | 2.0 | multiple before the volatility adjustment |
| `minimumRewardRiskRatio` | 2.0 | reward-to-risk floor |
| `minimumStopDistance` | 0.05 | tightest permitted stop |
| `maximumStopDistance` | 100.0 | widest permitted stop |
| `maximumLeverage` | 5.0 | leverage ceiling |
| `maximumQuantity` | 10 000 | quantity ceiling |
| `allowFractionalQuantity` | false | fractional units permitted |
| `instrumentRiskPercentOverrides` | empty | per-instrument risk percentages |
| `parallelEvaluation` | false | evaluate rules through the executor |
| `failFast` | false | propagate a rule failure |
| `strictValidation` | true | short-circuit when no stop can be derived |

There is no runtime mutation, no time source and no environment lookup: the same context and configuration always produce the same assessment. `referenceCapital` exists because sizing needs a budget; it is a policy constant, not a broker balance, and no account is read or synchronized anywhere.

---

## 7. Validation

| Condition | Behaviour |
|---|---|
| Null event | `RiskValidationException("Event must not be null.")` |
| Null context, config or rule set | rejected, naming the field |
| Null strategy evaluation, qualification or market assessment | rejected, naming the field |
| Strategy id, instrument, interval or timestamp mismatch across parts | rejected, naming `strategyId`, `instrument`, `interval` or `timestamp` |
| Option assessment of another instrument or instant | rejected, naming `options assessment` |
| Candle of another instrument, interval or instant | rejected, naming `candle` |
| Blank or null indicator key | rejected, naming `indicators` |
| Negative ATR | `RiskValidationException("ATR must not be negative")` |
| Zero or negative capital | rejected at configuration construction (`referenceCapital`) |
| Risk percent outside (0, 100] | rejected (`maximumRiskPercent`) |
| Session cap above the daily limit | rejected (`maximumSessionLossPercent`) |
| Maximum stop not above the minimum | rejected (`maximumStopDistance`) |
| Non-positive stop distance or ATR multiple | rejected (`minimumStopDistance`, `defaultATRMultiplier`) |
| Leverage outside (0, 100] | rejected (`maximumLeverage`) |
| Quantity ceiling ≤ 0 | rejected (`maximumQuantity`) |
| Instrument override above the global risk percent | rejected (`instrument risk percentage`) |
| Null or non-positive override | rejected |
| Empty rule set, null rule, blank rule id, duplicate rule id | rejected, naming the rule |

Negative prices are rejected earlier by the Phase 3 candle and chain models; the risk layer re-checks that the reference price is positive before sizing. Every message names the offending field and contains no payload data.

---

## 8. Performance

- The calculator and the engine hold no mutable state: no counters, no caches, no clock.
- The plan is derived once per assessment; rules read it instead of recomputing.
- Parallelism is opt-in and uses only the injected executor, so no pool is created or owned here.
- The service keeps four bounded maps (64 entries each: market assessments, option assessments, strategy evaluations, pending qualifications). Nothing grows without limit and nothing is persisted.
- The registry serves immutable sorted snapshots from a concurrent map.
- Measured in this environment: the 99 risk tests run in about 0.3 s and the complete 677-test suite in about 13 s. No latency target is asserted, because no approved budget exists for this layer.

---

## 9. Testing Summary

| Test class | Tests | Coverage |
|---|---|---|
| `RiskCalculatorTest` | 13 | ATR stop, volatility widening and narrowing, neutral adjustment, quantity flooring and fractional sizing, missing ATR, zero-quantity rounding, instrument override, option-spot fallback, reward extraction |
| `RiskRulesTest` | 20 | every built-in rule: baseline pass, breach, insufficient data, optional option data, identifier and context guards |
| `RiskEngineTest` | 16 | within limits, breach, strict and lenient missing-stop handling, contained failure, fail-fast, parallel order and determinism, registry snapshot, explicit rules, empty and duplicate rule sets, null inputs, immutability, sizing numbers and diagnostics, qualification reference |
| `RiskRuleRegistryTest` | 12 | registration, duplicate detection, replacement, unknown replacement, unregistration, lookup, ordered snapshots, snapshot immutability, catalog order, independent evaluation, idempotent snapshots |
| `RiskEngineConfigTest` | 15 | defaults, every override, round trip, zero capital, invalid risk percent, session above daily, inverted stop range, negative stop, invalid leverage, invalid reward ratio, overrides, derived limits, per-instrument resolution |
| `RiskContextTest` | 11 | identity and price fallbacks, null parts, strategy/instrument/timestamp/interval mismatches, foreign and stale options, foreign candle, negative ATR, blank indicator keys |
| `RiskAssessmentServiceTest` | 9 | publication with cached and staged inputs, batch publication, stale option handling, stale indicator handling, unrelated events, disabled engine, cache clearing, null event |
| `RiskConfigurationTest` | 3 | disabled wiring, enabled wiring with 15 rules and three subscribers, unsubscription on shutdown |

All 99 tests pass; the complete suite is **677 tests, 0 failures, 0 errors, 2 skipped**.

---

## 10. Problems Encountered

| Problem | Resolution |
|---|---|
| The qualification layer publishes its event from inside its own assessment handler, so the risk service sees a qualification before its market assessment or strategy evaluation is cached | The service stages unresolved qualifications in a bounded pending map and drains them when the missing input arrives, which is verified by a test that reverses the usual order |
| Sizing a position needs capital, but account state and capital allocation are out of scope | The policy carries a `referenceCapital` constant used only for arithmetic; nothing reads, tracks or synchronizes a real account |
| Phase 8 deliberately produces no target price, so a reward-to-risk check had no numerator | The option market's expected move is the reward reference; without an option assessment the rule reports `INSUFFICIENT_DATA` instead of inventing a target |
| Rules that need the derived plan could have recomputed different numbers | The engine derives one `RiskPlan` and hands the same instance to every rule |
| A tiny budget can round a quantity down to zero, which would silently publish a meaningless size | The calculator returns a plan without sizing and the strict engine reports `INSUFFICIENT_DATA` |
| Breaches such as a too-tight gap floor cannot be produced through the calculator while it follows the same policy | The rule tests construct explicit plans, so the breach branch is exercised rather than dead |
| Compact constructors reassign record components, so lambda validation could not capture them | Validation captures final locals before the optional checks |
| A pre-existing Phase 5 concurrency test failed once in a full run | Re-ran it in isolation (passed) and re-ran the full suite (passed); the risk layer does not touch that code and the flake is reported, not hidden |

---

## 11. Known Limitations

- Every value is a boundary calculation from configured policy, not a claim about what will happen. There is no probability, expectancy, simulation or backtest.
- `referenceCapital` is a constant. There is no account balance, no open-position aggregation, no realised or unrealised P&L and no capital allocation across strategies.
- Exposure and daily-loss checks compare the single planned position with a policy limit; they do not know about other open positions, so "daily loss used so far" is always zero in this layer.
- The reward reference is the option market's expected move. For instruments without an option assessment the reward-to-risk boundary is undetermined, and the assessment is at best `PARTIAL`.
- Gap protection is an ATR fraction, not a measured overnight gap; no prior-session data is read.
- Option premium risk is a cash-equivalent proxy (`quantity × expected move`), not a real premium outlay, because the chain summary carries no per-leg premiums in this model.
- The volatility adjustment is a fixed mapping (expanding 1.25, stable 1.0, contracting 0.85), not a fitted model.
- Fractional quantities are rounded to four decimals; whole-unit quantities are floored, so the planned risk can be slightly below the budget.
- Nothing is persisted, scheduled, emitted to a UI or retained across restarts; a missed event is gone.
- The assessment covers one instrument, one interval and one instant. There is no portfolio, no correlation and no cross-strategy view.

---

## 12. Recommendations for Phase 11

Do not implement these here:

1. Treat `WITHIN_LIMITS` as a descriptive input only. A later phase must not read it as approval to trade or as a direction.
2. If order generation is next, require an explicit policy that maps a `RiskAssessment` to an order intent, and keep order construction out of the risk layer.
3. Decide how multiple assessments for the same strategy and instant are ordered before building a consumer; the risk service publishes per qualification batch and does not deduplicate.
4. Do not add account balances, portfolio state or P&L to this model. Those need their own phase, their own contracts and their own review.
5. If daily-loss accumulation is required, it needs state that this layer deliberately does not keep; put that in a dedicated accounting phase rather than widening `RiskEngineConfig`.
6. Calibrate the ATR multiple, volatility mapping and reward floor against recorded data once a replay harness exists, and version any change that alters historical assessments.
7. Keep notifications, scheduling and persistence out of risk; they belong to infrastructure phases with their own failure semantics.
8. Preserve the separation between calculation and checking: a new boundary should be a new immutable rule, not a branch inside the engine.

---

## 13. Completion Gate

Phase 10 is implemented, tested and documented. The broker adapter, market domain, indicator engine, pipeline, calendar, market intelligence, options analytics, strategy and qualification layers were not modified beyond the additive event kind required for publication. No order placement, broker execution, portfolio management, capital allocation, account synchronization, P&L tracking, trade lifecycle, trailing stop, notification, scheduler, persistence, backtesting, optimization, AI/ML, recommendation or strategy change was started, and nothing was pushed or deployed.

**STOP — awaiting architectural review. Portfolio Management, Order Generation and Execution have not been started.**
