package com.terp.erp.iam.company;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.iam.company.CompanyDtos.CompanyCreateForm;
import com.terp.erp.iam.company.CompanyDtos.CompanyCreatedDto;
import com.terp.erp.iam.company.CompanyDtos.CompanyDto;
import com.terp.erp.iam.company.CompanyDtos.CompanyInfoDto;
import com.terp.erp.iam.company.CompanyDtos.CompanyUpdateForm;
import com.terp.erp.iam.company.CompanyDtos.StatusCountsDto;
import com.terp.erp.iam.constant.ECompanyStatus;
import com.terp.erp.iam.constant.EDefaultRole;
import com.terp.erp.iam.constant.EIamError;
import com.terp.erp.iam.constant.EUserStatus;
import com.terp.erp.iam.keycloak.KeycloakClient;
import com.terp.erp.iam.model.Company;
import com.terp.erp.iam.model.Plan;
import com.terp.erp.iam.model.Role;
import com.terp.erp.iam.model.UserAccount;
import com.terp.erp.iam.repository.CompanyRepository;
import com.terp.erp.iam.repository.PlanRepository;
import com.terp.erp.iam.repository.RoleRepository;
import com.terp.erp.iam.repository.UserAccountRepository;
import com.terp.erp.iam.role.RoleService;
import com.terp.erp.iam.saas.EBillingCycle;
import com.terp.erp.iam.saas.OrderService;
import com.terp.erp.iam.saas.SaasDtos.OrderDto;
import com.terp.erp.iam.saas.SubscriptionService;
import com.terp.erp.iam.security.LocalAuthorityResolver;
import com.terp.erp.iam.user.UserDtos.CreatedUserDto;
import com.terp.erp.iam.user.UserDtos.UserCreateForm;
import com.terp.erp.iam.user.UserDtos.UserDto;
import com.terp.erp.iam.user.UserService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Tenant lifecycle and the super admin companies list (docs/analysis/06 §3.2.1). Super admin only. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService {
    private static final Instant MIN = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX = Instant.parse("9999-01-01T00:00:00Z");

    private final CompanyRepository companies;
    private final RoleRepository roles;
    private final PlanRepository plans;
    private final UserAccountRepository users;
    private final RoleService roleService;
    private final UserService userService;
    private final OrderService orderService;
    private final SubscriptionService subscriptions;
    private final KeycloakClient keycloak;
    private final LocalAuthorityResolver authorityCache;
    private final com.terp.erp.iam.referral.ReferralService referral;
    private final com.terp.erp.iam.auth.EmailVerificationService verification;
    private final com.terp.erp.outbox.OutboxPublisher outbox;

    /**
     * @param from first created day included (UTC); null = no lower bound
     * @param to   last created day included (UTC); null = no upper bound
     */
    @Transactional(readOnly = true)
    public Page<CompanyDto> search(String keyword, LocalDate from, LocalDate to, ECompanyStatus status,
                                   Pageable pageable) {
        Map<Long, Plan> live = livePlans();
        return companies.search(kw(keyword), start(from), end(to), status, pageable)
                .map(c -> CompanyDto.of(c, subscriptions.effective(c, live)));
    }

    @Transactional(readOnly = true)
    public StatusCountsDto countByStatus(String keyword, LocalDate from, LocalDate to) {
        Map<ECompanyStatus, Long> counts = companies.countByStatus(kw(keyword), start(from), end(to)).stream()
                .collect(Collectors.toMap(r -> (ECompanyStatus) r[0], r -> (Long) r[1]));
        long active = counts.getOrDefault(ECompanyStatus.ACTIVE, 0L);
        long inactive = counts.getOrDefault(ECompanyStatus.SUSPENDED, 0L);
        return new StatusCountsDto(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    public CompanyInfoDto info(Long id) {
        Company c = require(id);
        var e = subscriptions.effective(c);
        var admins = users.findByCompanyAndRole(c.getId(), EDefaultRole.COMPANY_ADMIN.name()).stream()
                .map(UserDto::of).toList();
        return new CompanyInfoDto(CompanyDto.of(c, e), users.countByCompanyIdAndDeletedFalse(c.getId()),
                e.plan() == null ? null : e.plan().getMaxUsers(), admins);
    }

    @Transactional(readOnly = true)
    public CompanyDto get(Long id) {
        Company c = require(id);
        return CompanyDto.of(c, subscriptions.effective(c));
    }

    /** Company + default roles + default plan + first administrator (the Keycloak user is created last). */
    @Transactional
    public CompanyCreatedDto create(CompanyCreateForm form) {
        if (companies.existsByCodeAndDeletedFalse(form.code())) {
            throw ApiException.badRequest(EIamError.IAM_002);
        }
        Company c = new Company();
        c.setCode(form.code());
        c.setName(form.name());
        c.setEmail(form.email());
        c.setStatus(Boolean.FALSE.equals(form.enableLogin()) ? ECompanyStatus.SUSPENDED : ECompanyStatus.ACTIVE);
        subscriptions.assignDefault(c);
        c.setReferredByCompanyId(referral.referrerOf(form.referralCode()));
        c = companies.save(c);
        c.setReferralCode(com.terp.erp.iam.referral.ReferralService.codeFor(c.getId()));
        roleService.createDefaultRoles(c.getId());
        Role admin = roles.findByCompanyIdAndCodeAndDeletedFalse(c.getId(), EDefaultRole.COMPANY_ADMIN.name())
                .orElseThrow();
        CreatedUserDto adminUser = userService.createInCompany(c.getId(), new UserCreateForm(
                form.admin().username(), form.admin().email(), form.admin().fullName(), Set.of(admin.getId())), true);
        // Created by the super admin: the verification email is sent, but login is not held back (PO 2026-11-21).
        if (verification.enabled()) {
            verification.issue(c);
        }
        publishCreated(c, false);
        return new CompanyCreatedDto(CompanyDto.of(c, subscriptions.effective(c)), adminUser);
    }

    /**
     * Sign up (docs/analysis/06 §2.6): a new company whose first administrator is the person registering, with the
     * password they chose. The code is derived from the name. With Email Verification on, nobody of the company can
     * log in until the link sent to the email is opened (AUTH_016).
     */
    @Transactional
    public Company register(String companyName, String email, String fullName, String password, String referralCode) {
        Company c = new Company();
        c.setCode(freeCode(companyName));
        c.setName(companyName);
        c.setEmail(email);
        c.setStatus(ECompanyStatus.ACTIVE);
        subscriptions.assignDefault(c);
        c.setReferredByCompanyId(referral.referrerOf(referralCode));
        boolean verify = verification.enabled();
        c.setEmailVerifyRequired(verify);
        c.setEmailVerifiedAt(verify ? null : Instant.now());
        c = companies.save(c);
        c.setReferralCode(com.terp.erp.iam.referral.ReferralService.codeFor(c.getId()));
        roleService.createDefaultRoles(c.getId());
        userService.createSelfRegisteredAdmin(c.getId(), email, fullName, password);
        if (verify) {
            verification.issue(c);
        }
        publishCreated(c, true);
        log.info("Company {} self-registered", c.getCode());
        return c;
    }

    /** NEW_COMPANY platform webhook. No email address of the company in the payload. */
    private void publishCreated(Company c, boolean selfRegistered) {
        com.terp.erp.iam.platform.PlatformEvents.publish(outbox,
                com.terp.erp.iam.platform.PlatformEvents.COMPANY_CREATED, "COMPANY", c.getId(),
                com.terp.erp.outbox.Payload.of("companyId", c.getId(), "title", c.getName(), "name", c.getName(),
                        "code", c.getCode(), "selfRegistered", selfRegistered,
                        "status", c.getStatus() == null ? null : c.getStatus().name()));
    }

    /** Letters and digits of the name (accents dropped), at most 12, plus 4 random characters until unused. */
    String freeCode(String name) {
        String base = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        base = base.isEmpty() ? "CO" : base.substring(0, Math.min(12, base.length()));
        String code;
        do {
            code = base + "_" + com.terp.erp.iam.auth.AccountTokens.newToken()
                    .replaceAll("[^A-Za-z0-9]", "").toUpperCase(java.util.Locale.ROOT).substring(0, 4);
        } while (companies.existsByCodeAndDeletedFalse(code));
        return code;
    }

    /** Full replacement of the editable profile (the code is the tenant key and stays). */
    @Transactional
    public CompanyDto update(Long id, CompanyUpdateForm form) {
        Company c = require(id);
        c.setName(form.name());
        c.setEmail(form.email());
        return CompanyDto.of(c, subscriptions.effective(c));
    }

    /** "Disable Login": SUSPENDED rejects logins and every token of the company within the 30 s authority cache. */
    @Transactional
    public CompanyDto changeStatus(Long id, ECompanyStatus status) {
        Company c = require(id);
        c.setStatus(status);
        authorityCache.evictAll();
        return CompanyDto.of(c, subscriptions.effective(c));
    }

    @Transactional
    public OrderDto upgradePlan(Long id, Long planId, EBillingCycle cycle) {
        require(id);
        return orderService.upgradeByAdmin(id, planId, cycle);
    }

    /**
     * "Reset Password" (PO, 2026-11-18: the super admin types the new password): sets it as permanent for an account
     * of the company — by default its first active COMPANY_ADMIN — and ends every session of that account. The
     * password is neither stored nor logged.
     */
    @Transactional(readOnly = true)
    public UserDto resetPassword(Long id, Long userId, String newPassword) {
        Company c = require(id);
        UserAccount target = userId != null
                ? users.findByIdAndDeletedFalse(userId).filter(u -> u.getCompanyId().equals(c.getId()))
                        .orElseThrow(() -> ApiException.notFound(EIamError.IAM_009))
                : users.findByCompanyAndRole(c.getId(), EDefaultRole.COMPANY_ADMIN.name()).stream()
                        .filter(u -> u.getStatus() == EUserStatus.ACTIVE)
                        .findFirst().orElseThrow(() -> ApiException.badRequest(EIamError.IAM_016));
        keycloak.setPermanentPassword(target.getKeycloakId(), newPassword);
        keycloak.logoutSessions(target.getKeycloakId());
        log.info("Super admin reset the password of user {} in company {}", target.getId(), c.getCode());
        return UserDto.of(target);
    }

    /**
     * Soft delete (PO, 2026-11-18): rows stay for history, open orders are cancelled, and the code becomes free for a
     * new company. Not reversible through the API. Every account is removed from Keycloak and soft-deleted here
     * (PO, 2026-11-21): usernames are unique across the platform, so a disabled leftover would keep the employee's
     * email from being used by another company (IAM_010).
     */
    @Transactional
    public void delete(Long id) {
        Company c = require(id);
        for (UserAccount u : users.findByCompanyIdAndDeletedFalse(c.getId())) {
            keycloak.deleteUser(u.getKeycloakId());
            u.setDeleted(true);
            u.setStatus(EUserStatus.LOCKED);
        }
        orderService.cancelPendingOf(c.getId());
        c.setDeleted(true);
        c.setDeletedAt(java.time.Instant.now());
        c.setStatus(ECompanyStatus.SUSPENDED);
        authorityCache.evictAll();
        log.info("Company {} deleted (soft)", c.getCode());
    }

    private Company require(Long id) {
        return companies.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EIamError.IAM_001));
    }

    private Map<Long, Plan> livePlans() {
        return plans.findByDeletedFalseOrderByMonthlyPriceAsc().stream()
                .collect(Collectors.toMap(Plan::getId, Function.identity()));
    }

    private static String kw(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private static Instant start(LocalDate from) {
        return from == null ? MIN : from.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant end(LocalDate to) {
        return to == null ? MAX : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
