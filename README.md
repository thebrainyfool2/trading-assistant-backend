# Trading Assistant Backend

Spring Boot 4.1.2 backend foundation for the Trading Assistant platform. Java 21, Maven, Spring MVC, Bean Validation, and Actuator.

This backend is maintained as an independent Git repository. The future Android client lives in a separate repository under the workspace `frontend/` directory.

Phase 1 is infrastructure only. There is no market-data integration, database, authentication, trading logic, or WebSocket support.

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
└── system
    ├── api       System controller and HTTP DTO
    ├── application System information use case
    └── domain    Framework-independent system information model
```

The code follows constructor injection, interface-light layered boundaries, and DTO separation. A full repository layer is intentionally absent because Phase 1 has no persistence requirement.

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

The service definition uses the free compute plan, which is appropriate for Phase 1 validation but not for continuous monitoring. Because it was added through Render's public Git repository flow, automatic Git webhook deploys are unavailable unless the Render GitHub App is connected later; trigger manual deploys with `render deploys create srv-dbut8f9srm7s73ftpni0`. See the report for the networking implications.

## Security notes

- No API keys, secrets, credentials, or tokens are present in the repository.
- Render environment variables should hold future secrets; they must not be committed.
- User-supplied request IDs are accepted only if they match `[A-Za-z0-9._-]{1,64}`; otherwise the server generates a UUID.
- The Docker runtime uses an unprivileged user.
- Actuator exposure is restricted to health, with details disabled.
- The global exception handler returns safe messages and logs unexpected failures server-side.

## Testing

`mvn test` currently covers:

- Spring application context startup
- system information success envelope and build version
- health endpoint availability
- request ID echo and replacement of invalid identifiers
- unknown-resource 404 envelope
- unsupported-method 405 envelope
- Bean Validation failure mapping through the centralized handler
- Safe 500 response for unexpected exceptions, with no internal message leakage

The full Phase 1 results, including the live `prod` profile checks, are recorded in [`docs/phase-1-report.md`](docs/phase-1-report.md).
