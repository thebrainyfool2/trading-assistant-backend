# Phase 2 Implementation Report

**Project:** Trading Assistant

**Phase:** 2 — SmartAPI broker integration foundation

**Report date:** 18 November 2026

**Status:** Implementation, automated verification, and live authenticated verification complete. Awaiting architectural review.

---

## 1. Executive Summary

Phase 2 adds a reusable, read-only broker integration layer behind a broker abstraction. The application depends on `BrokerProvider` and its capability interfaces, never on Angel One classes. Order placement, order modification, cancellation, GTT, basket orders, portfolio, positions and holdings are intentionally absent.

Completed:

- Broker abstraction: `BrokerProvider`, `AuthenticationProvider`, `SessionProvider`, `MarketDataProvider`
- Broker-neutral models for sessions, profiles, quotes, depth, candles, instruments and feed ticks
- Conditional SmartAPI adapter, disabled by default
- Strongly typed, fail-fast SmartAPI configuration
- TOTP login using the maintained `otp-java` library
- Thread-safe session manager with expiry tracking, refresh-token renewal, re-login fallback and duplicate-login prevention
- JDK-backed Spring `RestClient` transport with connect/read timeouts, bounded retries, exponential backoff, request correlation and safe error translation
- Live quote retrieval (LTP, OHLC, FULL including best-five depth)
- Historical candle retrieval with documented interval spans
- Public instrument master download and mapping
- Streaming 2.0 WebSocket client with subscription, 30-second heartbeat, binary decoding and graceful close
- Structured operational logging with no credentials, OTPs or tokens
- 64 automated tests in the default suite, plus a separately run credentialed live test

Live verification completed on 18 November 2026:

- TOTP login and session issuance, capped at the next midnight in `Asia/Kolkata`
- Profile retrieval
- Live FULL quote for NSE token `3045`
- Historical daily candles for NSE token `3045`
- Instrument master: HTTP 200, 170,064 rows, 39,955,837 bytes
- Authenticated Streaming 2.0 WebSocket connection and graceful close with status `1000`

No order-related method exists in the broker API.

---

## 2. Discovery Report

### 2.1 Sources reviewed

Primary sources:

- <https://smartapi.angelone.in/docs>
- <https://smartapi.angelone.in/docs/User>
- <https://smartapi.angelone.in/docs/RateLimit>
- <https://smartapi.angelone.in/docs/Historical>
- <https://smartapi.angelone.in/docs/Instruments>
- <https://smartapi.angelone.in/docs/WebSocket2>
- <https://smartapi.angelone.in/docs/OptionGreeks>
- <https://smartapi.angelone.in/docs/CASDoc>
- Official Python SDK: <https://github.com/angel-one/smartapi-python>
- Official SmartAPI forum announcements, including <https://smartapi.angelone.in/smartapi/forum/post/18249>

The older `smartapi.angelbroking.com` documentation remains indexed and contains stale copies. It was not treated as authoritative when it conflicted with the current `smartapi.angelone.in` content.

### 2.2 Authentication and session findings

| Topic | Documented behavior | Phase 2 decision |
|---|---|---|
| Login endpoint | `POST /rest/auth/angelbroking/user/v1/loginByPassword` | Implemented. |
| Login body | `clientcode`, `password` (PIN), `totp`, optional `state` | Implemented. TOTP is generated immediately before login. |
| Login headers | `X-PrivateKey`, `X-UserType`, `X-SourceID`, `X-ClientLocalIP`, `X-ClientPublicIP`, `X-MACAddress` | Implemented. Live testing showed the login endpoint rejects a request without the client metadata headers using `AB1012` (documented as “Invalid Product Type”); all three metadata values are therefore required configuration. |
| TOTP | Required second factor | Generated with `otp-java`; never logged or exposed. |
| Session lifetime | Session remains active until midnight unless logout is called; official announcement states the authentication token expires at 00:00 daily | Local session expiry is capped at the next midnight in `Asia/Kolkata`, even if the JWT `exp` is later. |
| Login response | `jwtToken`, `refreshToken`, `feedToken` | All three are retained in memory in an internal session object. The public session status contains none. |
| Refresh endpoint | `POST /rest/auth/angelbroking/jwt/v1/generateTokens` with `refreshToken` | Implemented. It is not automatically retried because token rotation semantics are undocumented. |
| Refresh response | New `jwtToken`, `refreshToken`, `feedToken` | The returned refresh token is stored when present; otherwise the previous token is kept. |
| Refresh-token rotation | Not documented. The official SDK is internally inconsistent: one method discards the returned refresh token, another stores it. | Do not assume the old token is invalid or that the new one must replace it. Store the returned value when present, keep the previous value when omitted. |
| JWT TTL | Numeric TTL is not documented | Parse the JWT `exp` claim for local tracking without verifying its signature; cap at midnight. |
| Refresh-token TTL | Not documented | No assumption. If refresh is rejected with a session/auth/request error, re-login with TOTP. |
| Logout | `POST /rest/secure/angelbroking/user/v1/logout`; destroys the API session and invalidates the access token | Implemented. Local state is cleared even if the remote logout call fails. |
| Profile | `GET /rest/secure/angelbroking/user/v1/getProfile` | Implemented. Profile is not logged because it contains personal data. |
| OAuth | Announced in the forum but no OAuth endpoint is documented on the current API pages | Not implemented. The documented `loginByPassword` flow is used. |
| Static IP | Tied to order/GTT execution, not market-data or login | No order APIs, so no static-IP registration is required for this phase. |

