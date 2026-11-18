# Phase 3 Implementation Report

**Project:** Trading Assistant

**Phase:** 3 — Internal market data domain

**Report date:** 18 November 2026

**Status:** Implementation, automated verification, and live instrument-master mapping complete. Awaiting architectural review.

---

## 1. Executive Summary

Phase 3 establishes the language future Trading Assistant modules will use for market data. The application now owns immutable domain models for instruments, quotes, candles, market depth, live ticks and option contracts. All market-data services expose those domain models, not provider objects.

Completed:

- Broker-independent domain models for exchanges, segments, instruments, quotes, candles, depth, ticks, option contracts, intervals, option types, currencies and market status.
- Dedicated mapping classes for instruments, quotes, candles, market depth, ticks and option contracts.
- Broker-independent services: `InstrumentService`, `QuoteService`, `HistoricalDataService` and the `MarketDataService` facade.
- Domain feed events with a provider-to-domain adapter and graceful feed handle.
- One centralized Indian market-time policy: domain timestamps are UTC `Instant`s; SmartAPI requests are converted to `Asia/Kolkata` only at the provider boundary.
- `BigDecimal` prices throughout the domain; quantities and volumes remain integral.
- Domain validation and application exceptions for invalid input, malformed provider data, authentication failures and unavailable market data.
- 105 automated tests in the default suite, with two opt-in live tests skipped, plus a successful live mapping of the real 170,064-row instrument master.

Deliberately absent:

- Technical indicators, strategies, scoring, monitoring, risk and analytics
- Databases, persistence, caching and schedulers
- Android code
- Order placement, modification, cancellation, GTT or portfolio operations
- Option Greeks and option-chain assembly
- Notifications and backtesting

No order-related method exists in the domain or broker API.

---

## 2. Domain Model Documentation

The domain lives in `com.tradingassistant.market.domain`. Every model is immutable.

### 2.1 Identity and classification

| Model | Why it exists | Future usage |
|---|---|---|
| `Exchange` | Identifies the market venue without provider-specific names. | Routing, display and future multi-broker symbology. |
| `Segment` | Separates cash, derivatives, currency and commodity segments; also carries the stable segment code (`NSE`, `NFO`, `BFO`, `NCO`, `CDS`, `NCDEX`). | Correct token lookup, feed routing, provider capability checks. |
| `InstrumentType` | Broad category (`EQUITY`, `FUTURE`, `OPTION`, `INDEX`, `CURRENCY`, `COMMODITY`, `OTHER`). | Analytics selection; future instrument-specific behavior. |
| `OptionType` | Distinguishes `CALL` and `PUT`. | Option-contract lookup and future strategy/risk filters. |
| `Currency` | Declares the instrument currency; Indian instruments default to `INR`. | Future cross-market valuation and display. |
| `InstrumentKey` | Combines segment and symbol token, because a token is unique only within a segment. | All quote, candle, tick and feed operations. |

### 2.2 Instrument

`Instrument` contains:

- Identity: `InstrumentKey`, symbol, trading symbol, display name
- Classification: type, exchange, segment
- Trading metadata: tick size, lot size, freeze quantity
- Optional metadata: ISIN, currency, expiry, strike, option type, underlying

Index and non-tradable rows may carry zero tick/lot values because the provider uses zero or sentinel values. Option contracts enforce positive tick and lot values.

Future usage: instrument lookup, universe selection, option discovery, strategy filters and display metadata.

### 2.3 Quote

`Quote` contains normalized LTP, OHLC, previous close, average price, volume, open interest, last-trade quantity, change values, total buy/sell quantities, circuit limits, 52-week values, depth, receipt timestamp and `MarketStatus`.

The provider quote response has no quote timestamp, so `timestamp` is the application receipt instant in UTC. This is explicit rather than pretending it is an exchange event timestamp.

### 2.4 Candle

`Candle` contains instrument identity, interval, UTC timestamp and OHLCV. The instrument and interval travel with the candle, so future indicators do not need to recover context from a separate map.

Validation rejects non-positive prices, a high below low/open/close, a low above open/close, and negative volume. Volume remains a `long`.

### 2.5 MarketDepth and DepthLevel

`DepthLevel` stores price, optional quantity and optional orders. `MarketDepth` stores immutable buy and sell ladders. Null provider depth becomes an explicit empty ladder; null levels are rejected at the mapping boundary. Prices use `BigDecimal`.

### 2.6 Tick

`Tick` represents LTP, quote and snapshot updates. It contains:

