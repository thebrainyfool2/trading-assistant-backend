# Trading Assistant Backend

Spring Boot 4.1.2 application backend for the Trading Assistant platform. Java 21, Maven, Spring MVC, Bean Validation, Spring Security, MongoDB and Actuator.

This backend is maintained as an independent Git repository. The Android client lives in the separate repository under the workspace `frontend/` directory.

Phase 1 provides the operational backend foundation. Phase 2 adds a conditional, read-only Angel One SmartAPI integration behind a broker abstraction; it is disabled by default. Phase 3 adds the broker-independent market domain. Phase 4 adds sixteen technical indicators. Phase 5 adds candle aggregation, long-lived indicator sessions, immutable events, session lifecycle management and exchange calendars. Phase 6 adds market intelligence. Phase 7 adds option-chain and options analytics. Phase 8 adds deterministic strategy evaluation. Phase 9 adds trade qualification. Phase 10 adds risk and position sizing. Phase 11 contains a dormant execution model with no Spring beans and no API wiring. Phase 12 adds the single-owner application layer: MongoDB persistence for watchlists, preferences, notifications and settings, owner-scoped REST APIs, an in-memory read model fed by engine events, maintenance jobs, OpenAPI/observability and a seven-screen Kotlin/Jetpack Compose client. Phase 13 adds the live data foundation: watchlist-driven subscriptions, index/future aliases, all decision indicators, configurable holiday calendars and market-data freshness. Phase 14 adds the deterministic recommendation engine: explainable `BUY CALL`, `BUY PUT` or `NO TRADE` results from existing engine outputs, configurable scoring and option selection, and separate recommendation events. Phase 15 adds deterministic monitoring of manually recorded trades: event-driven rescoring, five monitoring states, four exit recommendations, persisted history/alerts and read-only monitoring APIs. Phase 16 adds instrument search, a recommendation read API with a reference entry/target/stop plan, manual fill registration/close and premium-level target/stop alerts; the Android client now has search, recommendation, fill and premium screens, and a fill is pinned to the recommendation timestamp the owner acted on. There is no AI, ML, backtesting, automated execution or order endpoint. See [`docs/phase-16-report.md`](docs/phase-16-report.md).

## Prerequisites

- JDK 21
- Maven 3.9.16 or later (the tested version is 3.9.16)
- MongoDB 6+ or an Atlas cluster for a running application (the test suite uses an in-process Mongo-compatible server)
- Docker only if you want to build the production image locally
- Android SDK 35 and JDK 17+ only for the separate `frontend/` client

## Build and test

```bash
mvn -B -ntp verify
mvn -B -ntp package
```

The Spring Boot Maven plugin produces an executable jar and a `META-INF/build-info.properties` file. The build version is exposed by `/api/v1/system/info`.

## Run locally

The `local` profile is the default. It falls back to
`mongodb://localhost:27017/tradingassistant` when `MONGODB_URI` is absent. There
is no login flow and no JWT secret to configure.

```bash
# Optional override:
export MONGODB_URI='mongodb://localhost:27017/tradingassistant'
mvn spring-boot:run
```

Or run the packaged jar:

```bash
java -jar target/trading-assistant-backend-0.1.0.jar
```

