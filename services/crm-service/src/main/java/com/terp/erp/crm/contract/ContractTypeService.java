package com.terp.erp.crm.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Contract types (ERP manual 7.5): name unique per company, soft-deleted only while no contract uses them. */
@Service
@RequiredArgsConstructor
public class ContractTypeService {
    private final ContractTypeRepository types;
    private final ContractRepository contracts;

    /** Create and full replacement. */
    public record TypeForm(
            @Schema(description = "Super admin only: the company to act on (create)", example = "10") Long companyId,
            @Schema(description = "Type name, unique in the company", example = "Test Maintenance Contract",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 150) String name,
            @Schema(description = "Can be chosen for new contracts", example = "true",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Boolean active) {
    }

    public record TypeDto(String id, String companyId, String name, boolean active,
                          @Schema(description = "Contracts (not deleted) of this type in the company; every "
                                  + "contract, not only the caller's own", example = "3") long contractCount) {
        static TypeDto of(ContractType t, long contractCount) {
            return new TypeDto(t.getId().toString(), t.getCompanyId().toString(), t.getName(), t.isActive(),
                    contractCount);
        }
    }

    @Transactional(readOnly = true)
    public List<TypeDto> list(Long companyId) {
        Long company = TenantGuard.resolveCompany(companyId, EContractError.CTR_001);
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] r : contracts.countByType(company)) {
            counts.put((Long) r[0], ((Number) r[1]).longValue());
        }
        return types.findByCompanyIdAndDeletedFalseOrderByNameAsc(company).stream()
                .map(t -> TypeDto.of(t, counts.getOrDefault(t.getId(), 0L))).toList();
    }

    @Transactional
    public TypeDto create(TypeForm f) {
        Long company = TenantGuard.resolveCompany(f.companyId(), EContractError.CTR_001);
        String name = f.name().trim();
        if (types.existsByCompanyIdAndNameIgnoreCaseAndDeletedFalse(company, name)) {
            throw ApiException.badRequest(EContractError.CTR_004, name);
        }
        ContractType t = new ContractType();
        t.setCompanyId(company);
        t.setName(name);
        t.setActive(f.active());
        return TypeDto.of(types.save(t), 0);
    }

    @Transactional
    public TypeDto update(Long id, TypeForm f) {
        ContractType t = require(id);
        String name = f.name().trim();
        if (types.existsByCompanyIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(t.getCompanyId(), name, id)) {
            throw ApiException.badRequest(EContractError.CTR_004, name);
        }
        t.setName(name);
        t.setActive(f.active());
        return TypeDto.of(t, contracts.countByContractTypeIdAndDeletedFalse(id));
    }

    @Transactional
    public void delete(Long id) {
        ContractType t = require(id);
        if (contracts.existsByContractTypeIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EContractError.CTR_005);
        }
        t.setDeleted(true);
    }

    /** Display name of a type already on a contract (no company check: the contract was checked). */
    String nameOf(Long id) {
        return types.findById(id).map(ContractType::getName).orElse(null);
    }

    ContractType require(Long id) {
        ContractType t = types.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EContractError.CTR_003));
        TenantGuard.assertSameCompany(t.getCompanyId(), EContractError.CTR_003);
        return t;
    }
}
