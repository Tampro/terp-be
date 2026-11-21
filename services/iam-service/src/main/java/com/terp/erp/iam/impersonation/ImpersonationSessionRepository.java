package com.terp.erp.iam.impersonation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ImpersonationSessionRepository extends JpaRepository<ImpersonationSession, Long> {
    Optional<ImpersonationSession> findByKeyHash(String keyHash);

    @Query("select s from ImpersonationSession s where :companyId is null or s.companyId = :companyId")
    Page<ImpersonationSession> search(Long companyId, Pageable pageable);
}