- `TickMode`: `LTP`, `QUOTE`, `SNAPSHOT`
- Instrument identity and sequence number
- Exchange event timestamp and application receipt timestamp
- LTP, average price, last-trade quantity, volume and open interest
- OHLC/previous close, total buy/sell quantities
- Depth, circuit limits, 52-week values and market status

Every received tick is timestamped by the application, even when the provider timestamp is missing. LTP ticks require a last-traded price.

### 2.7 OptionContract

`OptionContract` contains the underlying, expiry, strike, option type and its `Instrument`. Lot size and tick size are exposed from the instrument rather than duplicated. Construction validates:

- The instrument is an `OPTION`.
- The segment is a derivative segment.
- Expiry, strike, option type and underlying are present.
- Contract values agree with the instrument when the instrument already carries them.
- Tick size and lot size are positive.

No Greeks or option-chain composition is included.

### 2.8 Enumerations and request concepts

`QuoteMode` (`LTP`, `OHLC`, `FULL`) and `TickMode` (`LTP`, `QUOTE`, `SNAPSHOT`) prevent raw provider strings in application code. `Interval` covers the eight supported candle intervals. `MarketStatus` covers `PRE_OPEN`, `OPEN`, `CLOSED`, `AUCTION` and `UNKNOWN`.

---

## 3. Mapping Architecture

```text
SmartAPI JSON / binary packets
          │
          ▼
Angel One DTOs and protocol decoder          (broker adapter)
          │
          ▼
BrokerProvider token-free provider models    (approved Phase 2 boundary)
          │
          ▼
market.mapping mappers                       (domain boundary)
          │
          ▼
market.domain immutable models               (application language)
          │
          ▼
market.service broker-independent services
```

SmartAPI DTOs never leave the adapter. The Phase 2 `BrokerProvider` already returns token-free models; Phase 3's mappers translate those provider-neutral values into the richer application domain. This preserves the approved broker architecture and avoids a second HTTP/WebSocket implementation.

Dedicated mappers:

| Mapper | Responsibility |
|---|---|
| `InstrumentMapper` | Segment, type, expiry, option type, underlying, sentinel normalization |
| `QuoteMapper` | Quote fields, receipt timestamp, market status, depth |
| `CandleMapper` | Provider offset timestamp to UTC `Instant`, interval and instrument context |
| `MarketDepthMapper` | Buy/sell ladders and optional level fields |
| `TickMapper` | LTP/quote/snapshot modes, exchange and receipt timestamps, depth |
| `OptionContractMapper` | Validated option contract from an instrument; non-options return empty |

Provider exchange mapping is isolated in `ProviderExchangeMapping`; service-to-provider segment routing is isolated in `QuoteService`/`MarketDataService`.

### 3.1 Real instrument-master findings handled by the mapper

Live data exposed several formats that synthetic tests did not:

- Expiries use uppercase forms such as `28NOV2024` and `28NOV24`; parsing is case-insensitive.
- Some cash/index rows have blank instrument type; they map to `OTHER` rather than failing the entire master.
- Some index rows have zero tick size, zero or negative lot size, and negative strike sentinels; these are normalized without inventing tradable metadata.
- The master contains the `NCO` NSE commodity segment; the domain represents it as `NSE_COM` and the provider does not claim quote/feed support for it.
- NCDEX options place the option type before the strike, for example `DHANIYA06APR27CE13900FAPR27`; the mapper recognizes this suffix shape.
- Option symbols can have `CE`/`PE` at the end or followed by strike/contract suffixes.

These rules are covered by mapper tests and by the opt-in live instrument-master test.

---

## 4. Services

| Service | Responsibility | Boundary behavior |
|---|---|---|
| `InstrumentService` | Loads and maps instruments; finds by `InstrumentKey`; builds option contracts. | No cache in Phase 3; provider failures become application exceptions. |
| `QuoteService` | Validates/deduplicates keys, groups by segment, requests quotes and maps them with one receipt timestamp/status. | Enforces the 50-instrument quote limit; does not expose provider modes. |
| `HistoricalDataService` | Converts UTC instants to Indian market time, maps intervals and requests candles. | Converts malformed spans into `MarketDataValidationException`. |
| `MarketDataService` | Application facade for instruments, quotes, candles, options and feeds. | Exposes only domain models and domain listeners. |
| `MarketFeedAdapter` | Wraps a provider feed; maps each tick and wraps provider errors. | Hides provider connection and listener types. |
| `MarketServicesConfiguration` | Wires services only when `SMARTAPI_ENABLED=true`. | Phase 1 endpoints still start with no market services when disabled. |

No service caches market data, persists anything or starts a scheduler. `optionContracts()` intentionally loads the current master each call; a future phase must add an approved index/cache strategy before frequent use.

