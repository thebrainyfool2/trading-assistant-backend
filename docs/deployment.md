# Deployment Guide

This guide covers the single-owner Phase 13 backend. Nothing in this document
has been executed against a production environment by the implementation work;
the commands are the repeatable deployment procedure.

## 1. Prerequisites

- JDK 21
- Maven 3.9.16+
- A MongoDB Atlas cluster (or another MongoDB 6+ deployment)
- A Render account, or Docker for another host
- Read-only broker credentials only if live market data should run

The API starts without broker credentials. Analysis lists are then empty,
`/api/v1/system/status` reports the engine as `DISABLED`, and watchlists,
preferences and notifications still work. The application has no order,
portfolio, position or funds endpoint.

## 2. MongoDB

Create a database user with read/write access to one database. The database must
be named `tradingassistant`.

Atlas connection string:

```text
mongodb+srv://<user>:<password>@<cluster-host>/tradingassistant?retryWrites=true&w=majority
```

- Do not use the `boom` database.
- Do not place credentials in `application.yml`, `render.yaml`, source control or
  client code.
- Allow the deployment host's outbound addresses in Atlas Network Access.
- The driver uses a 5-second server-selection timeout and a 10-second socket read
  timeout, so an outage fails promptly instead of exhausting request threads.

The URI is read from `MONGODB_URI` in every profile. The local profile alone has
a credential-free `mongodb://localhost:27017/tradingassistant` fallback. If the
URI omits a database name, MongoDB defaults to `test`; always include
`/tradingassistant`.

Indexes are created idempotently at startup. If MongoDB is unreachable at boot,
startup continues, index creation is skipped with a warning, and
`/api/v1/system/status` reports `DEGRADED` until the database is reachable. See
[mongodb.md](mongodb.md).

## 3. Required environment

| Variable | Required | Default | Notes |
|---|---|---|---|
| `MONGODB_URI` | yes | local fallback only | Must include `/tradingassistant` |
| `CORS_ALLOWED_ORIGINS` | no | empty | Comma-separated browser origins |
| `SPRING_PROFILES_ACTIVE` | production | `local` | Set to `prod` in deployment |
| `PORT` | production | `10000` | HTTP port |
| `PLATFORM_JOBS_ENABLED` | no | `true` | Maintenance scheduling |
| `SMARTAPI_ENABLED` | no | `false` | Enables read-only market data |
| `SMARTAPI_API_KEY` | with broker | empty | Never commit |
| `SMARTAPI_CLIENT_CODE` | with broker | empty | Never commit |
| `SMARTAPI_PIN` | with broker | empty | Never commit |
| `SMARTAPI_TOTP_SECRET` | with broker | empty | Never commit |
| `SMARTAPI_CLIENT_LOCAL_IP` | with broker | empty | Required login metadata |
| `SMARTAPI_CLIENT_PUBLIC_IP` | with broker | empty | Required login metadata |
| `SMARTAPI_MAC_ADDRESS` | with broker | empty | Required login metadata |
| `LIVE_MARKET_INSTRUMENTS` | no | `NSE_CASH:2885,NSE_CASH:NIFTY,NSE_CASH:BANKNIFTY,NSE_FO:NIFTY` | Baseline subscriptions |
| `LIVE_MARKET_INCLUDE_WATCHLISTS` | no | `true` | Add watchlist instruments |
| `LIVE_MARKET_MAX_INSTRUMENTS` | no | `200` | Hard cap |
| `LIVE_TICK_MAX_AGE` | no | `2m` | Tick freshness policy |
| `LIVE_CANDLE_MAX_AGE` | no | `10m` | Candle freshness policy |
| `NSE_HOLIDAYS`, `BSE_HOLIDAYS`, `MCX_HOLIDAYS` | no | empty | Comma-separated ISO dates |

There is no login, JWT, refresh-token, BCrypt or administrator-bootstrap
configuration. A fixed local principal owns watchlists, preferences and
notifications.

## 4. Build and verify

```bash
cd backend
mvn -B -ntp verify
mvn -B -ntp package
```

The executable jar is `target/trading-assistant-backend-0.1.0.jar`. The build
also writes `META-INF/build-info.properties`, exposed through
`/api/v1/system/info`.

## 5. Run locally

```bash
export MONGODB_URI='mongodb://localhost:27017/tradingassistant'
java -jar target/trading-assistant-backend-0.1.0.jar
```

Useful checks:

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/api/v1/system/status
curl -i http://localhost:8080/api/v1/market/freshness
```

With the broker enabled, `/api/v1/market/freshness` is the quickest confirmation
that ticks and candles are arriving. `UNKNOWN` or an empty list means no live
observation has reached the process yet; it does not mean the endpoint failed.

## 6. Docker

```bash
docker build -t trading-assistant-backend .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e MONGODB_URI='mongodb+srv://.../tradingassistant' \
  trading-assistant-backend
```

The image runs as an unprivileged user. Secrets belong in the platform's secret
store, not in the image or a committed env file.

## 7. Render

1. Create a web service from the backend repository.
2. Build command: `mvn -B -ntp package -DskipTests`.
3. Start command: `java -jar target/trading-assistant-backend-0.1.0.jar`.
4. Add `MONGODB_URI` as a secret, including `/tradingassistant`.
5. Add broker secrets only when live monitoring is required.
6. Set the health check to `/actuator/health`.

The free Render plan spins down after inactivity and is not suitable for
continuous WebSocket monitoring.

## 8. Post-deploy checklist

- [ ] `/actuator/health` reports `UP`.
- [ ] `/api/v1/system/status` reports the database as `UP`.
- [ ] The database name in the URI is `tradingassistant`, not `boom`.
- [ ] No secret is present in logs, source, the client or an API response.
- [ ] If the broker is enabled, `/api/v1/market/freshness` shows observed
      instruments and the freshness policy is appropriate for the session.
- [ ] `NSE_HOLIDAYS`, `BSE_HOLIDAYS` and `MCX_HOLIDAYS` contain the current
      year's dates if calendar-aware sessions are required.
- [ ] No order, portfolio or execution endpoint is reachable.
