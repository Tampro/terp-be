package com.terp.erp.attendance.setting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttendanceSettingRepository extends JpaRepository<AttendanceSetting, Long> {
    Optional<AttendanceSetting> findByCompanyId(Long companyId);
}
