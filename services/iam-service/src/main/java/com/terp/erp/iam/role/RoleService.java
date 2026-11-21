package com.terp.erp.iam.role;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EDefaultRole;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.model.Permission;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.repository.PermissionRepository;
import com.terp.erp.iam.repository.RoleRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.role.RoleDtos.PermissionDto;
import com.terp.erp.iam.role.RoleDtos.RoleDto;
import com.terp.erp.iam.role.RoleDtos.RoleForm;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserAccountRepository users;
    private final LocalAuthorityResolver authorityCache;

    @Transactional(readOnly = true)
    public List<PermissionDto> listPermissions() {
        return permissions.findAllByOrderByModuleAscCodeAsc().stream().map(PermissionDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleDto> list(Long requestedCompanyId) {
        return list(requestedCompanyId, null);
    }

    /** Roles page search (docs/analysis/06 §10.1.2): keyword on name or code, case-insensitive. */
    @Transactional(readOnly = true)
    public List<RoleDto> list(Long requestedCompanyId, String keyword) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT);
        return roles.findByCompanyIdAndDeletedFalseOrderByIdAsc(companyId).stream()
                .filter(r -> kw.isEmpty() || r.getName().toLowerCase(java.util.Locale.ROOT).contains(kw)
                        || r.getCode().toLowerCase(java.util.Locale.ROOT).contains(kw))
                .map(RoleDto::of).toList();
    }

    @Transactional(readOnly = true)
    public RoleDto get(Long id) {
        return RoleDto.of(require(id));
    }

    @Transactional
    public RoleDto create(Long requestedCompanyId, RoleForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        if (roles.existsByCompanyIdAndCodeAndDeletedFalse(companyId, form.code())) {
            throw ApiException.badRequest(EIamError.IAM_005);
        }
        Role role = new Role();
        role.setCompanyId(companyId);
        role.setCode(form.code());
        role.setName(form.name());
        role.setDescription(form.description());
        role.setPermissions(resolveGrantable(form.permissions()));
        return RoleDto.of(roles.save(role));
    }

    /** Full replacement: description and permissions are set exactly as sent. */
    @Transactional
    public RoleDto update(Long id, RoleForm form) {
        Role role = require(id);
        if (role.isSystemRole()) {
            if (!role.getCode().equals(form.code())) {
                throw ApiException.badRequest(EIamError.IAM_006);
            }
        } else if (!role.getCode().equals(form.code())
                && roles.existsByCompanyIdAndCodeAndDeletedFalse(role.getCompanyId(), form.code())) {
            throw ApiException.badRequest(EIamError.IAM_005);
        }
        Set<Permission> next = resolveGrantable(form.permissions());
        // Keeps a company from locking itself out of role management.
        if (EDefaultRole.COMPANY_ADMIN.name().equals(role.getCode())
                && !codes(next).containsAll(EDefaultRole.COMPANY_ADMIN.getPermissions())) {
            throw ApiException.badRequest(EIamError.IAM_012);
        }
        role.setCode(form.code());
        role.setName(form.name());
        role.setDescription(form.description());
        role.setPermissions(next);
        authorityCache.evictAll();
        return RoleDto.of(role);
    }

    @Transactional
    public void delete(Long id) {
        Role role = require(id);
        if (role.isSystemRole()) {
            throw ApiException.badRequest(EIamError.IAM_006);
        }
        if (users.existsActiveUserWithRole(id)) {
            throw ApiException.badRequest(EIamError.IAM_007);
        }
        role.setDeleted(true);
        authorityCache.evictAll();
    }

    /** Seeds the default roles of a new company. */
    @Transactional
    public void createDefaultRoles(Long companyId) {
        for (EDefaultRole d : EDefaultRole.values()) {
            Role role = new Role();
            role.setCompanyId(companyId);
            role.setCode(d.name());
            role.setName(d.getDisplayName());
            role.setSystemRole(true);
            role.setPermissions(resolvePermissions(d.getPermissions()));
            roles.save(role);
        }
    }

    private Role require(Long id) {
        Role role = roles.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EIamError.IAM_004));
        TenantGuard.assertSameCompany(role.getCompanyId(), EIamError.IAM_004);
        return role;
    }

    /** Form input from a caller: besides existing, every code must be one the caller holds (no self-escalation). */
    private Set<Permission> resolveGrantable(Set<String> codes) {
        ErpPrincipal caller = CurrentUser.get();
        if (!caller.superAdmin() && !caller.permissions().containsAll(codes)) {
            Set<String> beyond = new TreeSet<>(codes);
            beyond.removeAll(caller.permissions());
            throw ApiException.forbidden(EIamError.IAM_015, String.join(", ", beyond));
        }
        return resolvePermissions(codes);
    }

    private Set<Permission> resolvePermissions(Set<String> codes) {
        if (codes.isEmpty()) {
            return new HashSet<>();
        }
        List<Permission> found = permissions.findByCodeIn(codes);
        if (found.size() != codes.size()) {
            Set<String> unknown = new TreeSet<>(codes);
            unknown.removeAll(codes(found));
            throw ApiException.badRequest(EIamError.IAM_008, String.join(", ", unknown));
        }
        return new HashSet<>(found);
    }

    private static Set<String> codes(java.util.Collection<Permission> ps) {
        return ps.stream().map(Permission::getCode).collect(Collectors.toSet());
    }
}
