package com.tradingassistant.platform.notification;

import com.mongodb.client.result.UpdateResult;
import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.platform.PlatformTestFixtures;
import com.tradingassistant.platform.error.PlatformException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final NotificationService service = new NotificationService(repository,
            mongoTemplate,
            Clock.fixed(PlatformTestFixtures.NOW, java.time.ZoneOffset.UTC));

    @Test
    void notifyPersistsAnUnreadNotification() {
        when(repository.save(any(NotificationDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.notify("user-1", NotificationType.ORDER_UPDATE, "Order filled",
                "Order 2885 was filled.");

        ArgumentCaptor<NotificationDocument> saved =
                ArgumentCaptor.forClass(NotificationDocument.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().read()).isFalse();
        assertThat(saved.getValue().type()).isEqualTo(NotificationType.ORDER_UPDATE);
        assertThat(saved.getValue().createdAt()).isEqualTo(PlatformTestFixtures.NOW);
    }

    @Test
    void invalidNotificationIsDroppedWithoutFailingTheCaller() {
        service.notify("user-1", NotificationType.SYSTEM_MESSAGE, "x".repeat(121),
                "message");

        verify(repository, never()).save(any());
    }

    @Test
    void listClampsThePageSize() {
        when(repository.findByUserIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Page.empty());

        service.list("user-1", -1, 5_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq("user-1"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void unreadCountUsesTheOwner() {
        when(repository.countByUserIdAndReadFalse("user-1")).thenReturn(4L);

        assertThat(service.unreadCount("user-1")).isEqualTo(4L);
    }

    @Test
    void markReadRejectsAnotherOwnersNotification() {
        when(repository.findByIdAndUserId("notification-1", "user-2"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead("user-2", "notification-1"))
                .isInstanceOf(PlatformException.class)
                .extracting(failure -> ((PlatformException) failure).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void markingAnAlreadyReadNotificationIsIdempotent() {
        when(repository.findByIdAndUserId("notification-1", "user-1"))
                .thenReturn(Optional.of(PlatformTestFixtures.notification(true)));

        NotificationDocument result = service.markRead("user-1", "notification-1");

        assertThat(result.read()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void markingUnreadNotificationRecordsTheReadInstant() {
        when(repository.findByIdAndUserId("notification-1", "user-1"))
                .thenReturn(Optional.of(PlatformTestFixtures.notification(false)));
        when(repository.save(any(NotificationDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDocument result = service.markRead("user-1", "notification-1");

        assertThat(result.read()).isTrue();
        assertThat(result.readAt()).isEqualTo(PlatformTestFixtures.NOW);
    }

    @Test
    void markAllReadUpdatesOnlyUnreadOwnedNotifications() {
        when(mongoTemplate.updateMulti(any(Query.class), any(Update.class),
                org.mockito.ArgumentMatchers.eq(NotificationDocument.class)))
                .thenReturn(UpdateResult.acknowledged(2, 2L, null));

        assertThat(service.markAllRead("user-1")).isEqualTo(2L);
    }

    @Test
    void retentionDeletesOnlyReadNotifications() {
        when(repository.deleteByReadTrueAndCreatedAtBefore(any())).thenReturn(3L);

        assertThat(service.purgeReadBefore(Duration.ofDays(30))).isEqualTo(3L);
    }
}
