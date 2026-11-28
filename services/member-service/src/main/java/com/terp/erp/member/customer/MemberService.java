package com.terp.erp.member.customer;

import com.terp.erp.exception.ApiException;
import com.terp.erp.member.client.ShopClient;
import com.terp.erp.member.common.EMemberError;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * A customer's membership of a shop. The shop's company comes from store-service (slug), the customer from the token
 * ({@code sub}); a customer only ever reads or changes their own row in that company.
 */
@Service
@RequiredArgsConstructor
public class MemberService {
    // TODO(assumption): "rời và đăng ký lại trong 5 ngày" (NFR-SEC-05) = 5 × 24 h, not calendar days.
    static final Duration REJOIN_WAIT = Duration.ofDays(5);

    private final CustomerRepository customers;
    private final ShopClient shops;
    private final Clock clock;

    public record JoinForm(
            @Schema(description = "Must be true", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean termsAgreed,
            @Schema(description = "Must be true", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Boolean privacyAgreed,
            @Schema(description = "Default false") Boolean marketingAgreed,
            @Schema(example = "2026-11", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 20) String termsVersion,
            @Schema(example = "2026-11", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 20) String privacyVersion) {
    }

    public record MemberDto(String customerId, String shopSlug, ECustomerStatus status, Instant joinedAt,
                            boolean marketingAgreed) {
    }

    @Transactional(readOnly = true)
    public MemberDto get(String slug, String sub) {
        String shop = norm(slug);
        return dto(active(shops.companyOf(shop), sub), shop);
    }

    @Transactional
    public MemberDto join(String slug, String sub, JoinForm f) {
        if (!Boolean.TRUE.equals(f.termsAgreed()) || !Boolean.TRUE.equals(f.privacyAgreed())) {
            throw ApiException.badRequest(EMemberError.MEM_005);
        }
        String shop = norm(slug);
        long companyId = shops.companyOf(shop);
        if (customers.findFirstByCompanyIdAndKeycloakSubAndStatus(companyId, sub, ECustomerStatus.ACTIVE).isPresent()) {
            throw ApiException.badRequest(EMemberError.MEM_002);
        }
        Instant now = clock.instant();
        customers.findFirstByCompanyIdAndKeycloakSubAndStatusOrderByLeftAtDesc(companyId, sub, ECustomerStatus.LEFT)
                .map(Customer::getLeftAt)
                .filter(left -> left != null && now.isBefore(left.plus(REJOIN_WAIT)))
                .ifPresent(left -> {
                    throw ApiException.badRequest(EMemberError.MEM_003, left.plus(REJOIN_WAIT).toString());
                });
        Customer c = new Customer();
        c.setCompanyId(companyId);
        c.setKeycloakSub(sub);
        c.setStatus(ECustomerStatus.ACTIVE);
        c.setTermsVersion(f.termsVersion().trim());
        c.setTermsAgreedAt(now);
        c.setPrivacyVersion(f.privacyVersion().trim());
        c.setPrivacyAgreedAt(now);
        c.setMarketingAgreed(Boolean.TRUE.equals(f.marketingAgreed()));
        c.setMarketingAgreedAt(c.isMarketingAgreed() ? now : null);
        c.setJoinedAt(now);
        try {
            return dto(customers.saveAndFlush(c), shop);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.badRequest(EMemberError.MEM_002); // a concurrent join of the same customer hit uk_customer_live
        }
    }

    /** The row stays as consent history (LEFT); the Keycloak account is kept. */
    @Transactional
    public void leave(String slug, String sub) {
        long companyId = shops.companyOf(norm(slug));
        Customer c = active(companyId, sub);
        c.setStatus(ECustomerStatus.LEFT);
        c.setLeftAt(clock.instant());
        c.setLiveKey(c.getId());
    }

    private Customer active(long companyId, String sub) {
        return customers.findFirstByCompanyIdAndKeycloakSubAndStatus(companyId, sub, ECustomerStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound(EMemberError.MEM_004));
    }

    /** Slugs are stored lower-cased (shop-setting); the same normalisation before asking store-service. */
    private static String norm(String slug) {
        return slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
    }

    private static MemberDto dto(Customer c, String shop) {
        return new MemberDto(String.valueOf(c.getId()), shop, c.getStatus(), c.getJoinedAt(),
                c.isMarketingAgreed());
    }
}
