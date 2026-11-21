package com.terp.erp.meeting.zoom;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ZoomSettingRepository extends JpaRepository<ZoomSetting, Long> {
    Optional<ZoomSetting> findByCompanyId(Long companyId);
}
