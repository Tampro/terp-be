package com.terp.erp.attendance.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Long employeeId, Long leaveTypeId, int year);

    List<LeaveBalance> findByCompanyIdAndYear(Long companyId, int year);

    List<LeaveBalance> findByCompanyIdAndYearAndEmployeeIdIn(Long companyId, int year, Collection<Long> employeeIds);
}
