package com.terp.erp.iam.email;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyEmailNotificationRepository extends JpaRepository<CompanyEmailNotification, Long> {
    List<CompanyEmailNotification> findByCompanyId(Long companyId);

    Optional<CompanyEmailNotification> findByCompanyIdAndTemplateCode(Long companyId, String templateCode);
}
