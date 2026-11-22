package com.terp.erp.notification.delivery;

import com.terp.erp.exception.ApiException;
import com.terp.erp.notification.TestLogin;
import com.terp.erp.notification.common.ENotificationError;
import com.terp.erp.notification.delivery.Delivery.EChannel;
import com.terp.erp.notification.delivery.Delivery.EStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryServiceTest {
    private final DeliveryRepository repo = mock(DeliveryRepository.class);
    private final DeliveryDispatcher dispatcher = mock(DeliveryDispatcher.class);
    private final DeliveryService service = new DeliveryService(repo, dispatcher,
            Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Delivery row(long id, long companyId, EStatus status) {
        Delivery d = new Delivery();
        d.setId(id);
        d.setCompanyId(companyId);
        d.setEventId("e-1");
        d.setEventType("support.ticket.created");
        d.setChannel(EChannel.WEBHOOK);
        d.setStatus(status);
        when(repo.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    private static void assertCode(Runnable r, ENotificationError code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    void anotherCompanysDeliveryIs404ForReadAndRetry() {
        TestLogin.as(7L, "NOTIFICATION_LOG_VIEW", "NOTIFICATION_LOG_MANAGE");
        row(1, 8L, EStatus.FAILED);

        assertCode(() -> service.get(1L), ENotificationError.NTF_002);
        assertCode(() -> service.retry(1L), ENotificationError.NTF_002);
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void aCompanyUserListsOnlyTheirOwnCompany() {
        TestLogin.as(7L, "NOTIFICATION_LOG_VIEW");
        Page<Delivery> empty = new PageImpl<>(List.of());
        when(repo.search(eq(7L), isNull(), isNull(), isNull(), isNull(), isNull(), any())).thenReturn(empty);

        service.list(8L, null, null, " ", null, null, Pageable.unpaged());

        verify(repo).search(eq(7L), isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void aSuperAdminMustNameTheCompany() {
        TestLogin.superAdmin();
        assertCode(() -> service.list(null, null, null, null, null, null, Pageable.unpaged()),
                ENotificationError.NTF_001);
    }

    @Test
    void onlyAFailedDeliveryIsRetried() {
        TestLogin.as(7L, "NOTIFICATION_LOG_MANAGE");
        row(2, 7L, EStatus.SENT);
        assertCode(() -> service.retry(2L), ENotificationError.NTF_003);

        Delivery failed = row(3, 7L, EStatus.FAILED);
        failed.setAttempts(5);
        failed.setLastErrorCode("HTTP_500");
        var dto = service.retry(3L);

        assertThat(dto.status()).isEqualTo(EStatus.PENDING);
        assertThat(dto.attempts()).isZero();
        verify(dispatcher).dispatch(List.of(3L));
    }
}
