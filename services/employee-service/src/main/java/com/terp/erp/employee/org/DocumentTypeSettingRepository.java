package com.terp.erp.employee.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeSettingRepository extends JpaRepository<DocumentTypeSetting, Long> {
    List<DocumentTypeSetting> findByCompanyId(Long companyId);

    Optional<DocumentTypeSetting> findByCompanyIdAndDocumentTypeId(Long companyId, Long documentTypeId);
}