### 4.1 Feed events

```text
Provider FeedTick
      │
      ▼
TickMapper.toDomain(tick, receivedAt, marketStatus)
      │
      ▼
MarketFeedListener.onTick(Tick)
```

The feed bridge also forwards connected, disconnected and error lifecycle events. Provider errors are wrapped in `MarketDataUnavailableException`; a mapping failure is delivered to the listener as an application error rather than escaping into the provider callback thread.

---

## 5. Validation Strategy

Domain records validate their own invariants so invalid objects cannot be constructed. Mappers translate provider data and report malformed payloads; services translate provider failures without leaking provider messages.

### 5.1 Domain validation

- Required identity, timestamps, intervals, modes and market status.
- Positive tick/lot for option contracts; non-negative metadata for instruments.
- Non-negative prices, quantities, volume, open interest and circuit values.
- OHLC consistency for candles and quote high/low where both are present.
- No null depth levels; null depth becomes an explicit empty ladder.
- LTP ticks require a last-traded price.
- Option-contract expiry, strike, option type, underlying and derivative-segment consistency.
- Immutable copies for depth lists.

### 5.2 Mapping and service failures

| Failure | Application result |
|---|---|
| Invalid caller input | `MarketDataValidationException` |
| Malformed provider row or tick | `MarketDataMappingException` |
| Provider authentication/session failure | `MarketDataAuthenticationException` |
| Provider/network/rate-limit failure | `MarketDataUnavailableException` |
| Missing/non-option contract | `Optional.empty()` from the option mapper; missing option fields are a mapping error |

No provider exception is returned directly from a market-data service. Error messages name domain fields and do not include payloads, credentials or provider bodies.

---

## 6. Time Handling

- **Internal standard:** every domain timestamp is a UTC `java.time.Instant`.
- **Market zone:** `Asia/Kolkata`, defined once in `MarketStatusResolver`.
- **Candle requests:** UTC request instants are converted to Indian local `LocalDateTime` only when constructing the Phase 2 provider request.
- **Quote timestamp:** application receipt time, because the provider quote payload does not supply one.
- **Tick timestamps:** `exchangeTimestamp` preserves the provider event time; `receivedAt` is always the application receipt time.
- **Session policy:** weekdays only; `PRE_OPEN` 09:00–09:15, `OPEN` 09:15–15:30, `AUCTION` 15:30–15:40, otherwise `CLOSED`.
- **Unknown:** a missing timestamp resolves to `MarketStatus.UNKNOWN`.
- **Holiday limitation:** exchange holidays are not modelled in Phase 3. A weekday holiday will appear as a normal session until a calendar source is approved.
- **Auction assumption:** the 15:30–15:40 closing-auction window is an explicit Phase 3 assumption, not a claim of exchange-specific holiday/auction coverage.

---

## 7. Numeric Precision

- Prices, strikes, tick sizes, circuit limits, 52-week values, averages and depth prices use `BigDecimal`.
- Quantities, volumes, open interest, order counts and lot sizes use integral types.
- No floating-point arithmetic is performed in the domain.
- No domain-level rounding is applied. Provider scale is preserved; Phase 2 already converts SmartAPI paise values with `BigDecimal.valueOf(value, 2)`.
- Domain comparisons use `BigDecimal.compareTo`, not `equals`, for numeric equality.
- Option lot size is exposed as `long`; option tick size remains `BigDecimal`.

Future arithmetic (VWAP, indicators, P&L) must define its own scale/rounding policy at the point of calculation; it is not appropriate to silently round market inputs in this phase.

---

## 8. Testing

### 8.1 Automated result

```text
mvn -B -ntp test
Tests run: 105, Failures: 0, Errors: 0, Skipped: 2
BUILD SUCCESS
```

The two skipped tests are opt-in live tests:

- `AngelOneLiveIntegrationTest` (Phase 2)
- `MarketDomainLiveIntegrationTest` (Phase 3 full instrument-master mapping)

Operational smoke check: the packaged jar started with the broker disabled, `/actuator/health` returned 200 UP, and `/api/v1/system/info` returned the expected application envelope. The enabled broker context was exercised by the live domain test.

### 8.2 Live domain verification

