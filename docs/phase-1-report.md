# Phase 1 Implementation Report

**Project:** Trading Assistant  
**Phase:** 1 — Production-ready backend foundation  
**Report date:** 18 November 2026  
**Status:** Implementation and local verification complete. Render deployment is **blocked** by the absence of a GitHub remote and initial commit. Awaiting architectural review and approval.

---

## 1. Executive Summary

Phase 1 delivers a clean, production-oriented Spring Boot backend foundation without starting any future-phase functionality.

Completed:

- Java 21 and Spring Boot 4.1.2 Maven project under `backend/`
- Layered package structure with constructor injection and DTO separation
- Versioned REST endpoint at `GET /api/v1/system/info`
- Actuator health endpoint at `GET /actuator/health`
- Standard success and error response envelopes
- Centralized exception handling for validation, binding, 404, 405, 415, malformed requests, and unexpected failures
- Request correlation through `X-Request-Id`, MDC logging, and response metadata
- `local` and `prod` profiles with externalized configuration
- Safe production defaults: no stack traces in responses, restricted Actuator exposure, non-root Docker runtime
- Multi-stage Dockerfile for Render
- Render Blueprint in `render.yaml`
- Maven tests and live-profile verification
- Repository documentation and the requested `frontend/` directory, intentionally reserved for the later Android phase

Explicitly not included: SmartAPI, MongoDB, authentication, WebSockets, market data, strategies, risk logic, trade monitoring, notifications, backtesting, paper trading, and Android code.

Deployment status: the Render Blueprint validates, and the application runs correctly under the production profile. A Render deployment could not be performed because the local Git repository has no GitHub remote or commit, and Render builds Docker services from Git. The authenticated Render CLI cannot upload local source. No Render URL therefore exists yet.

---

## 2. Architecture Summary

### 2.1 Chosen architecture

A **layered modular monolith** deployed as one Spring Boot process.

```text
HTTP request
    │
    ├── RequestIdFilter          correlation ID, MDC, response header
    │
    ├── Spring MVC / Actuator
    │
    ├── Controller               HTTP adapter, request/response DTOs
    │       │
    │       └── Application service   use case orchestration
    │               │
    │               └── Domain model  framework-independent data
    │
    └── GlobalExceptionHandler   stable error contract
```

### 2.2 Why this architecture

- **Modular monolith over microservices.** Phase 1 has one deployable concern: infrastructure. Separate services would add network failure modes, deployment coordination, and operational cost with no current benefit. Clear package boundaries preserve the option to extract modules later.
- **Layered boundaries.** Controllers do not contain business decisions; the application service owns use-case orchestration; the domain record is independent of HTTP and frameworks. A future repository layer can be added behind the application layer when persistence is actually required.
- **Constructor injection.** All collaborators are explicit, immutable, and testable. There is no field injection or service locator.
- **DTO separation.** `SystemInfo` is the domain model; `SystemInfoResponse` is the HTTP contract. Future changes to either side do not automatically break the other.
- **Standard transport contract.** Success and error envelopes give the future Android client one predictable parsing path, stable error codes, and request correlation metadata.
- **Actuator remains native.** Health responses use Actuator's own format so Render and standard monitoring tools can consume them without adaptation. Application endpoints use the platform envelope.
- **Profiles, not code branches.** Local and production differences are configuration only.
- **Docker on Render.** Render does not provide a native JVM runtime; Docker is the documented route for Java services and gives a reproducible Java 21 build.

### 2.3 Design decisions

