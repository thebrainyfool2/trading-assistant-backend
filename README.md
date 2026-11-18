# Trading Assistant Backend

Spring Boot 4.1.2 backend foundation for the Trading Assistant platform. Java 21, Maven, Spring MVC, Bean Validation, and Actuator.

This backend is maintained as an independent Git repository. The future Android client lives in a separate repository under the workspace `frontend/` directory.

Phase 1 provides the operational backend foundation. Phase 2 adds a conditional, read-only Angel One SmartAPI integration behind a broker abstraction; it is disabled by default. There is still no database, user authentication, trading logic, order placement, strategy engine, or Android client.

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

The service definition uses the free compute plan, which is appropriate for Phase 1/2 validation but not for continuous monitoring. Phase 2 deploys with the broker disabled unless `SMARTAPI_ENABLED=true` and all required values are set in Render. Automatic deploys from `main` are active; a documentation push during verification triggered a successful deployment. Manual deploys can also be triggered with `render deploys create srv-dbut8f9srm7s73ftpni0`. See the reports for the networking implications.

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

The opt-in live test is skipped unless `SMARTAPI_LIVE_TESTS=true` and real credentials are present:

```bash
SMARTAPI_LIVE_TESTS=true \
SMARTAPI_API_KEY='...' SMARTAPI_CLIENT_CODE='...' \
SMARTAPI_PIN='...' SMARTAPI_TOTP_SECRET='...' \
SMARTAPI_CLIENT_LOCAL_IP='...' SMARTAPI_CLIENT_PUBLIC_IP='...' \
SMARTAPI_MAC_ADDRESS='...' \
mvn -Dtest=AngelOneLiveIntegrationTest test
```

That test verifies authentication, profile, live quote, historical candles, instrument master, WebSocket connection and graceful close. It was run successfully on 18 November 2026 against a real account and never prints credentials or tokens. The request-metadata headers are mandatory; without them SmartAPI rejects login with `AB1012` (documented as “Invalid Product Type”).

Phase 1 results are in [`docs/phase-1-report.md`](docs/phase-1-report.md); Phase 2 results are in [`docs/phase-2-report.md`](docs/phase-2-report.md).
