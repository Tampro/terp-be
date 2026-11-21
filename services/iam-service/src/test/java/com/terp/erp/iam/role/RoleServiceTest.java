package com.terp.erp.iam.role;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EDefaultRole;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.constant.IamPermissions;
import com.terp.erp.iam.model.Permission;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.repository.PermissionRepository;
import com.terp.erp.iam.repository.RoleRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.role.RoleDtos.RoleForm;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import com.terp.erp.security.ErpPrincipal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleServiceTest {
    private RoleRepository roles;
    private PermissionRepository permissions;
    private RoleService service;

    @BeforeEach
    void setUp() {
        roles = mock(RoleRepository.class);
        permissions = mock(PermissionRepository.class);
        service = new RoleService(roles, permissions, mock(UserAccountRepository.class),
                mock(LocalAuthorityResolver.class));
        when(roles.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });
        when(permissions.findByCodeIn(anyCollection())).thenAnswer(inv -> {
            Collection<String> codes = inv.getArgument(0);
            return codes.stream().filter(c -> c.startsWith("IAM_")).map(RoleServiceTest::perm).toList();
        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cannotGrantAPermissionTheCallerDoesNotHold() {
        login(1L, 10L, IamPermissions.ROLE_CREATE, IamPermissions.USER_VIEW);
        RoleForm form = new RoleForm("TEAM_LEAD", "Team lead", null, Set.of(IamPermissions.ROLE_DELETE));

        assertThatThrownBy(() -> service.create(null, form))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getErrorCode()).isEqualTo(EIamError.IAM_015);
                    assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void createdRoleBelongsToTheCallersCompanyNotTheRequestedOne() {
        login(1L, 10L, IamPermissions.ROLE_CREATE, IamPermissions.USER_VIEW);
        var dto = service.create(99L, new RoleForm("TEAM_LEAD", "Team lead", null, Set.of(IamPermissions.USER_VIEW)));
        assertThat(dto.companyId()).isEqualTo("10");
    }

    @Test
    void unknownPermissionCodeIsRejected() {
        login(1L, 10L, IamPermissions.ROLE_CREATE, "NOPE_X");
        assertThatThrownBy(() -> service.create(null, new RoleForm("R", "R", null, Set.of("NOPE_X"))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.IAM_008);
    }

    @Test
    void roleOfAnotherCompanyIsNotFound() {
        login(1L, 10L, IamPermissions.ROLE_VIEW);
        Role other = role(5L, 11L, "HR", false, Set.of());
        when(roles.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.get(5L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void companyAdminRoleCannotLoseIamPermissions() {
        login(1L, 10L, IamPermissions.ALL.toArray(String[]::new));
        Role admin = role(7L, 10L, EDefaultRole.COMPANY_ADMIN.name(), true, IamPermissions.ALL);
        when(roles.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(admin));

        RoleForm reduced = new RoleForm(EDefaultRole.COMPANY_ADMIN.name(), "Admin", null,
                Set.of(IamPermissions.ROLE_VIEW));
        assertThatThrownBy(() -> service.update(7L, reduced))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.IAM_012);
    }

    @Test
    void systemRoleCannotBeRenamedOrDeleted() {
        login(1L, 10L, IamPermissions.ALL.toArray(String[]::new));
        Role hr = role(8L, 10L, "HR", true, Set.of());
        when(roles.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(hr));

        assertThatThrownBy(() -> service.update(8L, new RoleForm("HR2", "HR", null, Set.of())))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.IAM_006);
        assertThatThrownBy(() -> service.delete(8L))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EIamError.IAM_006);
    }

    private static Permission perm(String code) {
        Permission p = new Permission();
        p.setCode(code);
        p.setModule("IAM");
        return p;
    }

    private static Role role(Long id, Long companyId, String code, boolean system, Set<String> perms) {
        Role r = new Role();
        r.setId(id);
        r.setCompanyId(companyId);
        r.setCode(code);
        r.setName(code);
        r.setSystemRole(system);
        perms.forEach(c -> r.getPermissions().add(perm(c)));
        return r;
    }

    static void login(Long userId, Long companyId, String... perms) {
        ErpPrincipal p = new ErpPrincipal(userId, "kc-" + userId, "user" + userId, companyId, false, Set.of(),
                Set.of(perms));
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", p.keycloakId()));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), p.keycloakId());
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
