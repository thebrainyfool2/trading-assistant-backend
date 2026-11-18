# Trading Assistant Backend

Spring Boot 4.1.2 backend foundation for the Trading Assistant platform. Java 21, Maven, Spring MVC, Bean Validation, and Actuator.

This backend is maintained as an independent Git repository. The future Android client lives in a separate repository under the workspace `frontend/` directory.

Phase 1 provides the operational backend foundation. Phase 2 adds a conditional, read-only Angel One SmartAPI integration behind a broker abstraction; it is disabled by default. Phase 3 adds the broker-independent market domain and application services used by every future module. Phase 4 adds the reusable technical-indicator engine that consumes market candles. Phase 5 adds the runtime market-processing infrastructure: candle aggregation, long-lived indicator sessions, immutable event publication, session lifecycle management and a market calendar. Phase 6 adds the market-intelligence engine, which describes trend, momentum, volatility, participation, structure and session context without producing signals. There is still no database, user authentication, trading logic, order placement, strategy engine, option analytics, or Android client.

## Prerequisites

- JDK 21
- Maven 3.9.16 or later (the tested version is 3.9.16)
- Docker only if you want to build the production image locally

## Build and test

```bash
mvn -B -ntp test
mvn -B -ntp package
```

The Spring Boot Maven plugin produces an executable jar and a `META-INF/build-info.properties` file. The build version is exposed by `/api/v1/system/info`.

## Run locally

The `local` profile is the default.

```bash
mvn spring-boot:run
```

Or run the packaged jar:

```bash
java -jar target/trading-assistant-backend-0.1.0.jar
```

Useful local checks:

```bash
curl -i http://localhost:8080/actuator/health
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
- Only the Actuator `health` endpoint is exposed over HTTP.
- Health details and components are hidden.
- Error responses never include exception messages, stack traces, or exception class names.
- The console log pattern includes the request ID from MDC.
- JSON responses are compressed when they exceed 1 KB.
- Configuration values are externalized through environment variables; defaults are non-secret.

No `.env` file is required or read automatically. Supply environment variables through the shell, IDE run configuration, Render environment variables, or a deployment secret store.

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
└── system
    ├── api       System controller and HTTP DTO
    ├── application System information use case
    └── domain    Framework-independent system information model
```

The code follows constructor injection, interface-light layered boundaries, and DTO separation. A full repository layer is intentionally absent because no approved phase has a persistence requirement.

## Endpoints

### `GET /actuator/health`

Returns Actuator's health response. Render uses this path as its HTTP health check.

### `GET /api/v1/system/info`

Returns application name, Maven build version, active profile, and configured API version inside the standard success envelope.

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
- No intelligence, strategy, signal, risk, option, Greek, persistence, database, scheduler, notification, backtesting or trade-monitoring logic is included.

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

## Security notes

- No API keys, secrets, credentials, or tokens are present in the repository.
- Render environment variables should hold SmartAPI secrets; they must not be committed.
- `SMARTAPI_ENABLED` defaults to `false`. Enabling it without the required variables fails startup with a safe variable-name message.
- The broker session manager keeps tokens in memory only; it does not persist them.
- User-supplied request IDs are accepted only if they match `[A-Za-z0-9._-]{1,64}`; otherwise the server generates a UUID.
- The Docker runtime uses an unprivileged user.
- Actuator exposure is restricted to health, with details disabled.
- The global exception handler returns safe messages and logs unexpected failures server-side.

## Testing

`mvn test` currently covers:

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

Phase 1 results are in [`docs/phase-1-report.md`](docs/phase-1-report.md); Phase 2 results are in [`docs/phase-2-report.md`](docs/phase-2-report.md); Phase 3 results are in [`docs/phase-3-report.md`](docs/phase-3-report.md); Phase 4 results are in [`docs/phase-4-report.md`](docs/phase-4-report.md); Phase 5 results are in [`docs/phase-5-report.md`](docs/phase-5-report.md); Phase 6 results are in [`docs/phase-6-report.md`](docs/phase-6-report.md).
