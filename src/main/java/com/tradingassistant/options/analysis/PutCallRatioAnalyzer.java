package com.tradingassistant.options.analysis;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ExpiryChain;
import com.tradingassistant.options.domain.ExpiryPutCallRatio;
import com.tradingassistant.options.domain.PutCallRatioSummary;
import com.tradingassistant.options.domain.Strike;
import com.tradingassistant.options.math.DecimalMath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes put/call ratios by open interest and by volume.
 *
 * <p>A ratio is produced only when the call side is non-zero, so the value is
 * always interpretable. Configuration selects chain-wide ratios, per-expiry
 * ratios, or both.</p>
 */
public final class PutCallRatioAnalyzer {

    private final OptionsAnalyticsConfig config;

    public PutCallRatioAnalyzer(OptionsAnalyticsConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public PutCallRatioSummary analyze(ChainSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        long callOpenInterest = 0;
        long putOpenInterest = 0;
        long callVolume = 0;
        long putVolume = 0;
        List<ExpiryPutCallRatio> perExpiry = new ArrayList<>();

        for (ExpiryChain expiry : snapshot.expiries()) {
            long expiryCallInterest = 0;
            long expiryPutInterest = 0;
            long expiryCallVolume = 0;
            long expiryPutVolume = 0;
            for (Strike leg : expiry.legs()) {
                long interest = leg.openInterest().orElse(0L);
                long volume = leg.tradedVolume().orElse(0L);
                if (leg.isCall()) {
                    expiryCallInterest += interest;
                    expiryCallVolume += volume;
                } else {
                    expiryPutInterest += interest;
                    expiryPutVolume += volume;
                }
            }
            callOpenInterest += expiryCallInterest;
            putOpenInterest += expiryPutInterest;
            callVolume += expiryCallVolume;
            putVolume += expiryPutVolume;
            if (config.pcrAggregation() != com.tradingassistant.options.domain.PcrAggregation.CHAIN) {
                perExpiry.add(new ExpiryPutCallRatio(expiry.expiry(),
                        ratio(expiryPutInterest, expiryCallInterest),
                        ratio(expiryPutVolume, expiryCallVolume)));
            }
        }

        Optional<BigDecimal> chainInterestRatio = Optional.empty();
        Optional<BigDecimal> chainVolumeRatio = Optional.empty();
        if (config.pcrAggregation() != com.tradingassistant.options.domain.PcrAggregation.EXPIRY) {
            chainInterestRatio = ratio(putOpenInterest, callOpenInterest);
            chainVolumeRatio = ratio(putVolume, callVolume);
        }
        boolean anyRatio = chainInterestRatio.isPresent() || chainVolumeRatio.isPresent()
                || perExpiry.stream().anyMatch(entry -> entry.openInterestRatio().isPresent()
                        || entry.volumeRatio().isPresent());
        AssessmentStatus status = anyRatio ? AssessmentStatus.READY
                : AssessmentStatus.INSUFFICIENT_DATA;
        return new PutCallRatioSummary(chainInterestRatio, chainVolumeRatio, perExpiry,
                config.pcrAggregation(), status,
                "PCR by open interest " + chainInterestRatio.map(DecimalMath::format)
                        .orElse("unavailable") + ", by volume "
                        + chainVolumeRatio.map(DecimalMath::format).orElse("unavailable"));
    }

    private static Optional<BigDecimal> ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return Optional.empty();
        }
        return Optional.of(BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), DecimalMath.CONTEXT));
    }
}