The session manager serializes renewal so concurrent callers do not create duplicate sessions. It renews once when a rejected token is still inside the local validity window, then retries the safe read operation once.

### 2.3 Rate limits

Canonical table as published at <https://smartapi.angelone.in/docs/RateLimit>:

| Endpoint | Per second | Per minute | Per hour |
|---|---:|---:|---:|
| `loginByPassword` | 1 | — | — |
| `generateTokens` | 1 | — | 1000 |
| `getProfile` | 3 | — | 1000 |
| `logout` | 1 | — | — |
| `market/v1/quote` | 10 | 500 | 5000 |
| `historical/v1/getCandleData` | 2 | 40 | 2000 |
| `marketData/v1/optionGreek` | 1 | — | — |

Over-limit behavior is documented as HTTP 403 with a message about exceeding the access rate. The adapter maps both the documented 403 rate-limit response and HTTP 429, and retries only safe read operations with bounded backoff. The documentation is internally inconsistent:

- The quote page prose says one request per second, while the rate-limit table says 10 per second.
- Historical candle limits are published as 2/sec + 40/min + 2,000/hr in the canonical table, but other official pages show 80/min and 5,000/hr.
- Option Greeks is listed at 3/sec on one page and 1/sec on the canonical table.

Phase 2 uses the canonical table as the conservative reference. It does not implement a client-side rate limiter in this phase; callers must serialize work and stay within these limits. Documented HTTP 403 rate-limit responses and HTTP 429 are mapped and retried only for safe read operations with bounded exponential backoff. This is recorded as a Phase 3 improvement.

### 2.4 REST endpoints relevant to this phase

| API | Method and path | Auth | Notes |
|---|---|---|---|
| Login | `POST /rest/auth/angelbroking/user/v1/loginByPassword` | API key only | Returns JWT, refresh and feed tokens. |
| Refresh | `POST /rest/auth/angelbroking/jwt/v1/generateTokens` | JWT + refresh token | Returns a new token set. |
| Profile | `GET /rest/secure/angelbroking/user/v1/getProfile` | JWT | Personal data; not logged. |
| Logout | `POST /rest/secure/angelbroking/user/v1/logout` | JWT | Destroys the broker session. |
| Live quote | `POST /rest/secure/angelbroking/market/v1/quote/` | JWT | Modes LTP, OHLC and FULL; FULL includes best-five depth. |
| Historical candles | `POST /rest/secure/angelbroking/historical/v1/getCandleData` | JWT | Intervals `ONE_MINUTE` through `ONE_DAY`; response rows are `[timestamp, open, high, low, close, volume]`. |
| Historical OI | `POST /rest/secure/angelbroking/historical/v1/getOIData` | JWT | Not implemented; outside the minimum Phase 2 scope. |
| Instrument master | `GET https://margincalculator.angelone.in/OpenAPI_File/files/OpenAPIScripMaster.json` | None | Public JSON array. |
| Option Greeks | `POST /rest/secure/angelbroking/marketData/v1/optionGreek` | JWT | NSE only; 1/sec. Not implemented in Phase 2. |
| Option chain | No dedicated endpoint documented | — | Assemble from CE/PE quotes if a later phase requires it. |

### 2.5 Quote and depth findings

