package com.terp.erp.employee.org;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.org.DesignationDtos.DesignationForm;
import com.terp.erp.exception.ApiException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DesignationServiceTest {
    private final DesignationRepository designations = mock(DesignationRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final DesignationService service = new DesignationService(designations, departments, branches, employees);
    private final OrgService orgs = new OrgService(branches, departments, designations,
            mock(DocumentTypeRepository.class));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createCarriesDepartmentAndBranch() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(departments.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(department(5L, 10L, EOrgStatus.ACTIVE)));
        when(branches.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch()));
        when(designations.save(any())).thenAnswer(i -> {
            Designation d = i.getArgument(0);
            d.setId(7L);
            return d;
        });
        var d = service.create(new DesignationForm(" Dev ", 5L, " Writes code ", null));
        assertThat(d.name()).isEqualTo("Dev");
        assertThat(d.description()).isEqualTo("Writes code");
        assertThat(d.department().name()).isEqualTo("D5");
        assertThat(d.branch().name()).isEqualTo("HQ");
        assertThat(d.status()).isEqualTo(EOrgStatus.ACTIVE);
    }

    @Test
    void departmentOfAnotherCompanyIsNotFound() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(departments.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(department(5L, 11L, EOrgStatus.ACTIVE)));
        assertError(() -> service.create(new DesignationForm("Dev", 5L, null, null)), EEmployeeError.EMP_003);
    }

    @Test
    void nameTakenInDepartment() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(departments.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(department(5L, 10L, EOrgStatus.ACTIVE)));
        when(designations.existsByDepartmentIdAndNameAndDeletedFalseAndIdNot(5L, "Dev", -1L)).thenReturn(true);
        assertError(() -> service.create(new DesignationForm("Dev", 5L, null, null)), EEmployeeError.EMP_011);
    }

    @Test
    void moveToAnotherDepartmentRefusedWhileInUse() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(designations.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(designation(EOrgStatus.ACTIVE)));
        when(departments.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(department(6L, 10L, EOrgStatus.ACTIVE)));
        when(employees.existsByDesignationIdAndDeletedFalse(7L)).thenReturn(true);
        assertError(() -> service.update(7L, new DesignationForm("Dev", 6L, null, null)), EEmployeeError.EMP_010);
    }

    @Test
    void deleteRefusedWhileInUse() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(designations.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(designation(EOrgStatus.ACTIVE)));
        when(employees.existsByDesignationIdAndDeletedFalse(7L)).thenReturn(true);
        assertError(() -> service.delete(7L), EEmployeeError.EMP_010);
    }

    @Test
    void otherCompanyDesignationIsNotFound() {
        TestLogin.as(11L, "ORG_VIEW");
        when(designations.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(designation(EOrgStatus.ACTIVE)));
        assertError(() -> service.get(7L), EEmployeeError.EMP_004);
    }

    @Test
    void inactiveDesignationCannotBeChosenButIsKept() {
        when(branches.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch()));
        when(departments.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(department(5L, 10L, EOrgStatus.ACTIVE)));
        when(designations.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(designation(EOrgStatus.INACTIVE)));
        assertError(() -> orgs.assertChain(10L, 1L, 5L, 7L, null, null, null), EEmployeeError.EMP_031);
        orgs.assertChain(10L, 1L, 5L, 7L, 1L, 5L, 7L);
    }

    private static Branch branch() {
        Branch b = new Branch();
        b.setId(1L);
        b.setCompanyId(10L);
        b.setName("HQ");
        b.setStatus(EOrgStatus.ACTIVE);
        return b;
    }

    private static Department department(Long id, Long companyId, EOrgStatus status) {
        Department d = new Department();
        d.setId(id);
        d.setCompanyId(companyId);
        d.setBranchId(1L);
        d.setName("D" + id);
        d.setStatus(status);
        return d;
    }

    private static Designation designation(EOrgStatus status) {
        Designation d = new Designation();
        d.setId(7L);
        d.setCompanyId(10L);
        d.setDepartmentId(5L);
        d.setName("Dev");
        d.setStatus(status);
        return d;
    }

    private static void assertError(Runnable call, EEmployeeError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
