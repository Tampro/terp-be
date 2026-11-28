package com.terp.erp.order.mileage;

import com.terp.erp.exception.ApiException;
import com.terp.erp.order.common.EMileageError;
import com.terp.erp.order.mileage.MileageDtos.AccountDto;
import com.terp.erp.order.mileage.MileageDtos.EntryDto;
import com.terp.erp.order.mileage.MileageDtos.PolicyDto;
import com.terp.erp.order.mileage.MileageDtos.PolicyForm;
import com.terp.erp.order.order.CustomerOrderRepository;
import com.terp.erp.security.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Back Office policy and statements, and the member's own statement. */
@Service
@RequiredArgsConstructor
public class MileageService {
    private static final Duration SOON = Duration.ofDays(30);

    private final MileagePolicyRepository policies;
    private final MileageLotRepository lots;
    private final MileageEntryRepository entries;
    private final MileageLedger ledger;
    private final CustomerOrderRepository orders;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PolicyDto policy(Long requestedCompanyId) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EMileageError.MIL_002);
        return policies.findById(company).map(p -> PolicyDto.of(p, true))
                .orElseGet(() -> PolicyDto.of(MileagePolicy.defaults(company), false));
    }

    /** Full replacement of the company's policy. */
    @Transactional
    public PolicyDto savePolicy(Long requestedCompanyId, PolicyForm f) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EMileageError.MIL_002);
        if (f.minRedeem() % 10 != 0) {
            throw ApiException.badRequest(EMileageError.MIL_001, "minRedeem must be a multiple of 10");
        }
        MileagePolicy p = policies.findById(company).orElseGet(() -> MileagePolicy.defaults(company));
        p.setActive(f.active());
        p.setEarnRate(f.earnRate());
        p.setExpiryMonths(f.expiryMonths());
        p.setMinRedeem(f.minRedeem());
        return PolicyDto.of(policies.save(p), true);
    }

    /** Back Office: a customer known to the company through its orders (else 404 MIL_007). */
    @Transactional(readOnly = true)
    public AccountDto account(Long requestedCompanyId, long customerId, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EMileageError.MIL_002);
        requireCustomer(company, customerId);
        return statement(company, customerId, pageable);
    }

    @Transactional
    public AccountDto adjust(Long requestedCompanyId, long customerId, int points, String reason, Pageable pageable) {
        Long company = TenantGuard.resolveCompany(requestedCompanyId, EMileageError.MIL_002);
        requireCustomer(company, customerId);
        if (points == 0) {
            throw ApiException.badRequest(EMileageError.MIL_001, "points must not be 0");
        }
        ledger.adjust(company, customerId, points, reason.trim());
        return statement(company, customerId, pageable);
    }

    /** The member's own statement (company and customer from the membership). */
    @Transactional(readOnly = true)
    public AccountDto statement(long companyId, long customerId, Pageable pageable) {
        Instant now = clock.instant();
        Page<EntryDto> page = entries.findByCompanyIdAndCustomerIdOrderByIdDesc(companyId, customerId, pageable)
                .map(EntryDto::of);
        return new AccountDto(String.valueOf(customerId), lots.balance(companyId, customerId, now),
                lots.expiring(companyId, customerId, now, now.plus(SOON)), page.getContent(), page.getTotalElements());
    }

    // TODO(assumption): order-service knows a customer only through their orders; a member who never ordered here has
    //  no statement to show or adjust.
    private void requireCustomer(long companyId, long customerId) {
        if (!orders.existsByCustomerIdAndCompanyId(customerId, companyId)) {
            throw ApiException.notFound(EMileageError.MIL_007);
        }
    }
}