- Quote modes are `LTP`, `OHLC` and `FULL`.
- The quote page states a maximum of 50 symbols per request; Phase 2 enforces that limit.
- `FULL` returns best-five buy and sell depth (`depth.buy[]`, `depth.sell[]`) along with circuit limits, 52-week high/low, total buy/sell quantity and open interest.
- There is no standalone REST market-depth endpoint.
- The response envelope may contain both `data.fetched[]` and `data.unfetched[]`. Partial failures are returned as partial results; Phase 2 logs the unfetched count and returns the fetched rows.

### 2.6 Historical candles

- Intervals: `ONE_MINUTE`, `THREE_MINUTE`, `FIVE_MINUTE`, `TEN_MINUTE`, `FIFTEEN_MINUTE`, `THIRTY_MINUTE`, `ONE_HOUR`, `ONE_DAY`.
- Maximum date span per request: 30, 60, 100, 100, 200, 200, 400 and 2,000 days respectively.
- Request timestamps are unzoned `yyyy-MM-dd HH:mm`; this adapter interprets them as Indian market time by contract, without attaching a timezone to the outbound string.
- Responses use ISO offset timestamps such as `2023-09-06T11:15:00+05:30`.
- Phase 2 validates the requested span before sending and maps malformed rows to a broker invalid-response error.

### 2.7 Instrument master findings

- The documentation calls the file a "CSV dump", but the current URL returns a JSON array.
- Live check on 18 November 2026: HTTP 200, **170,064 rows**, **39,955,837 bytes**.
- Actual keys observed: `token`, `symbol`, `name`, `expiry`, `strike`, `tick_size`, `lotsize`, `instrumenttype`, `exch_seg`, `freeze_qty`, plus `is_cas_enabled`.
- The documented CSV column names (`exchange_token`, `tradingsymbol`, `lot_size`, `instrument_type`) differ from the JSON keys. Both variants are accepted.
- Tokens for expired derivatives may be reassigned to new expiries, so historical retrieval for an expired contract under an old token is not reliable. This is documented, not worked around.
- Phase 2 intentionally does not cache the instrument master. The full download is roughly 40 MB and must be indexed or cached by a later approved phase before frequent use.

### 2.8 WebSocket Streaming 2.0 findings

| Topic | Documented behavior | Phase 2 decision |
|---|---|---|
| URL | `wss://smartapisocket.angelone.in/smart-stream` | Implemented. |
| Auth headers | `Authorization`, `x-api-key`, `x-client-code`, `x-feed-token` | Implemented. The official Python SDK sends the raw JWT rather than a `Bearer` prefix; Phase 2 follows the SDK for the socket. |
| Connections | Up to three per client code | Caller-managed. |
| Subscription quota | 1,000 token-mode entries per session | Validated before connecting. |
| Modes | 1 LTP, 2 Quote, 3 Snap Quote | Implemented. |
| Depth mode | Mode 4 is present in the official Python SDK but absent from the current protocol page | Not implemented; documented as a documentation/SDK gap. |
| Exchange types | 1 NSE CM, 2 NSE FO, 3 BSE CM, 4 BSE FO, 5 MCX FO; SDK adds 7 and 13 | Phase 2 supports 1–5 for the feed. |
| Heartbeat | Client sends text `ping` every 30 seconds; server replies `pong` | Implemented at 30 seconds. The SDK's 10-second value is a client choice, not the documented interval. |
| Response | Binary, little-endian; LTP 51 bytes, Quote 123 bytes, Snap Quote 379 bytes; prices in paise | Implemented. |
| Circuit/52-week fields | Snap Quote includes them after the best-five block | Implemented. |
| Best-five depth | 10 packets of 20 bytes, buys followed by sells | Implemented from the documented layout and flags. The official SDK swaps the buy/sell arrays; that SDK bug is not copied. |
| Error frames | `E1001` invalid payload; `E1002` subscription limit exceeded | Mapped to a safe feed error; the raw frame is not logged. |
| Auth failure | HTTP 401 with `x-error-message` | Live unauthenticated check confirmed 401; the client forces one renewal on a 401 handshake and retries once. |
| Reconnect | No automatic reconnect requirement documented | Not implemented in Phase 2; the connection exposes lifecycle events so a later phase can add reconnect/resubscribe policy. |

The WebSocket feed is read-only. No order-status socket is used.

### 2.9 Option chain, Greeks and market limitations

- There is no documented dedicated option-chain REST endpoint. A chain must be assembled from CE/PE instrument tokens and quotes in a later phase.
- Option Greeks are available only for NSE through `marketData/v1/optionGreek`, at 1 request/second. The page contains stale sample paths under `order/v1`; the prose and official SDK use `marketData/v1`. Phase 2 does not calculate Greeks and does not call the endpoint.
- SmartAPI publishes no holiday calendar and no holiday-aware market-data behavior. Holiday handling must come from an exchange calendar in a later phase.
- The CAS/trading-time changes announced for 3 August 2026 affect order entry, not market-data retrieval. This platform does not place orders.

