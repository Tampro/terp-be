package com.terp.erp.employee.award;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.award.AwardTypeDtos.AwardTypeDto;
import com.terp.erp.employee.award.AwardTypeDtos.AwardTypeForm;
import com.terp.erp.employee.award.AwardTypeDtos.AwardTypeOption;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.util.List;

/**
 * Award Types page (docs/analysis/06 §6.3.6): one list per company, unique names (case-insensitive).
 * A type used by an award (§7.2.1) cannot be deleted (EMP_010).
 */
@Service
@RequiredArgsConstructor
public class AwardTypeService {
    private final AwardTypeRepository types;
    private final com.terp.erp.employee.lifecycle.EmployeeAwardRepository awards;

    @Transactional(readOnly = true)
    public Page<AwardTypeDto> search(Long requestedCompanyId, String keyword, EOrgStatus status, Pageable pageable) {
        return types.search(company(requestedCompanyId), status, DepartmentService.kw(keyword), pageable)
                .map(AwardTypeDto::of);
    }

    @Transactional(readOnly = true)
    public List<AwardTypeOption> options(Long requestedCompanyId) {
        return types.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(company(requestedCompanyId),
                EOrgStatus.ACTIVE).stream().map(t -> new AwardTypeOption(t.getId().toString(), t.getName(),
                t.getIcon())).toList();
    }

    @Transactional(readOnly = true)
    public AwardTypeDto get(Long id) {
        return AwardTypeDto.of(require(id));
    }

    @Transactional
    public AwardTypeDto create(Long requestedCompanyId, AwardTypeForm form) {
        AwardType t = new AwardType();
        t.setCompanyId(company(requestedCompanyId));
        apply(t, form, -1L);
        return AwardTypeDto.of(types.save(t));
    }

    /** Full replacement: omitted description and icon are cleared, omitted status is kept. */
    @Transactional
    public AwardTypeDto update(Long id, AwardTypeForm form) {
        AwardType t = require(id);
        apply(t, form, id);
        return AwardTypeDto.of(t);
    }

    @Transactional
    public AwardTypeDto changeStatus(Long id, EOrgStatus status) {
        AwardType t = require(id);
        t.setStatus(status);
        return AwardTypeDto.of(t);
    }

    @Transactional
    public void delete(Long id) {
        AwardType t = require(id);
        if (awards.existsByAwardTypeIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        t.setDeleted(true);
    }

    private void apply(AwardType t, AwardTypeForm f, Long selfId) {
        String name = f.name().trim();
        if (types.nameTaken(t.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        t.setName(name);
        t.setDescription(blankToNull(f.description()));
        t.setIcon(blankToNull(f.icon()));
        if (f.status() != null) {
            t.setStatus(f.status());
        }
    }

    private AwardType require(Long id) {
        AwardType t = types.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_042));
        TenantGuard.assertSameCompany(t.getCompanyId(), EEmployeeError.EMP_042);
        return t;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }
}
