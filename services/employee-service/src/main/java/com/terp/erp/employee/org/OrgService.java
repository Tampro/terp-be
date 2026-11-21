package com.terp.erp.employee.org;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Branch → department → designation, each owned by a company. A child must belong to its parent; a row still used
 * by a child or an employee cannot be deleted (EMP_010).
 */
@Service
@RequiredArgsConstructor
public class OrgService {
    private final BranchRepository branches;
    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final DocumentTypeRepository documentTypes;

    // ------------------------------------------------------------------ used by the employee module

    /**
     * Checks that branch ⊃ department ⊃ designation hold and belong to the company. Each level is optional, but a
     * lower level needs the one above it (EMP_023).
     */
    @Transactional(readOnly = true)
    public void assertChain(Long companyId, Long branchId, Long departmentId, Long designationId) {
        assertChain(companyId, branchId, departmentId, designationId, null, null, null);
    }

    /**
     * @param currentBranchId     the employee's branch today: keeping an inactive branch is allowed, choosing one is not
     * @param currentDepartmentId same for the department
     * @param currentDesignationId same for the designation
     */
    @Transactional(readOnly = true)
    public void assertChain(Long companyId, Long branchId, Long departmentId, Long designationId, Long currentBranchId,
                            Long currentDepartmentId, Long currentDesignationId) {
        if ((departmentId != null && branchId == null) || (designationId != null && departmentId == null)) {
            throw ApiException.badRequest(EEmployeeError.EMP_023);
        }
        if (branchId != null) {
            Branch b = branches.findByIdAndDeletedFalse(branchId)
                    .filter(x -> Objects.equals(x.getCompanyId(), companyId))
                    .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_002));
            if (b.getStatus() == EOrgStatus.INACTIVE && !Objects.equals(branchId, currentBranchId)) {
                throw ApiException.badRequest(EEmployeeError.EMP_029);
            }
            if (departmentId != null) {
                Department d = departments.findByIdAndDeletedFalse(departmentId)
                        .filter(x -> Objects.equals(x.getBranchId(), b.getId()))
                        .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_003));
                if (d.getStatus() == EOrgStatus.INACTIVE && !Objects.equals(departmentId, currentDepartmentId)) {
                    throw ApiException.badRequest(EEmployeeError.EMP_030);
                }
                if (designationId != null) {
                    Designation g = designations.findByIdAndDeletedFalse(designationId)
                            .filter(x -> Objects.equals(x.getDepartmentId(), d.getId()))
                            .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_004));
                    if (g.getStatus() == EOrgStatus.INACTIVE && !Objects.equals(designationId, currentDesignationId)) {
                        throw ApiException.badRequest(EEmployeeError.EMP_031);
                    }
                }
            }
        }
    }

    /** Document type usable by the company (system or its own). */
    @Transactional(readOnly = true)
    public DocumentType usableDocumentType(Long companyId, Long id) {
        return documentTypes.findByIdAndDeletedFalse(id)
                .filter(t -> t.getCompanyId() == null || Objects.equals(t.getCompanyId(), companyId))
                .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_005));
    }

    /** Id → name, for lists and exports. */
    @Transactional(readOnly = true)
    public Names names(Long companyId, Collection<Long> branchIds, Collection<Long> departmentIds,
                       Collection<Long> designationIds) {
        return new Names(
                toMap(branches.findByCompanyIdAndIdInAndDeletedFalse(companyId, branchIds), Branch::getId, Branch::getName),
                toMap(departments.findByCompanyIdAndIdInAndDeletedFalse(companyId, departmentIds), Department::getId,
                        Department::getName),
                toMap(designations.findByCompanyIdAndIdInAndDeletedFalse(companyId, designationIds), Designation::getId,
                        Designation::getName));
    }

    /** Whole organization of a company, for the import (names → ids). */
    @Transactional(readOnly = true)
    public Tree tree(Long companyId) {
        return new Tree(branches.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId),
                departments.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId),
                designations.findByCompanyIdAndDeletedFalseOrderByNameAsc(companyId));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> documentTypeNames(Long companyId) {
        return toMap(documentTypes.visibleTo(companyId), DocumentType::getId, DocumentType::getName);
    }

    public record Names(Map<Long, String> branches, Map<Long, String> departments, Map<Long, String> designations) {
    }

    public record Tree(List<Branch> branches, List<Department> departments, List<Designation> designations) {
    }

    // ------------------------------------------------------------------ helpers

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }

    private static <T> Map<Long, String> toMap(List<T> rows, Function<T, Long> id, Function<T, String> name) {
        return rows.stream().collect(Collectors.toMap(id, name, (a, b) -> a));
    }
}