---

## 3. Architecture

### 3.1 Dependency flow

```text
Application services
        │
        ▼
BrokerProvider  ──────────────► AuthenticationProvider
        │                       SessionProvider
        │                       MarketDataProvider
        ▼
AngelOneBrokerProvider
        │
        ├── AngelOneSessionManager ──► AngelOneAuthenticator ──► AngelOneHttpClient
        ├── AngelOneMarketDataProvider ──► AngelOneHttpClient
        │                              └─► AngelOneInstrumentMasterClient
        └── AngelOneMarketFeedClient ──► JDK WebSocket + BinaryTickDecoder
```

No class outside `com.tradingassistant.broker.angelone` imports a SmartAPI DTO or transport type. The rest of the application sees token-free models and capability interfaces.

### 3.2 Package responsibilities

| Package | Responsibility |
|---|---|
| `broker.api` | Capability interfaces consumed by the application. |
| `broker.model` | Broker-neutral quotes, depth, candles, instruments, profiles, session status and feed ticks. |
| `broker.exception` | Safe broker error categories and exceptions. |
| `broker.error` | Broker-to-platform exception advice; keeps the common layer independent of broker types. |
| `broker.config` | Typed SmartAPI configuration and conditional Spring wiring. |
| `broker.angelone` | Provider facade, internal session and session manager. |
| `broker.angelone.auth` | TOTP generation, JWT expiry parsing, login/refresh/profile/logout. |
| `broker.angelone.http` | SmartAPI transport, headers, retry policy and response translation. |
| `broker.angelone.market` | Quote, candle and instrument-master operations. |
| `broker.angelone.ws` | WebSocket connection, subscription, heartbeat, binary decoder and close. |
| `broker.angelone.dto` | Wire-format DTOs isolated to the adapter. |

### 3.3 Design decisions

- **Interface-first broker abstraction.** A future broker implements the same interfaces; application services do not change.
- **Conditional adapter.** `SMARTAPI_ENABLED=false` by default. This preserves the Phase 1 deployment and allows the code to ship before live credentials are available. When enabled, required secrets are validated at startup and missing values fail fast without printing values.
- **No token-bearing public models.** `SessionStatus` contains authentication state and expiry only. Tokens remain inside the adapter.
- **Synchronous REST.** `RestClient` with `JdkClientHttpRequestFactory` is a Spring-native blocking client, uses the JDK HTTP client, needs no additional dependency, and gives explicit connect/read timeouts. Reactive or WebClient was unnecessary for a read-only integration.
- **JDK WebSocket.** `java.net.http.WebSocket` avoids adding a second WebSocket stack and supports binary frames, text heartbeat and graceful close directly.
- **Bounded retries only where safe.** Login, token refresh and logout are never retried automatically. Quote, candles, profile and instrument master use the safe retry policy.
- **One renewal on rejection.** A 401 from a read operation forces a single session renewal and one retry. It does not loop.
- **No client-side rate limiter yet.** The documented limits are recorded; 429 handling exists. A quota-aware scheduler is a Phase 3 recommendation.
- **No order/persistence/analysis code.** The module contains only broker communication and normalization.

---

## 4. Authentication Flow

### 4.1 Login and session creation

```text
Caller
  │ ensureAuthenticated()
  ▼
SessionManager
  │ existing session not expiring? ── yes ──► return token-free status
  │ no
  ▼
synchronized lock
  │ re-check; another thread may have completed login
  ▼
Authenticator.login()
  │ TOTPGenerator.generate(now)          (value never logged)
  │ POST loginByPassword
  │ validate response envelope
  │ jwtToken + refreshToken + feedToken
  ▼
expiresAt = min(JWT exp, next midnight Asia/Kolkata)
  ▼
store in-memory session
  ▼
return SessionStatus (no tokens)
```

### 4.2 Renewal

```text
Session close to expiry or broker rejects token
  │
  ├─ refresh token present?
  │     └─ POST generateTokens
  │           ├─ success: replace token set; store returned refresh token if present
  │           └─ session/auth/request rejection: continue to full login
  │
  └─ full login
        └─ generate a fresh TOTP and POST loginByPassword
```

Network, rate-limit, configuration and invalid-response failures do **not** trigger a full login automatically. This avoids creating duplicate sessions when the broker is merely unavailable.

