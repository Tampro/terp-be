package com.terp.erp.iam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.terp.erp.iam.model.Permission;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    List<Permission> findByCodeIn(Collection<String> codes);

    List<Permission> findAllByOrderByModuleAscCodeAsc();
}
