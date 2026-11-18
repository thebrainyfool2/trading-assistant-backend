package com.tradingassistant.options.analysis;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.LiquidityState;
import com.tradingassistant.options.domain.LiquiditySummary;
import com.tradingassistant.options.domain.Strike;
import com.tradingassistant.options.domain.Tradability;
import com.tradingassistant.options.math.DecimalMath;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes the quoted quality of the chain: bid/ask spread, wide-spread
 * detection, thin-market detection and the resulting tradability band.
 *
 * <p>A leg is assessed only with a two-sided quote. A leg is thin when its open
 * interest or volume is below the configured floor. The aggregate state is
 * illiquid when more than half of the assessed legs are wide or thin, moderate
 * when some are, and liquid when none are. Tradability is the descriptive band
 * derived from that state.</p>
 */
public final class LiquidityAnalyzer {

    private final OptionsAnalyticsConfig config;

    public LiquidityAnalyzer(OptionsAnalyticsConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    public LiquiditySummary analyze(ChainSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        int assessed = 0;
        int wide = 0;
        int thin = 0;
        BigDecimal spreadTotal = BigDecimal.ZERO;

        for (Strike leg : snapshot.legs()) {
            Optional<BigDecimal> bid = leg.bestBid();
            Optional<BigDecimal> ask = leg.bestAsk();
            if (bid.isEmpty() || ask.isEmpty() || ask.get().compareTo(bid.get()) < 0) {
                continue;
            }
            BigDecimal mid = bid.get().add(ask.get())
                    .divide(DecimalMath.TWO, DecimalMath.CONTEXT);
            if (mid.signum() <= 0) {
                continue;
            }
            assessed++;
            BigDecimal spreadPercent = ask.get().subtract(bid.get())
                    .divide(mid, DecimalMath.CONTEXT)
                    .multiply(DecimalMath.HUNDRED, DecimalMath.CONTEXT);
            spreadTotal = spreadTotal.add(spreadPercent);
            if (spreadPercent.compareTo(config.maximumSpreadPercent()) > 0) {
                wide++;
            }
            if (leg.openInterest().orElse(0L) < config.minimumOpenInterest()
                    || leg.tradedVolume().orElse(0L) < config.minimumVolume()) {
                thin++;
            }
        }

        if (assessed == 0) {
            return new LiquiditySummary(0, 0, 0, Optional.empty(), LiquidityState.UNKNOWN,
                    Tradability.UNKNOWN, AssessmentStatus.INSUFFICIENT_DATA,
                    "no two-sided quote is available");
        }
        BigDecimal averageSpread = spreadTotal.divide(BigDecimal.valueOf(assessed),
                DecimalMath.CONTEXT);
        boolean majorityWide = wide * 2 > assessed;
        boolean majorityThin = thin * 2 > assessed;
        LiquidityState state;
        if (majorityWide || majorityThin) {
            state = LiquidityState.ILLIQUID;
        } else if (wide > 0 || thin > 0) {
            state = LiquidityState.MODERATE;
        } else {
            state = LiquidityState.LIQUID;
        }
        Tradability tradability = switch (state) {
            case LIQUID -> Tradability.HIGH;
            case MODERATE -> Tradability.MODERATE;
            case ILLIQUID -> Tradability.LOW;
            case UNKNOWN -> Tradability.UNKNOWN;
        };
        return new LiquiditySummary(assessed, wide, thin, Optional.of(averageSpread), state,
                tradability, AssessmentStatus.READY,
                "average spread " + DecimalMath.format(averageSpread) + "%, " + wide
                        + " wide and " + thin + " thin legs of " + assessed);
    }
}
