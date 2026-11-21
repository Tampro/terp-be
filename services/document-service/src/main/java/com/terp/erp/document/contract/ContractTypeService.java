package com.terp.erp.document.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.document.common.EDocumentError;
import com.terp.erp.document.common.EStatus;
import com.terp.erp.document.common.Texts;
import com.terp.erp.document.template.DocTemplateRepository;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contract Types page (docs/analysis/06 §9.4.6): name unique in the company (DOC_003); the Contracts column counts the
 * active contracts of the type; a type with contracts cannot be deleted (DOC_004).
 */
@Service
@RequiredArgsConstructor
public class ContractTypeService {
    private final ContractTypeRepository types;
    private final EmployeeContractRepository contracts;
    private final DocTemplateRepository templates;
    private final java.time.Clock clock;

    public record TypeForm(
            @Schema(example = "Fixed-term 12 months", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @Schema(description = "Months; omitted = open-ended", example = "12") @Min(1) @Max(600)
            Integer defaultDurationMonths,
            @Schema(description = "Months; omitted = 0", example = "2") @Min(0) @Max(24) Integer probationMonths,
            @Schema(description = "Days; omitted = 0", example = "30") @Min(0) @Max(365) Integer noticeDays,
            @Schema(description = "Omitted = false on create, kept on edit") Boolean renewable,
            @Schema(description = "Omitted = ACTIVE on create, kept on edit") EStatus status) {
    }

    /** contracts = the Contracts column (active contracts of the type). */
    public record TypeDto(String id, String name, String description, Integer defaultDurationMonths,
                          int probationMonths, int noticeDays, boolean renewable, EStatus status, long contracts,
                          Instant createdDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record Option(String id, String name, Integer defaultDurationMonths) {
    }

    @Transactional(readOnly = true)
    public Page<TypeDto> search(Long requestedCompanyId, String keyword, Boolean renewable, EStatus status,
                                Pageable pageable) {
        Page<ContractType> page = types.search(Texts.company(requestedCompanyId), renewable, status,
                Texts.kw(keyword), pageable);
        Map<Long, Long> counts = new HashMap<>();
        if (!page.isEmpty()) {
            contracts.countActiveByType(page.map(ContractType::getId).toList(), Texts.today(clock))
                    .forEach(r -> counts.put((Long) r[0], (Long) r[1]));
        }
        return page.map(t -> dto(t, counts.getOrDefault(t.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Boolean renewable) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : types.countByStatus(Texts.company(requestedCompanyId), renewable, Texts.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    /** The Contract Type dropdown: active types, with their default duration (to suggest an end date). */
    @Transactional(readOnly = true)
    public List<Option> options(Long requestedCompanyId) {
        return types.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(Texts.company(requestedCompanyId),
                        EStatus.ACTIVE).stream()
                .map(t -> new Option(t.getId().toString(), t.getName(), t.getDefaultDurationMonths())).toList();
    }

    @Transactional(readOnly = true)
    public TypeDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public TypeDto create(Long requestedCompanyId, TypeForm form) {
        ContractType t = new ContractType();
        t.setCompanyId(Texts.company(requestedCompanyId));
        apply(t, form);
        return dto(types.save(t), 0);
    }

    /** Full replacement; renewable and status omitted = kept. */
    @Transactional
    public TypeDto update(Long id, TypeForm form) {
        ContractType t = require(id);
        apply(t, form);
        return detail(t);
    }

    @Transactional
    public TypeDto changeStatus(Long id, EStatus status) {
        ContractType t = require(id);
        t.setStatus(status);
        return detail(t);
    }

    @Transactional
    public void delete(Long id) {
        ContractType t = require(id);
        if (contracts.existsByContractTypeIdAndDeletedFalse(t.getId())
                || templates.existsByContractTypeIdAndDeletedFalse(t.getId())) {
            throw ApiException.badRequest(EDocumentError.DOC_004);
        }
        t.setDeleted(true);
    }

    private void apply(ContractType t, TypeForm f) {
        String name = f.name().trim();
        if (types.existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(t.getCompanyId(), name,
                t.getId() == null ? -1L : t.getId())) {
            throw ApiException.badRequest(EDocumentError.DOC_003);
        }
        t.setName(name);
        t.setDescription(Texts.blankToNull(f.description()));
        t.setDefaultDurationMonths(f.defaultDurationMonths());
        t.setProbationMonths(f.probationMonths() == null ? 0 : f.probationMonths());
        t.setNoticeDays(f.noticeDays() == null ? 0 : f.noticeDays());
        if (f.renewable() != null) {
            t.setRenewable(f.renewable());
        }
        if (f.status() != null) {
            t.setStatus(f.status());
        }
    }

    private TypeDto detail(ContractType t) {
        return dto(t, contracts.countActiveByType(List.of(t.getId()), Texts.today(clock)).stream().mapToLong(r -> (Long) r[1]).sum());
    }

    static TypeDto dto(ContractType t, long contractCount) {
        return new TypeDto(t.getId().toString(), t.getName(), t.getDescription(), t.getDefaultDurationMonths(),
                t.getProbationMonths(), t.getNoticeDays(), t.isRenewable(), t.getStatus(), contractCount,
                t.getCreatedDate());
    }

    public ContractType require(Long id) {
        ContractType t = types.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EDocumentError.DOC_005));
        TenantGuard.assertSameCompany(t.getCompanyId(), EDocumentError.DOC_005);
        return t;
    }
}
