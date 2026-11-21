package com.terp.erp.employee.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {
    List<EmployeeDocument> findByEmployeeIdOrderByIdAsc(Long employeeId);

    Optional<EmployeeDocument> findByIdAndEmployeeId(Long id, Long employeeId);

    boolean existsByDocumentTypeId(Long documentTypeId);
}
