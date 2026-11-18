package com.tradingassistant.options.chain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ExpiryChain;
import com.tradingassistant.options.domain.OptionChain;
import com.tradingassistant.options.domain.Strike;
import com.tradingassistant.options.domain.StrikeGroup;
import com.tradingassistant.options.exception.OptionsValidationException;
import com.tradingassistant.options.math.ExpiryTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds an immutable {@link ChainSnapshot} from raw contracts and quotes.
 *
 * <p>The builder is stateless and deterministic: contracts are grouped by
 * expiry and strike, duplicates and mixed underlyings or segments are rejected,
 * expired expiries and contracts without a usable quote are excluded and
 * reported in the snapshot diagnostics, and everything is sorted before the
 * immutable chain is constructed.</p>
 *
 * <p>A contract without a usable quote is excluded rather than guessed, so a
 * chain never contains a leg whose analytics would rest on missing data. A
 * capture that leaves no quoted leg is rejected.</p>
 */
public final class OptionChainBuilder {

    private final OptionsAnalyticsConfig config;
    private final MoneynessClassifier moneynessClassifier;

    public OptionChainBuilder(OptionsAnalyticsConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.moneynessClassifier = new MoneynessClassifier(config);
    }

    public ChainSnapshot build(InstrumentKey spotInstrument,
                               String underlying,
                               BigDecimal spotPrice,
                               Instant capturedAt,
                               List<OptionContract> contracts,
                               Map<InstrumentKey, Quote> quotes) {
        Objects.requireNonNull(spotInstrument, "spotInstrument must not be null");
        Objects.requireNonNull(spotPrice, "spotPrice must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        if (underlying == null || underlying.isBlank()) {
            throw new OptionsValidationException("underlying must not be blank");
        }
        if (spotPrice.signum() <= 0) {
            throw new OptionsValidationException("spotPrice must be positive");
        }
        if (contracts == null || contracts.isEmpty()) {
            throw new OptionsValidationException("option chain must contain at least one contract");
        }
        if (contracts.stream().anyMatch(Objects::isNull)) {
            throw new OptionsValidationException("contracts must not contain null entries");
        }
        Map<InstrumentKey, Quote> quoteMap = quotes == null ? Map.of() : quotes;
        String normalizedUnderlying = underlying.trim().toUpperCase(Locale.ROOT);
        LocalDate marketDate = capturedAt.atZone(config.exchangeZone()).toLocalDate();

        Segment segment = null;
        Set<InstrumentKey> instruments = new HashSet<>();
        Set<String> strikeKeys = new HashSet<>();
        Map<LocalDate, List<OptionContract>> contractsByExpiry = new TreeMap<>();
        int expired = 0;

        for (OptionContract contract : contracts) {
            if (!contract.underlying().equalsIgnoreCase(normalizedUnderlying)) {
                throw new OptionsValidationException(
                        "option chain must contain exactly one underlying");
            }
            if (segment == null) {
                segment = contract.segment();
            } else if (segment != contract.segment()) {
                throw new OptionsValidationException(
                        "option chain must contain exactly one derivative segment");
            }
            if (!instruments.add(contract.instrument().key())) {
                throw new OptionsValidationException("duplicate option contract");
            }
            String strikeKey = contract.expiry() + "|" + canonical(contract.strike())
                    + "|" + contract.optionType();
            if (!strikeKeys.add(strikeKey)) {
                throw new OptionsValidationException("duplicate strike for expiry and option type");
            }
            if (contract.expiry().isBefore(marketDate)) {
                expired++;
                continue;
            }
            contractsByExpiry.computeIfAbsent(contract.expiry(), key -> new ArrayList<>())
                    .add(contract);
        }

        if (contractsByExpiry.isEmpty()) {
            throw new OptionsValidationException("option chain has no unexpired expiry");
        }

        List<LocalDate> expiryDates = new ArrayList<>(contractsByExpiry.keySet());
        Set<LocalDate> monthly = ExpiryClassifier.monthlyExpiries(expiryDates);
        List<ExpiryChain> expiryChains = new ArrayList<>(expiryDates.size());
        int unquoted = 0;
        int strikeCount = 0;
        int legCount = 0;

        for (int index = 0; index < expiryDates.size(); index++) {
            LocalDate expiry = expiryDates.get(index);
            ExpiryBuild build = buildExpiry(expiry, contractsByExpiry.get(expiry), quoteMap,
                    spotPrice);
            if (build.groups().isEmpty()) {
                unquoted += build.unquoted();
                continue;
            }
            unquoted += build.unquoted();
            strikeCount += build.groups().size();
            legCount += build.groups().stream().mapToInt(group -> group.legs().size()).sum();
            expiryChains.add(new ExpiryChain(
                    expiry,
                    ExpiryClassifier.kind(expiry, monthly),
                    ExpiryClassifier.proximity(expiryChains.size()),
                    ExpiryTime.daysToExpiry(expiry, capturedAt, config.exchangeZone()),
                    build.groups()));
        }

        if (expiryChains.isEmpty()) {
            throw new OptionsValidationException("option chain has no quoted contract");
        }

        OptionChain chain = new OptionChain(spotInstrument, normalizedUnderlying, segment,
                List.copyOf(expiryChains));
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("contracts", Integer.toString(contracts.size()));
        metadata.put("quotedLegs", Integer.toString(legCount));
        metadata.put("unquotedContracts", Integer.toString(unquoted));
        metadata.put("expiredContracts", Integer.toString(expired));
        metadata.put("expiries", Integer.toString(chain.expiries().size()));
        metadata.put("strikes", Integer.toString(strikeCount));
        metadata.put("atmDistancePercent", config.atmDistancePercent().toPlainString());
        AssessmentStatus status = unquoted == 0 && expired == 0
                ? AssessmentStatus.READY
                : AssessmentStatus.PARTIAL;
        return new ChainSnapshot(chain, capturedAt, spotPrice, status,
                Map.copyOf(metadata));
    }