| Decision | Rationale |
|---|---|
| Spring Boot 4.1.2 | Current stable line as of this report, officially compatible with Java 21. Spring Boot 3.5 is past its open-source support window (final OSS release 3.5.16, June 2026). |
| Java 21 | Required project baseline; available locally and supported by Spring Boot 4.1.2. |
| Maven | Required project build tool; standard Spring Boot parent/plugin model. |
| Single module | Small Phase 1 scope; avoids premature Maven multi-module complexity. Package boundaries carry the design. |
| `/api/v1` prefix | Explicit API versioning from the first endpoint, avoiding breaking changes later. |
| Custom response envelope | Chosen over RFC 9457 Problem Details because the Android client benefits from a single success/error shape and stable domain error codes. Safe HTTP status codes are still used. |
| `ErrorCode` in `common.api` | Error codes are part of the public API contract. Keeping them beside the error envelope avoids a dependency cycle with `common.error`. |
| `ObjectProvider<BuildProperties>` | Keeps startup robust when the build-info resource is unavailable (for example, some IDE runs), while exposing the real Maven version when packaged normally. |
| `X-Request-Id` validation | Prevents unbounded or injected values from reaching logs; invalid input is replaced with a UUID. |
| No database | Phase 1 has no persistence requirement. MongoDB will be introduced only when a concrete phase needs it. |
| No security dependency yet | Phase 1 exposes only operational endpoints and contains no user data. Authentication belongs to a later approved phase. |

### 2.4 Error contract

The handler maps:

| Condition | HTTP status | Error code |
|---|---|---|
| Bean Validation failure | 400 | `VALIDATION_FAILED` |
| Unreadable/malformed request body | 400 | `MALFORMED_REQUEST` |
| Type mismatch or missing required parameter | 400 | `VALIDATION_FAILED` |
| Unknown resource | 404 | `RESOURCE_NOT_FOUND` |
| Unsupported HTTP method | 405 | `METHOD_NOT_ALLOWED` |
| Unsupported media type | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| Unexpected server failure | 500 | `INTERNAL_ERROR` |

Client errors are logged at debug level. Unexpected failures are logged at error level with the exception server-side, while the response contains only a safe message. No stack trace, exception class, or internal message is returned.

---

## 3. Folder Structure

```text
TradingAssistant/
├── .gitattributes
├── .gitignore
├── README.md
├── render.yaml
├── backend/
│   ├── .dockerignore
│   ├── Dockerfile
│   ├── README.md
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/tradingassistant/
│       │   │   ├── TradingAssistantApplication.java
│       │   │   ├── common/
│       │   │   │   ├── api/
│       │   │   │   │   ├── ApiError.java
│       │   │   │   │   ├── ApiErrorResponse.java
│       │   │   │   │   ├── ApiMeta.java
│       │   │   │   │   ├── ApiResponse.java
│       │   │   │   │   ├── ErrorCode.java
│       │   │   │   │   ├── FieldViolation.java
│       │   │   │   │   └── RequestContext.java
│       │   │   │   ├── error/
│       │   │   │   │   └── GlobalExceptionHandler.java
│       │   │   │   └── web/
│       │   │   │       └── RequestIdFilter.java
│       │   │   ├── config/
│       │   │   │   └── ApplicationProperties.java
│       │   │   └── system/
│       │   │       ├── api/
│       │   │       │   ├── SystemController.java
│       │   │       │   └── dto/
│       │   │       │       └── SystemInfoResponse.java
│       │   │       ├── application/
│       │   │       │   └── SystemInfoService.java
│       │   │       └── domain/
│       │   │           └── SystemInfo.java
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-local.yml
│       │       └── application-prod.yml
│       └── test/
│           └── java/com/tradingassistant/
│               ├── TradingAssistantApplicationTests.java
│               ├── common/error/
│               │   └── GlobalExceptionHandlerTest.java
│               └── system/api/
│                   └── SystemEndpointIntegrationTest.java
├── frontend/
│   └── README.md              Reserved for Android/Kotlin/Compose; no code in Phase 1
└── docs/
    └── phase-1-report.md
```

### Package responsibilities

