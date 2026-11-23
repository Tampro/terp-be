package com.terp.erp.notification.feed;

import com.terp.erp.exception.ApiException;
import com.terp.erp.notification.common.ENotificationError;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * The caller's own feed: every query is keyed by the caller's (companyId, userId), so another user's row (same
 * company or not) answers 404 NTF_006. A super admin (no IAM user / company) has no feed: 403 NTF_007.
 */
@Service
@RequiredArgsConstructor
public class FeedService {
    public record FeedItemDto(String id, String eventType, String title, String linkType, String linkId,
                              boolean read, Instant readAt, Instant createdAt) {
        static FeedItemDto of(UserNotification n) {
            return new FeedItemDto(String.valueOf(n.getId()), n.getEventType(), n.getTitle(), n.getLinkType(),
                    n.getLinkId(), n.getReadAt() != null, n.getReadAt(), n.getCreatedAt());
        }
    }

    public record UnreadDto(long count) {
    }

    public record ReadAllDto(int marked) {
    }

    public record Me(Long companyId, Long userId) {
    }

    private final UserNotificationRepository repo;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<FeedItemDto> list(boolean unreadOnly, Pageable pageable) {
        Me me = me();
        return (unreadOnly ? repo.findByCompanyIdAndUserIdAndReadAtIsNull(me.companyId(), me.userId(), pageable)
                : repo.findByCompanyIdAndUserId(me.companyId(), me.userId(), pageable)).map(FeedItemDto::of);
    }

    @Transactional(readOnly = true)
    public UnreadDto unreadCount() {
        Me me = me();
        return new UnreadDto(repo.countByCompanyIdAndUserIdAndReadAtIsNull(me.companyId(), me.userId()));
    }

    /** Idempotent: an already read row keeps its first read time. */
    @Transactional
    public FeedItemDto markRead(Long id) {
        Me me = me();
        UserNotification n = repo.findByIdAndCompanyIdAndUserId(id, me.companyId(), me.userId())
                .orElseThrow(() -> ApiException.notFound(ENotificationError.NTF_006));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now(clock));
        }
        return FeedItemDto.of(n);
    }

    @Transactional
    public ReadAllDto markAllRead() {
        Me me = me();
        return new ReadAllDto(repo.markAllRead(me.companyId(), me.userId(), Instant.now(clock)));
    }

    static Me me() {
        ErpPrincipal p = CurrentUser.get();
        if (p.userId() == null || p.companyId() == null) {
            throw ApiException.forbidden(ENotificationError.NTF_007);
        }
        return new Me(p.companyId(), p.userId());
    }
}
