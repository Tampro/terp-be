package com.terp.erp.iam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.terp.erp.iam.model.Role;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    List<Role> findByCompanyIdAndDeletedFalseOrderByIdAsc(Long companyId);

    Optional<Role> findByIdAndDeletedFalse(Long id);

    List<Role> findByCompanyIdAndIdInAndDeletedFalse(Long companyId, Collection<Long> ids);

    Optional<Role> findByCompanyIdAndCodeAndDeletedFalse(Long companyId, String code);

    List<Role> findByCompanyIdAndCodeInAndDeletedFalse(Long companyId, Collection<String> codes);

    boolean existsByCompanyIdAndCodeAndDeletedFalse(Long companyId, String code);
}
