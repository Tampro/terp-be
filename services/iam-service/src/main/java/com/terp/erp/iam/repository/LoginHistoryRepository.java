package com.terp.erp.iam.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.terp.erp.iam.model.LoginHistory;

import java.util.Optional;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
    /**
     * companyId null = every row (super admin only, enforced by the service); keyword on username and IP; the caller
     * escapes with !.
     */
    @Query("select h from LoginHistory h where h.deleted = false "
            + "and (:companyId is null or h.companyId = :companyId) "
            + "and (:userId is null or h.userId = :userId) "
            + "and (:userType is null or h.userType = :userType) "
            + "and (:kw = '' or lower(h.username) like :kw escape '!' or lower(h.ipAddress) like :kw escape '!')")
    Page<LoginHistory> search(Long companyId, Long userId, LoginHistory.EUserType userType, String kw,
                              Pageable pageable);

    Optional<LoginHistory> findByIdAndDeletedFalse(Long id);
}
