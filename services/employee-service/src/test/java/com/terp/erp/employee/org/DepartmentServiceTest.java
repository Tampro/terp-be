package com.terp.erp.employee.org;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.org.DepartmentDtos.DepartmentForm;
import com.terp.erp.exception.ApiException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DepartmentServiceTest {
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final DesignationRepository designations = mock(DesignationRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final DepartmentService service = new DepartmentService(departments, branches, designations, employees);
    private final OrgService orgs = new OrgService(branches, departments, designations,
            mock(DocumentTypeRepository.class));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createInBranchOfTheCompany() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(branches.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch(1L, 10L, EOrgStatus.ACTIVE)));
        when(departments.save(any())).thenAnswer(i -> {
            Department d = i.getArgument(0);
            d.setId(5L);
            return d;
        });
        var d = service.create(new DepartmentForm(" HR ", 1L, " People ", null));
        assertThat(d.name()).isEqualTo("HR");
        assertThat(d.description()).isEqualTo("People");
        assertThat(d.branch().name()).isEqualTo("B1");
        assertThat(d.status()).isEqualTo(EOrgStatus.ACTIVE);
    }

    @Test
    void branchOfAnotherCompanyIsNotFound() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(branches.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch(1L, 11L, EOrgStatus.ACTIVE)));
        assertError(() -> service.create(new DepartmentForm("HR", 1L, null, null)), EEmployeeError.EMP_002);
    }

    @Test
    void moveToAnotherBranchRefusedWhileInUse() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(departments.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(department(5L, 1L, EOrgStatus.ACTIVE)));
        when(branches.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(branch(2L, 10L, EOrgStatus.ACTIVE)));
        when(employees.existsByDepartmentIdAndDeletedFalse(5L)).thenReturn(true);
        assertError(() -> service.update(5L, new DepartmentForm("HR", 2L, null, null)), EEmployeeError.EMP_010);
    }

    @Test
    void statusTabsAddUp() {
        TestLogin.as(10L, "ORG_VIEW");
        when(departments.countByStatus(10L, null, ""))
                .thenReturn(List.of(new Object[]{EOrgStatus.ACTIVE, 3L}, new Object[]{EOrgStatus.INACTIVE, 1L}));
        var c = service.statusCounts(null, null, null);
        assertThat(c.all()).isEqualTo(4);
        assertThat(c.active()).isEqualTo(3);
        assertThat(c.inactive()).isEqualTo(1);
    }

    @Test
    void keywordUsesBangAsEscape() {
        assertThat(DepartmentService.kw(" 50%_!A ")).isEqualTo("%50!%!_!!a%");
        assertThat(DepartmentService.kw("  ")).isEmpty();
    }

    @Test
    void inactiveDepartmentCannotBeChosenOrGetDesignations() {
        TestLogin.as(10L, "EMPLOYEE_UPDATE", "ORG_MANAGE");
        when(branches.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch(1L, 10L, EOrgStatus.ACTIVE)));
        when(departments.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(department(5L, 1L, EOrgStatus.INACTIVE)));
        assertError(() -> orgs.assertChain(10L, 1L, 5L, null, null, null, null), EEmployeeError.EMP_030);
        orgs.assertChain(10L, 1L, 5L, null, 1L, 5L, null);
        var designationService = new DesignationService(designations, departments, branches, employees);
        assertError(() -> designationService.create(new DesignationDtos.DesignationForm("Dev", 5L, null, null)),
                EEmployeeError.EMP_030);
    }

    private static Branch branch(Long id, Long companyId, EOrgStatus status) {
        Branch b = new Branch();
        b.setId(id);
        b.setCompanyId(companyId);
        b.setName("B" + id);
        b.setStatus(status);
        return b;
    }

    private static Department department(Long id, Long branchId, EOrgStatus status) {
        Department d = new Department();
        d.setId(id);
        d.setCompanyId(10L);
        d.setBranchId(branchId);
        d.setName("D" + id);
        d.setStatus(status);
        return d;
    }

    private static void assertError(Runnable call, EEmployeeError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
