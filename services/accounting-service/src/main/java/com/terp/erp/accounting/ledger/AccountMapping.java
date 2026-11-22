package com.terp.erp.accounting.ledger;

import com.terp.erp.accounting.coa.ChartAccount;
import com.terp.erp.accounting.coa.ChartAccountRepository;
import com.terp.erp.accounting.coa.VasChartSeeder;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The company's system account mapping (which account is "bank", "sales revenue", ...). */
@Service
@RequiredArgsConstructor
public class AccountMapping {
    private final SystemAccountMappingRepository mappings;
    private final ChartAccountRepository accounts;
    private final VasChartSeeder seeder;

    public record MappingDto(ESystemAccount key, String accountId, String accountCode, String accountName) {
    }

    public record MappingForm(
            @Schema(description = "Every system key mapped to an active account id of the company",
                    example = "{\"BANK\": \"2\", \"CASH\": \"1\"}", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Map<ESystemAccount, Long> accounts) {
    }

    /**
     * The account id playing {@code key} in the company; seeds the default chart on first use. 400 ACC_017 when the
     * key is unmapped or its account is inactive or gone.
     */
    @Transactional
    public Long require(Long companyId, ESystemAccount key) {
        seeder.ensureSeeded(companyId);
        return mappingOf(companyId, key)
                .flatMap(m -> accounts.findById(m.getAccountId()))
                .filter(a -> a.isActive() && Objects.equals(a.getCompanyId(), companyId))
                .map(ChartAccount::getId)
                .orElseThrow(() -> ApiException.badRequest(EAccountingError.ACC_017, key.name()));
    }

    // Not read-only: the first read seeds the chart.
    @Transactional
    public List<MappingDto> get(Long requestedCompanyId) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        seeder.ensureSeeded(companyId);
        Map<ESystemAccount, SystemAccountMapping> byKey = new java.util.EnumMap<>(ESystemAccount.class);
        mappings.findByCompanyId(companyId).forEach(m -> byKey.put(m.getSystemKey(), m));
        return Arrays.stream(ESystemAccount.values()).map(k -> {
            SystemAccountMapping m = byKey.containsKey(k) ? byKey.get(k) : mappingOf(companyId, k).orElse(null);
            ChartAccount a = m == null ? null : accounts.findById(m.getAccountId()).orElse(null);
            return a == null ? new MappingDto(k, null, null, null)
                    : new MappingDto(k, a.getId().toString(), a.getCode(), a.getName());
        }).toList();
    }

    /**
     * The mapping row of a key; a key added after the company was seeded (SALARY_PAYABLE) is mapped on first use to
     * the company's account with the key's default code, when that account exists.
     */
    private java.util.Optional<SystemAccountMapping> mappingOf(Long companyId, ESystemAccount key) {
        java.util.Optional<SystemAccountMapping> found = mappings.findByCompanyIdAndSystemKey(companyId, key);
        if (found.isPresent()) {
            return found;
        }
        return accounts.findByCompanyIdAndCode(companyId, key.defaultCode()).map(a -> {
            SystemAccountMapping m = new SystemAccountMapping();
            m.setCompanyId(companyId);
            m.setSystemKey(key);
            m.setAccountId(a.getId());
            return mappings.save(m);
        });
    }

    /** Full replacement: every key must be present and point to an active account of the company. */
    @Transactional
    public List<MappingDto> update(Long requestedCompanyId, MappingForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        seeder.ensureSeeded(companyId);
        for (ESystemAccount key : ESystemAccount.values()) {
            Long accountId = form.accounts().get(key);
            boolean valid = accountId != null && accounts.findById(accountId)
                    .filter(a -> a.isActive() && Objects.equals(a.getCompanyId(), companyId)).isPresent();
            if (!valid) {
                throw ApiException.badRequest(EAccountingError.ACC_017, key.name());
            }
        }
        for (ESystemAccount key : ESystemAccount.values()) {
            SystemAccountMapping m = mappings.findByCompanyIdAndSystemKey(companyId, key).orElseGet(() -> {
                SystemAccountMapping fresh = new SystemAccountMapping();
                fresh.setCompanyId(companyId);
                fresh.setSystemKey(key);
                return fresh;
            });
            m.setAccountId(form.accounts().get(key));
            mappings.save(m);
        }
        return get(companyId);
    }
}
