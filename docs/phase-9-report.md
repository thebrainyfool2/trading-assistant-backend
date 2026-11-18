# Phase 9 — Trade Qualification Engine

**Status: implemented, tested and documented. Awaiting architectural review.**
No risk calculation, position sizing, capital allocation, recommendation, ranking, order placement, broker communication, portfolio, persistence, scheduler, backtesting or AI/ML functionality was started.

---

## 1. Executive Summary

Phase 8 answered one question per strategy: do its predefined conditions currently evaluate to true? The answer is a status such as `PASSED` or `FAILED`, and that is all it is — an observation about the market, not a decision.

Phase 9 adds the next, deliberately narrow step: given a strategy evaluation, is it currently **eligible for downstream consideration**? It does not decide whether a trade should be taken, does not recommend a direction, does not calculate risk, does not size a position, does not allocate capital, does not choose between strategies and does not talk to a broker.

```text
StrategyEvaluatedEvent ──────────────┐
MarketAssessmentGeneratedEvent ─────┤   TradeQualificationService
OptionsAssessmentGeneratedEvent ────┘   (aligned staging, bounded, in-memory)
                                                │
                                                ▼
                                    TradeQualificationEngine (stateless)
                                                │
                        ┌───────────────────────┼───────────────────────┐
                        ▼                       ▼                       ▼
                   Rule A                  Rule B                  Rule N
                        │                       │                       │
                        └───────────────────────┼───────────────────────┘
                                                ▼
                                        TradeQualification
                                                │
                                                ▼
                                        TradeQualifiedEvent
```

Verification: `mvn -B -ntp verify` → **578 tests, 0 failures, 0 errors, 2 skipped, BUILD SUCCESS**, of which **80 tests are new in Phase 9**. The two skipped tests are the opt-in live broker tests from Phases 2 and 3.

---

## 2. Architecture

`com.tradingassistant.qualification` (20 main classes, 7 test classes plus fixtures):

| Package | Contents |
|---|---|
| `qualification.api` | `QualificationRule` contract: `id()` and `evaluate(context)` |
| `qualification.domain` | `TradeQualification`, `TradeQualificationContext`, `QualificationRuleEvaluation`, `QualificationStatus`, `RuleStatus`, `QualificationKey`, `QualificationLedger` |
| `qualification.rule` | `QualificationRules` — 11 immutable rule records — and `QualificationSupport` read-only helpers |
| `qualification.engine` | `TradeQualificationEngine` |
| `qualification.registry` | `QualificationRuleRegistry`, `BuiltInQualificationRules` |
| `qualification.service` | `TradeQualificationService` |
| `qualification.config` | `TradeQualificationConfig` |
| `qualification.validation` | `QualificationValidator` |
| `qualification.exception` | `QualificationException`, `QualificationValidationException`, `QualificationEvaluationException` |
| `qualification` | `QualificationConfiguration` Spring wiring |

Dependency direction:

- rules depend on the qualification context, the descriptive enums they compare and nothing else;
- the engine depends on the rule contract, the registry and the configuration, never on a concrete rule;
- the registry depends on rules and the configuration only;
- the service depends on the strategy evaluation model, the two assessment models, the candle provider interface, the event contracts and the ledger;
- the qualification layer never reads broker adapters, HTTP objects, persistence, the strategy engine or another qualification.

What the layer is allowed to read is closed by construction: `TradeQualificationContext` carries a `StrategyEvaluation`, a `MarketAssessment`, an optional `OptionsAssessment`, a `SessionAssessment` and an optional `Candle`. There is no broker object, no request, no database handle and no persistence hook in the model.

---

## 3. Qualification Model

**`TradeQualification`** — `strategyId`, `strategyName`, `instrument`, `interval`, `timestamp`, `status`, `ruleEvaluations`, `summary`. Immutable, with a copied rule list and a derived `qualified()`. It contains no recommendation, ranking, score, probability, position, quantity, capital, risk measure or order field, and there is no place to put one.

**`QualificationStatus`** — the aggregate answer:

