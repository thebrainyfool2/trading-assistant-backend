package com.tradingassistant.options.analysis;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ExpiryChain;
import com.tradingassistant.options.domain.MaxPainSummary;
import com.tradingassistant.options.domain.PainPoint;
import com.tradingassistant.options.domain.StrikeGroup;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes the max-pain curve of the nearest expiry.
 *
 * <p>For each candidate settlement strike the total pain is the aggregate
 * intrinsic value that would be paid to option holders: call open interest below
 * the candidate plus put open interest above it. The max-pain strike is the
 * candidate with the smallest total. Open interest is used as the contract
 * count, so the curve is expressed in premium points per lot.</p>
 */
public final class MaxPainAnalyzer {

    private final OptionsAnalyticsConfig config;

    public MaxPainAnalyzer(OptionsAnalyticsConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public MaxPainSummary analyze(ChainSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Optional<ExpiryChain> nearest = snapshot.nearestExpiry();
        if (nearest.isEmpty()) {
            return new MaxPainSummary(Optional.empty(), Optional.empty(), List.of(), List.of(),
                    AssessmentStatus.INSUFFICIENT_DATA, "chain has no expiry");
        }
        ExpiryChain expiry = nearest.get();
        List<StrikeGroup> groups = expiry.strikes();
        boolean hasInterest = groups.stream().anyMatch(group ->
                openInterest(group, true) > 0 || openInterest(group, false) > 0);
        if (!hasInterest) {
            return new MaxPainSummary(Optional.of(expiry.expiry()), Optional.empty(), List.of(),
                    List.of(), AssessmentStatus.INSUFFICIENT_DATA,
                    "nearest expiry carries no open interest");
        }
        List<PainPoint> curve = new ArrayList<>(groups.size());
        for (StrikeGroup candidate : groups) {
            BigDecimal callPain = BigDecimal.ZERO;
            BigDecimal putPain = BigDecimal.ZERO;
            for (StrikeGroup group : groups) {
                if (group.strike().compareTo(candidate.strike()) < 0) {
                    callPain = callPain.add(candidate.strike().subtract(group.strike())
                            .multiply(BigDecimal.valueOf(openInterest(group, true))));
                } else if (group.strike().compareTo(candidate.strike()) > 0) {
                    putPain = putPain.add(group.strike().subtract(candidate.strike())
                            .multiply(BigDecimal.valueOf(openInterest(group, false))));
                }
            }
            curve.add(new PainPoint(candidate.strike(), callPain, putPain,
                    callPain.add(putPain)));
        }
        PainPoint minimum = curve.stream()
                .min(Comparator.comparing(PainPoint::totalPain)
                        .thenComparing(PainPoint::strike))
                .orElseThrow();
        List<PainPoint> nearestStrikes = curve.stream()
                .sorted(Comparator.comparing(
                                (PainPoint point) -> point.strike().subtract(minimum.strike()).abs())
                        .thenComparing(PainPoint::strike))
                .limit(config.maxPainNearestStrikeCount())
                .sorted(Comparator.comparing(PainPoint::strike))
                .toList();
        return new MaxPainSummary(Optional.of(expiry.expiry()),
                Optional.of(minimum.strike()), List.copyOf(curve), nearestStrikes,
                AssessmentStatus.READY,
                "max pain " + minimum.strike().toPlainString() + " for " + expiry.expiry());
    }

    private static long openInterest(StrikeGroup group, boolean calls) {
        return (calls ? group.call() : group.put())
                .flatMap(leg -> leg.openInterest())
                .orElse(0L);
    }
}
