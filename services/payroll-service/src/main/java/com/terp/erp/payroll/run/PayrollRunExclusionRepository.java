package com.terp.erp.payroll.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollRunExclusionRepository extends JpaRepository<PayrollRunExclusion, Long> {
    List<PayrollRunExclusion> findByRunId(Long runId);

    Optional<PayrollRunExclusion> findByRunIdAndEmployeeId(Long runId, Long employeeId);
}
