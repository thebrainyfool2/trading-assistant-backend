# Phase 4 — Technical Indicator Engine

**Status:** Complete locally; awaiting architectural review.

**Scope:** Reusable, broker-independent technical indicator engine.

**Not included:** strategies, signals, scoring, risk, market regimes, option analytics, backtesting, persistence, scheduling, notifications or UI.

---

## 1. Executive Summary

Phase 4 adds the application's reusable technical-indicator layer. It consumes only immutable `Candle` domain objects from Phase 3 and produces immutable `IndicatorResult` objects. It has no dependency on SmartAPI DTOs, broker services, strategies, positions, users, options or risk.

Completed:

- Dedicated `com.tradingassistant.indicator` package
- Common `Indicator` lifecycle and `IndicatorFactory` extension point
- Pluggable `IndicatorEngine` with a standard registry
- Immutable result, series and status models with named values
- Validated `IndicatorConfig` and selectable `PriceSource`
- Sixteen reusable calculators covering all requested indicators
- Historical calculation with warm-up rows and incremental state
- Candle ordering, duplicate, OHLC, volume and gap validation
- BigDecimal-only calculations with DECIMAL128 division/square root
- 141 automated tests, 0 failures, 0 errors, 2 opt-in live tests skipped
- A 5,000-candle SMA test completed within the test bound

No trading recommendation, signal or strategy is produced.

---

## 2. Module Structure

```text
com.tradingassistant.indicator
├── api
│   ├── Indicator                 Stateful calculation contract
│   └── IndicatorFactory          Registration extension point
├── domain
│   ├── IndicatorResult           One immutable named-value observation
│   ├── IndicatorSeries           Immutable historical result sequence
│   ├── IndicatorStatus           OK / WARMING_UP
│   └── IndicatorValues           Stable output key names
├── engine
│   ├── IndicatorEngine           Registry and calculation facade
│   └── NamedIndicatorFactory     Named factory adapter
├── calculator
│   ├── AbstractIndicator         Lifecycle, validation and warm-up handling
│   ├── 16 indicator calculators
│   ├── DirectionalMovementCalculator
│   └── shared state helpers
├── config
│   ├── IndicatorConfig           Validated, immutable parameters
│   └── PriceSource               OPEN/HIGH/LOW/CLOSE/HL2/HLC3/OHLC4/HLCC4
├── validation
│   ├── CandleSeriesValidator     Ordering, OHLC, volume and incremental validation
│   └── CandleGap                 Detected interval gap
├── exception
│   ├── IndicatorException
│   ├── IndicatorValidationException
│   └── IndicatorCalculationException
└── util
    ├── IndicatorMath             BigDecimal arithmetic
    ├── CandleMath                TR, typical price, money-flow multiplier
    └── RollingWindow             Rolling decimal state
```

No indicator class imports broker or SmartAPI packages. The only market dependency is the approved `Candle` domain model.

### Dependency direction

```text
Candle (market.domain)
        │
        ▼
calculator ──► api/domain/config/util/validation
        │
        ▼
engine registry ──► caller/application
```

The calculator layer does not know who created the candle or why it was requested. The engine does not know about strategies, instruments beyond the candle, or broker sessions.

---

## 3. Engine and Interface Design

### Indicator contract

```java
String name();
String version();
int minimumCandles();
boolean supportsIncremental();

IndicatorSeries calculateSeries(List<Candle> candles);
IndicatorResult calculate(List<Candle> candles);

IndicatorResult update(Candle candle);
void reset();
```

`calculateSeries` starts from a clean state, validates the full input, produces one result per candle including warm-up rows, and returns an immutable `IndicatorSeries`. `calculate` returns the final observation. `update` advances one stateful instance; `reset` clears recursive state so the instance can be reused.

Each indicator owns its own state and must not be shared by concurrent streams. The engine creates independent instances, making future parallel execution possible without shared mutable calculator state.

### Engine

`IndicatorEngine.standard()` registers:

```text
SMA, EMA, WMA, RSI, MACD, STOCH_RSI, ATR, BOLLINGER, KELTNER,
DONCHIAN, ADX, DMI, VWAP, OBV, CMF, SUPERTREND
```

