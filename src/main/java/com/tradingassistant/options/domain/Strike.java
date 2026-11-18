package com.tradingassistant.options.domain;

import com.tradingassistant.market.domain.DepthLevel;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.domain.Quote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One quoted option leg of a chain: the contract, its immutable quote snapshot
 * and its moneyness at capture time.
 *
 * <p>The leg carries no analytics. Implied volatility and Greeks are produced
 * downstream from the same leg, so the chain structure stays independent of any
 * pricing model.</p>
 */
public record Strike(
        OptionContract contract,
        Quote quote,
        Moneyness moneyness) {

    public Strike {
        contract = Objects.requireNonNull(contract, "contract must not be null");
        quote = Objects.requireNonNull(quote, "quote must not be null");
        moneyness = Objects.requireNonNull(moneyness, "moneyness must not be null");
        if (!quote.instrument().equals(contract.instrument().key())) {
            throw new IllegalArgumentException("quote instrument must match the contract instrument");
        }
    }

    public OptionType optionType() {
        return contract.optionType();
    }

    public BigDecimal strike() {
        return contract.strike();
    }

    public LocalDate expiry() {
        return contract.expiry();
    }

    public boolean isCall() {
        return contract.optionType().isCall();
    }

    public boolean isAtTheMoney() {
        return moneyness == Moneyness.ATM;
    }

    /** Best bid price, empty when the feed supplied no buy ladder. */
    public Optional<BigDecimal> bestBid() {
        List<DepthLevel> levels = quote.depth().buy();
        return levels.isEmpty() ? Optional.empty() : Optional.of(levels.get(0).price());
    }

    /** Best ask price, empty when the feed supplied no sell ladder. */
    public Optional<BigDecimal> bestAsk() {
        List<DepthLevel> levels = quote.depth().sell();
        return levels.isEmpty() ? Optional.empty() : Optional.of(levels.get(0).price());
    }

    /** Last traded premium, empty when the quote carries none. */
    public Optional<BigDecimal> premium() {
        return Optional.ofNullable(quote.lastTradedPrice());
    }

    /** Open interest in contracts, empty when the quote carries none. */
    public Optional<Long> openInterest() {
        return Optional.ofNullable(quote.openInterest());
    }

    /** Traded volume in contracts, empty when the quote carries none. */
    public Optional<Long> tradedVolume() {
        return Optional.ofNullable(quote.volume());
    }

    /** Change in premium against the previous close, empty when unavailable. */
    public Optional<BigDecimal> premiumChange() {
        return Optional.ofNullable(quote.netChange());
    }
}