| Package | Responsibility |
|---|---|
| `com.tradingassistant` | Application entry point and component scanning. |
| `common.api` | Transport-level contract shared by endpoints: success/error envelopes, metadata, error codes, field violations, request context. |
| `common.error` | Centralized mapping from framework/application exceptions to the API error contract. |
| `common.web` | Servlet-level request concerns. Currently only request correlation. |
| `config` | Validated, typed configuration (`app.*`). |
| `system.api` | System operational controller and its HTTP DTO. |
| `system.application` | System information use case. No web or persistence dependency. |
| `system.domain` | Framework-independent system information model. |
| `resources` | Externalized configuration and profile-specific overrides. |
| `test` | Context smoke test, endpoint integration tests, and focused exception-handler test. |
| `frontend` | Explicit placeholder for the later Android client. No implementation belongs here in Phase 1. |

---

## 4. Dependencies

All dependencies are declared in `backend/pom.xml`. Versions are managed by the Spring Boot parent except Java.

| Dependency | Scope | Why it is needed |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-parent:4.1.2` | Build parent | Provides Spring Boot dependency management, plugin versions, sensible defaults, and the Maven lifecycle configuration. |
| `spring-boot-starter-webmvc` | Runtime | Spring MVC REST support, embedded Tomcat, JSON handling, and the servlet stack. This is the Spring Boot 4 replacement for the older `spring-boot-starter-web`. |
| `spring-boot-starter-validation` | Runtime | Bean Validation and Hibernate Validator. Enables future request validation and currently supports the global validation error contract. |
| `spring-boot-starter-actuator` | Runtime | Production health endpoint used by Render and future monitoring. |
| `spring-boot-starter-test` | Test | JUnit Jupiter, AssertJ, Mockito, and Spring Boot test support. |
| `spring-boot-starter-webmvc-test` | Test | MockMvc auto-configuration for the endpoint integration tests in Spring Boot 4. |
| `spring-boot-maven-plugin` | Build | Repackages the application as an executable jar and generates `META-INF/build-info.properties` for the version endpoint. |

Deliberately excluded: Lombok, MapStruct, OpenAPI/Swagger, Spring Security, Spring Data MongoDB, WebSocket libraries, HTTP clients, caching, messaging, and FCM. None is required by Phase 1, and each would be an unjustified maintenance and security surface.

---

## 5. Configuration

### 5.1 Profiles

| Profile | Activation | Purpose |
|---|---|---|
| `local` | Default profile when no active profile is set | Local development on port 8080, DEBUG logging for application packages |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` (set by `render.yaml`) | Render deployment, port from `PORT`, INFO logging |

`application.yml` holds shared configuration. Profile-specific files contain only differences.

### 5.2 Values and environment variables

| Property / variable | Value or default | Notes |
|---|---|---|
| `spring.application.name` | `trading-assistant-api` | Internal application identity. |
| `SPRING_PROFILES_ACTIVE` | `local` by default | `render.yaml` sets `prod`. |
| `SERVER_ADDRESS` | `0.0.0.0` | Required for Render's proxy to reach the container. |
| `SERVER_PORT` | `8080` | Local HTTP port. |
| `PORT` | `10000` in `prod` | Render sets `PORT`; production profile reads it. |
| `APP_NAME` | `Trading Assistant API` | Returned by the system endpoint. |
| `APP_DESCRIPTION` | Market analysis and trade monitoring platform | Application metadata; no secret. |
| `APP_API_VERSION` | `v1` | API version reported by the system endpoint. |

No secrets are required or present in Phase 1. Future SmartAPI credentials and database URIs must be supplied as Render environment variables or a managed secret store, never committed.

### 5.3 Logging

The console pattern is:

```text
%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%thread] [%X{requestId:-}] %logger{36} - %msg%n
```

- Request ID is included through MDC for every request handled by `RequestIdFilter`.
- Logs go to stdout, which Render captures; no ephemeral file appender is configured.
- `local` enables DEBUG for `com.tradingassistant`; `prod` uses INFO.
- Unexpected server failures log the full exception server-side.
- Client errors log at DEBUG to avoid noisy production logs.

Structured JSON logging was intentionally not added. It is a reasonable later improvement for log aggregation, but it is not needed for Phase 1 and would add format configuration without a consumer.

