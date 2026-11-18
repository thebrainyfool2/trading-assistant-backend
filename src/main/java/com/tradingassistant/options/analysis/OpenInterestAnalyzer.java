package com.tradingassistant.options.analysis;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ChainStateSnapshot;
import com.tradingassistant.options.domain.ExpiryChain;
import com.tradingassistant.options.domain.LegState;
import com.tradingassistant.options.domain.OpenInterestBuildUp;
import com.tradingassistant.options.domain.OpenInterestSummary;
import com.tradingassistant.options.domain.OptionLegKey;
import com.tradingassistant.options.domain.Strike;
import com.tradingassistant.options.domain.StrikeGroup;
import com.tradingassistant.options.domain.StrikeOpenInterest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes open interest, volume, concentration and positioning.
 *
 * <p>Concentration is the strike carrying the largest call or put open interest.
 * Support and resistance zones are the strikes with the largest put and call
 * open interest respectively. Build-up combines the leg's premium change with
 * its open-interest change. All of these are descriptions of where open interest
 * sits; none of them is a level, a signal or a recommendation.</p>
 */
public final class OpenInterestAnalyzer {

    private final OptionsAnalyticsConfig config;

    public OpenInterestAnalyzer(OptionsAnalyticsConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public OpenInterestSummary analyze(ChainSnapshot snapshot, ChainStateSnapshot previous) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        ChainStateSnapshot earlier = previous == null ? ChainStateSnapshot.empty() : previous;
        List<StrikeOpenInterest> strikes = new ArrayList<>();
        long totalCallOpenInterest = 0;
        long totalPutOpenInterest = 0;
        long totalCallVolume = 0;
        long totalPutVolume = 0;

        for (ExpiryChain expiry : snapshot.expiries()) {
            for (StrikeGroup group : expiry.strikes()) {
                LegDescription call = describe(group.call().orElse(null), earlier);
                LegDescription put = describe(group.put().orElse(null), earlier);
                totalCallOpenInterest += call.openInterest();
                totalPutOpenInterest += put.openInterest();
                totalCallVolume += call.volume();
                totalPutVolume += put.volume();
                strikes.add(new StrikeOpenInterest(expiry.expiry(), group.strike(),
                        call.openInterest(), put.openInterest(),
                        call.openInterestChange(), put.openInterestChange(),
                        call.volume(), put.volume(),
                        call.volumeChange(), put.volumeChange(),
                        call.buildUp(), put.buildUp()));
            }
        }

        Optional<BigDecimal> callConcentration = concentration(strikes, true);
        Optional<BigDecimal> putConcentration = concentration(strikes, false);
        List<BigDecimal> support = zones(strikes, false);
        List<BigDecimal> resistance = zones(strikes, true);

        AssessmentStatus status;
        if (totalCallOpenInterest == 0 && totalPutOpenInterest == 0
                && totalCallVolume == 0 && totalPutVolume == 0) {
            status = AssessmentStatus.INSUFFICIENT_DATA;
        } else if (earlier.isEmpty()) {
            status = AssessmentStatus.PARTIAL;
        } else {
            status = AssessmentStatus.READY;
        }
        return new OpenInterestSummary(totalCallOpenInterest, totalPutOpenInterest,
                totalCallVolume, totalPutVolume, strikes, callConcentration, putConcentration,
                support, resistance, status,
                "call OI " + totalCallOpenInterest + ", put OI " + totalPutOpenInterest
                        + " across " + strikes.size() + " strikes");
    }

    private LegDescription describe(Strike leg, ChainStateSnapshot previous) {
        if (leg == null) {
            return LegDescription.empty();
        }
        long openInterest = leg.openInterest().orElse(0L);
        long volume = leg.tradedVolume().orElse(0L);
        Optional<LegState> earlier = previous.leg(OptionLegKey.of(leg));
        Optional<Long> openInterestChange = leg.openInterest().isPresent() && earlier.isPresent()
                ? Optional.of(openInterest - earlier.get().openInterest())
                : Optional.empty();
        Optional<Long> volumeChange = leg.tradedVolume().isPresent() && earlier.isPresent()
                ? Optional.of(volume - earlier.get().volume())
                : Optional.empty();
        Optional<BigDecimal> priceChange = priceChange(leg, earlier);
        OpenInterestBuildUp buildUp = buildUp(priceChange, openInterestChange);
        return new LegDescription(openInterest, volume, openInterestChange, volumeChange, buildUp);
    }

    private Optional<BigDecimal> priceChange(Strike leg, Optional<LegState> earlier) {
        Optional<BigDecimal> reported = leg.premiumChange();
        if (reported.isPresent()) {
            return reported;
        }
        if (earlier.isEmpty() || leg.premium().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(leg.premium().get().subtract(earlier.get().premium()));
    }

    private OpenInterestBuildUp buildUp(Optional<BigDecimal> priceChange,
                                        Optional<Long> openInterestChange) {
        if (priceChange.isEmpty() || openInterestChange.isEmpty()) {
            return OpenInterestBuildUp.UNKNOWN;
        }
        int priceDirection = priceChange.get().signum();
        int interestDirection = openInterestChange.get() > 0 ? 1
                : openInterestChange.get() < 0 ? -1 : 0;
        if (priceDirection == 0 || interestDirection == 0) {
            return OpenInterestBuildUp.NEUTRAL;
        }
        if (priceDirection > 0) {
            return interestDirection > 0
                    ? OpenInterestBuildUp.LONG_BUILD_UP
                    : OpenInterestBuildUp.SHORT_COVERING;
        }
        return interestDirection > 0
                ? OpenInterestBuildUp.SHORT_BUILD_UP
                : OpenInterestBuildUp.LONG_UNWINDING;
    }

    private Optional<BigDecimal> concentration(List<StrikeOpenInterest> strikes, boolean calls) {
        StrikeOpenInterest best = null;
        for (StrikeOpenInterest entry : strikes) {
            long interest = calls ? entry.callOpenInterest() : entry.putOpenInterest();
            if (interest <= 0) {
                continue;
            }
            if (best == null) {
                best = entry;
                continue;
            }
            long bestInterest = calls ? best.callOpenInterest() : best.putOpenInterest();
            if (interest > bestInterest
                    || (interest == bestInterest && entry.strike().compareTo(best.strike()) < 0)) {
                best = entry;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.strike());
    }

    private List<BigDecimal> zones(List<StrikeOpenInterest> strikes, boolean calls) {
        return strikes.stream()
                .filter(entry -> (calls ? entry.callOpenInterest() : entry.putOpenInterest()) > 0)
                .sorted((left, right) -> {
                    long leftInterest = calls ? left.callOpenInterest() : left.putOpenInterest();
                    long rightInterest = calls ? right.callOpenInterest() : right.putOpenInterest();
                    int byInterest = Long.compare(rightInterest, leftInterest);
                    return byInterest != 0 ? byInterest : left.strike().compareTo(right.strike());
                })
                .limit(config.supportResistanceStrikeCount())
                .map(StrikeOpenInterest::strike)
                .toList();
    }

    private record LegDescription(
            long openInterest,
            long volume,
            Optional<Long> openInterestChange,
            Optional<Long> volumeChange,
            OpenInterestBuildUp buildUp) {

        static LegDescription empty() {
            return new LegDescription(0L, 0L, Optional.empty(), Optional.empty(),
                    OpenInterestBuildUp.UNKNOWN);
        }
    }
}
