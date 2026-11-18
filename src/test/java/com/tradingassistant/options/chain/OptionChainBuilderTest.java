package com.tradingassistant.options.chain;

import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.domain.ExpiryChain;
import com.tradingassistant.options.domain.ExpiryKind;
import com.tradingassistant.options.domain.ExpiryProximity;
import com.tradingassistant.options.domain.Moneyness;
import com.tradingassistant.options.domain.Strike;
import com.tradingassistant.options.domain.StrikeGroup;
import com.tradingassistant.options.exception.OptionsValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionChainBuilderTest {

    private final OptionChainBuilder builder = new OptionChainBuilder(OptionsTestFixtures.CONFIG);

    @Test
    void buildsOrderedChainWithStrikeGroups() {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, List.of("540", "460", "500"));
        Map<InstrumentKey, Quote> quotes = OptionsTestFixtures.quoted(contracts, "500", "0.25",
                1_000L, 500L, "0.05", "0.05");

        ChainSnapshot snapshot = builder.build(OptionsTestFixtures.SPOT,
                OptionsTestFixtures.UNDERLYING, new BigDecimal("500"),
                OptionsTestFixtures.START, contracts, quotes);

        assertThat(snapshot.status()).isEqualTo(AssessmentStatus.READY);
        assertThat(snapshot.expiries()).hasSize(1);
        ExpiryChain expiry = snapshot.expiries().get(0);
        assertThat(expiry.strikes()).extracting(StrikeGroup::strike)
                .containsExactly(new BigDecimal("460"), new BigDecimal("500"),
                        new BigDecimal("540"));
        assertThat(expiry.legCount()).isEqualTo(6);
        assertThat(expiry.proximity()).isEqualTo(ExpiryProximity.NEAREST);
        assertThat(expiry.daysToExpiry()).isEqualTo(8L);
        assertThat(expiry.strikes().get(1).legs()).extracting(Strike::optionType)
                .containsExactly(OptionType.CALL, OptionType.PUT);
    }

    @Test
    void classifiesAtTheMoneyInTheMoneyAndDeepInTheMoney() {
        LocalDate expiry = OptionsTestFixtures.EXPIRY;
        List<OptionContract> contracts = new ArrayList<>();
        for (String strike : List.of("440", "490", "500", "510")) {
            contracts.add(OptionsTestFixtures.contract(OptionsTestFixtures.UNDERLYING, expiry,
                    strike, OptionType.CALL));
            contracts.add(OptionsTestFixtures.contract(OptionsTestFixtures.UNDERLYING, expiry,
                    strike, OptionType.PUT));
        }
        Map<InstrumentKey, Quote> quotes = OptionsTestFixtures.quoted(contracts, "500", "0.25",
                1_000L, 500L, "0.05", "0.05");

        ChainSnapshot snapshot = OptionsTestFixtures.snapshot(contracts, quotes, "500");
        Map<String, Strike> legs = new LinkedHashMap<>();
        for (Strike leg : snapshot.legs()) {
            legs.put(leg.strike().toPlainString() + "|" + leg.optionType(), leg);
        }

        assertThat(legs.get("500|CALL").moneyness()).isEqualTo(Moneyness.ATM);
        assertThat(legs.get("500|PUT").moneyness()).isEqualTo(Moneyness.ATM);
        assertThat(legs.get("490|CALL").moneyness()).isEqualTo(Moneyness.ITM);
        assertThat(legs.get("490|PUT").moneyness()).isEqualTo(Moneyness.OTM);
        assertThat(legs.get("510|CALL").moneyness()).isEqualTo(Moneyness.OTM);
        assertThat(legs.get("510|PUT").moneyness()).isEqualTo(Moneyness.ITM);
        assertThat(legs.get("440|CALL").moneyness()).isEqualTo(Moneyness.DEEP_ITM);
        assertThat(legs.get("440|PUT").moneyness()).isEqualTo(Moneyness.DEEP_OTM);
    }

    @Test
    void fallsBackToTheNearestStrikeWhenNoStrikeIsInsideTheAtmBand() {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, List.of("480", "490"));
        Map<InstrumentKey, Quote> quotes = OptionsTestFixtures.quoted(contracts, "500", "0.25",
                1_000L, 500L, "0.05", "0.05");

        ChainSnapshot snapshot = OptionsTestFixtures.snapshot(contracts, quotes, "500");

        assertThat(snapshot.legs()).filteredOn(Strike::isAtTheMoney)
                .extracting(leg -> leg.strike().toPlainString())
                .containsExactly("490", "490");
    }

    @Test
    void classifiesWeeklyAndMonthlyExpiriesByMonth() {
        LocalDate weekly = LocalDate.of(2026, 11, 19);
        LocalDate monthly = LocalDate.of(2026, 11, 26);
        LocalDate far = LocalDate.of(2026, 12, 31);
        List<OptionContract> contracts = new ArrayList<>();
        for (LocalDate expiry : List.of(weekly, monthly, far)) {
            contracts.addAll(OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING, expiry,
                    List.of("500")));
        }
        Map<InstrumentKey, Quote> quotes = OptionsTestFixtures.quoted(contracts, "500", "0.25",
                1_000L, 500L, "0.05", "0.05");

        ChainSnapshot snapshot = OptionsTestFixtures.snapshot(contracts, quotes, "500");

        assertThat(snapshot.expiries()).hasSize(3);
        assertThat(snapshot.expiries().get(0).kind()).isEqualTo(ExpiryKind.WEEKLY);
        assertThat(snapshot.expiries().get(1).kind()).isEqualTo(ExpiryKind.MONTHLY);
        assertThat(snapshot.expiries().get(2).kind()).isEqualTo(ExpiryKind.MONTHLY);
        assertThat(snapshot.expiries()).extracting(ExpiryChain::proximity)
                .containsExactly(ExpiryProximity.NEAREST, ExpiryProximity.NEXT,
                        ExpiryProximity.FAR);
    }

    @Test
    void rejectsDuplicateContracts() {
        OptionContract contract = OptionsTestFixtures.contract(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, "500", OptionType.CALL);
        Map<InstrumentKey, Quote> quotes = OptionsTestFixtures.quoted(List.of(contract), "500",
                "0.25", 100L, 100L, "0.05", "0.05");

        assertThatThrownBy(() -> builder.build(OptionsTestFixtures.SPOT,
                OptionsTestFixtures.UNDERLYING, new BigDecimal("500"),
                OptionsTestFixtures.START, List.of(contract, contract), quotes))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("duplicate option contract");
    }

    @Test
    void rejectsDuplicateStrikeForExpiryAndType() {
        OptionContract first = OptionsTestFixtures.contract(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, "500", OptionType.CALL);
        OptionContract second = new OptionContract(
                new com.tradingassistant.market.domain.Instrument(
                        new InstrumentKey(Segment.NSE_FO, "other-token"), "SBIN-ALT", "SBIN-ALT",
                        "alternate", com.tradingassistant.market.domain.InstrumentType.OPTION,
                        new BigDecimal("0.05"), 75L, null, null,
                        com.tradingassistant.market.domain.Currency.INR,
                        OptionsTestFixtures.EXPIRY, new BigDecimal("500.00"), OptionType.CALL,
                        OptionsTestFixtures.UNDERLYING),
                OptionsTestFixtures.UNDERLYING, OptionsTestFixtures.EXPIRY,
                new BigDecimal("500.00"), OptionType.CALL);
        Map<InstrumentKey, Quote> quotes = OptionsTestFixtures.quoted(List.of(first), "500",
                "0.25", 100L, 100L, "0.05", "0.05");

        assertThatThrownBy(() -> builder.build(OptionsTestFixtures.SPOT,
                OptionsTestFixtures.UNDERLYING, new BigDecimal("500"),
                OptionsTestFixtures.START, List.of(first, second), quotes))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("duplicate strike");
    }

    @Test
    void rejectsMixedUnderlyings() {
        OptionContract sbIn = OptionsTestFixtures.contract(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, "500", OptionType.CALL);
        OptionContract infy = OptionsTestFixtures.contract(OptionsTestFixtures.OTHER_UNDERLYING,
                OptionsTestFixtures.EXPIRY, "500", OptionType.CALL);

        assertThatThrownBy(() -> builder.build(OptionsTestFixtures.SPOT,
                OptionsTestFixtures.UNDERLYING, new BigDecimal("500"),
                OptionsTestFixtures.START, List.of(sbIn, infy), Map.of()))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("exactly one underlying");
    }

    @Test
    void rejectsMixedDerivativeSegments() {
        OptionContract nse = OptionsTestFixtures.contract(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, "500", OptionType.CALL, Segment.NSE_FO);
        OptionContract bse = OptionsTestFixtures.contract(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, "510", OptionType.CALL, Segment.BSE_FO);

        assertThatThrownBy(() -> builder.build(OptionsTestFixtures.SPOT,
                OptionsTestFixtures.UNDERLYING, new BigDecimal("500"),
                OptionsTestFixtures.START, List.of(nse, bse), Map.of()))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("exactly one derivative segment");
    }

    @Test
    void rejectsEmptyContractList() {
        assertThatThrownBy(() -> builder.build(OptionsTestFixtures.SPOT,
                OptionsTestFixtures.UNDERLYING, new BigDecimal("500"),
                OptionsTestFixtures.START, List.of(), Map.of()))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("at least one contract");
    }

    @Test
    void rejectsNonPositiveSpotPrice() {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, List.of("500"));

        assertThatThrownBy(() -> builder.build(OptionsTestFixtures.SPOT,
                OptionsTestFixtures.UNDERLYING, BigDecimal.ZERO, OptionsTestFixtures.START,
                contracts, Map.of()))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("spotPrice must be positive");
    }

    @Test
    void excludesExpiredExpiriesAndReportsThem() {
        LocalDate expired = OptionsTestFixtures.expiryInDays(-1);
        List<OptionContract> contracts = new ArrayList<>(
                OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING,
                        OptionsTestFixtures.EXPIRY, List.of("500")));
        contracts.addAll(OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING, expired,
                List.of("500")));
        Map<InstrumentKey, Quote> quotes = OptionsTestFixtures.quoted(contracts, "500", "0.25",
                1_000L, 500L, "0.05", "0.05");

        ChainSnapshot snapshot = OptionsTestFixtures.snapshot(contracts, quotes, "500");

        assertThat(snapshot.expiries()).hasSize(1);
        assertThat(snapshot.status()).isEqualTo(AssessmentStatus.PARTIAL);
        assertThat(snapshot.metadata()).containsEntry("expiredContracts", "2");
    }

    @Test
    void excludesLegsWithoutAQuoteAndReportsThem() {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, List.of("490", "500", "510"));
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>(
                OptionsTestFixtures.quoted(contracts, "500", "0.25", 1_000L, 500L,
                        "0.05", "0.05"));
        quotes.remove(contracts.get(0).instrument().key());

        ChainSnapshot snapshot = OptionsTestFixtures.snapshot(contracts, Map.copyOf(quotes),
                "500");

        assertThat(snapshot.chain().legCount()).isEqualTo(5);
        assertThat(snapshot.metadata()).containsEntry("unquotedContracts", "1");
        assertThat(snapshot.status()).isEqualTo(AssessmentStatus.PARTIAL);
    }

    @Test
    void rejectsChainWithoutAnyQuotedLeg() {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, List.of("500"));

        assertThatThrownBy(() -> builder.build(OptionsTestFixtures.SPOT,
                OptionsTestFixtures.UNDERLYING, new BigDecimal("500"),
                OptionsTestFixtures.START, contracts, Map.of()))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("no quoted contract");
    }

    @Test
    void rejectsChainWithoutAnyUnexpiredContract() {
        LocalDate expired = OptionsTestFixtures.expiryInDays(-2);
        List<OptionContract> contracts = OptionsTestFixtures.ladder(
                OptionsTestFixtures.UNDERLYING, expired, List.of("500"));
        Map<InstrumentKey, Quote> quotes = OptionsTestFixtures.quoted(contracts, "500", "0.25",
                100L, 100L, "0.05", "0.05");

        assertThatThrownBy(() -> builder.build(OptionsTestFixtures.SPOT,
                OptionsTestFixtures.UNDERLYING, new BigDecimal("500"),
                OptionsTestFixtures.START, contracts, quotes))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("no unexpired expiry");
    }

    @Test
    void exposesImmutableStrikeLists() {
        ChainSnapshot snapshot = OptionsTestFixtures.defaultSnapshot();
        List<StrikeGroup> strikes = snapshot.expiries().get(0).strikes();

        assertThatThrownBy(() -> strikes.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void usesTheConfiguredAtmBand() {
        OptionsAnalyticsConfig wideBand = OptionsAnalyticsConfig.builder()
                .atmDistancePercent(new BigDecimal("2.5"))
                .build();
        OptionChainBuilder wideBuilder = new OptionChainBuilder(wideBand);
        List<OptionContract> contracts = OptionsTestFixtures.ladder(OptionsTestFixtures.UNDERLYING,
                OptionsTestFixtures.EXPIRY, List.of("490", "500", "510"));
        Map<InstrumentKey, Quote> quotes = OptionsTestFixtures.quoted(contracts, "500", "0.25",
                1_000L, 500L, "0.05", "0.05");

        ChainSnapshot snapshot = wideBuilder.build(OptionsTestFixtures.SPOT,
                OptionsTestFixtures.UNDERLYING, new BigDecimal("500"),
                OptionsTestFixtures.START, contracts, quotes);

        assertThat(snapshot.legs()).filteredOn(Strike::isAtTheMoney)
                .hasSize(6);
    }
}
