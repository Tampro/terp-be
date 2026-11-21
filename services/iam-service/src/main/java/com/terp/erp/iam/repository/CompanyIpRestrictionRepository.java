package com.terp.erp.iam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.terp.erp.iam.model.CompanyIpRestriction;

/** The IP restriction switch of each company. */
public interface CompanyIpRestrictionRepository extends JpaRepository<CompanyIpRestriction, Long> {
}