### 5.4 Ports and lifecycle

- Local: `http://localhost:8080`.
- Render: container listens on `0.0.0.0:${PORT}`, default 10000.
- Graceful shutdown enabled with `spring.lifecycle.timeout-per-shutdown-phase: 20s`.
- Response compression enabled for JSON responses over 1 KB.

### 5.5 Actuator

- Only `health` is exposed over HTTP.
- Health details and components are never shown.
- Liveness/readiness probes are enabled.
- The Render health check is `/actuator/health`.
- No metrics, environment, heap, thread dump, or other operational endpoints are exposed.

### 5.6 Error and security defaults

- `server.error.include-message: never`
- `server.error.include-stacktrace: never`
- `server.error.include-exception: false`
- Whitelabel error page disabled.
- Caller-supplied request IDs are limited to `[A-Za-z0-9._-]{1,64}`.
- Docker runs as a non-root user.
- `.gitignore` excludes environment files, key stores, logs, and build output.

---

## 6. Deployment

### 6.1 Local run

Verified with JDK 21.0.12 and Maven 3.9.16:

```bash
cd backend
mvn -B -ntp test
mvn -B -ntp -DskipTests package
PORT=18080 SPRING_PROFILES_ACTIVE=prod \
  java -jar target/trading-assistant-backend-0.1.0.jar
```

Observed startup:

```text
Starting TradingAssistantApplication v0.1.0 using Java 21.0.12
The following 1 profile is active: "prod"
Tomcat initialized with port 18080 (http)
Tomcat started on port 18080 (http) with context path '/'
Started TradingAssistantApplication in 2.577 seconds
```

Observed responses:

```text
GET /actuator/health              -> 200, {"groups":["liveness","readiness"],"status":"UP"}
GET /api/v1/system/info           -> 200, version 0.1.0, environment prod
X-Request-Id: phase1-local-check  -> echoed in header and meta.requestId
GET /api/v1/unknown               -> 404, RESOURCE_NOT_FOUND envelope
POST /api/v1/system/info          -> 405, METHOD_NOT_ALLOWED envelope
```

The server was stopped after the checks; port 18080 no longer accepted connections.

### 6.2 Render deployment design

The root `render.yaml` defines:

- service type: web
- runtime: Docker
- service root: `backend`
- Dockerfile: `./Dockerfile` relative to the service root
- Docker context: `.` relative to the service root
- health check: `/actuator/health`
- auto-deploy: on commits to the connected branch
- build filter: `backend/**`
- production profile and public application metadata
- free compute plan for Phase 1 only

The Dockerfile is multi-stage:

1. `maven:3.9.16-eclipse-temurin-21` builds and tests the application.
2. `eclipse-temurin:21-jre-alpine` provides the runtime.
3. A non-root `app` user runs the jar.
4. The container exposes port 10000 and respects Render's `PORT`.

Render requires a web service to bind to `0.0.0.0` and the `PORT` environment variable. Both are configured.

Blueprint validation:

```text
render blueprints validate ./render.yaml --output json
{
  "plan": {
    "services": ["trading-assistant-api"],
    "totalActions": 1
  },
  "valid": true
}
```

### 6.3 Deployment status

**Not deployed.**

- **Render URL:** none
- **Deployment status:** blocked before service creation
- **Reason:** the local repository is initialized on `main`, but it has no remote, no initial commit, and no GitHub repository. Render builds Docker services from a Git repository. The authenticated Render CLI confirms an existing workspace and an unrelated service (`maze-reminder-bot-ohio`), but its `services create` command requires `--repo`; the CLI does not upload local source.
- **Existing Render resources:** the unrelated existing service was not modified.
- **Local Docker verification:** not performed because Docker is not installed on this machine. The Maven jar, production profile, HTTP contract, and Blueprint schema were verified independently.

### 6.4 Deployment procedure once a repository exists