The constructor accepts a list of `IndicatorFactory` instances. A future indicator can be added by supplying a factory and calculator; the engine, result model and callers do not need to change. Types are case-insensitive; duplicate and unknown types fail with `IndicatorValidationException`.

### Missing-candle detection

`IndicatorEngine.detectGaps(candles)` delegates to `CandleSeriesValidator.findGaps`. It reports `CandleGap(previous, next, expectedInterval, missingCandles)` but does not insert synthetic candles or reject daily weekend/holiday gaps. This keeps detection explicit and avoids silently inventing market data. Indicators operate on the supplied ordered candles only.

### Result model

`IndicatorResult` contains:

- indicator name and version
- timestamp
- `IndicatorStatus`
- `warmup` boolean
- immutable `Map<String, BigDecimal>` of named values
- immutable metadata map

Warm-up results have no values and expose `requiredCandles` metadata. The `values` and `metadata` maps are immutable copies. No indicator returns a raw array or positional array.

Single-line outputs use `IndicatorValues.VALUE`. MACD, Stochastic RSI, Bollinger, Keltner, Donchian, DMI and Supertrend use named keys such as `macd`, `signal`, `histogram`, `percentK`, `percentD`, `upper`, `middle`, `lower`, `plusDI`, `minusDI`, `supertrend` and `direction`.

---

## 4. Implemented Indicators

All indicators support historical and incremental calculation. `minimumCandles()` is the historical minimum; `update()` returns `WARMING_UP` until the same point.

### Trend

| Indicator | Purpose / formula | Inputs | Outputs | Parameters | Warm-up | Limitations |
|---|---|---|---|---|---|---|
| SMA | Arithmetic mean of source prices | Source price | `value` | `period`, `source` | `period` | Equal weighting; no recency bias |
| EMA | Exponentially weighted mean, alpha `2/(period+1)` | Source price | `value` | `period`, `source` | `period` | Seeded with first source value; not a full-history infinite EMA |
| WMA | Linearly weighted mean, oldest weight 1 | Source price | `value` | `period`, `source` | `period` | O(period) weighted sum per update |

### Momentum

| Indicator | Purpose / formula | Inputs | Outputs | Parameters | Warm-up | Limitations |
|---|---|---|---|---|---|---|
| RSI | Wilder relative strength index | Close | `value` | `period`, `source` | `period + 1` | Uses Wilder smoothing; flat prices resolve to 50, zero-loss rising prices to 100 |
| MACD | Fast EMA minus slow EMA, signal EMA and histogram | Source price | `macd`, `signal`, `histogram` | `fastPeriod`, `slowPeriod`, `signalPeriod`, `source` | `slowPeriod + signalPeriod - 1` | EMAs are seeded from the first value; not a fresh full-history initialization |
| Stochastic RSI | Stochastic position of RSI, then smoothed K and D | Close | `percentK`, `percentD` | `period`, `smoothing`, `source` | `(period+1) + period - 1 + 2*(smoothing-1)` | One smoothing parameter is used for both K and D; zero RSI range resolves to 50 |

### Volatility

| Indicator | Purpose / formula | Inputs | Outputs | Parameters | Warm-up | Limitations |
|---|---|---|---|---|---|---|
| ATR | Wilder average true range | OHLC | `value` | `period` | `period + 1` | First candle seeds previous close and contributes no TR |
| Bollinger Bands | SMA plus/minus deviation times population standard deviation | Source price | `upper`, `middle`, `lower` | `period`, `deviation`, `source` | `period` | Population deviation, not sample deviation |
| Keltner Channels | EMA of typical price plus/minus ATR multiple | HLC3 and OHLC | `upper`, `middle`, `lower` | `period`, ATR `smoothing`, `multiplier` | `max(period, smoothing+1)` | Uses typical-price EMA rather than a configurable source |
| Donchian Channels | Rolling highest high, lowest low and midpoint | High/low | `upper`, `middle`, `lower` | `period` | `period` | Does not distinguish trading sessions or holidays |

### Trend strength

| Indicator | Purpose / formula | Inputs | Outputs | Parameters | Warm-up | Limitations |
|---|---|---|---|---|---|---|
| ADX | Wilder average of directional index | OHLC | `adx` | `period` | `2 * period` | DI and DX warm up internally; ADX is absent until the second smoothing stage completes |
| DMI | Wilder +DI and -DI lines | OHLC | `plusDI`, `minusDI` | `period` | `period + 1` | Does not include ADX; use the separate ADX indicator |