### 4.3 Rejection retry

```text
read operation (quote/candle/profile)
  │
  ├─ success ──► return normalized model
  │
  └─ 401 / AG8001 / AG8002 / AB8050 / AB8051
        │ forceRenew()
        │ retry the same safe read exactly once
        └─ success or propagate a safe error
```

### 4.4 Logout

The local session is cleared first, then the broker logout call is attempted. A remote failure is logged and does not leave the local token usable.

### 4.5 Token lifecycle summary

- JWT expiry: read from the `exp` claim for local scheduling, capped at the next midnight in India.
- Refresh token: retained in memory; returned rotated values are stored when present.
- Feed token: retained separately; required for the WebSocket handshake.
- No token, PIN, OTP or API key is logged or returned by a public model.
- No token is persisted to disk or a database.

---

## 5. Configuration

The integration is disabled by default. When `SMARTAPI_ENABLED=true`, `SMARTAPI_API_KEY`, `SMARTAPI_CLIENT_CODE`, `SMARTAPI_PIN`, `SMARTAPI_TOTP_SECRET`, `SMARTAPI_CLIENT_LOCAL_IP`, `SMARTAPI_CLIENT_PUBLIC_IP` and `SMARTAPI_MAC_ADDRESS` are required. Startup fails with a message naming the missing variable and never includes its value.

| Environment variable | Required when enabled | Purpose | Safe default |
|---|---|---|---|
| `SMARTAPI_ENABLED` | No | Enables the adapter and conditional bean | `false` |
| `SMARTAPI_API_KEY` | Yes | SmartAPI application key | empty |
| `SMARTAPI_CLIENT_CODE` | Yes | Angel One client/account code | empty |
| `SMARTAPI_PIN` | Yes | Angel One PIN used as the login password | empty |
| `SMARTAPI_TOTP_SECRET` | Yes | Base32 TOTP shared secret | empty |
| `SMARTAPI_STATE` | No | Login state value echoed by the broker | `prod` |
| `SMARTAPI_CLIENT_LOCAL_IP` | Yes | `X-ClientLocalIP` request metadata | empty |
| `SMARTAPI_CLIENT_PUBLIC_IP` | Yes | `X-ClientPublicIP` request metadata | empty |
| `SMARTAPI_MAC_ADDRESS` | Yes | `X-MACAddress` request metadata | empty |
| `SMARTAPI_BASE_URL` | No | REST base URL | `https://apiconnect.angelone.in` |
| `SMARTAPI_WEBSOCKET_URL` | No | Streaming URL | `wss://smartapisocket.angelone.in/smart-stream` |
| `SMARTAPI_INSTRUMENT_MASTER_URL` | No | Public instrument file | documented Angel One URL |
| `SMARTAPI_CONNECT_TIMEOUT` | No | TCP/TLS connect timeout | `5s` |
| `SMARTAPI_READ_TIMEOUT` | No | REST read timeout | `20s` |
| `SMARTAPI_SESSION_EXPIRY_SKEW` | No | Renew before expiry | `60s` |
| `SMARTAPI_MAX_RETRIES` | No | Safe-read retries after first attempt | `2` |
| `SMARTAPI_RETRY_BASE_DELAY` | No | First backoff delay | `250ms` |
| `SMARTAPI_RETRY_MAX_DELAY` | No | Backoff cap | `2s` |

`.env.example` documents the same names with empty secret values. Spring Boot does not read `.env` automatically; values must be supplied through the shell, IDE run configuration, container or Render environment.

---

## 6. HTTP Layer

### 6.1 Client choice

`RestClient` with `JdkClientHttpRequestFactory`:

- Spring-native and synchronous, matching the application's Spring MVC stack.
- Uses `java.net.http.HttpClient` without adding Apache HttpClient, Netty or WebClient.
- Supports explicit connect timeout on the JDK client and read timeout on the request factory.
- Keeps request construction and response conversion consistent with the rest of Spring.
- Avoids a reactive dependency for a read-only integration.

### 6.2 Headers

The transport sets the documented headers:

- `Content-Type: application/json`
- `Accept: application/json`
- `X-UserType: USER`
- `X-SourceID: WEB`
- `X-PrivateKey` when configured
- `Authorization: Bearer <jwt>` for REST calls when a session exists
- Required `X-ClientLocalIP`, `X-ClientPublicIP`, `X-MACAddress`; the live login endpoint rejected valid credentials with `AB1012` until these headers were present