Useful local checks:

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/api/v1/system/status
curl -i http://localhost:8080/api/v1/system/info
curl -i -H 'X-Request-Id: local-check-001' http://localhost:8080/api/v1/system/info
curl -i http://localhost:8080/api/v1/unknown
curl -i -X POST http://localhost:8080/api/v1/system/info
```

Run under the production profile with a Render-style port:

```bash
PORT=10000 SPRING_PROFILES_ACTIVE=prod java -jar target/trading-assistant-backend-0.1.0.jar
```

## Configuration

| File | Purpose |
|---|---|
| `src/main/resources/application.yml` | Shared settings, safe defaults, logging pattern, Actuator exposure, application metadata |
| `src/main/resources/application-local.yml` | Local development logging levels |
| `src/main/resources/application-prod.yml` | Production port from `PORT` and production logging levels |

Important behavior:

- Server binds to `0.0.0.0` by default so Render can reach it.
- `PORT` defaults to `10000` in the production profile; local uses `SERVER_PORT` or `8080`.
- Graceful shutdown is enabled with a 20-second lifecycle timeout.
- Actuator exposes `health`, `info`, `metrics` and, in `prod`, `prometheus`.
- Health details and components are hidden.
- Error responses never include exception messages, stack traces, or exception class names.
- The console log pattern includes the request ID from MDC.
- JSON responses are compressed when they exceed 1 KB.
- Configuration values are externalized through environment variables; defaults are non-secret.

No `.env` file is required or read automatically. Supply environment variables through the shell, IDE run configuration, Render environment variables, or a deployment secret store. A populated `.env` file is never read by Spring Boot and must not be committed.

Application environment variables:

| Variable | Required | Purpose |
|---|---|---|
| `MONGODB_URI` | production | Connection string including `/tradingassistant` |
| `CORS_ALLOWED_ORIGINS` | no | Comma-separated browser origins |
| `PLATFORM_JOBS_ENABLED` | no | Maintenance scheduling, default `true` |
| `TOKEN_CLEANUP_CRON` | no | Default hourly at `:15` UTC |
| `NOTIFICATION_RETENTION_DAYS` | no | Default `30` |
| `TOKEN_RETENTION_DAYS` | no | Default `7` |
| `LIVE_MARKET_INSTRUMENTS` | no | Baseline tokens/aliases |
| `LIVE_MARKET_INCLUDE_WATCHLISTS` | no | Default `true` |
| `LIVE_MARKET_MAX_INSTRUMENTS` | no | Default `200` |
| `LIVE_TICK_MAX_AGE` / `LIVE_CANDLE_MAX_AGE` | no | Freshness thresholds |
| `NSE_HOLIDAYS` / `BSE_HOLIDAYS` / `MCX_HOLIDAYS` | no | ISO holiday dates |

See [`docs/deployment.md`](docs/deployment.md) and [`docs/mongodb.md`](docs/mongodb.md).

## Package structure

```text
com.tradingassistant
├── TradingAssistantApplication.java
├── common
│   ├── api       Response envelopes, error catalog, field violations, request context
│   ├── error     Centralized exception handling
│   └── web       Request correlation servlet filter
├── config        Validated, typed application configuration
├── broker
│   ├── api       BrokerProvider, AuthenticationProvider, SessionProvider, MarketDataProvider
│   ├── model     Token-free broker-neutral quotes, candles, instruments, sessions and feed ticks
│   ├── config    Conditional wiring and validated SmartAPI configuration
│   ├── error     Broker-to-platform exception translation
│   ├── exception Safe broker error categories
│   └── angelone  SmartAPI adapter: auth, session, REST market data, WebSocket feed
├── market
│   ├── domain    Immutable instruments, quotes, candles, depth, ticks and options
│   ├── mapping   Provider-to-domain mappers and boundary conversion
│   ├── service   Instrument, quote, historical, feed and market-data services
│   ├── time      Indian market session and UTC/IST conversion policy
│   └── exception Application market-data failures
├── indicator
│   ├── api       Indicator lifecycle and factory contracts
│   ├── domain    Immutable results, series, statuses and value keys
│   ├── engine    Pluggable registry and calculation facade
│   ├── calculator Sixteen trend, momentum, volatility, strength and volume calculators
│   ├── config    Validated parameters and selectable price sources
│   ├── validation Candle ordering, OHLC, volume and gap checks
│   ├── exception Indicator validation and calculation failures
│   └── util      Decimal math, candle math and rolling windows
├── pipeline
│   ├── aggregation Tick-to-candle aggregation, policies and bucket alignment
│   ├── calendar    Exchange calendars, trading days, sessions and holiday hooks
│   ├── event       Sealed immutable events, publisher contract and event bus
│   ├── session     Indicator session registry, lifecycle policies and snapshots
│   ├── exception   Safe pipeline, session and delivery failures
│   └── (root)      Processing pipeline, subscriptions, lifecycle manager and wiring
├── intelligence
│   ├── api         MarketAnalyzer extension contract
│   ├── analysis    Trend, momentum, volatility, participation, structure and session analyzers
│   ├── config      Immutable, validated intelligence configuration and warm-up policy
│   ├── domain      Analysis context, immutable assessment model and statuses
│   ├── engine      Composition, overall status and confidence
│   ├── session     Bounded per-instrument analysis history
│   ├── service     Indicator-update to assessment event adapter
│   └── exception   Intelligence validation failures
├── options
│   ├── analysis    Greeks, open interest, PCR, max pain, expected move and liquidity analyzers
│   ├── chain       Chain builder, moneyness and expiry classifiers
│   ├── config      Immutable, validated options analytics configuration
│   ├── domain      Chain, assessment, Greeks, volatility, interest and liquidity models
│   ├── engine      Composition and overall assessment status
│   ├── greeks      Black-Scholes-Merton pricing and sensitivity engine
│   ├── math        Decimal elementary functions and expiry time
│   ├── service     Chain provider and options assessment event adapter
│   ├── volatility  IV solver, per-leg resolver and bounded IV history
│   └── exception   Options validation failures
├── strategy
│   ├── api         StrategyRule contract
│   ├── condition   Read-only condition lookups used by rules
│   ├── config      Immutable, validated strategy engine configuration
│   ├── domain      Definitions, evaluations, rule outcomes and context
│   ├── engine      Composition, status aggregation and optional parallel evaluation
│   ├── registry    Thread-safe registry and built-in sample strategies
│   ├── rule        Indicator, assessment and option rule records
│   ├── service     Market data staging and assessment-to-evaluation adapter
│   ├── validation  Definition, context and batch validation
│   └── exception   Strategy validation and evaluation failures
├── qualification
│   ├── api         QualificationRule contract
│   ├── config      Immutable, validated qualification configuration
│   ├── domain      Qualifications, rule outcomes, context, key and bounded ledger
│   ├── engine      Rule evaluation, status aggregation and optional parallel evaluation
│   ├── registry    Thread-safe rule registry and built-in qualification rules
│   ├── rule        Session, assessment, strategy, confidence and liquidity rules
│   ├── service     Assessment staging and strategy-to-qualification adapter
│   ├── validation  Context, rule batch and limit validation
│   └── exception   Qualification validation and evaluation failures
├── risk
│   ├── api         RiskRule contract
│   ├── config      Immutable, validated risk policy
│   ├── domain      Assessments, context, plan, stop, sizing and validations
│   ├── engine      Composition, status aggregation and optional parallel evaluation
│   ├── math        Pure stop and position-size derivation
│   ├── registry    Thread-safe rule registry and built-in risk rules
│   ├── rule        Qualification, stop, risk, leverage, exposure and reward rules
│   ├── service     Aligned staging and qualification-to-assessment adapter
│   ├── validation  Context and rule batch validation
│   └── exception   Risk validation and evaluation failures
├── execution
│   ├── api         ExecutionRule contract
│   ├── broker      Broker adapter contract, paper adapter and adapter registry
│   ├── config      Immutable execution policy and paper/live mode
│   ├── domain      Orders, executions, reports, positions, portfolio and results
│   ├── engine      Create, validate, submit, apply and publish
│   ├── event       Execution event sink contract
│   ├── order       Order store, state machine and request factory
│   ├── portfolio   Thread-safe portfolio and change description
│   ├── position    Pure position arithmetic
│   ├── registry    Rule registry, built-in rule catalog and broker registry
│   ├── rule        Quantity, price, stop, target, session, margin and limit rules
│   ├── service     Pipeline staging, marks and intent-to-request adapter
│   ├── validation  Request, order-id, rule-batch and mode validation
│   └── exception   Execution validation, broker and general failures
├── platform
│   ├── api           Controllers, request/response DTOs, pages and OpenAPI
│   ├── config        Mongo timeouts, index initializer, event bridge, scheduling
│   ├── security      Fixed local principal, CORS, and retained legacy auth types
│   ├── user          Accounts, roles, status and bootstrap admin
│   ├── watchlist     Per-account watchlists
│   ├── preferences   Theme, interval and notification preferences
│   ├── settings      Application-wide settings
│   ├── notification  In-app notification records
│   ├── history       Order, trade and portfolio-snapshot persistence
│   ├── executionapp  Order use case, portfolio query and engine gateway
│   ├── readmodel     Bounded analysis projection fed by engine events
│   ├── jobs          Maintenance jobs (no trading)
│   └── error         Platform exception mapping
└── system
    ├── api       System controller and HTTP DTO
    ├── application System information use case
    └── domain    Framework-independent system information model
```

The code follows constructor injection, explicit DTO separation and a one-way dependency: `platform` may use engine types, but no engine package imports `platform`. Phase 12 adds MongoDB repositories and documents; engine caches and assessments remain in memory.

## Endpoints

### `GET /actuator/health`

Returns Actuator's health response. Render uses this path as its HTTP health check.

### `GET /api/v1/system/info`

Returns application name, Maven build version, active profile, and configured API version inside the standard success envelope.

### `GET /api/v1/system/status` (public)

Reports database reachability, engine availability, read-model size, version and uptime. It never exposes credentials or connection strings.

The complete Phase 12 contract is in [`docs/api-reference.md`](docs/api-reference.md) and the generated OpenAPI description at `/v3/api-docs` (Swagger UI at `/swagger-ui.html`).

## Docker

The multi-stage `Dockerfile`:

1. builds the application with Maven 3.9.16 and Temurin 21 in a builder stage;
2. runs the tests during the image build;
3. copies only the executable jar into a Temurin 21 JRE Alpine runtime;
4. runs as a non-root user;
5. exposes port 10000 and starts the jar with container-aware memory limits.

Build and run locally if Docker is available:

```bash
docker build -t trading-assistant-backend .
docker run --rm -p 10000:10000 -e PORT=10000 -e SPRING_PROFILES_ACTIVE=prod trading-assistant-backend
```

The Dockerfile is at this repository's root. Render builds it from the connected repository with `dockerfilePath: ./Dockerfile`.

## Render deployment

This repository root contains the Blueprint. Render reads it from the connected Git repository:

```yaml
services:
  - type: web
    name: trading-assistant-api
    runtime: docker
    dockerfilePath: ./Dockerfile
    healthCheckPath: /actuator/health
