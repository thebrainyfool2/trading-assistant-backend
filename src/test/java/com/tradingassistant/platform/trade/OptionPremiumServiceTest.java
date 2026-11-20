package com.tradingassistant.platform.trade;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.domain.QuoteMode;
import com.tradingassistant.market.service.MarketDataService;
import com.tradingassistant.market.time.MarketStatusResolver;
import com.tradingassistant.monitoring.MonitoringTestFixtures;
import com.tradingassistant.monitoring.domain.ActiveTradeMonitoring;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.domain.ManualTradeStatus;
import com.tradingassistant.monitoring.service.MonitoringQueryService;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.platform.instruments.InstrumentCatalog;
import com.tradingassistant.platform.notification.NotificationService;
import com.tradingassistant.platform.notification.NotificationType;
import com.tradingassistant.platform.security.LocalPrincipalFilter;
import com.tradingassistant.recommendation.domain.RecommendationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OptionPremiumServiceTest {

    private final MonitoringQueryService queries = mock(MonitoringQueryService.class);
    private final InstrumentCatalog catalog = mock(InstrumentCatalog.class);
    private final MarketDataService marketData = mock(MarketDataService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final MarketCalendarRegistry calendars = MarketCalendarRegistry.defaults();

    @Test
    void alertsOnceWhenThePremiumTargetIsReached() {
        var contract = OptionsTestFixtures.contract("SBIN", OptionsTestFixtures.EXPIRY,
                "500", OptionType.CALL);
        ManualTrade trade = trade(new BigDecimal("25"), new BigDecimal("12"),
                contract.instrument().tradingSymbol());
        when(queries.active()).thenReturn(List.of(
                new ActiveTradeMonitoring(trade, Optional.empty())));
        when(catalog.findOption(any(), anyString(), any(), any(), any()))
                .thenReturn(Optional.of(contract.instrument()));
        when(marketData.quotes(eq(QuoteMode.LTP), anyList())).thenReturn(List.of(
                OptionsTestFixtures.quote(contract, "30", "0", 1_000L, 500L,
                        "29", "31")));
        OptionPremiumService service = service(Clock.fixed(
                OptionsTestFixtures.START, ZoneOffset.UTC));

        service.refresh();
        service.refresh();

        verify(notifications, times(1)).notify(
                eq(LocalPrincipalFilter.OWNER_ID), eq(NotificationType.TRADE_ALERT),
                contains("target"), contains("Review the position"));
    }

    @Test
    void alertsWhenThePremiumStopIsReached() {
        var contract = OptionsTestFixtures.contract("SBIN", OptionsTestFixtures.EXPIRY,
                "500", OptionType.CALL);
        ManualTrade trade = trade(new BigDecimal("40"), new BigDecimal("12"),
                contract.instrument().tradingSymbol());
        when(queries.active()).thenReturn(List.of(
                new ActiveTradeMonitoring(trade, Optional.empty())));
        when(catalog.findOption(any(), anyString(), any(), any(), any()))
                .thenReturn(Optional.of(contract.instrument()));
        when(marketData.quotes(eq(QuoteMode.LTP), anyList())).thenReturn(List.of(
                OptionsTestFixtures.quote(contract, "10", "0", 1_000L, 500L,
                        "9", "11")));
        OptionPremiumService service = service(Clock.fixed(
                OptionsTestFixtures.START, ZoneOffset.UTC));

        service.refresh();

        verify(notifications).notify(eq(LocalPrincipalFilter.OWNER_ID),
                eq(NotificationType.TRADE_ALERT), contains("stop"),
                contains("Review the position"));
    }

    @Test
    void doesNotAlertOutsideTheMarketSession() {
        var contract = OptionsTestFixtures.contract("SBIN", OptionsTestFixtures.EXPIRY,
                "500", OptionType.CALL);
        ManualTrade trade = trade(new BigDecimal("25"), new BigDecimal("12"),
                contract.instrument().tradingSymbol());
        when(queries.active()).thenReturn(List.of(
                new ActiveTradeMonitoring(trade, Optional.empty())));
        when(catalog.findOption(any(), anyString(), any(), any(), any()))
                .thenReturn(Optional.of(contract.instrument()));
        when(marketData.quotes(eq(QuoteMode.LTP), anyList())).thenReturn(List.of(
                OptionsTestFixtures.quote(contract, "30", "0", 1_000L, 500L,
                        "29", "31")));
        OptionPremiumService service = service(Clock.fixed(
                OptionsTestFixtures.START.plus(Duration.ofHours(8)), ZoneOffset.UTC));

        service.refresh();

        verify(notifications, never()).notify(anyString(), any(), anyString(),
                anyString());
    }

    private OptionPremiumService service(Clock clock) {
        return new OptionPremiumService(queries, catalog, marketData, calendars,
                notifications, TradePlanConfig.defaults(), clock);
    }

    private static ManualTrade trade(BigDecimal target, BigDecimal stop,
                                     String optionSymbol) {
        return new ManualTrade("trade-1", MonitoringTestFixtures.SPOT,
                MonitoringTestFixtures.INTERVAL, RecommendationType.BUY_CALL,
                optionSymbol, new BigDecimal("500"), OptionsTestFixtures.EXPIRY,
                MonitoringTestFixtures.START, new BigDecimal("20"),
                new BigDecimal("75"), "", ManualTradeStatus.ACTIVE,
                Optional.of(target), Optional.of(stop));
    }
}
