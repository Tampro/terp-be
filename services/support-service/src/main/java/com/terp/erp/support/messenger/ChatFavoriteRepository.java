package com.terp.erp.support.messenger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatFavoriteRepository extends JpaRepository<ChatFavorite, Long> {
    List<ChatFavorite> findByCompanyIdAndUserIdOrderByIdDesc(Long companyId, Long userId);

    Optional<ChatFavorite> findByCompanyIdAndUserIdAndFavoriteUserId(Long companyId, Long userId, Long favoriteUserId);
}