The WebSocket handshake sends the raw JWT in `Authorization`, matching the official Python SDK, plus `x-api-key`, `x-client-code` and `x-feed-token`.

### 6.3 Timeouts and retries

- Connect timeout: `SMARTAPI_CONNECT_TIMEOUT` (default 5 seconds).
- Read timeout: `SMARTAPI_READ_TIMEOUT` (default 20 seconds).
- Safe retry statuses: 403 rate-limit responses, 429, 500, 502, 503 and 504, plus network failures.
- Retry count is bounded by `SMARTAPI_MAX_RETRIES` (0–5).
- Delay grows exponentially from `SMARTAPI_RETRY_BASE_DELAY` to `SMARTAPI_RETRY_MAX_DELAY`.
- `Retry-After` is honored when present.
- Login, token refresh and logout use `RetryPolicy.none()`.
- No retry occurs after a 400/401/403/404 or an invalid response.

### 6.4 Error translation

| Broker condition | Internal category | API error code | HTTP status |
|---|---|---|---|
| Invalid credentials/account | `AUTHENTICATION_FAILED` | `BROKER_AUTHENTICATION_FAILED` | 502 |
| Invalid TOTP | `INVALID_TOTP` | `BROKER_AUTHENTICATION_FAILED` | 502 |
| Expired/invalid session or refresh token | `SESSION_EXPIRED` | `BROKER_SESSION_EXPIRED` | 503 |
| Rate limit | `RATE_LIMITED` | `BROKER_RATE_LIMITED` | 429 |
| Other broker rejection | `REQUEST_REJECTED` | `BROKER_REQUEST_REJECTED` | 502 |
| Maintenance/server failure | `UNAVAILABLE`/`NETWORK` | `BROKER_UNAVAILABLE` | 503 |
| Malformed/empty payload | `INVALID_RESPONSE` | `BROKER_INVALID_RESPONSE` | 502 |
| Feed failure | `WEBSOCKET` | `BROKER_INVALID_RESPONSE` | 502 |
| Invalid startup configuration | `CONFIGURATION` | `BROKER_CONFIGURATION_ERROR` | 500 |

The broker-module `BrokerExceptionHandler` returns only the platform's safe message and logs the broker error code. Raw broker messages and response bodies are not returned. The common exception handler remains broker-independent.

### 6.5 Logging and correlation

- Every request uses `X-Request-Id` from the existing MDC context.
- Request logs contain method, path, attempt and status; never headers, request bodies or tokens.
- Session logs contain expiry and whether tokens are present, never the values.
- WebSocket logs contain lifecycle status and counts, never the handshake headers.
- Unexpected listener failures log only the exception class name.

---

## 7. Endpoint Verification

| Capability | Authentication | Result | Evidence / limitation |
|---|---|---|---|
| TOTP generation | None | Verified | RFC 6238 vector `94287082` at epoch 59 with an 8-digit test generator; production defaults are 6 digits/SHA1/30s. |
| Login | API key + client code + PIN + TOTP + client metadata | **Live verified** | Successful login produced a refreshable session and a feed token. Missing client metadata produced HTTP 400 / `AB1012`; with the required headers the same credentials succeeded. |
| Session renewal | Refresh token | Implemented; mock-verified | Renewal, rotation fallback, re-login fallback, duplicate-login prevention and forced renewal after rejection tested. Live refresh was not forced during the short verification run. |
| Profile | JWT | **Live verified** | Profile returned successfully for the configured client. Documented fields mapped; exchanges/products tolerate string or array forms. |
| Live quote | JWT | **Live verified** | FULL quote for NSE token `3045` returned successfully. LTP/OHLC/FULL mapping, best-five depth, 52-week/circuit fields, partial unfetched handling and the 50-symbol limit are unit-tested. |
| Historical candles | JWT | **Live verified** | Daily candles for NSE token `3045` returned successfully. All 8 intervals and documented spans are validated locally; row mapping is tested. |
| Instrument master | None | **Live verified** | HTTP 200; 170,064 rows; 39,955,837 bytes. Actual JSON keys confirmed; mapping test covers `token`, `symbol`, `lotsize`, `instrumenttype`, `tick_size`, `exch_seg`, `freeze_qty`. |
| WebSocket endpoint | JWT + feed token | **Live verified** | Authenticated handshake, LTP subscription, connection and graceful close with status `1000` succeeded. Unauthenticated handshake returns HTTP 401 with `Invalid Header - Invalid Feed Token`. Subscription serialization, binary LTP/quote/snap-quote decoding, ping/pong and close are unit-tested. |
| WebSocket reconnect | JWT + feed token | Not implemented | Lifecycle callbacks and close semantics are available; reconnect/resubscribe belongs to a later phase. |
| Option chain | JWT | Not implemented | No dedicated endpoint is documented. |
| Option Greeks | JWT | Not implemented | Documented NSE-only endpoint; analytics/Greeks are outside Phase 2 scope. |

