package com.terp.erp.document.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.document.client.EmployeeClient;
import com.terp.erp.document.client.EmployeeClient.Employee;
import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.document.common.EStatus;
import com.terp.erp.document.common.Texts;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Employee Contracts page (docs/analysis/06 §9.4.2). The employee is checked in employee-service as the caller; the
 * contract number comes from the id (CT-yyyy-000123); the end date defaults to the type's duration. An ACTIVE contract
 * past its end date reads as EXPIRED; Near Expire = active, ending within {@value #NEAR_EXPIRE_DAYS} days.
 * TODO(assumption): Contract Amount = the basic salary (per month) — confirm with BA.
 */
@Service
@RequiredArgsConstructor
public class EmployeeContractService {
    static final int NEAR_EXPIRE_DAYS = 30;

    private final EmployeeContractRepository contracts;
    private final ContractTypeRepository types;
    private final EmployeeClient employees;
    private final Clock clock;

    public record ContractForm(
            @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long contractTypeId,
            @Schema(example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
            @Schema(description = "Omitted = start + the type's default duration (open-ended types: none)")
            LocalDate endDate,
            @Schema(example = "15000000") @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal basicSalary,
            @Size(max = 20000) String termsAndConditions) {
    }

    public record TypeRef(String id, String name) {
    }

    public record Person(String id, String employeeNo, String fullName, String email, String profileImageUrl) {
    }

    /** status = effective; progressPercent = share of the term elapsed (null when open-ended). */
    public record ContractDto(String id, String contractNumber, TypeRef contractType, Person employee,
                              LocalDate startDate, LocalDate endDate, long durationDays, BigDecimal progressPercent,
                              BigDecimal basicSalary, BigDecimal contractAmount, String termsAndConditions,
                              EmployeeContract.EStatus status, EmployeeContract.EStatus storedStatus,
                              String documentFileId, Instant createdDate) {
    }

    public record StatusCounts(long all, Map<EmployeeContract.EStatus, Long> byStatus) {
    }

    public record Cards(long activeContracts, long nearExpire, long draftContracts) {
    }

    public record Filter(String keyword, Long contractTypeId, Long employeeId) {
    }

    @Transactional(readOnly = true)
    public Page<ContractDto> search(Long requestedCompanyId, Filter f, EmployeeContract.EStatus status,
                                    Pageable pageable) {
        Long companyId = Texts.company(requestedCompanyId);
        Page<EmployeeContract> page = contracts.search(companyId, f.contractTypeId(), f.employeeId(), status,
                Texts.kw(f.keyword()), today(), pageable);
        Map<Long, ContractType> byType = typesOf(page.getContent());
        Map<Long, Employee> people = page.isEmpty() ? Map.of()
                : employees.byIds(page.map(EmployeeContract::getEmployeeId).toList(), companyId);
        return page.map(c -> dto(c, byType.get(c.getContractTypeId()), people.get(c.getEmployeeId())));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, Filter f) {
        Map<EmployeeContract.EStatus, Long> by = new EnumMap<>(EmployeeContract.EStatus.class);
        for (EmployeeContract.EStatus s : EmployeeContract.EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : contracts.countByStatus(Texts.company(requestedCompanyId), f.contractTypeId(),
                f.employeeId(), Texts.kw(f.keyword()), today())) {
            by.merge((EmployeeContract.EStatus) r[0], (Long) r[1], Long::sum);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    /** Active Contract, Contract Near Expire, Draft Contract. */
    @Transactional(readOnly = true)
    public Cards cards(Long requestedCompanyId) {
        Long companyId = Texts.company(requestedCompanyId);
        StatusCounts c = statusCounts(requestedCompanyId, new Filter(null, null, null));
        LocalDate today = today();
        return new Cards(c.byStatus().get(EmployeeContract.EStatus.ACTIVE),
                contracts.countNearExpire(companyId, today, today.plusDays(NEAR_EXPIRE_DAYS)),
                c.byStatus().get(EmployeeContract.EStatus.DRAFT));
    }

    @Transactional(readOnly = true)
    public ContractDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public ContractDto create(Long requestedCompanyId, ContractForm form) {
        EmployeeContract c = new EmployeeContract();
        c.setCompanyId(Texts.company(requestedCompanyId));
        apply(c, form);
        contracts.saveAndFlush(c);
        c.setContractNumber("CT-%d-%06d".formatted(c.getStartDate().getYear(), c.getId()));
        return detail(c);
    }

    /** Full replacement of the details; the number and status stay. */
    @Transactional
    public ContractDto update(Long id, ContractForm form) {
        EmployeeContract c = require(id);
        apply(c, form);
        return detail(c);
    }

    /** Update Contract Status. */
    @Transactional
    public ContractDto changeStatus(Long id, EmployeeContract.EStatus status) {
        EmployeeContract c = require(id);
        c.setStatus(status);
        return detail(c);
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    private void apply(EmployeeContract c, ContractForm f) {
        ContractType t = types.findByIdAndDeletedFalse(f.contractTypeId())
                .filter(x -> x.getCompanyId().equals(c.getCompanyId()))
                .orElseThrow(() -> ApiException.badRequest(EDocumentError.DOC_005));
        if (t.getStatus() == EStatus.INACTIVE && !t.getId().equals(c.getContractTypeId())) {
            throw ApiException.badRequest(EDocumentError.DOC_015, t.getName());
        }
        LocalDate end = f.endDate() != null ? f.endDate() : t.getDefaultDurationMonths() == null ? null
                : f.startDate().plusMonths(t.getDefaultDurationMonths()).minusDays(1);
        if (end != null && end.isBefore(f.startDate())) {
            throw ApiException.badRequest(EDocumentError.DOC_018);
        }
        // employee-service answers EMP_001 for another company's employee (relayed)
        Employee e = employees.employee(f.employeeId());
        if (e == null || !String.valueOf(c.getCompanyId()).equals(e.companyId())) {
            throw ApiException.badRequest(EDocumentError.DOC_013);
        }
        c.setContractTypeId(t.getId());
        c.setEmployeeId(f.employeeId());
        c.setEmployeeName(e.fullName());
        c.setEmployeeNo(e.employeeNo());
        c.setEmployeeEmail(e.email());
        c.setStartDate(f.startDate());
        c.setEndDate(end);
        c.setBasicSalary(f.basicSalary() == null ? BigDecimal.ZERO : f.basicSalary());
        c.setTermsAndConditions(Texts.blankToNull(f.termsAndConditions()));
    }

    EmployeeContract.EStatus effective(EmployeeContract c) {
        return c.getStatus() == EmployeeContract.EStatus.ACTIVE && c.getEndDate() != null
                && c.getEndDate().isBefore(today()) ? EmployeeContract.EStatus.EXPIRED : c.getStatus();
    }

    /** Share of [start, end] elapsed today, 0–100. */
    static BigDecimal progress(LocalDate start, LocalDate end, LocalDate today) {
        if (end == null) {
            return null;
        }
        long total = ChronoUnit.DAYS.between(start, end) + 1;
        long done = Math.max(0, Math.min(total, ChronoUnit.DAYS.between(start, today) + 1));
        return BigDecimal.valueOf(done * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private ContractDto detail(EmployeeContract c) {
        Employee e = employees.byIds(List.of(c.getEmployeeId()), c.getCompanyId()).get(c.getEmployeeId());
        return dto(c, types.findById(c.getContractTypeId()).orElse(null), e);
    }

    private Map<Long, ContractType> typesOf(List<EmployeeContract> list) {
        return list.isEmpty() ? Map.of() : types.findAllById(list.stream().map(EmployeeContract::getContractTypeId)
                .distinct().toList()).stream().collect(Collectors.toMap(ContractType::getId, Function.identity()));
    }

    private ContractDto dto(EmployeeContract c, ContractType t, Employee e) {
        Person p = new Person(c.getEmployeeId().toString(), e != null ? e.employeeNo() : c.getEmployeeNo(),
                e != null ? e.fullName() : c.getEmployeeName(), e != null ? e.email() : c.getEmployeeEmail(),
                e == null ? null : e.profileImageUrl());
        long days = c.getEndDate() == null ? 0 : ChronoUnit.DAYS.between(c.getStartDate(), c.getEndDate()) + 1;
        return new ContractDto(c.getId().toString(), c.getContractNumber(),
                t == null ? null : new TypeRef(t.getId().toString(), t.getName()), p, c.getStartDate(),
                c.getEndDate(), days, progress(c.getStartDate(), c.getEndDate(), today()), c.getBasicSalary(),
                c.getBasicSalary(), c.getTermsAndConditions(), effective(c), c.getStatus(),
                c.getDocumentFileId() == null ? null : c.getDocumentFileId().toString(), c.getCreatedDate());
    }

    private LocalDate today() {
        return Texts.today(clock);
    }

    public EmployeeContract require(Long id) {
        EmployeeContract c = contracts.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EDocumentError.DOC_017));
        TenantGuard.assertSameCompany(c.getCompanyId(), EDocumentError.DOC_017);
        return c;
    }
}
