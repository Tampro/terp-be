package com.terp.erp.iam.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.constant.EDefaultRole;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.model.Permission;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.RoleRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import com.terp.erp.iam.user.UserDtos.CreatedUserDto;
import com.terp.erp.iam.user.UserDtos.UserCreateForm;
import com.terp.erp.iam.user.UserDtos.UserDto;
import com.terp.erp.iam.user.UserDtos.UserProfileForm;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    static final String EVENT_USER_CREATED = "iam.user.created";
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final KeycloakClient keycloak;
    private final LocalAuthorityResolver authorityCache;
    private final com.terp.erp.iam.repository.CompanyRepository companies;
    private final com.terp.erp.iam.saas.SubscriptionService subscriptions;
    private final com.terp.erp.iam.email.EmailNotifier notifier;
    private final OutboxPublisher outbox;
    private final com.terp.erp.iam.saas.PlanSeatEnforcer seats;
    private final org.springframework.beans.factory.ObjectProvider<KeycloakOrphanJob> orphans;

    @Transactional(readOnly = true)
    public Page<UserDto> search(Long requestedCompanyId, String keyword, Pageable pageable) {
        return search(requestedCompanyId, keyword, null, pageable);
    }

    /** roleId: only accounts holding this role (Users list Role filter, docs/analysis/06 §10.1.1). */
    @Transactional(readOnly = true)
    public Page<UserDto> search(Long requestedCompanyId, String keyword, Long roleId, Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        return users.search(companyId, keyword == null ? "" : keyword.trim(), roleId, pageable).map(UserDto::of);
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return UserDto.of(require(id));
    }

    /**
     * Creates the Keycloak user first (with a temporary password), then the ERP row. If the transaction rolls back
     * after Keycloak succeeded, the Keycloak user is deleted again ({@link #deleteInKeycloakOnRollback}), so the
     * administrator can retry with the same username.
     */
    @Transactional
    public CreatedUserDto create(Long requestedCompanyId, UserCreateForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EIamError.IAM_003);
        return createInCompany(companyId, form);
    }

    @Transactional
    public CreatedUserDto createInCompany(Long companyId, UserCreateForm form) {
        return createInCompany(companyId, form, false);
    }

    /** @param firstAdminOfNewCompany sends COMPANY_CREATE instead of USER_CREATE */
    @Transactional
    public CreatedUserDto createInCompany(Long companyId, UserCreateForm form, boolean firstAdminOfNewCompany) {
        if (users.existsByUsernameAndDeletedFalse(form.username())) {
            throw ApiException.badRequest(EIamError.IAM_010);
        }
        var company = companies.findByIdAndDeletedFalse(companyId)
                .orElseThrow(() -> ApiException.notFound(EIamError.IAM_001));
        subscriptions.assertCanAddUser(company);
        Set<Long> roleIds = new HashSet<>(form.roleIds());
        if (form.roleCodes() != null && !form.roleCodes().isEmpty()) {
            List<Role> byCode = roles.findByCompanyIdAndCodeInAndDeletedFalse(companyId, form.roleCodes());
            if (byCode.size() != form.roleCodes().size()) {
                throw ApiException.badRequest(EIamError.IAM_011);
            }
            byCode.forEach(r -> roleIds.add(r.getId()));
        }
        Set<Role> assigned = resolveAssignable(companyId, roleIds);
        String temporaryPassword = form.password() != null ? form.password() : generatePassword();
        String keycloakId = keycloak.createUser(form.username(), form.email(), form.fullName(), temporaryPassword);
        deleteInKeycloakOnRollback(keycloakId);

        UserAccount u = new UserAccount();
        u.setKeycloakId(keycloakId);
        u.setCompanyId(companyId);
        u.setUsername(form.username());
        u.setEmail(form.email());
        u.setFullName(form.fullName());
        u.setStatus(EUserStatus.ACTIVE);
        u.setRoles(assigned);
        UserAccount saved = users.save(u);
        publishCreated(saved);
        // Sent after commit; the temporary password only travels in this email and the response.
        if (firstAdminOfNewCompany) {
            notifier.companyCreated(saved, company, temporaryPassword);
        } else {
            notifier.userCreated(saved, company, temporaryPassword);
        }
        return new CreatedUserDto(UserDto.of(saved), temporaryPassword);
    }

    /**
     * First administrator of a self-registered company (Sign up, docs/analysis/06 §2.6): the password is the one the
     * person chose, so it is permanent and no account email with a temporary password is sent.
     */
    @Transactional
    public UserAccount createSelfRegisteredAdmin(Long companyId, String email, String fullName, String password) {
        if (users.existsByUsernameAndDeletedFalse(email)) {
            throw ApiException.badRequest(EIamError.IAM_010);
        }
        Role admin = roles.findByCompanyIdAndCodeAndDeletedFalse(companyId,
                com.terp.erp.iam.constant.EDefaultRole.COMPANY_ADMIN.name()).orElseThrow();
        String keycloakId = keycloak.createUser(email, email, fullName, password, false);
        deleteInKeycloakOnRollback(keycloakId);
        UserAccount u = new UserAccount();
        u.setKeycloakId(keycloakId);
        u.setCompanyId(companyId);
        u.setUsername(email);
        u.setEmail(email);
        u.setFullName(fullName);
        u.setStatus(EUserStatus.ACTIVE);
        u.setRoles(new HashSet<>(Set.of(admin)));
        UserAccount saved = users.save(u);
        publishCreated(saved);
        return saved;
    }

    /**
     * NEW_USER webhook / chat line. In the same transaction as the row: a failed DB write publishes nothing (the
     * Keycloak user is deleted again on rollback). No email address, username (often the
     * email) or password in the payload.
     */
    /**
     * Compensation for a user created in Keycloak inside a transaction that then rolls back (a DB constraint, a
     * failure at commit, or a later step such as company sign-up). Runs after the rollback, on the same thread. If
     * Keycloak is down too, the id goes to keycloak_orphan and {@link KeycloakOrphanJob} deletes it later; meanwhile
     * the orphan cannot use ERP (AUTH_006).
     */
    private void recordOrphan(String keycloakId) {
        KeycloakOrphanJob job = orphans.getIfAvailable();
        if (job == null) {
            return;
        }
        try {
            job.record(keycloakId);
        } catch (RuntimeException ex) {
            log.error("Keycloak orphan {} not queued: {} — remove it by hand", keycloakId, ex.getClass().getSimpleName());
        }
    }

    private void deleteInKeycloakOnRollback(String keycloakId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    keycloak.deleteUser(keycloakId);
                    log.info("User creation rolled back: Keycloak user {} deleted", keycloakId);
                } catch (RuntimeException ex) {
                    log.error("User creation rolled back but Keycloak user {} could not be deleted: {}; queued for "
                            + "retry", keycloakId, ex.getClass().getSimpleName());
                    recordOrphan(keycloakId);
                }
            }
        });
    }

    private void publishCreated(UserAccount u) {
        outbox.publish(u.getCompanyId(), EVENT_USER_CREATED, "USER", u.getId(), Payload.of(
                "userId", u.getId(), "title", u.getFullName(), "name", u.getFullName()));
        // The platform's NEW_USER webhook (reference "super admin" New User). TODO(assumption): every new account
        // of every company, not only the ones the super admin creates.
        com.terp.erp.iam.platform.PlatformEvents.publish(outbox, com.terp.erp.iam.platform.PlatformEvents.USER_CREATED,
                "USER", u.getId(), Payload.of("userId", u.getId(), "title", u.getFullName(), "name",
                        u.getFullName(), "companyId", u.getCompanyId()));
    }

    @Transactional
    public UserDto replaceRoles(Long id, Set<Long> roleIds) {
        UserAccount u = require(id);
        assertNotSelf(u);
        u.setRoles(resolveAssignable(u.getCompanyId(), roleIds));
        authorityCache.evictAll();
        return UserDto.of(u);
    }

    /** LOCKED disables the Keycloak user and ends its sessions; the 30 s authority cache is cleared too. */
    @Transactional
    public UserDto changeStatus(Long id, EUserStatus status) {
        UserAccount u = require(id);
        assertNotSelf(u);
        if (status == EUserStatus.ACTIVE && u.getStatus() != EUserStatus.ACTIVE && u.getCompanyId() != null) {
            // Within the plan's active-account limit, so a downgrade's locks cannot simply be undone (SAAS_011).
            companies.findByIdAndDeletedFalse(u.getCompanyId()).ifPresent(seats::assertCanActivate);
        }
        u.setStatus(status);
        keycloak.setEnabled(u.getKeycloakId(), status == EUserStatus.ACTIVE);
        authorityCache.evictAll();
        return UserDto.of(u);
    }

    /**
     * Edit user: full name and email, in Keycloak first (a duplicate email is IAM_017), then here. Not one's own
     * account (IAM_013).
     */
    @Transactional
    public UserDto updateProfile(Long id, UserProfileForm form) {
        UserAccount u = require(id);
        assertNotSelf(u);
        String email = form.email().trim();
        String name = form.fullName().trim();
        keycloak.updateProfile(u.getKeycloakId(), email, name);
        u.setEmail(email);
        u.setFullName(name);
        return UserDto.of(u);
    }

    /**
     * Delete user: soft-deleted here and removed from Keycloak (sessions end, the username is free again). Not one's
     * own account (IAM_013), nor the company's last active administrator (IAM_018).
     */
    @Transactional
    public void delete(Long id) {
        UserAccount u = require(id);
        assertNotSelf(u);
        boolean admin = u.getRoles().stream().anyMatch(r -> EDefaultRole.COMPANY_ADMIN.name().equals(r.getCode()));
        if (admin && u.getStatus() == EUserStatus.ACTIVE && users.findByCompanyAndRole(u.getCompanyId(),
                EDefaultRole.COMPANY_ADMIN.name()).stream().filter(x -> x.getStatus() == EUserStatus.ACTIVE
                && !x.getId().equals(u.getId())).findAny().isEmpty()) {
            throw ApiException.badRequest(EIamError.IAM_018);
        }
        u.setDeleted(true);
        u.setStatus(EUserStatus.LOCKED);
        keycloak.deleteUser(u.getKeycloakId());
        authorityCache.evictAll();
    }

    /** Administrator sets a permanent password for an account of their company ("Change Password"). */
    @Transactional
    public UserDto changePassword(Long id, String newPassword) {
        UserAccount u = require(id);
        assertNotSelf(u);
        keycloak.setPermanentPassword(u.getKeycloakId(), newPassword);
        return UserDto.of(u);
    }

    private UserAccount require(Long id) {
        UserAccount u = users.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EIamError.IAM_009));
        TenantGuard.assertSameCompany(u.getCompanyId(), EIamError.IAM_009);
        return u;
    }

    private static void assertNotSelf(UserAccount target) {
        if (Objects.equals(CurrentUser.get().userId(), target.getId())) {
            throw ApiException.badRequest(EIamError.IAM_013);
        }
    }

    /**
     * Roles must be of the target company, and a company user may only hand out roles whose permissions they hold
     * themselves — otherwise IAM_USER_UPDATE alone would let HR make anyone COMPANY_ADMIN.
     */
    private Set<Role> resolveAssignable(Long companyId, Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Role> found = roles.findByCompanyIdAndIdInAndDeletedFalse(companyId, roleIds);
        if (found.size() != roleIds.size()) {
            throw ApiException.badRequest(EIamError.IAM_011);
        }
        ErpPrincipal caller = CurrentUser.get();
        if (!caller.superAdmin()) {
            Set<String> needed = found.stream().flatMap(r -> r.getPermissions().stream())
                    .map(Permission::getCode).collect(Collectors.toSet());
            needed.removeAll(caller.permissions());
            if (!needed.isEmpty()) {
                throw ApiException.forbidden(EIamError.IAM_015, String.join(", ", needed));
            }
        }
        return new HashSet<>(found);
    }

    /** 16 chars with at least one of each class, so it always meets the realm policy (deploy/keycloak). */
    static String generatePassword() {
        String[] classes = {"ABCDEFGHJKLMNPQRSTUVWXYZ", "abcdefghijkmnopqrstuvwxyz", "23456789", "!@#$%"};
        List<Character> chars = new java.util.ArrayList<>(16);
        for (String c : classes) {
            chars.add(c.charAt(RANDOM.nextInt(c.length())));
        }
        while (chars.size() < 16) {
            chars.add(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        java.util.Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder(16);
        chars.forEach(sb::append);
        return sb.toString();
    }
}