---

## 8. Testing

### 8.1 Automated result

```text
mvn -B -ntp verify
Tests run: 64, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

The single skipped test in the default suite is `AngelOneLiveIntegrationTest`, guarded by `SMARTAPI_LIVE_TESTS=true`. It was then run separately with the real credentials:

```text
mvn -B -ntp -Dtest=AngelOneLiveIntegrationTest test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 8.2 Coverage by behavior

| Area | Tests |
|---|---|
| TOTP | RFC 6238 vector, whitespace normalization, missing-secret rejection |
| JWT/session | Expiry parsing, malformed token handling, expiry boundaries, token redaction |
| Configuration | Disabled wiring, missing-secret fail-fast, HTTPS validation |
| Authentication | Login request, midnight cap, refresh fallback, profile mapping, logout, expired JWT |
| Session manager | No-login status, one-login concurrency, renewal, re-login fallback, no fallback on network failure, forced renewal, logout clearing, missing feed token |
| HTTP transport | Headers, retries, rate-limit mapping, TOTP envelope mapping, malformed response |
| Market data | Quote mapping/depth, partial failures, 50-symbol limit, candle mapping/span limit, instrument master mapping, one-retry session recovery |
| WebSocket | Subscription JSON, ping/pong, LTP/quote/snap-quote binary decoding, buy/sell depth, short/unknown packet rejection, graceful idempotent close |
| API contract | Broker unavailable maps to 503 `BROKER_UNAVAILABLE` without leaking the broker message |

No numeric coverage percentage is claimed. JaCoCo or another coverage gate is not configured; the table lists behaviors covered by the tests. A coverage gate is a Phase 3 recommendation.

### 8.3 Live and packaged verification status

Performed:

- Packaged application with the broker disabled: `prod` profile started, health 200 UP, system info 200.
- Packaged application with the broker enabled using test-only values: context started, health 200 UP, and no login/REST call occurred during startup.
- Packaged application with the broker enabled and secrets absent: exited with status 1 and a safe `SMARTAPI_API_KEY is required` startup message; the application did not start.
- Live TOTP login, profile, FULL quote, daily historical candles, instrument master, authenticated WebSocket and graceful close.
- Live WebSocket close status `1000` after the test subscription.
- All non-credentialed automated tests.

Important live finding:

- The login endpoint returned HTTP 400 / `AB1012` when the three client metadata headers were absent. The documented code text is “Invalid Product Type,” but the same credentials and TOTP succeeded once `X-ClientLocalIP`, `X-ClientPublicIP` and `X-MACAddress` were supplied. These values are now required configuration when the broker is enabled.
- No token, OTP, PIN or API key was written to disk or committed by the verification run.

The verified command form is:

```bash
SMARTAPI_LIVE_TESTS=true \
SMARTAPI_API_KEY='...' SMARTAPI_CLIENT_CODE='...' \
SMARTAPI_PIN='...' SMARTAPI_TOTP_SECRET='...' \
SMARTAPI_CLIENT_LOCAL_IP='...' SMARTAPI_CLIENT_PUBLIC_IP='...' \
SMARTAPI_MAC_ADDRESS='...' \
mvn -Dtest=AngelOneLiveIntegrationTest test
```

---

## 9. Problems Encountered

### 9.1 Spring Boot 4 uses Jackson 3 packages

**Problem:** Initial imports used `com.fasterxml.jackson.databind`, but Spring Boot 4.1 brings Jackson 3 (`tools.jackson.databind`). Compilation failed.

**Resolution:** Transport/DTO code now uses `tools.jackson.databind`; Jackson annotations remain the compatible `com.fasterxml.jackson.annotation` package. Tests compile and pass.

### 9.2 Instrument master schema differs from documentation

**Problem:** The documented CSV-style keys and the actual JSON keys differ. The first mapping test exposed `lotsize` and `instrumenttype` as null.

**Resolution:** DTO aliases accept both names. Live data confirmed the actual keys and row count.

### 9.3 Instrument master is much larger than a typical config file

**Problem:** The public file is approximately 40 MB and 170,064 rows. Loading it into a list is memory-heavy.

