package com.tradingassistant.platform.api;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.error.PlatformException;
import com.tradingassistant.platform.executionapp.OrderApplicationService;
import com.tradingassistant.platform.history.OrderHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest {

    private final OrderApplicationService orders = mock(OrderApplicationService.class);
    private final OrderHistoryService history = mock(OrderHistoryService.class);
    private final MockMvc mockMvc = WebTestSupport.mvc(
            new OrderController(orders, history));

    @BeforeEach
    void signIn() {
        WebTestSupport.authenticate(PlatformTestFixtures.principal());
    }

    @AfterEach
    void signOut() {
        WebTestSupport.clearAuthentication();
    }

    @Test
    void placementReturnsCreatedWithTheRecordedOrder() throws Exception {
        when(orders.place(eq("user-1"), any()))
                .thenReturn(PlatformTestFixtures.orderRecord("user-1"));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.engineOrderId").value("ENG-1"))
                .andExpect(jsonPath("$.data.status").value("FILLED"))
                .andExpect(jsonPath("$.data.strategyId").value("EMA_TREND"));
    }

    @Test
    void unavailableEngineReturnsTheDocumentedError() throws Exception {
        when(orders.place(eq("user-1"), any())).thenThrow(
                new PlatformException(ErrorCode.ENGINE_UNAVAILABLE,
                        "Execution is disabled."));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("ENGINE_UNAVAILABLE"));
    }

    @Test
    void ordersAreListedPageWiseForTheOwner() throws Exception {
        when(history.listForUser("user-1", 0, 20)).thenReturn(
                new PageImpl<>(List.of(PlatformTestFixtures.orderRecord("user-1"))));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void aSingleOrderIsReadThroughTheOwnerScopedLookup() throws Exception {
        when(history.requireForUser("user-1", "order-1"))
                .thenReturn(PlatformTestFixtures.orderRecord("user-1"));

        mockMvc.perform(get("/api/v1/orders/order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbolToken").value("2885"));
    }

    @Test
    void cancelReturnsTheUpdatedOrder() throws Exception {
        when(orders.cancel("user-1", "order-1"))
                .thenReturn(PlatformTestFixtures.orderRecord("user-1"));

        mockMvc.perform(post("/api/v1/orders/order-1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.engineOrderId").value("ENG-1"));

        verify(orders).cancel("user-1", "order-1");
    }

    @Test
    void orderWithoutDirectionIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategyId\":\"EMA_TREND\","
                                + "\"segment\":\"NSE_CASH\","
                                + "\"symbolToken\":\"2885\","
                                + "\"type\":\"MARKET\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        verify(orders, org.mockito.Mockito.never()).place(any(), any());
    }

    private static String orderBody() {
        return "{\"strategyId\":\"EMA_TREND\",\"segment\":\"NSE_CASH\","
                + "\"symbolToken\":\"2885\",\"side\":\"BUY\",\"type\":\"MARKET\","
                + "\"timeInForce\":\"DAY\"}";
    }
}