| Status | Meaning |
|---|---|
| `QUALIFIED` | every rule passed: eligible for downstream consideration |
| `DISQUALIFIED` | at least one rule failed: not eligible |
| `PARTIAL` | some rules passed while others could not be decided |
| `INSUFFICIENT_DATA` | no rule could be decided because required data was missing |
| `UNKNOWN` | rules produced no decision, for example after a contained failure |

**`RuleStatus`** is separate on purpose: a rule reports `PASSED`, `FAILED`, `INSUFFICIENT_DATA` or `UNKNOWN`, while the aggregate says what that means for the qualification. A rule that cannot decide never pretends it failed.

**`QualificationRuleEvaluation`** — rule id, status, deterministic reason, descriptive metadata (observed value and threshold). `passed()` is derived from the status, so a pass can never coexist with a non-passing status.

**`TradeQualificationContext`** — the validated input boundary. It derives the session from the market assessment and rejects any mix of parts that disagree:

- strategy evaluation instrument, timestamp and interval must equal the market assessment's;
- the supplied session phase must equal the market assessment session phase;
- an option assessment must belong to the same instrument;
- a candle must match the instrument, interval and instant.

The single deliberate exception is the option assessment **timestamp**: staleness is a qualification condition, so it is reported by a rule (`OPTIONS_FRESH`) instead of refusing the context. That keeps the rule observable and testable.

**`QualificationKey`** — `strategyId` + `instrument` + `timestamp`. It is the identity used for duplicate suppression and contains no outcome.

**`QualificationLedger`** — bounded, thread-safe memory of qualified keys (default 1 024, LRU eviction). It is a collaborator owned by the service, not global mutable state, not a cache of assessments and not persistence.

---

## 4. Qualification Rules

Eleven deterministic rules, each an immutable record:

| Id | Condition | Passes | Fails | Insufficient |
|---|---|---|---|---|
| `SESSION_OPEN` | session phase is tradeable | `OPENING`, `REGULAR`, `CLOSING` | `PRE_OPEN`, `AFTER_MARKET`, `UNKNOWN` | calendar status not `READY` |
| `MARKET_ASSESSMENT_READY` | market assessment fully ready | `READY` | `PARTIAL` | `INSUFFICIENT_DATA`, `UNKNOWN` |
| `OPTIONS_REQUIRED` | option data present when required | present and `READY`; absent and not required | present but not `READY` | required and absent |
| `OPTIONS_FRESH` | option data describes the evaluated instant | timestamps equal; absent and not required | timestamps differ | required and absent |
| `SESSION_ALIGNED` | market and option assessments agree on the phase | phases equal | phases differ | option session not `READY`; required and absent |
| `STRATEGY_STATUS_KNOWN` | strategy did not report `UNKNOWN` | any status except `UNKNOWN` | `UNKNOWN` | — |
| `STRATEGY_STATUS_PASSED` | strategy actually passed | `PASSED` | `FAILED`, `PARTIAL`, `UNKNOWN` | `INSUFFICIENT_DATA` |
| `MARKET_CONFIDENCE` | confidence reaches the configured floor | level ≥ floor | level < floor | level `UNKNOWN` |
| `MARKET_PARTICIPATION` | participation reaches the configured floor (default `AVERAGE`) | ≥ `AVERAGE` | `WEAK` | `UNKNOWN` |
| `OPTION_LIQUIDITY` | option liquidity is usable | `LIQUID`, `MODERATE`; absent and not required | `ILLIQUID` | `UNKNOWN`; required and absent |
| `DUPLICATE_QUALIFICATION` | this strategy/instrument/instant was not already qualified | key not in the ledger | key already recorded | — |

Design properties:

- every rule holds configuration only, so one instance serves every qualification;
- rules never see the registry, the engine or each other, and cannot influence another rule's input;
- missing data is reported as `INSUFFICIENT_DATA` with a field-specific reason, never replaced by a default;
- thresholds live in the rule instance or the configuration, so an evaluation is reproducible;
- `OPTIONS_*` rules accept a `required` flag so a non-option strategy is not blocked by the absence of a chain, while an option-dependent qualification can demand one.

Participation is used as the market liquidity proxy because the market assessment describes volume participation and deliberately has no liquidity state; option liquidity comes from the chain's `LiquiditySummary`.

---

## 5. Registry

`QualificationRuleRegistry` is a thread-safe map of immutable rules:

- `register` — rejects a null rule, a blank id and a duplicate id, and enforces `maxRules`;
- `replace` — atomically swaps a rule with the same id and rejects an unknown id;
- `unregister`, `lookup`, `contains`, `size`;
- `snapshot()` — immutable list sorted by identifier, so one qualification always sees one consistent, ordered rule set.

Duplicate detection uses `putIfAbsent`, so two concurrent registrations of the same id cannot both succeed. No rule is mutated in place: changing a threshold means registering a replacement.

`BuiltInQualificationRules` is the deterministic catalog. It assembles the eleven rules from the configuration and the ledger, with explicit ordering so event payloads, reports and tests always agree.

---

## 6. Qualification Engine

`TradeQualificationEngine` holds configuration, the registry and an injected `Executor`. It is stateless.

Flow:

1. validate the context and the rule batch (non-null, non-empty, no duplicate ids, within `maxRules`);
2. when `strictValidation` is on and an option assessment is required but absent, report `INSUFFICIENT_DATA` for every rule without evaluating them, naming the missing input once;
3. otherwise evaluate every rule against the same immutable context, sequentially or through the executor;
4. aggregate the outcomes and build the immutable qualification.

Aggregation:

| Situation | Status |
|---|---|
| any rule failed | `DISQUALIFIED` |
| every rule passed | `QUALIFIED` |
| some passed, some undecided, `allowPartialQualification=true` | `QUALIFIED`, with the undecided rules still visible and the summary saying so |
| some passed, some undecided | `PARTIAL` |
| nothing passed, at least one `UNKNOWN` | `UNKNOWN` |
| nothing passed, only `INSUFFICIENT_DATA` | `INSUFFICIENT_DATA` |

Failure policy: an unexpected rule exception becomes an `UNKNOWN` rule evaluation when `failFast=false`, and a `QualificationEvaluationException` naming the rule when `failFast=true`.

Parallel evaluation submits each rule to the injected executor as a `FutureTask` and reassembles results in registry order, so the output is identical to sequential evaluation. The application wires a direct executor; the layer never creates or owns a pool.

---

## 7. Event Integration

Consumed:

- `StrategyEvaluatedEvent` — the evaluations to qualify;
- `MarketAssessmentGeneratedEvent` — the market description required for a context;
- `OptionsAssessmentGeneratedEvent` — the chain description when one exists.

Published: `TradeQualifiedEvent(List<TradeQualification>, occurredAt)`.

`MarketEvent` is sealed, and a sealed interface in the unnamed module requires permitted subtypes in the same package, so the event lives in `com.tradingassistant.pipeline.event`. This is additive: one permitted record and one `MarketEventType` constant (`TRADE_QUALIFIED`). No existing event, field or meaning changed.

Service behaviour:

| Situation | Behaviour |
|---|---|
| evaluation arrives after its assessment is cached | qualified immediately, one event per assessment batch |
| evaluation arrives before its assessment | staged in a bounded pending map, qualified when the assessment arrives |
| assessment of another instant already cached | treated as absent; the evaluation is staged, not mismatched |
| option assessment of another instant | not attached; the option rules report the missing input |
| candle of another instant | ignored; the context receives no candle |
| duplicate key already qualified | suppressed, counted, nothing published |
| earlier result was not qualified | not recorded in the ledger, so a later event may qualify the same key |
| unrelated event | ignored |
| engine disabled | every event is ignored and nothing is staged |

The staging exists because the bus is synchronous and the strategy service publishes its `StrategyEvaluatedEvent` from inside its own assessment handler. A consumer registered after the strategy layer therefore sees the evaluation before it sees the assessment. Staging makes the qualification independent of subscription order and of that nested publication, which is verified by a dedicated test.

Bounded state: 64 market assessments, 64 option assessments, 64 pending batches, 1 024 ledger keys — all evicting, none persistent.

---

## 8. Validation

