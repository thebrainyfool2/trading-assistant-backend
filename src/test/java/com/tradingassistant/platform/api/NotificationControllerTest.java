package com.tradingassistant.platform.api;

import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final MockMvc mockMvc = WebTestSupport.mvc(
            new NotificationController(notifications));

    @BeforeEach
    void signIn() {
        WebTestSupport.authenticate(PlatformTestFixtures.principal());
    }

    @AfterEach
    void signOut() {
        WebTestSupport.clearAuthentication();
    }

    @Test
    void notificationsAreListedForTheOwner() throws Exception {
        when(notifications.list("user-1", 0, 20)).thenReturn(
                new PageImpl<>(List.of(PlatformTestFixtures.notification(false))));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].type").value("LOGIN_ALERT"))
                .andExpect(jsonPath("$.data.items[0].read").value(false));
    }

    @Test
    void unreadCountIsReported() throws Exception {
        when(notifications.unreadCount("user-1")).thenReturn(2L);

        mockMvc.perform(get("/api/v1/notifications/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(2));
    }

    @Test
    void singleNotificationCanBeMarkedRead() throws Exception {
        when(notifications.markRead("user-1", "notification-1"))
                .thenReturn(PlatformTestFixtures.notification(true));

        mockMvc.perform(put("/api/v1/notifications/notification-1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        verify(notifications).markRead("user-1", "notification-1");
    }

    @Test
    void allNotificationsCanBeMarkedRead() throws Exception {
        when(notifications.unreadCount("user-1")).thenReturn(0L);

        mockMvc.perform(put("/api/v1/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(0));

        verify(notifications).markAllRead("user-1");
    }
}
