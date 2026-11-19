package com.tradingassistant.platform.api;

import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.watchlist.WatchlistDocument;
import com.tradingassistant.platform.watchlist.WatchlistItem;
import com.tradingassistant.platform.watchlist.WatchlistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WatchlistControllerTest {

    private final WatchlistService watchlists = mock(WatchlistService.class);
    private final MockMvc mockMvc = WebTestSupport.mvc(
            new WatchlistController(watchlists));

    @BeforeEach
    void signIn() {
        WebTestSupport.authenticate(PlatformTestFixtures.principal());
    }

    @AfterEach
    void signOut() {
        WebTestSupport.clearAuthentication();
    }

    @Test
    void listsAreScopedToTheAuthenticatedOwner() throws Exception {
        when(watchlists.list("user-1")).thenReturn(List.of(watchlist()));

        mockMvc.perform(get("/api/v1/watchlists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("list-1"))
                .andExpect(jsonPath("$.data[0].name").value("Momentum"))
                .andExpect(jsonPath("$.data[0].items[0].symbol").value("RELIANCE"));

        verify(watchlists).list("user-1");
    }

    @Test
    void createReturnsCreated() throws Exception {
        when(watchlists.create("user-1", "Momentum")).thenReturn(watchlist());

        mockMvc.perform(post("/api/v1/watchlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Momentum\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Momentum"));
    }

    @Test
    void addingAnInstrumentReturnsTheUpdatedList() throws Exception {
        when(watchlists.addItem("user-1", "list-1", "2885", "RELIANCE",
                Segment.NSE_CASH)).thenReturn(watchlist());

        mockMvc.perform(post("/api/v1/watchlists/list-1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbolToken\":\"2885\",\"symbol\":\"RELIANCE\","
                                + "\"segment\":\"NSE_CASH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void removingAnItemDelegatesTheOwner() throws Exception {
        WatchlistDocument empty = new WatchlistDocument("list-1", "user-1",
                "Momentum", List.of(), PlatformTestFixtures.NOW,
                PlatformTestFixtures.NOW);
        when(watchlists.removeItem("user-1", "list-1", "item-1"))
                .thenReturn(empty);

        mockMvc.perform(delete("/api/v1/watchlists/list-1/items/item-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        verify(watchlists).removeItem("user-1", "list-1", "item-1");
    }

    @Test
    void deleteRemovesTheOwnedList() throws Exception {
        when(watchlists.require("user-1", "list-1")).thenReturn(watchlist());

        mockMvc.perform(delete("/api/v1/watchlists/list-1"))
                .andExpect(status().isOk());

        verify(watchlists).delete(eq("user-1"), eq("list-1"));
    }

    private static WatchlistDocument watchlist() {
        return new WatchlistDocument("list-1", "user-1", "Momentum",
                List.of(new WatchlistItem("item-1", "2885", "RELIANCE",
                        Segment.NSE_CASH, PlatformTestFixtures.NOW)),
                PlatformTestFixtures.NOW, PlatformTestFixtures.NOW);
    }
}