| Condition | Behaviour |
|---|---|
| Null event | `QualificationValidationException("Event must not be null.")` |
| Null context | rejected, naming `context` |
| Null strategy evaluation, market assessment or session | `NullPointerException` naming the field |
| Strategy evaluation and market assessment instrument mismatch | rejected, naming `instrument` |
| Timestamp mismatch | rejected, naming `timestamp` |
| Interval mismatch | rejected, naming `interval` |
| Session phase contradicting the market assessment | rejected, naming `session` |
| Option assessment of another instrument | rejected, naming `options assessment` |
| Candle of another instrument, interval or instant | rejected, naming `candle` |
| Null rule, blank rule id | rejected, naming `rule` / `rule id` |
| Duplicate rule id in a batch | rejected, naming the id |
| Duplicate rule id on registration | rejected, naming the id |
| Replacement of an unknown rule id | rejected, naming the id |
| More rules than `maxRules` | rejected by the registry, the batch validator and the configuration |
| Empty rule set | rejected |
| Null minimum confidence, or `UNKNOWN` as a minimum | rejected, naming `minimumConfidence` |
| Out-of-range `maxRules` | rejected by the configuration |
| Null minimum participation, or `UNKNOWN` as a minimum | rejected at rule construction |

Every message names the offending field or identifier and contains no payload data.

---

## 9. Configuration

`TradeQualificationConfig` is an immutable record with a builder and construction-time validation:

| Knob | Default | Purpose |
|---|---|---|
| `enabled` | true | whether the service qualifies at all |
| `strictValidation` | true | report a missing required option assessment once, without evaluating rules |
| `failFast` | false | propagate an unexpected rule failure instead of containing it |
| `minimumConfidence` | `HIGH` | lowest acceptable market confidence level |
| `requireOptions` | false | whether an option assessment is required |
| `allowPartialQualification` | false | report a result whose only undecided rules are data-dependent as `QUALIFIED` |
| `maxRules` | 16 | maximum rules in one qualification (1–256) |
| `parallelEvaluation` | false | evaluate rules through the injected executor |

There is no runtime mutation. `UNKNOWN` is rejected as a minimum confidence because "at least unknown" would accept every level and silently disable the check.

---

## 10. Performance

- The engine holds no mutable state: no counters, no caches, no time source. Identical inputs produce equal qualifications.
- Rules are immutable and evaluated once per qualification; no rule recomputes another rule's work.
- Parallelism is opt-in and uses only the injected executor, so no thread pool is created or owned by this layer.
- The registry serves immutable sorted snapshots from a concurrent map; evaluation never blocks registration beyond one map read.
- Every service cache is bounded: 64 assessments per kind, 64 pending batches, 1 024 ledger keys. A long-running process cannot grow without limit.
- Measured in this environment: the 80 qualification tests run in under a second and the complete 578-test suite in about 13 s. No latency target is asserted, because no approved budget exists for this layer.

---

## 11. Testing

| Test class | Tests | Coverage |
|---|---|---|
| `QualificationRulesTest` | 23 | every built-in rule: pass, fail, insufficient, optional and required option behaviour, duplicate detection, id exposure, invalid construction |
| `TradeQualificationEngineTest` | 18 | qualified, disqualified, partial, insufficient, unknown, fail-fast propagation, partial-as-qualified policy, strict and lenient option handling, parallel order and determinism, explicit rule sets, empty/duplicate/oversized batches, null inputs, result immutability |
| `QualificationRuleRegistryTest` | 9 | registration, duplicate detection, replacement, unknown replacement, unregistration, lookup, sorted snapshots, snapshot immutability, rule limit, null and blank rules |
| `TradeQualificationServiceTest` | 12 | publication with cached and staged assessments, duplicate suppression, disqualified publication, non-qualified repetition, aligned and stale option data, batch publication, unrelated events, disabled engine, cache clearing, null event |
| `TradeQualificationContextTest` | 9 | derived identity and session, null parts, foreign instruments, timestamp and interval mismatch, session contradiction, foreign option instrument, candle mismatch, permitted stale option timestamp |
| `TradeQualificationConfigTest` | 6 | defaults, every override, builder round trip, invalid minimum confidence, invalid rule limits, null rejection |
| `QualificationConfigurationTest` | 3 | disabled wiring, enabled wiring with eleven rules and two subscribers, unsubscription on shutdown |

All 80 tests pass; the complete suite is **578 tests, 0 failures, 0 errors, 2 skipped**.

---

## 12. Problems Encountered

