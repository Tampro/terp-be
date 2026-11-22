package com.terp.erp.document.contract;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.document.TestLogin;
import com.terp.erp.document.client.EmployeeClient;
import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.document.common.EStatus;
import com.terp.erp.document.contract.EmployeeContractService.ContractForm;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeContractServiceTest {
    private final EmployeeContractRepository contracts = mock(EmployeeContractRepository.class);
    private final ContractTypeRepository types = mock(ContractTypeRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final EmployeeContractService service = new EmployeeContractService(contracts, types, employees,
            Clock.fixed(Instant.parse("2026-11-21T02:00:00Z"), ZoneOffset.UTC));
    private final ContractType fixed = new ContractType();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "CONTRACT_MANAGE");
        fixed.setId(3L);
        fixed.setCompanyId(10L);
        fixed.setName("Fixed 12");
        fixed.setDefaultDurationMonths(12);
        when(types.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(fixed));
        when(types.findById(3L)).thenReturn(Optional.of(fixed));
        EmployeeClient.Employee e = new EmployeeClient.Employee("12", "10", "EMP12", "Test Twelve",
                "t12@example.invalid", null, "ACTIVE", null, null, null);
        when(employees.employee(12L)).thenReturn(e);
        when(employees.byIds(any(), eq(10L))).thenReturn(Map.of(12L, e));
        when(contracts.saveAndFlush(any())).thenAnswer(i -> {
            EmployeeContract c = i.getArgument(0);
            c.setId(123L);
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createNumbersAndDefaultsTheEnd() {
        var c = service.create(null, form(LocalDate.of(2026, 12, 1), null));
        assertThat(c.contractNumber()).isEqualTo("CT-2026-000123");
        assertThat(c.endDate()).isEqualTo(LocalDate.of(2027, 11, 30));
        assertThat(c.durationDays()).isEqualTo(365);
        assertThat(c.status()).isEqualTo(EmployeeContract.EStatus.DRAFT);
        assertThat(c.employee().fullName()).isEqualTo("Test Twelve");
        assertThat(c.contractAmount()).isEqualByComparingTo("15000000");
        fixed.setDefaultDurationMonths(null);
        assertThat(service.create(null, form(LocalDate.of(2026, 12, 1), null)).endDate()).isNull();
    }

    @Test
    void progressAndEffectiveStatus() {
        // 2026-01-01 … 2026-12-31 (365 days), today 2026-11-21 = day 325
        assertThat(EmployeeContractService.progress(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 11, 21))).isEqualByComparingTo("89.04");
        assertThat(EmployeeContractService.progress(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31),
                LocalDate.of(2026, 11, 21))).isEqualByComparingTo("0");
        assertThat(EmployeeContractService.progress(LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 11, 21)))
                .isNull();
        EmployeeContract c = new EmployeeContract();
        c.setId(5L);
        c.setCompanyId(10L);
        c.setContractTypeId(3L);
        c.setEmployeeId(12L);
        c.setStartDate(LocalDate.of(2025, 1, 1));
        c.setEndDate(LocalDate.of(2026, 11, 20));
        c.setStatus(EmployeeContract.EStatus.ACTIVE);
        when(contracts.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(c));
        assertThat(service.get(5L).status()).isEqualTo(EmployeeContract.EStatus.EXPIRED);
        assertThat(service.get(5L).storedStatus()).isEqualTo(EmployeeContract.EStatus.ACTIVE);
        c.setCompanyId(11L);
        assertCode(() -> service.get(5L), EDocumentError.DOC_017);
    }

    @Test
    void rules() {
        assertCode(() -> service.create(null, form(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1))),
                EDocumentError.DOC_018);
        fixed.setStatus(EStatus.INACTIVE);
        assertCode(() -> service.create(null, form(LocalDate.of(2026, 12, 1), null)), EDocumentError.DOC_015);
        fixed.setCompanyId(11L);
        assertCode(() -> service.create(null, form(LocalDate.of(2026, 12, 1), null)), EDocumentError.DOC_005);
    }

    @Test
    void signedContractIsLockedAndMovesFollowTheTable() {
        EmployeeContract c = new EmployeeContract();
        c.setId(5L);
        c.setCompanyId(10L);
        c.setContractTypeId(3L);
        c.setEmployeeId(12L);
        c.setStartDate(LocalDate.of(2026, 12, 1));
        c.setStatus(EmployeeContract.EStatus.DRAFT);
        when(contracts.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(c));
        service.update(5L, form(LocalDate.of(2026, 12, 1), null));
        assertCode(() -> service.changeStatus(5L, EmployeeContract.EStatus.RENEWED), EDocumentError.DOC_028);
        service.changeStatus(5L, EmployeeContract.EStatus.ACTIVE);
        assertCode(() -> service.update(5L, form(LocalDate.of(2026, 12, 1), null)), EDocumentError.DOC_027);
        assertCode(() -> service.delete(5L), EDocumentError.DOC_027);
        assertCode(() -> service.changeStatus(5L, EmployeeContract.EStatus.DRAFT), EDocumentError.DOC_028);
        service.changeStatus(5L, EmployeeContract.EStatus.TERMINATED);
        assertCode(() -> service.changeStatus(5L, EmployeeContract.EStatus.ACTIVE), EDocumentError.DOC_028);
        assertCode(() -> service.delete(5L), EDocumentError.DOC_027);
        assertThat(c.isDeleted()).isFalse();
        c.setStatus(EmployeeContract.EStatus.PENDING);
        service.delete(5L);
        assertThat(c.isDeleted()).isTrue();
    }

    private static ContractForm form(LocalDate start, LocalDate end) {
        return new ContractForm(12L, 3L, start, end, new BigDecimal("15000000"), "Terms");
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EDocumentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