```

**Live deployment:** <https://trading-assistant-api-x856.onrender.com>

- Health: <https://trading-assistant-api-x856.onrender.com/actuator/health>
- System info: <https://trading-assistant-api-x856.onrender.com/api/v1/system/info>
- Service ID: `srv-dbut8f9srm7s73ftpni0`
- Region: Oregon
- Repository visibility: public, so Render can build without a GitHub App connection in the new Render account

The service definition uses the free compute plan, which is appropriate for Phase 1/2/3 validation but not for continuous monitoring. Phase 2 deploys with the broker disabled unless `SMARTAPI_ENABLED=true` and all required values are set in Render. Automatic deploys from `main` are active; a documentation push during verification triggered a successful deployment. Manual deploys can also be triggered with `render deploys create srv-dbut8f9srm7s73ftpni0`. See the reports for the networking implications.

## SmartAPI integration (Phase 2)

Phase 2 establishes read-only broker communication. Application code depends on `BrokerProvider` and the other interfaces in `com.tradingassistant.broker.api`; it never depends directly on SmartAPI classes. Order placement, modification, cancellation, GTT, basket orders, portfolio, positions and holdings are deliberately absent.

The integration is **disabled by default** so the Phase 1 operational endpoints continue to start without broker credentials. Enable it only in an environment where all required values are present:

```bash
export SMARTAPI_ENABLED=true
export SMARTAPI_API_KEY='...'
export SMARTAPI_CLIENT_CODE='...'
export SMARTAPI_PIN='...'
export SMARTAPI_TOTP_SECRET='...'
# Required request metadata:
export SMARTAPI_CLIENT_LOCAL_IP='...'
export SMARTAPI_CLIENT_PUBLIC_IP='...'
export SMARTAPI_MAC_ADDRESS='...'