    private ExpiryBuild buildExpiry(LocalDate expiry,
                                    List<OptionContract> contracts,
                                    Map<InstrumentKey, Quote> quotes,
                                    BigDecimal spotPrice) {
        Map<BigDecimal, OptionContract> calls = new TreeMap<>(BigDecimal::compareTo);
        Map<BigDecimal, OptionContract> puts = new TreeMap<>(BigDecimal::compareTo);
        for (OptionContract contract : contracts) {
            if (contract.optionType().isCall()) {
                calls.put(contract.strike(), contract);
            } else {
                puts.put(contract.strike(), contract);
            }
        }
        TreeSet<BigDecimal> strikes = new TreeSet<>(BigDecimal::compareTo);
        strikes.addAll(calls.keySet());
        strikes.addAll(puts.keySet());

        Set<BigDecimal> atTheMoney = atTheMoneyStrikes(strikes, spotPrice);
        List<StrikeGroup> groups = new ArrayList<>(strikes.size());
        int unquoted = 0;
        for (BigDecimal strike : strikes) {
            Strike call = buildLeg(calls.get(strike), quotes, spotPrice, atTheMoney,
                    OptionType.CALL);
            Strike put = buildLeg(puts.get(strike), quotes, spotPrice, atTheMoney,
                    OptionType.PUT);
            if (calls.containsKey(strike) && call == null) {
                unquoted++;
            }
            if (puts.containsKey(strike) && put == null) {
                unquoted++;
            }
            if (call == null && put == null) {
                continue;
            }
            groups.add(new StrikeGroup(strike,
                    call == null ? Optional.empty() : Optional.of(call),
                    put == null ? Optional.empty() : Optional.of(put)));
        }
        return new ExpiryBuild(List.copyOf(groups), unquoted);
    }

    private Strike buildLeg(OptionContract contract,
                            Map<InstrumentKey, Quote> quotes,
                            BigDecimal spotPrice,
                            Set<BigDecimal> atTheMoney,
                            OptionType type) {
        if (contract == null) {
            return null;
        }
        Quote quote = quotes.get(contract.instrument().key());
        if (!usable(quote)) {
            return null;
        }
        boolean atm = atTheMoney.stream()
                .anyMatch(strike -> strike.compareTo(contract.strike()) == 0);
        return new Strike(contract, quote,
                moneynessClassifier.classify(spotPrice, contract.strike(), type, atm));
    }

    private Set<BigDecimal> atTheMoneyStrikes(TreeSet<BigDecimal> strikes, BigDecimal spot) {
        Set<BigDecimal> withinBand = new TreeSet<>(BigDecimal::compareTo);
        for (BigDecimal strike : strikes) {
            if (moneynessClassifier.withinAtmBand(spot, strike)) {
                withinBand.add(strike);
            }
        }
        if (!withinBand.isEmpty()) {
            return withinBand;
        }
        BigDecimal nearest = null;
        BigDecimal bestDistance = null;
        for (BigDecimal strike : strikes) {
            BigDecimal distance = strike.subtract(spot).abs();
            if (bestDistance == null || distance.compareTo(bestDistance) < 0) {
                bestDistance = distance;
                nearest = strike;
            }
        }
        return nearest == null ? Set.of() : Set.of(nearest);
    }

    private static boolean usable(Quote quote) {
        return quote != null
                && (quote.lastTradedPrice() != null || !quote.depth().isEmpty());
    }

    private static String canonical(BigDecimal strike) {
        BigDecimal stripped = strike.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }

    private record ExpiryBuild(List<StrikeGroup> groups, int unquoted) {
    }
}