1. Create a GitHub repository and push this monorepo to its default branch. A private repository is appropriate; no secrets are present.
2. In Render, choose **New → Blueprint**, connect the repository, and apply the root `render.yaml`.
3. Render builds `backend/Dockerfile` and deploys the web service.
4. Verify:
   - `https://<service-name>.onrender.com/actuator/health` returns `200` with status `UP`.
   - `https://<service-name>.onrender.com/api/v1/system/info` returns the success envelope with `environment: "prod"` and version `0.1.0`.
   - An unknown path returns the 404 error envelope.
5. Retrieve the service's outbound IP information using the procedure in Section 7.
6. Replace the free plan with an always-on paid plan before continuous monitoring or WebSocket work.

No deployment should be considered complete until the public health and system endpoints have been checked on the deployed origin.

---

## 7. Networking

### 7.1 Render outbound networking

Render services send outbound traffic through **shared IP ranges associated with their region**. The ranges are shared across all services in that region; they are not a single per-service address. Render documents the ranges in CIDR notation and states that a service may use any address in its associated ranges.

**Actual ranges for this project could not be determined because no Render service exists yet.** They must not be guessed.

### 7.2 How to retrieve the outbound IP information

After deployment:

1. Open the Render Dashboard.
2. Open the **specific service** details page, not the workspace home.
3. Open the **Connect** dropdown in the upper right.
4. Switch to the **Outbound** tab and copy the listed CIDR ranges.

Alternatively, use the authenticated Render API:

```http
GET https://api.render.com/v1/services/{serviceId}/outbound-ips
Authorization: Bearer <RENDER_API_TOKEN>
```

The response distinguishes shared and dedicated outbound IPs and lists the addresses or ranges that apply to the service.

### 7.3 Are dedicated static IPs required?

Not for the current Phase 1 scope. There is no outbound third-party integration yet.

Render's shared ranges are suitable for many external services that accept CIDR allowlists. If a future provider requires a single stable address or does not support CIDR allowlisting, Render offers **dedicated outbound IPs**:

- Requires a Pro workspace plan or higher.
- Each IP set contains three IPv4 addresses in one region.
- IP sets are region-scoped and can be scoped to an environment or the whole workspace.
- Render charges an additional monthly fee per IP set. At the time of this report the published price is $100/month per set; confirm current pricing before purchasing.

No dedicated IP set was created because it would add cost and is not justified by Phase 1.

### 7.4 Relationship to future SmartAPI registration

Angel One's current SmartAPI documentation states that a registered **static IP is mandatory for order and GTT execution**. Market-data, authentication, and other non-order APIs do not require a static IP according to Angel One's published SmartAPI guidance.

This platform is explicitly not an order-placement system: the human user always executes trades manually. Therefore:

- A Render dedicated IP should **not** be purchased solely for the planned analysis/monitoring scope.
- Before Phase 2 SmartAPI registration, confirm the current Angel One requirements against the official SmartAPI documentation. Requirements can change.
- If a future approved phase ever needs order execution or a provider that mandates static egress, evaluate Render dedicated IPs at that time. Do not implement order placement now.

### 7.5 Networking limitations discovered

1. Outbound ranges are regional and shared; they are not a per-service static address.
2. Actual ranges require a deployed service. None exists yet.
3. Dedicated IPs require a paid workspace plan and a separate monthly fee.
4. Render's **free web service spins down after 15 minutes without traffic** and wakes on the next request. This is acceptable for a Phase 1 smoke test, but **not acceptable for continuous live trade monitoring or WebSocket sessions**. An always-on paid compute plan is required before those phases.

---

## 8. Testing

### 8.1 Automated tests

Command:

```bash
cd backend
mvn -B -ntp test
```

Result: **9 tests run, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS.**

