package com.terp.erp.employee.org;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.org.BranchDtos.BranchDto;
import com.terp.erp.employee.org.BranchDtos.BranchForm;
import com.terp.erp.employee.org.BranchDtos.BranchOption;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.util.List;
import java.util.Locale;

/**
 * Branches page (docs/analysis/06 §6.3.1). Inactive branches keep their departments and employees but cannot be
 * chosen for an employee (EMP_029); a branch still used cannot be deleted (EMP_010).
 */
@Service
@RequiredArgsConstructor
public class BranchService {
    private final BranchRepository branches;
    private final DepartmentRepository departments;
    private final EmployeeRepository employees;

    @Transactional(readOnly = true)
    public Page<BranchDto> search(Long requestedCompanyId, String keyword, EOrgStatus status, Pageable pageable) {
        Long companyId = company(requestedCompanyId);
        String kw = keyword == null || keyword.isBlank() ? ""
                : "%" + escape(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return branches.search(companyId, status, kw, pageable).map(BranchDto::of);
    }

    @Transactional(readOnly = true)
    public List<BranchOption> options(Long requestedCompanyId) {
        return branches.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(company(requestedCompanyId), EOrgStatus.ACTIVE)
                .stream().map(b -> new BranchOption(b.getId().toString(), b.getName())).toList();
    }

    @Transactional(readOnly = true)
    public BranchDto get(Long id) {
        Branch b = require(id);
        return BranchDto.of(b, departments.countByBranchIdAndDeletedFalse(id), employees.countByBranchIdAndDeletedFalse(id));
    }

    @Transactional
    public BranchDto create(Long requestedCompanyId, BranchForm form) {
        Long companyId = company(requestedCompanyId);
        Branch b = new Branch();
        b.setCompanyId(companyId);
        apply(b, form, -1L);
        return BranchDto.of(branches.save(b));
    }

    /** Full replacement; omitted optional fields are cleared, omitted status keeps the current one. */
    @Transactional
    public BranchDto update(Long id, BranchForm form) {
        Branch b = require(id);
        apply(b, form, id);
        return BranchDto.of(b);
    }

    @Transactional
    public BranchDto changeStatus(Long id, EOrgStatus status) {
        Branch b = require(id);
        b.setStatus(status);
        return BranchDto.of(b);
    }

    @Transactional
    public void delete(Long id) {
        Branch b = require(id);
        if (departments.existsByBranchIdAndDeletedFalse(id) || employees.existsByBranchIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        b.setDeleted(true);
    }

    private void apply(Branch b, BranchForm f, Long selfId) {
        String name = f.name().trim();
        if (branches.existsByCompanyIdAndNameAndDeletedFalseAndIdNot(b.getCompanyId(), name, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        b.setName(name);
        b.setAddress(blankToNull(f.address()));
        b.setCity(blankToNull(f.city()));
        b.setState(blankToNull(f.state()));
        b.setCountry(blankToNull(f.country()));
        b.setPostalCode(blankToNull(f.postalCode()));
        b.setPhone(blankToNull(f.phone()));
        b.setEmail(blankToNull(f.email()));
        if (f.status() != null) {
            b.setStatus(f.status());
        }
    }

    private Branch require(Long id) {
        Branch b = branches.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_002));
        TenantGuard.assertSameCompany(b.getCompanyId(), EEmployeeError.EMP_002);
        return b;
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
