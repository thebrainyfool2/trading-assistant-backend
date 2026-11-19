# Deployment Guide

This guide covers the Phase 12 backend. Nothing in this document has been
executed against a production environment by the implementation work; the
commands are the repeatable deployment procedure.

## 1. Prerequisites

- JDK 21
- Maven 3.9.16+
- A MongoDB Atlas cluster (or another MongoDB 6+ deployment)
- A Render account, or Docker for another host
- Broker credentials only if the trading engine should run

The API can start without broker credentials. Market analysis, order placement
and live positions then report `ENGINE_UNAVAILABLE`/`DISABLED`; accounts,
watchlists, preferences, settings, notifications and history still work.

## 2. MongoDB

Create a database user with read/write access to one database. The database must
be named `trading_assistant`.

Atlas connection string:

```text
mongodb+srv://<user>:<password>@<cluster-host>/trading_assistant?retryWrites=true&w=majority
```

- Do not use the `boom` database.
- Do not place credentials in `application.yml`, `render.yaml`, source control or
  client code.
- Allow the deployment host's outbound addresses in Atlas Network Access.
- The driver uses a 5-second server-selection timeout and a 10-second socket read
  timeout, so an outage fails promptly instead of exhausting request threads.

The URI is read from `MONGODB_URI` in every profile. The local profile alone has a
credential-free `mongodb://localhost:27017/trading_assistant` fallback. If the
URI omits a database name, MongoDB defaults to `test`; always include
`/trading_assistant`.

Indexes are created idempotently at startup. If MongoDB is unreachable at boot,
startup continues, index creation is skipped with a warning, and `/api/v1/system/status`
reports `DEGRADED` until the database is reachable. See
[mongodb.md](mongodb.md).

## 3. Required environment

| Variable | Required | Default | Notes |
|---|---|---|---|
| `MONGODB_URI` | yes | local fallback only | Must include `/trading_assistant` |
| `JWT_SECRET` | production | empty | Base64-encoded 32+ byte HMAC key |
| `JWT_ISSUER` | no | `trading-assistant` | Written to and validated from tokens |
| `JWT_ACCESS_TOKEN_TTL` | no | `15m` | Access-token lifetime |
| `JWT_REFRESH_TOKEN_TTL` | no | `30d` | Opaque refresh-token lifetime |
| `BCRYPT_STRENGTH` | no | `12` | 4–31 |
| `APP_ADMIN_EMAIL` | no | empty | Optional bootstrap admin |
| `APP_ADMIN_PASSWORD` | no | empty | Required with the email; store as a secret |
| `CORS_ALLOWED_ORIGINS` | no | empty | Comma-separated browser origins |
| `SPRING_PROFILES_ACTIVE` | production | `local` | Set to `prod` in deployment |
| `PORT` | Render | `10000` in prod | Render supplies this |
| `PLATFORM_JOBS_ENABLED` | no | `true` | Maintenance jobs only |
| `TOKEN_CLEANUP_CRON` | no | `0 15 * * * *` | UTC |
| `PORTFOLIO_SNAPSHOT_CRON` | no | `0 */15 * * * *` | UTC |
| `NOTIFICATION_RETENTION_DAYS` | no | `30` | Read notifications |
| `TOKEN_RETENTION_DAYS` | no | `7` | Expired/revoked refresh tokens |

Broker variables (`SMARTAPI_*`) are documented in the backend README and
`.env.example`. They are not required for Phase 12 startup.

Production startup refuses to continue if `JWT_SECRET` is blank. A non-empty
secret that does not decode to at least 256 bits also fails startup. Development
profiles generate an ephemeral signing key when the variable is absent, so tokens
are invalidated by a restart.

Generate a secret without committing it:

```bash
openssl rand -base64 32
```

## 4. Build and verify locally

```bash
cd backend
mvn -B -ntp verify
```

Expected Phase 12 result: 1,072 tests, 0 failures, 2 skipped.

Build the jar:

```bash
mvn -B -ntp package
java -jar target/trading-assistant-backend-0.1.0.jar
```

Run the production profile locally with a throwaway database and secret:

```bash
MONGODB_URI='mongodb://localhost:27017/trading_assistant' \
JWT_SECRET="$(openssl rand -base64 32)" \
SPRING_PROFILES_ACTIVE=prod \
PORT=10000 \
java -jar target/trading-assistant-backend-0.1.0.jar
```

Startup is not the same as readiness. Confirm HTTP responses:

```bash
curl -fsS http://localhost:10000/actuator/health
curl -fsS http://localhost:10000/api/v1/system/status
curl -fsS http://localhost:10000/v3/api-docs >/dev/null
```

`/api/v1/system/status` should report `database: UP`. `engine` is `DISABLED`
unless the broker is configured.