### Volume

| Indicator | Purpose / formula | Inputs | Outputs | Parameters | Warm-up | Limitations |
|---|---|---|---|---|---|---|
| VWAP | Session cumulative typical-price volume / cumulative volume | HLC3 and volume | `value` | None (session policy) | 1 | Resets when the `Asia/Kolkata` local date changes; zero cumulative volume falls back to current typical price |
| OBV | Cumulative signed volume versus previous close | Close and volume | `value` | None | 1 | Starts at zero; first candle has no directional contribution |
| CMF | Rolling money-flow volume / rolling volume | OHLC and volume | `value` | `period` | `period` | Zero-range candle contributes a zero money-flow multiplier; zero volume sum yields zero |

### Trend following

| Indicator | Purpose / formula | Inputs | Outputs | Parameters | Warm-up | Limitations |
|---|---|---|---|---|---|---|
| Supertrend | ATR bands with final-band ratchet and trend direction | HL2, OHLC and ATR | `supertrend`, `direction` | `period`, `multiplier` | `period + 1` | The first ready candle establishes the initial band using the standard close-versus-band rule; no exchange calendar |

### Configuration defaults

`IndicatorConfig.defaults()` provides:

```text
period=14, fastPeriod=12, slowPeriod=26, signalPeriod=9,
smoothing=3, multiplier=2, deviation=2, source=CLOSE
```

Indicators consume only the fields relevant to them. Common production examples are period 20 for SMA/Bollinger/Donchian, period 14 for RSI/ATR/ADX, MACD 12/26/9, Keltner 20/10/2, and Supertrend 10/3. Callers should choose parameters explicitly for their timeframe and instrument.

Factory methods:

- `IndicatorConfig.period(period, source)`
- `IndicatorConfig.macd(fast, slow, signal, source)`
- `IndicatorConfig.stochasticRsi(period, smoothing, source)`
- `IndicatorConfig.bollinger(period, deviation, source)`
- `IndicatorConfig.keltner(period, atrPeriod, multiplier, source)`
- `IndicatorConfig.supertrend(period, multiplier, source)`
- `IndicatorConfig.defaults()`

All positive periods and multipliers are validated. MACD additionally requires `fast < slow`.

---

## 5. Validation Strategy

`CandleSeriesValidator` rejects:

- null series
- empty series
- null candles
- null timestamps or OHLC values
- missing/incomplete OHLC
- negative prices
- negative volume
- high below low
- open/close outside high/low
- duplicate timestamps
- out-of-order timestamps
- insufficient candles for a historical calculation

Incremental updates apply the same timestamp and candle checks, preventing duplicate or late candles from corrupting recursive state.

`IndicatorConfig` rejects non-positive periods, smoothing, multiplier and deviation, and a null source. MACD rejects `fast >= slow`. `IndicatorCalculationException` is reserved for mathematically undefined operations such as division by zero; calculators guard normal zero-denominator cases explicitly.

`findGaps` detects interval-sized gaps but deliberately does not reject them, because weekends and exchange holidays are legitimate. It does not synthesize candles.

---

## 6. Precision Strategy

- Prices, averages, bands, ATR, RSI, ADX, DMI, VWAP, CMF, OBV and Supertrend values use `BigDecimal`.
- Addition, subtraction and multiplication remain exact for the operands supplied.
- Division and square root use `MathContext.DECIMAL128` with the JDK's deterministic rounding.
- No `double` or `float` is used in indicator calculations.
- No display rounding is applied by the engine; callers decide presentation scale.
- Comparisons use `compareTo`, not `equals`, when values have different scales.
- Rolling sums retain exact decimal operands; only normalized divisions and square roots introduce DECIMAL128 rounding.
- Zero-denominator policies are explicit: RSI handles flat/one-sided moves, Stochastic RSI uses 50 for a zero range, CMF uses zero for zero volume, and VWAP falls back to typical price for zero cumulative volume.

This avoids binary floating-point accumulation error. It does not claim arbitrary-precision output for non-terminating ratios; DECIMAL128 is the documented precision boundary.

---

## 7. Performance Strategy

