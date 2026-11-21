package com.terp.erp.employee.org;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.org.DepartmentDtos.DepartmentDto;
import com.terp.erp.employee.org.DepartmentDtos.DepartmentForm;
import com.terp.erp.employee.org.DepartmentDtos.DepartmentOption;
import com.terp.erp.employee.org.DepartmentDtos.StatusCounts;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Departments page (docs/analysis/06 §6.3.2). A department belongs to one branch of the same company. Inactive ones
 * keep their designations and employees but cannot be newly chosen (EMP_030); a department still used cannot be
 * deleted or moved to another branch (EMP_010).
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departments;
    private final BranchRepository branches;
    private final DesignationRepository designations;
    private final EmployeeRepository employees;

    @Transactional(readOnly = true)
    public Page<DepartmentDto> search(Long requestedCompanyId, String keyword, Long branchId, EOrgStatus status,
                                      Pageable pageable) {
        Long companyId = company(requestedCompanyId);
        Page<Department> page = departments.search(companyId, branchId, status, kw(keyword), pageable);
        Map<Long, String> names = branchNames(companyId, page.getContent().stream().map(Department::getBranchId)
                .collect(Collectors.toSet()));
        return page.map(d -> DepartmentDto.of(d, names.get(d.getBranchId()), null, null));
    }

    /** Counts of the status tabs (All, Active, Inactive) under the keyword and branch filters. */
    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long branchId) {
        long active = 0;
        long inactive = 0;
        for (Object[] row : departments.countByStatus(company(requestedCompanyId), branchId, kw(keyword))) {
            if (row[0] == EOrgStatus.ACTIVE) {
                active = (Long) row[1];
            } else {
                inactive = (Long) row[1];
            }
        }
        return new StatusCounts(active + inactive, active, inactive);
    }

    @Transactional(readOnly = true)
    public List<DepartmentOption> options(Long requestedCompanyId, Long branchId) {
        Long companyId = company(requestedCompanyId);
        List<Department> list = branchId == null
                ? departments.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(companyId, EOrgStatus.ACTIVE)
                : departments.findByCompanyIdAndBranchIdAndStatusAndDeletedFalseOrderByNameAsc(companyId, branchId,
                EOrgStatus.ACTIVE);
        return list.stream().map(d -> new DepartmentOption(d.getId().toString(), d.getName(), d.getBranchId().toString()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentDto get(Long id) {
        Department d = require(id);
        return DepartmentDto.of(d, branchName(d), designations.countByDepartmentIdAndDeletedFalse(id),
                employees.countByDepartmentIdAndDeletedFalse(id));
    }

    @Transactional
    public DepartmentDto create(DepartmentForm form) {
        Branch b = requireBranch(form.branchId(), null);
        Department d = new Department();
        d.setCompanyId(b.getCompanyId());
        apply(d, b, form, -1L);
        return DepartmentDto.of(departments.save(d), b.getName(), null, null);
    }

    /** Full replacement. Moving to another branch is refused while a designation or an employee uses it. */
    @Transactional
    public DepartmentDto update(Long id, DepartmentForm form) {
        Department d = require(id);
        Branch b = requireBranch(form.branchId(), d.getBranchId());
        if (!Objects.equals(b.getCompanyId(), d.getCompanyId())) {
            throw ApiException.notFound(EEmployeeError.EMP_002);
        }
        if (!Objects.equals(d.getBranchId(), b.getId())
                && (designations.existsByDepartmentIdAndDeletedFalse(id) || employees.existsByDepartmentIdAndDeletedFalse(id))) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        apply(d, b, form, id);
        return DepartmentDto.of(d, b.getName(), null, null);
    }

    @Transactional
    public DepartmentDto changeStatus(Long id, EOrgStatus status) {
        Department d = require(id);
        d.setStatus(status);
        return DepartmentDto.of(d, branchName(d), null, null);
    }

    @Transactional
    public void delete(Long id) {
        Department d = require(id);
        if (designations.existsByDepartmentIdAndDeletedFalse(id) || employees.existsByDepartmentIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        d.setDeleted(true);
    }

    private void apply(Department d, Branch b, DepartmentForm f, Long selfId) {
        String name = f.name().trim();
        if (departments.existsByBranchIdAndNameAndDeletedFalseAndIdNot(b.getId(), name, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        d.setBranchId(b.getId());
        d.setName(name);
        d.setDescription(f.description() == null || f.description().isBlank() ? null : f.description().trim());
        if (f.status() != null) {
            d.setStatus(f.status());
        }
    }

    /** The branch must be of the caller's company; choosing an inactive one is refused, keeping it is not. */
    private Branch requireBranch(Long branchId, Long currentBranchId) {
        Branch b = branches.findByIdAndDeletedFalse(branchId)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_002));
        TenantGuard.assertSameCompany(b.getCompanyId(), EEmployeeError.EMP_002);
        if (b.getStatus() == EOrgStatus.INACTIVE && !Objects.equals(branchId, currentBranchId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_029);
        }
        return b;
    }

    private Department require(Long id) {
        Department d = departments.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_003));
        TenantGuard.assertSameCompany(d.getCompanyId(), EEmployeeError.EMP_003);
        return d;
    }

    private String branchName(Department d) {
        return branches.findByIdAndDeletedFalse(d.getBranchId()).map(Branch::getName).orElse(null);
    }

    private Map<Long, String> branchNames(Long companyId, Set<Long> ids) {
        return branches.findByCompanyIdAndIdInAndDeletedFalse(companyId, ids).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName));
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }

    /** "!" is the LIKE escape character of the query. */
    public static String kw(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + k + "%";
    }
}