| Test | What it verifies | Expected | Actual |
|---|---|---|---|
| `TradingAssistantApplicationTests.contextLoads` | Full Spring application context starts with the local default profile | Context loads | Passed |
| `SystemEndpointIntegrationTest.systemInfoReturnsSuccessEnvelope` | `GET /api/v1/system/info` returns 200, JSON, success envelope, name, version, environment, API version, metadata | 200 + complete envelope | Passed |
| `SystemEndpointIntegrationTest.suppliedRequestIdIsEchoedInHeaderAndResponseMetadata` | A valid `X-Request-Id` is echoed and reflected in `meta.requestId` | Same value in both places | Passed |
| `SystemEndpointIntegrationTest.invalidSuppliedRequestIdIsReplaced` | An invalid request ID is rejected and replaced with a UUID | UUID in header, original value absent | Passed |
| `SystemEndpointIntegrationTest.healthEndpointIsUp` | Actuator health endpoint is exposed and reports UP | 200 + `status: UP` | Passed |
| `SystemEndpointIntegrationTest.unknownResourceReturnsNotFoundEnvelope` | Unknown API path maps to the standard 404 envelope | 404 + `RESOURCE_NOT_FOUND` | Passed |
| `SystemEndpointIntegrationTest.unsupportedMethodReturnsMethodNotAllowedEnvelope` | Unsupported method maps to the standard 405 envelope | 405 + `METHOD_NOT_ALLOWED` | Passed |
| `GlobalExceptionHandlerTest.invalidRequestBodyReturnsValidationErrorEnvelope` | Bean Validation failure maps to 400 with field details | 400 + `VALIDATION_FAILED` + `name` field | Passed |
| `GlobalExceptionHandlerTest.unexpectedExceptionReturnsSafeInternalErrorEnvelope` | An unexpected exception maps to 500 without exposing its message | 500 + `INTERNAL_ERROR`, no internal detail | Passed |

The validation test uses a test-only controller because Phase 1 intentionally has no production endpoint that accepts a request body. This verifies the centralized handler without inventing unnecessary business endpoints.

### 8.2 Live production-profile verification

Command:

```bash
PORT=18080 SPRING_PROFILES_ACTIVE=prod \
  java -jar target/trading-assistant-backend-0.1.0.jar
```

| Check | Expected | Actual |
|---|---|---|
| Application starts on the configured port | Tomcat on 18080, `prod` active | Passed, started in 2.577 seconds |
| `GET /actuator/health` | 200, status UP | Passed |
| `GET /api/v1/system/info` | 200, version 0.1.0, environment prod | Passed |
| Valid request ID header | Echoed in header and metadata | Passed |
| Unknown path | 404 standard error envelope | Passed |
| Unsupported method | 405 standard error envelope | Passed |
| Shutdown | Process stops and port closes | Passed |

### 8.3 Blueprint validation

```text
render blueprints validate ./render.yaml --output json
```

Expected: valid Blueprint with one planned service. Actual: `"valid": true`, one service (`trading-assistant-api`). Passed.

### 8.4 Not tested / limitations

- **Docker image build:** not run locally because Docker is not installed. The Dockerfile is syntactically reviewed and follows Render's documented multi-stage Docker pattern, but remote image construction remains unverified until a Render deploy.
- **Render deployment:** not performed. No public URL exists.
- **Render outbound IP retrieval:** impossible before service creation.
- **Android client:** intentionally not implemented or tested.
- **Load, performance, and security penetration testing:** out of Phase 1 scope.

---

## 9. Problems Encountered

### 9.1 Render deployment blocked by missing GitHub repository — unresolved

**Problem:** Render deploys Docker web services from a Git repository. A local `.git` repository has been initialized on `main`, but it has no remote, no initial commit, and no GitHub repository. The Render CLI is authenticated, but the CLI's service creation requires a repository URL; there is no local-source upload command.

**Impact:** The "Deploy to Render" and "Verify deployment" tasks cannot be completed in this session.

**Possible solutions:**

1. The user creates an empty GitHub repository and pushes this monorepo. This is the recommended path.
2. The user provides an existing repository URL and explicitly authorizes pushing the project.
3. The user connects the repository in the Render Dashboard and applies the Blueprint manually.