- `update` is O(1) for SMA, EMA, WMA, RSI, MACD, ATR, ADX, DMI, VWAP, OBV, CMF and Supertrend (with small constant window scans).
- WMA and Stochastic RSI perform O(period) work when their rolling window is full.
- Donchian scans its bounded period window.
- Only the state needed for the next observation is retained; no full historical list is retained by indicators except the bounded Donchian window.
- No global cache, scheduled recalculation or background service is introduced. Indicator state is owned by the instance, which avoids cross-instrument contamination and enables independent parallel streams.
- A 5,000-candle SMA run is covered by an automated timeout test. The default suite completes in roughly 11–13 seconds including the existing Spring/broker tests.
- No parallel executor is introduced in this phase; the design supports it because instances are independent.

---

## 8. Testing

Command:

```bash
mvn -B -ntp test
```

Result on 18 November 2026:

```text
Tests run: 141, Failures: 0, Errors: 0, Skipped: 2
BUILD SUCCESS
```

The two skipped tests are the existing opt-in Phase 2 and Phase 3 live broker tests, skipped because `SMARTAPI_LIVE_TESTS` is not set.

Coverage areas:

- Reference SMA, EMA and WMA values
- Hand-calculated Wilder RSI sequence and a published Wilder reference dataset
- Flat-price MACD zero behavior
- ATR, Bollinger, Keltner and Donchian reference values
- DMI/ADX warm-up and bounds
- VWAP volume weighting, OBV signed volume and CMF zero-range behavior
- Supertrend direction/state
- All 16 indicators: historical calculation, warm-up rows, latest `OK` status and full incremental parity
- Reset/reuse behavior
- Duplicate, out-of-order and insufficient candle validation
- Null/empty input and immutable result maps
- PriceSource formulas
- Interval-gap detection
- 5,000-candle performance bound

Reference implementations are mathematical/manual rather than a third-party TA library because the project intentionally has no additional runtime dependency for this phase. The tests encode independent expected values for the foundational formulas and compare every indicator's incremental output against its historical calculation.

---

## 9. Problems Encountered

| Problem | Resolution |
|---|---|
| Keltner's EMA initially advanced only after ATR became ready, breaking its declared warm-up minimum | Advance the typical-price EMA from the first candle and gate only the final output on both EMA and ATR readiness |
| Multi-line indicators could otherwise return positional arrays | Introduced named immutable value maps and stable keys |
| Recursive state can be corrupted by duplicate/out-of-order candles | Shared validator checks both full series and incremental `update` calls |
| Missing candles are not always invalid because of weekends/holidays | Added explicit gap detection without auto-filling or rejecting |
| Non-terminating indicator divisions | Centralized DECIMAL128 division/square root and documented the precision boundary |
| Zero-denominator cases in RSI, Stochastic RSI, CMF and VWAP | Defined deterministic documented fallback values instead of throwing or emitting NaN |
| Stoch RSI needs K and D smoothing but the common config has one smoothing field | Reused the one smoothing parameter for both lines and documented the limitation |
| ADX and DMI share expensive directional state | Extracted `DirectionalMovementCalculator` and exposed separate ADX/DMI calculators |
| Supertrend's first band is implementation-sensitive | Documented the standard initial close-versus-band rule and covered direction/state output |

---

## 10. Recommendations for Phase 5

Do not implement these in Phase 4:

1. Define an application-facing indicator API/event contract with batch and incremental use cases.
2. Add a keyed indicator-session registry for multiple instruments/timeframes, with explicit lifecycle and eviction.
3. Add multi-timeframe alignment and session/calendar-aware indicator policies.
4. Add persisted indicator snapshots only if a later phase approves persistence.
5. Add stream integration that consumes `MarketFeed` ticks and aggregates them into candles before indicator updates.
6. Add cross-validation against a trusted TA library as a development-only verification dependency, not production runtime code.
7. Add parallel batch calculation and benchmark thresholds once real dataset sizes and latency targets are known.
8. Keep strategies, signals, scoring, risk, market regimes, Greeks and option-chain analysis out until separately approved.

---

## 11. Completion Gate

Phase 4 is implemented, tested, and documented. The market and broker layers were not modified. No Phase 5 functionality, strategy logic, signals, persistence, scheduling, Android code or deployment change was started.

**STOP — awaiting architectural review.**
