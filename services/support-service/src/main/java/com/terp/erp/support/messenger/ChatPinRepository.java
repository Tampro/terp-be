package com.terp.erp.support.messenger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatPinRepository extends JpaRepository<ChatPin, Long> {
    List<ChatPin> findByCompanyIdAndUserId(Long companyId, Long userId);

    Optional<ChatPin> findByCompanyIdAndUserIdAndPeerUserId(Long companyId, Long userId, Long peerUserId);
}
