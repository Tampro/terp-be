package com.terp.erp.crm.contract;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContractSettingRepository extends JpaRepository<ContractSetting, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ContractSetting s where s.companyId = :companyId")
    Optional<ContractSetting> lock(@Param("companyId") Long companyId);
}
