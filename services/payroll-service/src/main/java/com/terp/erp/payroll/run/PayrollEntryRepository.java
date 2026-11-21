package com.terp.erp.payroll.run;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollEntryRepository extends JpaRepository<PayrollEntry, Long> {
    Page<PayrollEntry> findByRunIdOrderByEmployeeNameAsc(Long runId, Pageable pageable);

    List<PayrollEntry> findByRunId(Long runId);

    Optional<PayrollEntry> findByIdAndRunId(Long id, Long runId);

    List<PayrollEntry> findByCompanyIdAndEmployeeId(Long companyId, Long employeeId);
}
