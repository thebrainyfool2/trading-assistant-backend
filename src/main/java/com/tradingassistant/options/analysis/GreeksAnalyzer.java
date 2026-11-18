package com.tradingassistant.options.analysis;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ExpiryChain;
import com.tradingassistant.options.domain.Greeks;
import com.tradingassistant.options.domain.GreeksAssessment;
import com.tradingassistant.options.domain.GreeksSummary;
import com.tradingassistant.options.domain.OptionLegKey;
import com.tradingassistant.options.domain.Strike;
import com.tradingassistant.options.greeks.GreeksEngine;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes the Greeks of every priced leg and describes the listed exposure.
 *
 * <p>Net exposure weights each leg's Greeks by its open interest, with calls
 * positive and puts negative. This is a description of listed exposure, not a
 * position and not a risk measure: open interest counts both sides of every
 * contract.</p>
 */
public final class GreeksAnalyzer {

    private final GreeksEngine engine;

    public GreeksAnalyzer(GreeksEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    public GreeksSummary analyze(ChainSnapshot snapshot,
                                 Map<OptionLegKey, BigDecimal> impliedVolatilities) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(impliedVolatilities, "impliedVolatilities must not be null");
        Greeks netExposure = Greeks.zero();
        int evaluated = 0;
        int priced = 0;

        for (Strike leg : snapshot.legs()) {
            BigDecimal volatility = impliedVolatilities.get(OptionLegKey.of(leg));
            if (volatility == null) {
                continue;
            }
            priced++;
            Optional<Greeks> greeks = evaluate(snapshot, leg, volatility);
            if (greeks.isEmpty()) {
                continue;
            }
            evaluated++;
            if (leg.openInterest().isPresent()) {
                BigDecimal weight = BigDecimal.valueOf(leg.openInterest().get())
                        .multiply(BigDecimal.valueOf(leg.isCall() ? 1 : -1));
                netExposure = netExposure.add(greeks.get().scale(weight));
            }
        }

        Optional<Greeks> atmCall = Optional.empty();
        Optional<Greeks> atmPut = Optional.empty();
        Optional<ExpiryChain> nearest = snapshot.nearestExpiry();
        if (nearest.isPresent() && nearest.get().atTheMoney().isPresent()) {
            atmCall = nearest.get().atTheMoney().get().call()
                    .flatMap(leg -> evaluate(snapshot, leg,
                            impliedVolatilities.get(OptionLegKey.of(leg))));
            atmPut = nearest.get().atTheMoney().get().put()
                    .flatMap(leg -> evaluate(snapshot, leg,
                            impliedVolatilities.get(OptionLegKey.of(leg))));
        }

        AssessmentStatus status;
        if (evaluated == 0) {
            status = AssessmentStatus.INSUFFICIENT_DATA;
        } else if (atmCall.isPresent() && atmPut.isPresent() && evaluated >= priced) {
            status = AssessmentStatus.READY;
        } else {
            status = AssessmentStatus.PARTIAL;
        }
        return new GreeksSummary(atmCall, atmPut, netExposure, evaluated, status,
                "evaluated " + evaluated + " of " + priced + " legs with " + engine.modelName());
    }

    private Optional<Greeks> evaluate(ChainSnapshot snapshot, Strike leg, BigDecimal volatility) {
        if (volatility == null) {
            return Optional.empty();
        }
        GreeksAssessment assessment = engine.evaluate(leg.optionType(), snapshot.spotPrice(),
                leg.strike(), leg.expiry(), snapshot.capturedAt(), volatility);
        return assessment.greeks();
    }

    /**
     * Greek of a leg for the given volatility, exposed for diagnostics.
     */
    public Optional<Greeks> greeksOf(ChainSnapshot snapshot, Strike leg, BigDecimal volatility) {
        Objects.requireNonNull(leg, "leg must not be null");
        return evaluate(snapshot, leg, volatility);
    }

    /**
     * Signed weight used for listed exposure: calls positive, puts negative.
     */
    public static int exposureSign(OptionType type) {
        return type.isCall() ? 1 : -1;
    }
}
