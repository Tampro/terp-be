package com.terp.erp.accounting.coa;

import com.terp.erp.accounting.bank.BankAccountRepository;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.ledger.JournalEntryRepository;
import com.terp.erp.accounting.ledger.SystemAccountMappingRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Account types and ledger accounts of a company's chart; the VAS default chart is seeded on first list. */
@Service
@RequiredArgsConstructor
public class ChartOfAccountsService {
    private final AccountTypeRepository types;
    private final ChartAccountRepository accounts;
    private final AccountCategoryRepository categories;
    private final SystemAccountMappingRepository mappings;
    private final JournalEntryRepository journal;
    private final BankAccountRepository bankAccounts;
    private final VasChartSeeder seeder;

    public record AccountTypeForm(
            @Schema(example = "ASSETS", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            EAccountCategory category,
            @Schema(example = "CA", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Pattern(regexp = "[A-Za-z0-9_.-]{1,20}") String code,
            @Schema(example = "Tài sản ngắn hạn", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String name,
            @Schema(example = "DEBIT", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            ENormalBalance normalBalance,
            @Schema(example = "true") boolean active) {
    }

    public record AccountTypeDto(String id, String companyId, EAccountCategory category, String code, String name,
                                 ENormalBalance normalBalance, boolean active, Instant createdDate) {
    }

    public record AccountForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long accountTypeId,
            @Schema(example = "1121", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Pattern(regexp = "[A-Za-z0-9_.-]{1,20}") String code,
            @Schema(example = "Tiền gửi ngân hàng VND", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String name,
            @Schema(description = "null = the account type's normal balance", example = "DEBIT")
            ENormalBalance normalBalance,
            @Schema(description = "null = 0", example = "0") @Digits(integer = 15, fraction = 4)
            BigDecimal openingBalance,
            @Schema(description = "Inactive accounts take no posting", example = "true") boolean active,
            @Schema(description = "Parent account of the same company (null = top level)", example = "2")
            Long parentId,
            @Schema(example = "VND current account") @Size(max = 500) String description) {
    }

    public record AccountDto(String id, String companyId, String accountTypeId, String code, String name,
                             ENormalBalance normalBalance, BigDecimal openingBalance, BigDecimal currentBalance,
                             boolean active, String parentId, String description, Instant createdDate,
                             Instant updatedDate) {
    }

    // ---- account types

    @Transactional
    public Page<AccountTypeDto> searchTypes(Long requestedCompanyId, EAccountCategory category, Boolean active,
                                            Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        seeder.ensureSeeded(companyId);
        return types.search(companyId, category, active, pageable).map(ChartOfAccountsService::dto);
    }

    @Transactional(readOnly = true)
    public AccountTypeDto getType(Long id) {
        return dto(requireType(id));
    }

    @Transactional
    public AccountTypeDto createType(Long requestedCompanyId, AccountTypeForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        AccountType t = new AccountType();
        t.setCompanyId(companyId);
        apply(t, form);
        return dto(types.save(t));
    }

    @Transactional
    public AccountTypeDto updateType(Long id, AccountTypeForm form) {
        AccountType t = requireType(id);
        apply(t, form);
        return dto(t);
    }

    @Transactional
    public void deleteType(Long id) {
        AccountType t = requireType(id);
        if (accounts.existsByAccountTypeId(t.getId())) {
            throw ApiException.badRequest(EAccountingError.ACC_032);
        }
        types.delete(t);
    }

    // ---- accounts

    @Transactional
    public Page<AccountDto> searchAccounts(Long requestedCompanyId, Long typeId, ENormalBalance normalBalance,
                                           Boolean active, String keyword, Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        seeder.ensureSeeded(companyId);
        return accounts.search(companyId, typeId, normalBalance, active, kw(keyword), pageable)
                .map(ChartOfAccountsService::dto);
    }

    @Transactional(readOnly = true)
    public AccountDto getAccount(Long id) {
        return dto(requireAccount(id));
    }

    @Transactional
    public AccountDto createAccount(Long requestedCompanyId, AccountForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        ChartAccount a = new ChartAccount();
        a.setCompanyId(companyId);
        apply(a, form);
        BigDecimal opening = orZero(form.openingBalance());
        a.setOpeningBalance(opening);
        a.setCurrentBalance(opening);
        return dto(accounts.save(a));
    }

    /** Full replacement; a new opening balance moves the current balance by the difference. */
    @Transactional
    public AccountDto updateAccount(Long id, AccountForm form) {
        ChartAccount a = accounts.lockById(id).orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_010));
        TenantGuard.assertSameCompany(a.getCompanyId(), EAccountingError.ACC_010);
        apply(a, form);
        BigDecimal opening = orZero(form.openingBalance());
        a.setCurrentBalance(a.getCurrentBalance().add(opening.subtract(a.getOpeningBalance())));
        a.setOpeningBalance(opening);
        return dto(a);
    }

    /** Refused (ACC_013) while the account has journal lines, children, a mapping, a category or a bank account. */
    @Transactional
    public void deleteAccount(Long id) {
        ChartAccount a = requireAccount(id);
        if (journal.existsLineOnAccount(a.getId()) || accounts.existsByParentId(a.getId())
                || mappings.existsByAccountId(a.getId()) || categories.existsByGlAccountId(a.getId())
                || bankAccounts.existsByGlAccountId(a.getId())) {
            throw ApiException.badRequest(EAccountingError.ACC_013);
        }
        accounts.delete(a);
    }

    /** A ledger account of the caller's company (ACC_010, 404 otherwise). */
    public ChartAccount requireAccount(Long id) {
        ChartAccount a = accounts.findById(id).orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_010));
        TenantGuard.assertSameCompany(a.getCompanyId(), EAccountingError.ACC_010);
        return a;
    }

    private AccountType requireType(Long id) {
        AccountType t = types.findById(id).orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_009));
        TenantGuard.assertSameCompany(t.getCompanyId(), EAccountingError.ACC_009);
        return t;
    }

    private void apply(AccountType t, AccountTypeForm f) {
        String code = f.code().trim();
        if (types.existsByCompanyIdAndCodeAndIdNot(t.getCompanyId(), code, t.getId() == null ? -1L : t.getId())) {
            throw ApiException.badRequest(EAccountingError.ACC_031);
        }
        t.setCategory(f.category());
        t.setCode(code);
        t.setName(f.name().trim());
        t.setNormalBalance(f.normalBalance());
        t.setActive(f.active());
    }

    private void apply(ChartAccount a, AccountForm f) {
        AccountType type = types.findById(f.accountTypeId())
                .filter(t -> Objects.equals(t.getCompanyId(), a.getCompanyId()))
                .orElseThrow(() -> ApiException.badRequest(EAccountingError.ACC_009));
        String code = f.code().trim();
        if (accounts.existsByCompanyIdAndCodeAndIdNot(a.getCompanyId(), code, a.getId() == null ? -1L : a.getId())) {
            throw ApiException.badRequest(EAccountingError.ACC_011);
        }
        if (f.parentId() != null) {
            checkParent(a, f.parentId());
        }
        a.setAccountTypeId(type.getId());
        a.setCode(code);
        a.setName(f.name().trim());
        a.setNormalBalance(f.normalBalance() == null ? type.getNormalBalance() : f.normalBalance());
        a.setActive(f.active());
        a.setParentId(f.parentId());
        a.setDescription(blankToNull(f.description()));
    }

    /** The parent must be of the same company and must not be the account itself or one of its descendants. */
    private void checkParent(ChartAccount a, Long parentId) {
        Set<Long> seen = new HashSet<>();
        Long current = parentId;
        while (current != null) {
            if (Objects.equals(current, a.getId()) || !seen.add(current)) {
                throw ApiException.badRequest(EAccountingError.ACC_012);
            }
            ChartAccount p = accounts.findById(current)
                    .filter(x -> Objects.equals(x.getCompanyId(), a.getCompanyId()))
                    .orElseThrow(() -> ApiException.badRequest(EAccountingError.ACC_012));
            current = p.getParentId();
        }
    }

    private static AccountTypeDto dto(AccountType t) {
        return new AccountTypeDto(t.getId().toString(), t.getCompanyId().toString(), t.getCategory(), t.getCode(),
                t.getName(), t.getNormalBalance(), t.isActive(), t.getCreatedDate());
    }

    private static AccountDto dto(ChartAccount a) {
        return new AccountDto(a.getId().toString(), a.getCompanyId().toString(), a.getAccountTypeId().toString(),
                a.getCode(), a.getName(), a.getNormalBalance(), a.getOpeningBalance(), a.getCurrentBalance(),
                a.isActive(), a.getParentId() == null ? null : a.getParentId().toString(), a.getDescription(),
                a.getCreatedDate(), a.getUpdatedDate());
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String kw(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + k + "%";
    }
}
