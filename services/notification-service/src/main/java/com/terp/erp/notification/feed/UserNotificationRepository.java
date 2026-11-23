package com.terp.erp.notification.feed;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    Page<UserNotification> findByCompanyIdAndUserId(Long companyId, Long userId, Pageable pageable);

    Page<UserNotification> findByCompanyIdAndUserIdAndReadAtIsNull(Long companyId, Long userId, Pageable pageable);

    long countByCompanyIdAndUserIdAndReadAtIsNull(Long companyId, Long userId);

    Optional<UserNotification> findByIdAndCompanyIdAndUserId(Long id, Long companyId, Long userId);

    @Modifying
    @Query("update UserNotification n set n.readAt = :now where n.companyId = :companyId and n.userId = :userId "
            + "and n.readAt is null")
    int markAllRead(@Param("companyId") Long companyId, @Param("userId") Long userId, @Param("now") Instant now);
}
