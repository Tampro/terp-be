package com.terp.erp.store.shop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShopSettingRepository extends JpaRepository<ShopSetting, Long> {
    Optional<ShopSetting> findByCompanyId(Long companyId);

    boolean existsBySlugAndCompanyIdNot(String slug, Long companyId);
}