mvn spring-boot:run
```

When enabled, startup validates all required values, including the client metadata headers, and fails fast without printing them. The metadata headers are mandatory in practice: a live login without them was rejected with `AB1012`. When disabled, no `BrokerProvider` bean is created. See [`.env.example`](.env.example) for the full variable list. Spring Boot does not read `.env` automatically; load values through your shell, IDE, container, or Render environment.

Capabilities:

- TOTP login via the maintained `otp-java` library; OTP values are never logged or exposed.
- Thread-safe session manager with duplicate-login prevention, local expiry tracking, refresh-token renewal and re-login fallback.
- Session expiry is capped at the next midnight in `Asia/Kolkata`, following the documented SmartAPI rule, even when the JWT claim extends further.
- Live quotes (LTP/OHLC/FULL, including best-five depth for FULL), historical candles, the public instrument master, and the Streaming 2.0 WebSocket feed.
- Bounded retries with exponential backoff only for safe read-only calls; login, refresh and logout are never automatically retried.
- Safe error translation into the platform error contract; no tokens, PINs, OTPs or raw broker bodies are logged or returned.

The full discovery, architecture, endpoint verification and limitations are in [`docs/phase-2-report.md`](docs/phase-2-report.md).

## Market domain (Phase 3)

Phase 3 is the application's internal market language. SmartAPI DTOs remain in the adapter; the application consumes only immutable domain models and domain services.

- `market.domain`: `Instrument`, `Quote`, `Candle`, `MarketDepth`, `DepthLevel`, `Tick`, `OptionContract`, `Exchange`, `Segment`, `Interval`, `MarketStatus`, `QuoteMode`, `TickMode`, `OptionType` and `Currency`.
- `market.mapping`: dedicated instrument, quote, candle, depth, tick and option-contract mappers, plus the market-time policy.
- `market.service`: `InstrumentService`, `QuoteService`, `HistoricalDataService`, `MarketDataService`, domain feed listener and feed handle. Services are wired only when `SMARTAPI_ENABLED=true`.
- Domain timestamps are UTC `Instant`s; SmartAPI request times are converted to `Asia/Kolkata` only at the provider boundary. Quotes use application receipt time because the provider quote payload has no timestamp.
- Prices use `BigDecimal`; no floating-point arithmetic or silent rounding is applied to market values. Quantities and volumes are integral.
- No cache, database, scheduler, strategy, risk engine, Greeks, option chain or order operation is included.

The full domain documentation, mapping architecture, validation/time/precision policy and test results are in [`docs/phase-3-report.md`](docs/phase-3-report.md).

## Technical indicator engine (Phase 4)

Phase 4 is a standalone mathematical layer over Phase 3 candles. It does not know about strategies, signals, positions, users, options, risk or broker sessions.

- `indicator.api`: `Indicator` lifecycle and `IndicatorFactory` extension point.
- `indicator.domain`: immutable `IndicatorResult`, `IndicatorSeries`, status and named value keys.
- `indicator.engine`: `IndicatorEngine` registry for historical calculation and independent incremental instances.
- `indicator.calculator`: SMA, EMA, WMA, RSI, MACD, Stochastic RSI, ATR, Bollinger Bands, Keltner Channels, Donchian Channels, ADX, DMI, VWAP, OBV, CMF and Supertrend.
- `indicator.config`: validated periods, smoothing, multiplier, deviation and `PriceSource`.
- `indicator.validation`: null, OHLC, volume, duplicate, ordering, insufficient-history and interval-gap checks.
- Historical calculations retain warm-up rows; incremental updates reuse state and reject duplicate/out-of-order candles.
- All outputs are immutable named-value maps. Prices and calculations use `BigDecimal`; division and square root use DECIMAL128.
- No strategy, signal, trade, risk, option, persistence, scheduler or notification logic is included.

The full architecture, formulas, defaults, validation, precision, performance and testing report is in [`docs/phase-4-report.md`](docs/phase-4-report.md).

## Market processing infrastructure (Phase 5)

Phase 5 turns the Phase 2-4 libraries into a running pipeline. It contains infrastructure only: no interpretation, no strategy and no signals.

- `pipeline.aggregation`: `CandleAggregator`, `AggregationPolicy`, `AggregationResult`, `AggregationStatus` and session-anchored or market-date bucket aligners for one through sixty minutes and one day.
- `pipeline.session`: `IndicatorSessionRegistry`, `IndicatorSession`, `IndicatorSessionKey`, `SessionRegistryPolicy`, `IndicatorSessionSnapshot` and a lifecycle listener. One long-lived indicator instance per instrument, interval and canonicalized configuration.
- `pipeline.event`: sealed `MarketEvent` (`TickReceivedEvent`, `CandleCompletedEvent`, `IndicatorUpdatedEvent`, `SessionStartedEvent`, `SessionExpiredEvent`), `MarketEventBus`, publisher and listener contracts.
- `pipeline.calendar`: `MarketCalendar`, `WeeklyMarketCalendar`, `TradingSession`, `SessionWindow` and `MarketCalendarRegistry` for NSE, BSE, MCX, CDS and NCDEX.
- `pipeline`: `MarketProcessingPipeline` (tick -> aggregator -> candle -> session -> event), `MarketSessionLifecycleManager` (start, drain, idle sweep, shutdown, recovery hook) and conditional Spring wiring.
- Aggregation never fills an empty interval, never restates a closed candle and never invents a candle outside a session. Duplicate and out-of-order ticks are rejected by sequence number, and future timestamps are bounded by a skew tolerance.
- Indicators and all other state remain inside sessions; the registry never evicts silently and expires only idle sessions or on shutdown.
- No intelligence, strategy, signal, risk, option, Greek, persistence, database, scheduler, notification, backtesting or trade-monitoring logic is included in the pipeline.

The full architecture, aggregation and session policies, event model, validation and concurrency strategy, and testing report is in [`docs/phase-5-report.md`](docs/phase-5-report.md).

## Market intelligence engine (Phase 6)

Phase 6 describes the market. It consumes completed candles, indicator results and calendar session context and produces one immutable `MarketAssessment` per candle; it never produces a signal, a recommendation, a score, a position size or a risk measure.

- `intelligence.domain`: `MarketAssessment` with separate `TrendAssessment`, `MomentumAssessment`, `VolatilityAssessment`, `ParticipationAssessment`, `StructureAssessment` and `SessionAssessment`, plus `AssessmentStatus` and `Confidence`. All records are immutable and carry named fields only.
- `intelligence.analysis`: six independent analyzers (trend, momentum, volatility, participation, market structure and session context) implementing one `MarketAnalyzer` contract. No analyzer calls another.
- `intelligence.engine`: `MarketIntelligenceEngine` composes the analyzers, validates required indicators, and derives overall status and confidence.
- `intelligence.config`: immutable `MarketIntelligenceConfig` with validated thresholds, session windows, confidence thresholds, required indicator types and a `PERMISSIVE`/`STRICT` warm-up policy.
- `intelligence.service`: consumes `IndicatorUpdatedEvent`, keeps a bounded per-instrument history, and publishes `MarketAssessmentGeneratedEvent`.
- Warm-up is never silent: each aspect reports `READY`, `PARTIAL`, `INSUFFICIENT_DATA` or `UNKNOWN`, and an indicator that is missing, warming up or lacking a named value is excluded rather than assumed.
- Input mismatches are rejected deterministically: null input, empty indicator sets, duplicate indicator types, mixed instruments or intervals, non-ascending candles and indicators not aligned to the latest candle.
- No options, Greeks, implied volatility, PCR, risk, strategy, trade qualification, position sizing, order, persistence, database, scheduler, AI or notification logic is included.

The full architecture, assessment model, analyzer rules, configuration, warm-up and validation policy, event integration and testing report is in [`docs/phase-6-report.md`](docs/phase-6-report.md).

## Option chain & options analytics engine (Phase 7)

Phase 7 describes the derivatives market. It consumes option contracts, quotes, a spot price and the market assessment of the same instant, and produces one immutable `OptionsAssessment`; it never produces a signal, a recommendation, a score, a trade direction, a position size or a risk measure.

- `options.chain`: `OptionChainBuilder` groups contracts by expiry and strike, orders them deterministically, rejects duplicate contracts and mixed underlyings or segments, excludes expired expiries and unquoted legs (reporting both), and classifies moneyness and weekly/monthly expiries.
- `options.domain`: immutable `OptionChain`, `ExpiryChain`, `StrikeGroup`, `Strike` and `ChainSnapshot`, plus `OptionsAssessment` with separate chain, volatility, Greeks, open-interest, PCR, expected-move, liquidity and max-pain summaries.
- `options.greeks`: `BlackScholesModel` and `GreeksEngine` compute delta, gamma, theta, vega and rho in `BigDecimal`, with documented units and deterministic rejection of invalid inputs.
- `options.volatility`: implied-volatility solving by bracketed bisection, per-leg resolution, a bounded in-memory history, and ATM volatility, change, rank, percentile, trend, smile, skew and prepared surface.
- `options.analysis`: open-interest and volume change, concentration, support/resistance zones, build-up classification, PCR by open interest and volume, max-pain curve, expected move and liquidity description.
- `options.service`: consumes `MarketAssessmentGeneratedEvent`, resolves a chain capture through `OptionChainProvider`, keeps per-underlying volatility history and previous chain state, and publishes `OptionsAssessmentGeneratedEvent`.
- `options.config`: immutable `OptionsAnalyticsConfig` covering ATM band and deep-moneyness thresholds, liquidity floors, IV and Greek model parameters, PCR aggregation, expected-move method and horizons, smile range and max-pain nearest strikes.
- Validation is deterministic: empty chains, mixed underlyings or segments, duplicate contracts or strikes, missing spot, chain/assessment timestamp or instrument mismatch, out-of-range volatility, unsolvable premiums and invalid configurations are rejected or reported as unavailable, never guessed.
- No strategy, recommendation, signal, entry/exit, risk, sizing, capital allocation, portfolio, order, broker execution, database, persistence, scheduler, notification, AI, ML or backtesting logic is included.

The full architecture, chain and domain model, pricing and IV design, analytics rules, validation strategy, event integration and testing report is in [`docs/phase-7-report.md`](docs/phase-7-report.md).

## Strategy evaluation engine (Phase 8)

Phase 8 answers one question per strategy: do its predefined conditions currently evaluate to true? It consumes market and option assessments, evaluates immutable rule collections and publishes immutable evaluations; it never recommends, ranks, qualifies, sizes or trades.

- `strategy.domain`: `StrategyDefinition` (identity, category, declared inputs, ordered rules), `StrategyEvaluation` (status, rule evaluations, summary), `RuleEvaluation` (derived `passed()`, deterministic reason, descriptive metadata) and the validated `StrategyEvaluationContext`.
- `strategy.rule`: fourteen immutable rule records across indicator, assessment and option rules. Every rule reports `PASSED`, `FAILED` or `INSUFFICIENT_DATA` with a field-specific reason and never assumes a missing value.
- `strategy.engine`: `StrategyEvaluationEngine` is stateless and filters disabled strategies. Status aggregation is deterministic, `strictValidation` reports missing declared inputs once, `failFast` controls rule-failure propagation, and parallel evaluation uses only an injected executor and preserves input order.
- `strategy.registry`: thread-safe `StrategyRegistry` with atomic register, replace, unregister, lookup, enabled/disabled snapshots, duplicate detection and configured limits; `BuiltInStrategies` provides fifteen deterministic sample strategies.
- `strategy.service`: consumes `MarketAssessmentGeneratedEvent` and `OptionsAssessmentGeneratedEvent`, evaluates only when the two describe the same instrument and instant, keeps bounded per-instrument staging, and publishes `StrategyEvaluatedEvent`.
- `strategy.config`: immutable `StrategyEngineConfig` with enable, parallel, limit, fail-fast and strict-validation knobs; no runtime mutation.
- No recommendation, ranking, trade qualification, risk, sizing, capital allocation, order, portfolio, backtesting, persistence, scheduler or notification logic is included.

The full architecture, rule system, registry, engine semantics, validation, event integration and testing report is in [`docs/phase-8-report.md`](docs/phase-8-report.md).

## Trade qualification engine (Phase 9)

Phase 9 answers one question per strategy evaluation: is it currently eligible for downstream consideration? It consumes strategy evaluations and the aligned assessments, applies immutable qualification rules and publishes immutable qualifications; it never recommends, ranks, sizes, allocates or trades.

- `qualification.domain`: `TradeQualification` (status, rule evaluations, summary), `QualificationRuleEvaluation` (derived `passed()`, deterministic reason, descriptive metadata), `QualificationStatus`, `RuleStatus`, `QualificationKey` and the bounded `QualificationLedger`. No recommendation, ranking, score, risk, position, quantity, capital or order field exists.
- `qualification.rule`: eleven immutable rules — session open, market assessment ready, option requirement, option freshness, session alignment, strategy status known, strategy status passed, minimum confidence, minimum participation, option liquidity and duplicate qualification. Every rule reports `INSUFFICIENT_DATA` with a field-specific reason instead of assuming a missing value.
- `qualification.engine`: `TradeQualificationEngine` is stateless. Aggregation is deterministic (`QUALIFIED`, `DISQUALIFIED`, `PARTIAL`, `INSUFFICIENT_DATA`, `UNKNOWN`), `strictValidation` reports a missing required option assessment once, `failFast` controls rule-failure propagation and `allowPartialQualification` decides whether undecided-only gaps still qualify.
- `qualification.registry`: thread-safe `QualificationRuleRegistry` with atomic register, replace, unregister, lookup, sorted immutable snapshots, duplicate detection and the configured rule limit; `BuiltInQualificationRules` assembles the default set.
- `qualification.service`: consumes `StrategyEvaluatedEvent` and both assessment events, qualifies only aligned data, stages evaluations whose assessment has not arrived yet in a bounded pending map, suppresses repeated qualification of the same strategy/instrument/instant and publishes `TradeQualifiedEvent`.
- `qualification.config`: immutable `TradeQualificationConfig` with enable, strict validation, fail-fast, minimum confidence, option requirement, partial qualification, rule limit and parallel toggles; no runtime mutation.
- No risk, position sizing, capital allocation, order, portfolio, backtesting, persistence, scheduler or notification logic is included.

The full architecture, qualification model, rule semantics, registry, engine, event integration, validation and testing report is in [`docs/phase-9-report.md`](docs/phase-9-report.md).

## Risk management and position sizing (Phase 10)

Phase 10 describes the risk boundaries of a qualified trade: how far a volatility-derived stop sits, what quantity a configured risk budget would fund and whether every policy limit holds. It approves nothing, recommends nothing, allocates no capital across strategies and places no order.

- `risk.domain`: `RiskAssessment` (status, risk budget, maximum permitted loss, stop, ATR multiple, volatility adjustment, sizing, leverage ceiling, reward-to-risk, daily-loss and exposure validations, rule outcomes, diagnostics), `RiskContext` (aligned strategy, qualification, market, optional option assessment and candle, indicators), `RiskPlan` (derived numbers), `StopCalculation`, `PositionSizing` and the three validation records. No approval, order, fill, account balance or profit projection exists.
- `risk.math`: `RiskCalculator` derives one plan per assessment — ATR × a volatility-adjusted multiple gives the stop, the risk budget divided by the stop gives the quantity, quantity × price gives the notional and leverage. Missing ATR or price yields a plan without a stop or size rather than a guessed value.
- `risk.rule`: fifteen immutable rules covering qualification eligibility, ATR stop derivation, minimum and maximum stop distance, volatility adjustment, risk percent, daily and session loss limits, open exposure, leverage, quantity, reward-to-risk, gap protection, option premium risk and instrument overrides.
- `risk.engine`: `RiskEngine` is stateless. Aggregation is deterministic (`WITHIN_LIMITS`, `LIMIT_BREACHED`, `PARTIAL`, `INSUFFICIENT_DATA`, `UNKNOWN`), `strictValidation` short-circuits when no stop can be derived, `failFast` controls rule-failure propagation and parallel evaluation uses only an injected executor with preserved registry order.
- `risk.registry`: thread-safe `RiskRuleRegistry` with atomic register, replace, unregister, lookup, duplicate detection and sorted immutable snapshots; `BuiltInRiskRules` is the ordered catalog.
- `risk.service`: consumes `TradeQualifiedEvent` plus the assessment and strategy events, stages qualifications whose inputs have not arrived yet in a bounded map, uses only aligned option and indicator data and publishes `RiskAssessmentGeneratedEvent`.
- `risk.config`: immutable `RiskEngineConfig` with a reference capital (a policy constant, never account state), risk and loss percentages, ATR multiple, stop range, leverage, quantity, fractional, override, fail-fast and parallel toggles.
- No order placement, broker execution, portfolio management, capital allocation, account synchronization, P&L tracking, trade lifecycle, trailing stop, notification, scheduler, persistence, backtesting or AI/ML logic is included.

The full architecture, domain model, rule semantics, engine design, configuration, validation and testing report is in [`docs/phase-10-report.md`](docs/phase-10-report.md).

## Order management, portfolio and execution (Phase 11, dormant)

Phase 11 contains an execution model, but it is **dormant**: no Spring bean, controller, live-supervisor call site or broker order adapter wires it into the running application. The current product never places an order. The description below records the module's design, not an available feature.

Phase 11 converts a qualified, risk-assessed trade into an executable lifecycle **only when a caller explicitly supplies a direction**. The risk assessment provides the quantity and protective stop; `OrderIntent` provides the side, order type, prices and time in force. Execution never infers a side and never sizes beyond the assessment.

- `execution.domain`: immutable `TradeOrder`, `OrderRequest`, `OrderIntent`, `OrderExecution`, `ExecutionReport`, `ExecutionRequest`, `ExecutionResult`, `Position`, `PortfolioSnapshot`, `PortfolioSummary`, `TradeLifecycle`, rule evaluations and diagnostics. Order status covers `CREATED` through `EXPIRED`; position status covers `OPENING`, `OPEN`, `PARTIALLY_CLOSED` and `CLOSED`; market, limit, stop and stop-limit orders and day, IOC and GTC validity are supported.
- `execution.engine`: `ExecutionEngine` creates the order, evaluates every rule, submits through the configured adapter with bounded retries of retryable failures, interprets the broker report, applies fills to the portfolio and publishes the resulting events. Illegal transitions are impossible: `OrderStateMachine` owns the forward-only table.
- `execution.rule`: thirteen immutable rules — `VALID_QUANTITY`, `VALID_PRICE`, `VALID_STOP`, `VALID_TARGET`, `MARKET_OPEN`, `BROKER_CONNECTED`, `ACCOUNT_READY`, `SUFFICIENT_MARGIN`, `ORDER_NOT_DUPLICATE`, `POSITION_LIMIT`, `LEVERAGE_LIMIT`, `VALID_PRODUCT` and `VALID_ORDER_TYPE`. An undecidable rule blocks submission.
- `execution.broker`: `BrokerAdapter` performs only place, cancel, modify, status and positions. `PaperBrokerAdapter` is deterministic and supports configured partial fills, rejections, transient failures and disconnection. `BrokerAdapterRegistry` resolves the configured name. Live mode is refused while only the paper adapter exists.
- `execution.portfolio`: one thread-safe `PortfolioService` owns open and closed positions, realised and unrealised P&L, available capital, blocked margin (working orders), used margin (open positions) and exposure. A partial fill re-blocks the remaining quantity's margin.
- `execution.position`: `PositionCalculator` is pure arithmetic for opening, adding and closing, with weighted average entry and direction-signed realised P&L.
- `execution.order`: bounded LRU `OrderStore` (orders and broker identities), the state machine and `OrderRequestFactory`, which refuses mismatched assessments and unusable sizes.
- `execution.service`: consumes risk, qualification, market, strategy and options events, stages them in bounded caches by strategy, instrument and instant, applies marks to open positions and turns an `OrderIntent` into an `ExecutionRequest`.
- `execution.config`: immutable `ExecutionConfig` with enable, paper/live mode, broker selection, reference capital, maximum open positions, leverage, margin percentage, maximum quantity, allowed types and segments, default time to live, retry count, timeout, retention bound and market-open requirement.
- Events published: `OrderCreated`, `OrderValidated`, `OrderSubmitted`, `OrderAccepted`, `OrderRejected`, `OrderPartiallyFilled`, `OrderFilled`, `OrderCancelled`, `PositionOpened`, `PositionModified`, `PositionClosed`, `PortfolioUpdated` and `TradeCompleted`.
- No AI, machine learning, optimization, backtesting, recommendation change, strategy change, persistence or live broker execution is included.

The full architecture, domain model, order engine, broker integration, portfolio, position lifecycle, event integration, validation, configuration, performance and testing report is in [`docs/phase-11-report.md`](docs/phase-11-report.md).

## Application layer and Android client (Phase 12+)

Phase 12 adds the single-owner application without changing the engine calculations.

- `platform.security`: a fixed local principal (`local-owner`) is installed for every request. There is no registration, login, JWT, refresh token or account role flow. The security boundary is the single deployment owner, not a multi-user account system.
- `platform.api`: read-only market, options, strategy and risk projections; watchlists; preferences; application settings; notifications; and operational status under `/api/v1`.
- `platform.readmodel`: `PipelineSnapshotStore` listens to the existing event bus and keeps the latest 128 market/options/strategy/risk/qualification descriptions. Analysis endpoints project those descriptions; they never recalculate them and never write engine caches to MongoDB.
- `platform.notification`, `watchlist`, `preferences`, `settings`: durable owner records. Nine collection definitions remain on the `tradingassistant` database; the dormant orders/trades records are not exposed by any API.
- `platform.jobs`: token cleanup, notification retention and read-model trimming. No job places, modifies or executes a trade.
- Android: `frontend/` contains a Kotlin/Jetpack Compose client with Hilt, Retrofit, StateFlow, Material 3, a dark-only theme and seven screens. It is a renderer of backend values, not a second calculation engine.

Important operational notes:

- The engine beans exist only when the broker is enabled. Without them, `/api/v1/system/status` reports `DISABLED` and analysis lists are empty.
- `MONGODB_URI` is the only production database configuration. The database name is `tradingassistant`; never use `boom`.
- The analysis read model is in memory and rebuilt after restart; it is not history.
- There is no order, trade, portfolio, position, holding or funds endpoint. Phase 11 execution code is dormant and the source guardrails reject wiring it into the live or HTTP application.
- `runtime` limits and historical architecture findings are documented in [`docs/architecture-review.md`](docs/architecture-review.md).

See the [Phase 12 report](docs/phase-12-report.md) for the earlier delivery record and the [Phase 13 report](docs/phase-13-report.md) for the current data foundation.

## Live data foundation (Phase 13)

Phase 13 makes the read-only live stream watchlist-driven and decision-ready.

- `LiveSubscriptionPlanner` builds one deterministic set from configured instruments and every watchlist item, collapses duplicates, rejects segments without a documented feed and enforces a hard cap.
- `LiveInstrumentResolver` resolves readable aliases from the instrument master with a TTL cache. Numeric tokens bypass the master. Index aliases prefer the index; derivative aliases select the nearest non-expired future and never guess an option.
- `LiveMarketRunner` reconciles on startup, on `WatchlistChangedEvent` and on a configurable interval. Added instruments are subscribed and seeded with history; removed instruments release pipeline, strategy, intelligence and options state. A generation guard ignores callbacks from a closed feed.
- `LiveIndicators` configures all sixteen indicators: SMA, EMA, WMA, RSI, MACD, Stochastic RSI, ATR, Bollinger, Keltner, Donchian, ADX, DMI, VWAP, OBV, CMF and Supertrend.
- The market calendar accepts comma-separated ISO NSE/BSE/MCX holidays through configuration; invalid dates fail startup explicitly.
- `LiveOptionChainService` stages the nearest-expiry, closest-strike option chain for each monitored cash/index instrument from the instrument master and batched `FULL` quotes, then hands it to the existing `InMemoryOptionChainProvider`. It is read-only and refresh-throttled.
- `AngelOneHttpClient` classifies SmartAPI's plain-text 403 "exceeding access rate" response as a retryable rate limit; historical warm-up uses a slower bounded retry policy.
- `MarketDataFreshnessService` observes ticks and completed candles in a bounded map and exposes `FRESH`/`STALE`/`UNKNOWN` plus `OPEN`/`CLOSED`/`UNKNOWN` at `GET /api/v1/market/freshness`.
- `ArchitectureGuardrailTest` rejects AI/ML dependencies and markers, randomness/wall-clock calls in engine packages, execution imports in the live/API layer and any order/portfolio endpoint.

Configuration is documented in [`.env.example`](.env.example) and the [API reference](docs/api-reference.md). The database name is `tradingassistant` in the local fallback, environment example and current documentation.

## Recommendation engine (Phase 14)

Phase 14 adds a deterministic recommendation layer above the existing engines.

- `RecommendationContext` freezes already-computed market, indicator, options, strategy, qualification and risk outputs for one instrument and timestamp.
- Twenty-one independent scorers return a bounded score, explanation and diagnostics. The configured weights are relative and normalized, so no hardcoded total or confidence percentage exists.
- `RecommendationEngine` applies qualification, risk, options, session, directional-edge and confidence gates, then selects only `BUY_CALL`, `BUY_PUT` or `NO_TRADE`.
- `OptionSelector` reads the captured chain and the configured weekly/monthly, nearest/next/far and ATM/ITM/OTM policy. It emits expiry, strike and moneyness only; it does not emit entry, target, stop, exit, quantity or position size.
- `RecommendationBatch.ranked()` provides deterministic internal ranking by confidence, weighted score, type and instrument.
- `RecommendationService` stages existing events and publishes `RecommendationGeneratedEvent`, `RecommendationUpdatedEvent` and `RecommendationExpiredEvent` on a separate hierarchy. The sealed `MarketEvent` contract is unchanged.
- `app.recommendation` configures every weight, the minimum confidence, directional edge, PCR/IV bands, allowed expiries and strike policy. Invalid, missing, duplicate or negative configuration is rejected at startup.
- `ArchitectureGuardrailTest` rejects AI/ML, execution, broker, Android and HTTP dependencies in the recommendation package and rejects indicator/analytics recalculation.

Phase 16 exposes the recommendation through `GET /api/v1/recommendations`; no Android recommendation UI exists yet. The live service waits for the options stage and chain for the exact market timestamp rather than inventing a contract; if the chain is unavailable, no recommendation is published. The recommendation itself still contains no entry, target, stop, exit, quantity or position size; the separate read-side plan derives reference boundaries from the existing risk stop and chain.

## Trade monitoring (Phase 15)

Phase 15 adds continuous deterministic monitoring above the recommendation layer.

- `ManualTrade` records an option trade the owner executed outside the application. It contains local identity, instrument, contract, entry economics and `ACTIVE`/`CLOSED` status only; it has no broker order id, execution state, stop, target or automation field.
- `TradeMonitoringContext` joins the active trade, current recommendation, market assessment, indicators, options assessment, strategy, qualification, risk and freshness. Inputs come from `RecommendationService.latestContext(...)`; monitoring never recalculates indicators, options analytics or recommendations.
- `TradeMonitoringEngine` reuses every Phase 14 component score and computes current confidence, trade-aligned directional edge, previous values, deltas, component changes, warnings, an explanation and diagnostics.
- States are `HEALTHY`, `WEAKENING`, `AT_RISK`, `EXIT_RECOMMENDED` and `INVALIDATED`. Exit recommendations are `HOLD`, `WATCH_CLOSELY`, `PREPARE_EXIT` and `EXIT_NOW`; these are recommendations only and never execute.
- `MonitoringService` rescans active trades on completed candles, input updates and recommendation updates. Repeated identical results are not persisted and do not emit duplicate events.
- Mongo stores `manual_trades`, the latest `monitoring_results`, significant `monitoring_history` and `monitoring_alerts`.
- `MonitoringStartedEvent`, `MonitoringUpdatedEvent`, `MonitoringStateChangedEvent`, `MonitoringWarningEvent`, `ExitRecommendedEvent` and `MonitoringStoppedEvent` form a separate hierarchy; the sealed `MarketEvent` contract is unchanged.
- Read-only endpoints are `GET /api/v1/monitoring/active`, `/history`, `/{tradeId}` and `/alerts`. Phase 15 has no POST/PUT/PATCH/DELETE; Phase 16 adds manual fill and premium-status APIs outside the monitoring package, but still no execution or broker-order endpoint. There is no Android implementation.
- `app.monitoring` configures warning/exit confidence, confidence/edge/component deterioration, warning/at-risk component counts, strategy disagreement, maximum stale duration and the event-driven interval.
- `ArchitectureGuardrailTest` rejects execution/broker/Android/HTTP-client dependencies, indicator/options/risk/recommendation recalculation, write endpoints, randomness and clock use in the deterministic monitoring core.

Manual trade recording was available through `MonitoringService.register(...)` for the application/future UI layer in Phase 15. Phase 16 exposes the write path.

## Actionable workflow (Phase 16)

Phase 16 connects search, recommendation, manual execution and premium monitoring without adding an order path.

- `InstrumentCatalog` caches the instrument master for ten minutes; `InstrumentSearchService` returns cash/index instruments by default and option/future instruments when explicitly requested.
- `RecommendationQueryService` exposes the current recommendation and derives an optional `TradePlan` from the selected chain leg, the existing ATR stop, ATM delta, risk capital and reward/risk policy. The plan is labelled with its delta source and never becomes an order.
- `ManualTrade` now stores optional `targetPremium`/`stopPremium`, so a later recommendation cannot move the owner's recorded boundaries.
- `POST /api/v1/monitoring/trades` records a manually executed fill. `POST /api/v1/monitoring/trades/{tradeId}/close` closes local monitoring only.
- `OptionPremiumService` polls active option contracts in bounded batches, compares LTP with target/stop/expiry and raises a persisted `TRADE_ALERT` notification once per boundary. It skips alerts outside market hours and never exits automatically.
- `GET /api/v1/instruments/search`, `GET /api/v1/recommendations`, `GET /api/v1/monitoring/premium` and `GET /api/v1/monitoring/premium/{tradeId}` are read endpoints. The Android client does not consume them yet.
- `app.trade-plan` configures fallback delta, fallback percentages, premium polling and alert enablement.

## Security notes

- No API keys, secrets, credentials, or tokens are present in the repository.
- Render environment variables should hold SmartAPI secrets; they must not be committed.
- `SMARTAPI_ENABLED` defaults to `false`. Enabling it without the required variables fails startup with a safe variable-name message.
- The broker session manager keeps tokens in memory only; it does not persist them.
- User-supplied request IDs are accepted only if they match `[A-Za-z0-9._-]{1,64}`; otherwise the server generates a UUID.
- The Docker runtime uses an unprivileged user.
- Actuator exposure is restricted to health, info, metrics and (production) Prometheus; health details are disabled.
- MongoDB credentials come only from the environment. No connection string is logged or returned.
- There is no login or token surface to harden in the single-owner application; network access control is the deployment boundary.
- The global exception handler returns safe messages and logs unexpected failures server-side.
- See [`docs/architecture-review.md`](docs/architecture-review.md) for historical security findings; it predates the single-owner correction.

## Testing

`mvn -B -ntp test` currently runs **1,146 tests with 0 failures, 0 errors and 2 skipped** (the two opt-in live broker tests). Phase 15 adds monitoring engine/state/exit/delta tests, validation and stale-data tests, service lifecycle/history/event tests, persistence document/store coverage, read-only API tests, configuration and Spring wiring tests, and extended source guardrails; Phase 16 adds instrument-catalog/search, trade-plan, manual-trade command, premium-monitor, property-binding and route-level API tests plus a workflow execution guardrail; the existing engine, broker and platform tests remain green. The live option-chain staging and plain-text 403 retry paths add their own focused tests.

Coverage includes:

- Spring application context startup and the disabled-by-default broker wiring
- system information success envelope and build version
- health endpoint availability
- request ID echo and replacement of invalid identifiers
- unknown-resource 404, unsupported-method 405, validation and safe 500 envelopes
- RFC 6238 TOTP vectors
- JWT expiry parsing
- fail-fast broker configuration validation
- SmartAPI login, refresh, profile and logout mapping
- session expiry, renewal, rejection retry, concurrent duplicate-login prevention and logout clearing
- quote, candle and instrument-master mapping, including partial quote failures and documented limits
- HTTP headers, bounded retries, rate-limit and malformed-response translation
- WebSocket subscription serialization, ping/pong, binary tick decoding and graceful close
- Phase 3 domain validation, all market mappers, market-time policy, service orchestration, feed bridging and conditional wiring
- Phase 4 reference values, all sixteen indicators, warm-up, incremental parity, reset behavior, configuration, validation, gap detection, immutability and a 5,000-candle performance bound
- Phase 5 aggregation across every interval, interval rollover, duplicate/late/future/out-of-session ticks, empty intervals, calendar sessions, holidays and special sessions, session creation and expiry, event order and delivery, registry lookup and concurrency, lifecycle shutdown and a multi-instrument replay dataset
- Phase 6 trend, momentum, volatility, participation, structure and session scenarios, warm-up and strict-policy handling, missing and misaligned indicators, configuration validation, immutable outputs, event publication, replay parity and a 2,000-event multi-instrument replay
- Phase 7 chain construction, expiry and moneyness classification, Greek reference values and limits, implied-volatility recovery and history statistics, smile, skew and surface, open-interest change and build-up, PCR aggregation, max pain, expected move, liquidity, validation, event publication, replay idempotence, multi-underlying isolation, a 410-leg five-expiry chain and configuration validation
- Phase 8 strategy registration and replacement, duplicate detection, all fifteen built-in strategies, rule evaluation, partial and insufficient outcomes, strict input handling, fail-fast containment, parallel determinism, immutable models and snapshots, event publication, cache alignment, instrument isolation and configuration validation
- Phase 9 qualification success, failure, partial and insufficient outcomes, unknown states, duplicate suppression, every built-in rule, confidence and participation floors, session and option-liquidity rules, option freshness and session alignment, timestamp and instrument mismatches, registry operations, parallel determinism, immutability, event publication, staging order, configuration and wiring
- Phase 10 stop and size derivation, volatility adjustment, fractional and whole-unit quantities, instrument overrides, every built-in risk rule, within-limit, breach, partial and insufficient outcomes, fail-fast containment, parallel determinism, immutable models, registry operations, configuration validation, staging order, aligned option and indicator filtering, event publication and Spring wiring
- Phase 11 order creation, validation and every rejection path, the forward-only order state machine, order store eviction and duplicate detection, the paper broker (fills, partial fills, rejections, retries, cancellation), broker and rule registries, portfolio margins, realised and unrealised P&L, position open/add/partial close/close arithmetic, the engine lifecycle and event order, cancellation rules, bounded service caches, marks, immutability, configuration, concurrency and Spring wiring
- Legacy auth-service/JWT unit tests retained for the dormant security types; single-owner watchlist ownership and limits; analysis controller contracts; notification preferences and records; application settings; read-model projection; maintenance jobs; the local-principal Spring security chain and Mongo-compatible repository integration
- Phase 13 alias resolution, deterministic subscription planning, supervisor reconciliation and cleanup, all-indicator wiring, holiday configuration, freshness status/session projection, and source-level AI/execution guardrails
- Phase 14 every scorer and its explanation/diagnostics, weighted contribution and confidence arithmetic, deterministic ranking, BUY CALL/BUY PUT/NO TRADE decisions, stale/missing/misaligned input rejection, option expiry/strike selection, weight/threshold configuration and binding, generated/updated/expired event staging, and recommendation-specific source guardrails
- Phase 15 manual trade validation, every monitoring state transition, confidence/edge/component deltas, qualification/risk/session/trend/structure/liquidity deterioration, BUY CALL/BUY PUT monitoring, stale-context retention, duplicate-scan suppression, history and alert persistence through the store port, monitoring events, read-only API projections, configuration binding/validation, Spring wiring and deterministic-core guardrails
- Phase 16 cached instrument search and option resolution, search default filtering, ATR/ATM-delta trade-plan derivation, plan quantities and boundaries, manual fill registration/close, premium target/stop alerts, duplicate/closed-session suppression, trade-plan property binding, route-level API contracts and a no-execution workflow guardrail

The separate Android repository has 21 JVM tests (API contract, login/register, dashboard, orders, watchlist and settings view models) and three compiled Compose UI tests. Android instrumented tests require an emulator and are not part of this backend build.

The opt-in live tests are skipped unless `SMARTAPI_LIVE_TESTS=true` and real credentials are present:

```bash
SMARTAPI_LIVE_TESTS=true \
SMARTAPI_API_KEY='...' SMARTAPI_CLIENT_CODE='...' \
SMARTAPI_PIN='...' SMARTAPI_TOTP_SECRET='...' \
SMARTAPI_CLIENT_LOCAL_IP='...' SMARTAPI_CLIENT_PUBLIC_IP='...' \
SMARTAPI_MAC_ADDRESS='...' \
mvn -Dtest=AngelOneLiveIntegrationTest test
```

The Phase 2 test verifies authentication, profile, live quote, historical candles, instrument master, WebSocket connection and graceful close. The Phase 3 test maps the real instrument master and option contracts into the domain. Both were run successfully against a real account and never print credentials or tokens. The request-metadata headers are mandatory; without them SmartAPI rejects login with `AB1012` (documented as “Invalid Product Type”).

Phase 1 results are in [`docs/phase-1-report.md`](docs/phase-1-report.md); Phase 2 results are in [`docs/phase-2-report.md`](docs/phase-2-report.md); Phase 3 results are in [`docs/phase-3-report.md`](docs/phase-3-report.md); Phase 4 results are in [`docs/phase-4-report.md`](docs/phase-4-report.md); Phase 5 results are in [`docs/phase-5-report.md`](docs/phase-5-report.md); Phase 6 results are in [`docs/phase-6-report.md`](docs/phase-6-report.md); Phase 7 results are in [`docs/phase-7-report.md`](docs/phase-7-report.md); Phase 8 results are in [`docs/phase-8-report.md`](docs/phase-8-report.md); Phase 9 results are in [`docs/phase-9-report.md`](docs/phase-9-report.md); Phase 10 results are in [`docs/phase-10-report.md`](docs/phase-10-report.md); Phase 11 results are in [`docs/phase-11-report.md`](docs/phase-11-report.md); Phase 12 results are in [`docs/phase-12-report.md`](docs/phase-12-report.md); Phase 13 results are in [`docs/phase-13-report.md`](docs/phase-13-report.md); Phase 14 results are in [`docs/phase-14-report.md`](docs/phase-14-report.md); Phase 15 results are in [`docs/phase-15-report.md`](docs/phase-15-report.md). The full API contract is [`docs/api-reference.md`](docs/api-reference.md), deployment is [`docs/deployment.md`](docs/deployment.md), MongoDB is [`docs/mongodb.md`](docs/mongodb.md), and the 20-section review is [`docs/architecture-review.md`](docs/architecture-review.md).
