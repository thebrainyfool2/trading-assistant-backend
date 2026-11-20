package com.tradingassistant.recommendation.engine;

import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ExpiryChain;
import com.tradingassistant.options.domain.Strike;
import com.tradingassistant.options.domain.StrikeGroup;
import com.tradingassistant.recommendation.config.ExpirySelection;
import com.tradingassistant.recommendation.config.RecommendationConfig;
import com.tradingassistant.recommendation.config.StrikeMode;
import com.tradingassistant.recommendation.domain.OptionSelection;
import com.tradingassistant.recommendation.domain.RecommendationContext;
import com.tradingassistant.recommendation.domain.RecommendationType;
import com.tradingassistant.recommendation.exception.RecommendationValidationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic expiry and strike selection. Expiries come from the chain,
 * strike offsets come from configuration, and the selected leg comes from the
 * captured chain. No premium, entry, target, stop or size is produced.
 */
public final class OptionSelector {

    public Optional<OptionSelection> select(RecommendationType type,
                                             RecommendationContext context,
                                             RecommendationConfig config) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (type == RecommendationType.NO_TRADE) {
            return Optional.empty();
        }
        ChainSnapshot chain = context.chain().orElse(null);
        if (chain == null) {
            return Optional.empty();
        }
        validateChain(chain, context);
        ExpiryChain expiry = chooseExpiry(chain, config);
        OptionType optionType = type == RecommendationType.BUY_CALL
                ? OptionType.CALL : OptionType.PUT;
        int atmIndex = atmIndex(expiry, chain.spotPrice());
        int selectedIndex = strikeIndex(atmIndex, config.strikeSelection().mode(),
                config.strikeSelection().offsetSteps(), optionType);
        List<StrikeGroup> groups = expiry.strikes();
        if (selectedIndex < 0 || selectedIndex >= groups.size()) {
            throw new RecommendationValidationException(
                    "configured strike selection falls outside the expiry chain");
        }
        StrikeGroup group = groups.get(selectedIndex);
        Strike leg = group.leg(optionType).orElseThrow(() ->
                new RecommendationValidationException(
                        "selected strike has no " + optionType + " leg"));
        return Optional.of(new OptionSelection(expiry.expiry(), expiry.kind(),
                group.strike(), optionType, leg.moneyness(),
                config.strikeSelection().mode(),
                Math.abs(selectedIndex - atmIndex)));
    }

    private void validateChain(ChainSnapshot chain, RecommendationContext context) {
        if (chain.status()
                != com.tradingassistant.intelligence.domain.AssessmentStatus.READY) {
            throw new RecommendationValidationException(
                    "option chain is not ready");
        }
        if (!chain.spotInstrument().equals(context.market().instrument())) {
            throw new RecommendationValidationException(
                    "option chain does not match the market instrument");
        }
        if (!chain.capturedAt().equals(context.market().timestamp())) {
            throw new RecommendationValidationException(
                    "option chain does not match the market timestamp");
        }
        if (chain.expiries().isEmpty()) {
            throw new RecommendationValidationException(
                    "option chain has no expiries");
        }
    }

    private ExpiryChain chooseExpiry(ChainSnapshot chain, RecommendationConfig config) {
        List<ExpiryChain> allowed = chain.expiries().stream()
                .filter(expiry -> config.allowedExpiryKinds().contains(expiry.kind()))
                .toList();
        if (allowed.isEmpty()) {
            throw new RecommendationValidationException(
                    "option chain has no allowed expiry");
        }
        ExpirySelection selection = config.expirySelection();
        int index = switch (selection) {
            case NEAREST -> 0;
            case NEXT -> 1;
            case FAR -> allowed.size() - 1;
        };
        if (index < 0 || index >= allowed.size()) {
            throw new RecommendationValidationException(
                    "configured expiry selection is unavailable");
        }
        return allowed.get(index);
    }

    private int atmIndex(ExpiryChain expiry, BigDecimal spotPrice) {
        Optional<StrikeGroup> atm = expiry.atTheMoney();
        if (atm.isPresent()) {
            return expiry.strikes().indexOf(atm.get());
        }
        StrikeGroup nearest = expiry.nearestTo(spotPrice).orElseThrow(() ->
                new RecommendationValidationException(
                        "expiry chain has no strike near spot"));
        return expiry.strikes().indexOf(nearest);
    }

    private int strikeIndex(int atmIndex, StrikeMode mode, int offset,
                            OptionType optionType) {
        if (mode == StrikeMode.ATM) {
            return atmIndex;
        }
        if (mode == StrikeMode.ITM) {
            return optionType.isCall() ? atmIndex - offset : atmIndex + offset;
        }
        return optionType.isCall() ? atmIndex + offset : atmIndex - offset;
    }
}