```text
SMARTAPI_LIVE_TESTS=true ... mvn -Dtest=MarketDomainLiveIntegrationTest test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The live check downloaded and mapped **170,064 instruments**, verified every mapped instrument had a token, trading symbol and segment, and verified that the real option set produced option contracts. It exposed and drove fixes for the sentinel/format cases documented in section 3.1.

### 8.3 Coverage by behavior

| Area | Tests |
|---|---|
| Domain validation | Required fields, negative values, OHLC consistency, option consistency, empty depth |
| Time | Pre-open/open/auction/closed, weekends, unknown, UTC ↔ IST conversion |
| Instrument mapping | Equity, index, NCO, NSE/NFO/NCDEX options, expiry formats, sentinels, invalid rows |
| Quote mapping | Full fields, receipt timestamp, status, depth, null input |
| Candle mapping | Offset-to-UTC conversion, interval/context, malformed OHLC |
| Depth mapping | Both sides, optional fields, null sides |
| Tick mapping | LTP and snapshot modes, receipt time, depth, null input |
| Option contracts | Valid option, non-option empty, missing fields |
| Services | Instrument lookup/options, quote grouping/limits/status, historical request conversion/limits, failure translation |
| Feed bridge | Subscription translation, domain tick, lifecycle callbacks, error wrapping, close |
| Wiring | Services absent when disabled, facade present when enabled |
| Existing Phase 1/2 | All prior tests remain green |

No numeric coverage percentage is claimed; JaCoCo is not configured. The behavioral matrix above is the coverage inventory. A coverage gate is a Phase 4 recommendation.

### 8.4 Edge cases explicitly covered

- Null/blank required fields
- Negative prices/quantities and invalid ranges
- Expiry case and multiple formats
- Zero/sentinel index metadata
- Unknown segments and invalid option symbols
- Null depth and null sides
- Missing/negative provider values
- Unsupported NCO quote/feed routing
- Provider failure translation

---

## 9. Problems Encountered

### 9.1 Expiry format case

**Problem:** The first mapper version used case-sensitive patterns and rejected `28NOV2024`/`28NOV24`.

**Resolution:** All documented patterns now use case-insensitive parsing, with tests for each format.

### 9.2 Provider sentinel values

**Problem:** Real index rows use zero tick size, negative lot size and negative strike sentinels. Strict positive validation rejected them.

**Resolution:** Instruments permit zero tick/lot for non-options and the mapper normalizes negative non-option sentinels. Option contracts still require positive tick/lot.

### 9.3 Missing instrument type

**Problem:** Some cash/index rows have no `instrumenttype`.

**Resolution:** Missing type maps to `InstrumentType.OTHER`; the instrument remains usable for identity/display while options still require explicit option fields.

### 9.4 Unsupported NCO segment

**Problem:** The live master contains `NCO` (NSE commodity), absent from the Phase 2 provider exchange enum.

**Resolution:** The domain adds `Segment.NSE_COM`. Quote/feed routing rejects NCO explicitly because the approved provider cannot route it; instrument mapping remains complete.

### 9.5 NCDEX option symbol shape

**Problem:** NCDEX option symbols place `CE`/`PE` before the strike and contract suffix, so an `endsWith("CE")` parser rejected them.

**Resolution:** The mapper recognizes option type followed by optional strike/contract suffix and is covered by a dedicated test.

### 9.6 Service wiring and Phase 1 startup

**Problem:** The domain services need a broker provider, while Phase 1 must start without one.

**Resolution:** `MarketServicesConfiguration` is conditional on `SMARTAPI_ENABLED=true`. Disabled startup and enabled startup were both verified.

### 9.7 Domain dependency direction

**Problem:** Mapping provider-specific fields directly in services would make application services understand SmartAPI formats.

**Resolution:** All provider-specific field mapping is isolated in `market.mapping`; services only call provider capabilities and return domain models. SmartAPI DTOs remain inside the adapter.

---

## 10. Recommendations for Phase 4

Do not implement these in Phase 3:

1. Add an approved instrument index/cache and refresh policy; the live master is approximately 170k rows/40 MB and is currently reloaded per call.
2. Add an exchange calendar/holiday source so `MarketStatusResolver` can move beyond weekday assumptions.
3. Add market-data API endpoints or an application event publisher once the consumer contract is approved; no endpoints were added in Phase 3.
4. Add rate-limit coordination for multi-call quote/instrument workflows.
5. Add feed reconnect/resubscribe policy and feed observability above the broker adapter.
6. Add a formal coverage gate and mutation/contract tests.
7. Add persistence only in a separate approved phase; it is explicitly outside Phase 3.
8. Keep indicators, strategies, risk, Greeks and option-chain assembly out until separately approved.

---

## Approval Gate

Phase 3 implementation, domain tests, service tests, opt-in full-master live mapping, enabled/disabled wiring checks and documentation are complete.

**Awaiting architectural review. No Phase 4 work has started. No order-placement functionality exists.**
