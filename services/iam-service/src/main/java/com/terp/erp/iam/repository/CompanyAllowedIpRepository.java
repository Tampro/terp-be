package com.terp.erp.iam.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.terp.erp.iam.model.CompanyAllowedIp;

import java.util.List;

public interface CompanyAllowedIpRepository extends JpaRepository<CompanyAllowedIp, Long> {
    List<CompanyAllowedIp> findByCompanyId(Long companyId);

    Page<CompanyAllowedIp> findByCompanyId(Long companyId, Pageable pageable);

    boolean existsByCompanyIdAndIpAddressAndIdNot(Long companyId, String ipAddress, Long id);
}