## 5. Docker

The existing multi-stage `Dockerfile` builds with Maven and runs as a non-root
user:

```bash
docker build -t trading-assistant-api:phase12 .
docker run --rm -p 10000:10000 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e PORT=10000 \
  -e MONGODB_URI='mongodb+srv://.../trading_assistant' \
  -e JWT_SECRET="$(openssl rand -base64 32)" \
  trading-assistant-api:phase12
```

The HTTP port comes from `PORT`; the container exposes 10000. Do not bake secrets
into the image. The image runs as the unprivileged `app` user.

## 6. Render

`render.yaml` describes the web service and the non-secret defaults. Create the
service from the repository, then set secrets in the Render dashboard.

1. Connect the backend repository (or use the existing service).
2. Use the Docker runtime and `./Dockerfile`.
3. Set `SPRING_PROFILES_ACTIVE=prod`.
4. Add `MONGODB_URI` as a secret. Include `/trading_assistant`.
5. Add `JWT_SECRET` as a secret (`sync: false` in the blueprint).
6. Optionally add `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` as secrets.
7. Add allowed browser origins to `CORS_ALLOWED_ORIGINS` only if a web client
   calls the API. The Android client does not need CORS.
8. Set the health check to `/actuator/health`.
9. Allow Render's outbound addresses in Atlas.
10. Deploy and verify the endpoints below on the deployed origin.

Render free instances sleep after inactivity and are unsuitable for continuous
market monitoring. Use a paid instance and a persistent process for the broker
pipeline. Do not enable broker credentials on a sleeping free instance.

## 7. Post-deployment verification

```bash
BASE=https://<service-host>

curl -fsS "$BASE/actuator/health"
curl -fsS "$BASE/api/v1/system/status"
curl -fsS "$BASE/actuator/info"
curl -fsS "$BASE/v3/api-docs" >/dev/null
```

Then run a complete account flow on the deployed origin:

```bash
curl -s -X POST "$BASE/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"verify@example.com","displayName":"Verify","password":"strong-password"}'
```

Verify login, an authenticated `GET /api/v1/portfolio`, a foreign-origin request
being rejected when CORS is restricted, and sign-out. A health check returning
200 is not evidence that authentication or MongoDB writes work.

Do not use a real trading account for a smoke test on a free instance.

## 8. Android client configuration

The Android app compiles its API base URL from the Gradle property `apiBaseUrl`:

```bash
cd frontend
./gradlew assembleDebug -PapiBaseUrl=https://<service-host>/
```

Default debug value: `http://10.0.2.2:8080/` (host loopback from the Android
emulator). Cleartext HTTP is permitted only for `10.0.2.2`, `localhost` and
`127.0.0.1`; all other hosts require HTTPS. A physical device needs a reachable
HTTPS host or an adb reverse tunnel.

Use a dedicated non-production account for Android smoke tests.

## 9. Security checklist

- [ ] `JWT_SECRET` is a fresh Base64 32+ byte value, stored only in the host's
      secret store.
- [ ] `MONGODB_URI` uses a least-privilege database user and TLS.
- [ ] The database name is `trading_assistant`.
- [ ] No credentials are in Git, `render.yaml`, logs or API responses.
- [ ] `SPRING_PROFILES_ACTIVE=prod`; startup fails without a signing secret.
- [ ] MongoDB health indicator is enabled in production.
- [ ] CORS is restricted to required browser origins (or empty for Android-only).
- [ ] Actuator exposes only health, info, metrics and Prometheus.
- [ ] The bootstrap administrator password is rotated after first login.
- [ ] Backups and Atlas alerts are configured.
- [ ] Broker credentials, if enabled, belong to a dedicated account.

## 10. Rollback and operations

- Keep the previous image/jar; redeploying it is the rollback.
- Index creation is additive and idempotent. No migration framework is used in
  Phase 12; document any future schema change before applying it.
- Portfolio snapshots are captured every 15 minutes while the engine is running
  and retained independently of the process lifetime.
- Refresh-token cleanup, notification retention and read-model trimming are
  maintenance jobs. They do not trade.
- If MongoDB is unavailable, affected endpoints return
  `503 DATABASE_UNAVAILABLE`; system status reports `DEGRADED`.
- If the broker/engine is unavailable, order routes return
  `503 ENGINE_UNAVAILABLE`; analysis lists may be empty.

## 11. Known deployment limitations

- MongoDB Atlas was not contacted by the Phase 12 test suite. Repository tests use
  an in-process Mongo-compatible server.
- No deployment was performed as part of Phase 12.
- Android instrumented tests were compiled but not executed because no emulator
  is installed in the development environment.
- There is no rate limiting, MFA, email verification, password reset or key
  rotation; treat these as production hardening items, not implemented features.
