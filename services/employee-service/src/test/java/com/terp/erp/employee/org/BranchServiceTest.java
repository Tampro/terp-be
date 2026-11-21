package com.terp.erp.employee.org;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.org.BranchDtos.BranchForm;
import com.terp.erp.exception.ApiException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BranchServiceTest {
    private final BranchRepository branches = mock(BranchRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final BranchService service = new BranchService(branches, departments, employees);
    private final OrgService orgs = new OrgService(branches, departments, mock(DesignationRepository.class),
            mock(DocumentTypeRepository.class));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createDefaultsToActiveAndTrimsFields() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(branches.save(any())).thenAnswer(i -> {
            Branch b = i.getArgument(0);
            b.setId(1L);
            return b;
        });
        var d = service.create(null, new BranchForm(" HQ ", "  ", "Ha Noi", null, null, null, "024", "hq@example.com", null));
        assertThat(d.name()).isEqualTo("HQ");
        assertThat(d.address()).isNull();
        assertThat(d.status()).isEqualTo(EOrgStatus.ACTIVE);
        assertThat(d.companyId()).isEqualTo("10");
    }

    @Test
    void keywordIsEscapedAndLowerCased() {
        TestLogin.as(10L, "ORG_VIEW");
        when(branches.search(eq(10L), eq(null), any(), any())).thenReturn(Page.empty());
        service.search(null, " 50%_A ", null, PageRequest.of(0, 20));
        verify(branches).search(10L, null, "%50\\%\\_a%", PageRequest.of(0, 20));
    }

    @Test
    void branchInUseCannotBeDeleted() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(branches.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch(1L, 10L, EOrgStatus.ACTIVE)));
        when(employees.existsByBranchIdAndDeletedFalse(1L)).thenReturn(true);
        assertError(() -> service.delete(1L), EEmployeeError.EMP_010);
    }

    @Test
    void branchOfAnotherCompanyIsNotFound() {
        TestLogin.as(10L, "ORG_VIEW");
        when(branches.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch(1L, 11L, EOrgStatus.ACTIVE)));
        assertError(() -> service.get(1L), EEmployeeError.EMP_002);
    }

    @Test
    void inactiveBranchCannotBeChosenButCanBeKept() {
        TestLogin.as(10L, "EMPLOYEE_UPDATE");
        when(branches.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(branch(1L, 10L, EOrgStatus.INACTIVE)));
        assertError(() -> orgs.assertChain(10L, 1L, null, null, null, null, null), EEmployeeError.EMP_029);
        orgs.assertChain(10L, 1L, null, null, 1L, null, null);
        var deps = new DepartmentService(departments, branches, mock(DesignationRepository.class), employees);
        assertError(() -> deps.create(new DepartmentDtos.DepartmentForm("IT", 1L, null, null)), EEmployeeError.EMP_029);
    }

    @Test
    void optionsListOnlyActiveBranches() {
        TestLogin.as(10L, "EMPLOYEE_VIEW");
        when(branches.findByCompanyIdAndStatusAndDeletedFalseOrderByNameAsc(10L, EOrgStatus.ACTIVE))
                .thenReturn(List.of(branch(2L, 10L, EOrgStatus.ACTIVE)));
        assertThat(service.options(null)).extracting(BranchDtos.BranchOption::id).containsExactly("2");
    }

    private static Branch branch(Long id, Long companyId, EOrgStatus status) {
        Branch b = new Branch();
        b.setId(id);
        b.setCompanyId(companyId);
        b.setName("B" + id);
        b.setStatus(status);
        return b;
    }

    private static void assertError(Runnable call, EEmployeeError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
