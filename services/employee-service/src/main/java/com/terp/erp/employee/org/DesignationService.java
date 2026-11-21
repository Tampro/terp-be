package com.terp.erp.employee.org;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.org.DesignationDtos.DesignationDto;
import com.terp.erp.employee.org.DesignationDtos.DesignationForm;
import com.terp.erp.employee.org.DesignationDtos.DesignationOption;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Designations page (docs/analysis/06 §6.3.3). A designation belongs to one department of the same company. Inactive
 * ones keep their employees but cannot be newly chosen (EMP_031); one still used cannot be deleted or moved to another
 * department (EMP_010).
 */
@Service
@RequiredArgsConstructor
public class DesignationService {
    private final DesignationRepository designations;
    private final DepartmentRepository departments;
    private final BranchRepository branches;
    private final EmployeeRepository employees;

    @Transactional(readOnly = true)
    public Page<DesignationDto> search(Long requestedCompanyId, String keyword, Long departmentId, EOrgStatus status,
                                       Pageable pageable) {
        Long companyId = company(requestedCompanyId);
        Page<Designation> page = designations.search(companyId, departmentId, status, DepartmentService.kw(keyword),
                pageable);
        Map<Long, Department> deps = departments.findByCompanyIdAndIdInAndDeletedFalse(companyId,
                        page.getContent().stream().map(Designation::getDepartmentId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Department::getId, Function.identity()));
        Set<Long> branchIds = deps.values().stream().map(Department::getBranchId).collect(Collectors.toSet());
        Map<Long, String> branchNames = branches.findByCompanyIdAndIdInAndDeletedFalse(companyId, branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName));
        return page.map(d -> {
            Department dep = deps.get(d.getDepartmentId());
            return DesignationDto.of(d, dep, dep == null ? null : branchNames.get(dep.getBranchId()), null);
        });
    }

    @Transactional(readOnly = true)
    public List<DesignationOption> options(Long requestedCompanyId, Long departmentId) {
        Long companyId = company(requestedCompanyId);
        List<Designation> list = departmentId == null
                ? designations.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(companyId, EOrgStatus.ACTIVE)
                : designations.findByCompanyIdAndDepartmentIdAndStatusAndDeletedFalseOrderByNameAsc(companyId,
                departmentId, EOrgStatus.ACTIVE);
        return list.stream()
                .map(d -> new DesignationOption(d.getId().toString(), d.getName(), d.getDepartmentId().toString()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DesignationDto get(Long id) {
        Designation d = require(id);
        return dto(d, employees.countByDesignationIdAndDeletedFalse(id));
    }

    @Transactional
    public DesignationDto create(DesignationForm form) {
        Department dep = requireDepartment(form.departmentId(), null);
        Designation d = new Designation();
        d.setCompanyId(dep.getCompanyId());
        apply(d, dep, form, -1L);
        return DesignationDto.of(designations.save(d), dep, branchName(dep), null);
    }

    /** Full replacement. Moving to another department is refused while an employee uses it. */
    @Transactional
    public DesignationDto update(Long id, DesignationForm form) {
        Designation d = require(id);
        Department dep = requireDepartment(form.departmentId(), d.getDepartmentId());
        if (!Objects.equals(dep.getCompanyId(), d.getCompanyId())) {
            throw ApiException.notFound(EEmployeeError.EMP_003);
        }
        if (!Objects.equals(d.getDepartmentId(), dep.getId()) && employees.existsByDesignationIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        apply(d, dep, form, id);
        return DesignationDto.of(d, dep, branchName(dep), null);
    }

    @Transactional
    public DesignationDto changeStatus(Long id, EOrgStatus status) {
        Designation d = require(id);
        d.setStatus(status);
        return dto(d, null);
    }

    @Transactional
    public void delete(Long id) {
        Designation d = require(id);
        if (employees.existsByDesignationIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        d.setDeleted(true);
    }

    private void apply(Designation d, Department dep, DesignationForm f, Long selfId) {
        String name = f.name().trim();
        if (designations.existsByDepartmentIdAndNameAndDeletedFalseAndIdNot(dep.getId(), name, selfId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        d.setDepartmentId(dep.getId());
        d.setName(name);
        d.setDescription(f.description() == null || f.description().isBlank() ? null : f.description().trim());
        if (f.status() != null) {
            d.setStatus(f.status());
        }
    }

    /** The department must be of the caller's company; choosing an inactive one is refused, keeping it is not. */
    private Department requireDepartment(Long departmentId, Long currentDepartmentId) {
        Department dep = departments.findByIdAndDeletedFalse(departmentId)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_003));
        TenantGuard.assertSameCompany(dep.getCompanyId(), EEmployeeError.EMP_003);
        if (dep.getStatus() == EOrgStatus.INACTIVE && !Objects.equals(departmentId, currentDepartmentId)) {
            throw ApiException.badRequest(EEmployeeError.EMP_030);
        }
        return dep;
    }

    private Designation require(Long id) {
        Designation d = designations.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_004));
        TenantGuard.assertSameCompany(d.getCompanyId(), EEmployeeError.EMP_004);
        return d;
    }

    private DesignationDto dto(Designation d, Long employeeCount) {
        Department dep = departments.findByIdAndDeletedFalse(d.getDepartmentId()).orElse(null);
        return DesignationDto.of(d, dep, dep == null ? null : branchName(dep), employeeCount);
    }

    private String branchName(Department dep) {
        return branches.findByIdAndDeletedFalse(dep.getBranchId()).map(Branch::getName).orElse(null);
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }
}