| Problem | Resolution |
|---|---|
| Listener order decides whether the market assessment is known when a strategy evaluation arrives, and the strategy service publishes from inside its own assessment handler | The service stages evaluations it cannot yet qualify in a bounded pending map and drains the matching batch when the assessment arrives, which is verified by a test that reverses the usual order |
| Qualifying each evaluation separately produced one event per strategy instead of one per batch | The service now groups a strategy event by instrument, instant and interval and qualifies each group at once |
| An early generic `option assessment` rule made option-dependent and non-option strategies impossible to distinguish, because `StrategyEvaluation` does not carry the definition's declared inputs | The three option rules take a `required` flag from the configuration, and the strategy status rules still ensure `INSUFFICIENT_DATA` strategies never qualify |
| Two `Executor` beans made constructor injection ambiguous | The qualification bean is named `qualificationExecutor` and the engine bean qualifies it explicitly; the Spring wiring test proves both configurations resolve |
| A compact constructor reassigned record components, so lambdas validating the optional parts could not capture them | Validation captures final locals before the optional checks |
| Duplicate suppression could have suppressed a better-informed second evaluation of the same candle | Only `QUALIFIED` results are recorded, and only the event is suppressed; a disqualified attempt leaves the key free so a later, aligned attempt can still qualify |
| Rejecting a stale option timestamp at the context boundary would have made the freshness rule untestable and unreachable | The context rejects a foreign instrument but permits a stale timestamp, so `OPTIONS_FRESH` reports it as a rule outcome |

---

## 13. Known Limitations

- Qualification is policy, not truth: it applies configured rules to one evaluation and says whether that evaluation is eligible. It never says the strategy is good, profitable or likely to work.
- No risk, position size, capital, stop, target, order or execution concept exists anywhere in the model, by design.
- The ledger is in-memory and bounded. Duplicate suppression is best-effort within the remembered window and does not survive a restart.
- Duplicate keys are `strategyId` + `instrument` + `timestamp` only; two different intervals at the same instant are treated as distinct keys, and two different strategy definitions sharing an id are indistinguishable.
- `requireOptions` is configured, not inferred: `StrategyEvaluation` does not expose the definition's category or declared inputs, so option dependence cannot be derived from the evaluation alone.
- Default rules do not read the optional candle. It is carried in the context for rules that need price or volume, but no built-in rule currently requires it.
- Thresholds such as the participation floor and the confidence floor are fixed defaults, not calibrated values.
- Qualification runs when an event arrives; there is no scheduler, replay, backtesting or historical comparison.
- No persistence, audit trail or notification exists: a missed `TradeQualifiedEvent` is gone.
- Qualification is single-instant and single-instrument; no cross-strategy or cross-interval reasoning happens here.

---

## 14. Recommendations for Phase 10

Do not implement these here:

1. Treat `QUALIFIED` as an eligibility input only. A risk phase must not infer direction, conviction or expected return from it.
2. Keep risk calculation and position sizing as separate, explicitly reviewed models with their own immutable results; do not extend `TradeQualification` with risk or size fields.
3. Decide the unit of risk explicitly (per trade, per instrument, per session) and the ordering between qualification and risk evaluation before writing code.
4. Require an explicit policy for what happens when a qualification is superseded by a later event for the same candle, and document whether risk consumes the latest or the first.
5. Keep capital allocation and portfolio state out of the risk phase until both have their own review; they depend on account state that does not exist yet.
6. If duplicate suppression must survive a restart, move the ledger behind a store in a dedicated storage phase rather than widening this layer.
7. Calibrate the confidence and participation floors against recorded assessments once a replay harness exists, and version any change that alters historical qualifications.
8. Keep the qualification layer free of scheduling, notification and execution; those are later phases with their own contracts.

---

## 15. Completion Gate

Phase 9 is implemented, tested and documented. The broker adapter, market domain, indicator engine, pipeline, calendar, market intelligence, options analytics and strategy layers were not modified beyond the additive event kind required for publication. No recommendation, ranking, risk calculation, position sizing, capital allocation, order placement, broker communication, portfolio, backtesting, AI/ML, persistence, scheduler or notification functionality was started, and nothing was pushed or deployed.

**STOP — awaiting architectural review. Risk Management & Position Sizing has not been started.**