**Resolution:** Phase 2 does not cache it and documents the size. A later phase should index it or stream it into a bounded store. No caching was added because the phase explicitly limits caching to authentication/session needs.

### 9.4 Rate-limit documentation conflicts

**Problem:** Quote and historical limits disagree across official pages; the quote prose says 1/sec while its table says 10/sec.

**Resolution:** The canonical table is recorded, the implementation enforces the 50-symbol quote cap and historical span caps, and 429 is handled with bounded retries. No client-side scheduler is claimed. This remains a documented gap.

### 9.5 Refresh-token rotation is unspecified

**Problem:** The response returns a new refresh token, but the docs do not state whether the old token is invalidated or whether the new token must be persisted. The official SDK is inconsistent.

**Resolution:** Store the returned refresh token when present; keep the previous one when absent. If refresh is rejected with an auth/session/request error, perform a full login. The phase does not assume rotation semantics that the documentation does not provide.

### 9.6 Expiry TTLs are undocumented

**Problem:** Numeric JWT and refresh-token TTLs are absent. The session is documented as ending at midnight.

**Resolution:** Parse the JWT `exp` for local scheduling and cap validity at the next midnight in `Asia/Kolkata`. Re-login when refresh is rejected. This follows documented behavior without inventing TTLs.

### 9.7 WebSocket depth mode is in the SDK but not the current docs

**Problem:** Mode 4 (20-level depth) is implemented in the official Python SDK but absent from the Streaming 2.0 protocol page.

**Resolution:** Phase 2 supports the documented modes 1–3 and documents mode 4 as a gap. It does not silently rely on an undocumented wire format.

### 9.8 Official SDK swaps best-five depth arrays

**Problem:** The SDK's Snap Quote parser assigns the sell block to the buy key and vice versa.

**Resolution:** The decoder uses the documented order and depth flags. The SDK bug is not copied.

### 9.9 Login client metadata was incorrectly treated as optional

**Problem:** The first live login attempt with complete credentials returned HTTP 400 / `AB1012`. The adapter only sent `X-ClientLocalIP`, `X-ClientPublicIP` and `X-MACAddress` when configured, leaving them absent by default. Supplying the documented values made the same credentials succeed.

**Resolution:** The three metadata values are now required when the broker is enabled, validated at startup, documented in `.env.example` and the report, and covered by configuration tests. The live test passed after the fix.

### 9.10 No client-side rate limiter or reconnect loop

**Problem:** The adapter handles 429 and has bounded retries but does not serialize calls against the per-second/minute/hour quotas. It also does not auto-reconnect the feed.

**Resolution:** Documented as Phase 3 recommendations. Adding a quota scheduler and reconnect policy without a concrete workload would be speculative and outside the approved scope.

---

## 10. Recommendations

1. **Keep live credentials only in environment variables or a secret store.** Do not put the PIN, TOTP secret, API key, client metadata or tokens in Git.
2. **Rotate the API key and TOTP secret** after review because they were supplied directly in the conversation; consider changing the PIN as well.
3. **Add a client-side rate limiter** for login, quote, historical and profile calls using the canonical limits, with priority for interactive quotes.
4. **Add instrument-master indexing/caching** in a later phase. The full file is roughly 40 MB; parse it once into a bounded index with explicit refresh and expiry handling.
5. **Add WebSocket reconnect and resubscribe policy** with heartbeat-timeout detection, exponential reconnect backoff and subscription-budget tracking. Keep graceful close as the default shutdown path.
6. **Implement historical pagination/chunking** by interval and exchange calendar, including holiday awareness. Do not assume the broker supplies a holiday calendar.
7. **Add option-chain assembly and Greeks retrieval only in the approved options phase.** Use `marketData/v1/optionGreek`, not the stale `order/v1` sample path.
8. **Add observability:** broker request latency/error counters, feed connection state, session-renewal counts, and an optional health indicator that does not force a login on every probe.
9. **Add CI** with Maven verification and a separately gated live-integration job using repository secrets.
10. **Keep the broker interface free of order methods.** If order execution is ever approved, it requires a separate security and risk design; it must not be added implicitly to this read-only provider.
11. **Do not begin Phase 3 until Phase 2 is reviewed and approved.**

---

## Approval Gate

Phase 2 implementation, automated tests, live authenticated verification, and graceful WebSocket disconnect are complete. The live test used real credentials supplied only through the process environment and completed without exposing them. Session refresh remains mock-verified; the short live run did not force a refresh.

**Awaiting architectural review. No Phase 3 work has started. No order-placement functionality exists.**