A local Git repository was initialized on `main`, but no GitHub repository was created, committed, or pushed, because those external actions were not explicitly requested and could expose or relocate the user's code.

### 9.2 Docker unavailable locally — limitation

**Problem:** `docker` is not installed on the machine, so the production image could not be built locally.

**Impact:** Docker build behavior and the non-root runtime were not executed locally. Maven packaging and the jar were fully verified.

**Possible solutions:**

- Install Docker Desktop and run `docker build -t trading-assistant-backend backend`.
- Let Render build the image after the repository is connected, then inspect the deploy logs.

### 9.3 Render free plan is not suitable for live monitoring — accepted Phase 1 trade-off

**Problem:** The Blueprint uses `plan: free` to avoid incurring an unapproved monthly charge. Free web services spin down after 15 minutes of inactivity and take roughly a minute to wake.

**Impact:** Fine for a Phase 1 health check; unsuitable for continuous market monitoring, WebSocket connections, or timely alerts.

**Solution:** Before Phase 2 live-monitoring work, change the plan to an always-on paid compute plan (currently `0.5c-512mb` at approximately $7/month, or larger). This is a deliberate billing decision for the user/reviewer.

### 9.4 Actual outbound IP ranges unavailable — expected

**Problem:** Render outbound ranges are tied to the deployed service and region. No service exists yet.

**Impact:** Cannot supply the current CIDR list or confirm allowlisting with a future provider.

**Solution:** Deploy first, then retrieve the ranges from the service's **Connect → Outbound** panel or the Render API. Do not use another service's ranges.

### 9.5 First test run had one assertion failure — resolved

**Problem:** The health test expected `application/json`, but Spring Boot Actuator correctly returns `application/vnd.spring-boot.actuator.v3+json`.

**Impact:** One test failed; the application behavior was correct.

**Solution:** The test now accepts the Actuator vendor JSON content type while still asserting HTTP 200 and `status: UP`. No production code was weakened. All tests pass.

### 9.6 No CI pipeline — known gap

**Problem:** There is no GitHub Actions or equivalent CI configuration yet.

**Impact:** Tests currently depend on a developer running Maven locally. This does not block Phase 1, but it weakens long-term quality gates.

**Solution:** Add a minimal Java 21/Maven CI workflow in a later approved phase. It was not added now to avoid scope expansion beyond the requested Phase 1 deliverables.

---

## 10. Recommendations

1. **Approve Phase 1** after reviewing the architecture, error contract, and test evidence.
2. **Create/push the GitHub repository**, connect it to Render, and apply the Blueprint. Verify the public health and system endpoints on the deployed origin before treating deployment as complete.
3. **Record the deployed service's outbound ranges** from Render after deployment. Do not assume the ranges of the existing unrelated service apply.
4. **Upgrade to an always-on Render plan before live monitoring.** The free plan's spin-down behavior conflicts with continuous market monitoring and WebSockets.
5. **Keep SmartAPI credentials out of the repository.** Phase 2 should use Render environment variables or a managed secret store, with separate values for local and deployed environments.
6. **Confirm Angel One requirements at Phase 2 kickoff.** Current documentation ties static-IP allowlisting to order/GTT APIs, not market data or login. Since this platform never places orders, dedicated Render IPs are not currently justified; revisit only if requirements change.
7. **Introduce MongoDB only when a phase has a concrete persistence requirement.** Define collections, indexes, retention, and migration/initialization strategy before adding the dependency.
8. **Add CI and automated deployment checks** after the repository is on GitHub. At minimum, run `mvn verify` for every pull request.
9. **Consider structured JSON logging and metrics** when a log aggregation or monitoring backend exists. Do not add them speculatively.
10. **Do not start Phase 2 implementation** until this report is reviewed and explicitly approved.

---

## Approval Gate

Phase 1 implementation is complete and locally verified. Render deployment is blocked by the missing GitHub remote and initial commit. The next action requires explicit reviewer approval and, for deployment, an authorized GitHub repository connection.

**Awaiting approval. No further phase work has started.**
