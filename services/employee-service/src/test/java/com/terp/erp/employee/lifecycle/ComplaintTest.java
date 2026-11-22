package com.terp.erp.employee.lifecycle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.lifecycle.ComplaintService.AssignForm;
import com.terp.erp.employee.lifecycle.ComplaintService.ComplaintForm;
import com.terp.erp.employee.lifecycle.ComplaintService.FollowUpForm;
import com.terp.erp.employee.lifecycle.ComplaintService.ResolveForm;
import com.terp.erp.employee.lifecycle.EmployeeComplaint.EStatus;
import com.terp.erp.exception.ApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplaintTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final LifecycleCatalogRepository items = mock(LifecycleCatalogRepository.class);
    private final EmployeeComplaintRepository repo = mock(EmployeeComplaintRepository.class);
    private final LifecycleSupport support = new LifecycleSupport(employees, mock(MediaClient.class));
    private final LifecycleCatalogService catalogs = new LifecycleCatalogService(items,
            mock(EmployeeWarningRepository.class), mock(EmployeeTerminationRepository.class),
            mock(TripExpenseRepository.class), repo);
    private final ComplaintService service = new ComplaintService(repo, catalogs, items, support);

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "LIFECYCLE_MANAGE", "LIFECYCLE_VIEW");
        List<Employee> all = List.of(employee(1L), employee(2L), employee(3L));
        for (Employee e : all) {
            when(employees.findByIdAndDeletedFalse(e.getId())).thenReturn(Optional.of(e));
        }
        when(employees.findAllById(any())).thenReturn(all);
        when(items.findByIdAndKindAndDeletedFalse(5L, ELifecycleCatalogKind.COMPLAINT_TYPE))
                .thenReturn(Optional.of(item(5L, ELifecycleCatalogKind.COMPLAINT_TYPE)));
        when(items.findByIdAndKindAndDeletedFalse(6L, ELifecycleCatalogKind.RESOLUTION_TYPE))
                .thenReturn(Optional.of(item(6L, ELifecycleCatalogKind.RESOLUTION_TYPE)));
        when(repo.save(any())).thenAnswer(i -> {
            EmployeeComplaint c = i.getArgument(0);
            c.setId(9L);
            when(repo.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(c));
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousComplainantHiddenWithoutManage() {
        assertCode(() -> service.create(form(1L, 1L, false)), EEmployeeError.EMP_068);
        var c = service.create(form(1L, 2L, true));
        assertThat(c.complainant().id()).isEqualTo("1");
        TestLogin.as(10L, "LIFECYCLE_VIEW");
        var seen = service.get(9L);
        assertThat(seen.anonymous()).isTrue();
        assertThat(seen.complainant()).isNull();
        assertThat(seen.against().id()).isEqualTo("2");
    }

    @Test
    void assignResolveFollowUp() {
        service.create(form(1L, 2L, false));
        assertCode(() -> service.assign(9L, new AssignForm(2L, null)), EEmployeeError.EMP_069);
        assertCode(() -> service.assign(9L, new AssignForm(3L, DAY.minusDays(1))), EEmployeeError.EMP_052);
        var a = service.assign(9L, new AssignForm(3L, DAY.plusDays(7)));
        assertThat(a.status()).isEqualTo(EStatus.ASSIGNED);
        assertThat(a.assignment().assignedTo().id()).isEqualTo("3");
        // the accused cannot become the handler through an edit either
        assertCode(() -> service.update(9L, form(1L, 3L, false)), EEmployeeError.EMP_069);

        assertCode(() -> service.followUp(9L, new FollowUpForm("Check", DAY, null)), EEmployeeError.EMP_070);
        assertCode(() -> service.resolve(9L, new ResolveForm(6L, null, null, DAY.plusDays(2), null,
                DAY.plusDays(1))), EEmployeeError.EMP_052);
        assertCode(() -> service.resolve(9L, new ResolveForm(5L, null, null, DAY, null, null)),
                EEmployeeError.EMP_050);
        var r = service.resolve(9L, new ResolveForm(6L, " Talked ", "Apology", DAY.plusDays(2), "Check in",
                DAY.plusDays(30)));
        assertThat(r.status()).isEqualTo(EStatus.RESOLVED);
        assertThat(r.resolution().investigationNotes()).isEqualTo("Talked");
        var f = service.followUp(9L, new FollowUpForm("Checked", DAY.plusDays(31), "Satisfied"));
        assertThat(f.followUp().feedback()).isEqualTo("Satisfied");
    }

    @Test
    void typesInUseCannotBeDeleted() {
        when(repo.existsByResolutionTypeIdAndDeletedFalse(6L)).thenReturn(true);
        assertCode(() -> catalogs.delete(ELifecycleCatalogKind.RESOLUTION_TYPE, 6L), EEmployeeError.EMP_010);
        when(repo.existsByComplaintTypeIdAndDeletedFalse(5L)).thenReturn(true);
        assertCode(() -> catalogs.delete(ELifecycleCatalogKind.COMPLAINT_TYPE, 5L), EEmployeeError.EMP_010);
    }

    @Test
    void transitionTable() {
        var c = service.create(form(1L, 2L, false));
        assertThat(c.status()).isEqualTo(EStatus.PENDING);
        // PENDING: cannot jump to IN_PROGRESS, cannot be resolved, cannot set ASSIGNED / RESOLVED by plain status
        assertCode(() -> service.changeStatus(9L, EStatus.IN_PROGRESS), EEmployeeError.EMP_096);
        assertCode(() -> service.changeStatus(9L, EStatus.ASSIGNED), EEmployeeError.EMP_096);
        assertCode(() -> service.changeStatus(9L, EStatus.RESOLVED), EEmployeeError.EMP_096);
        assertCode(() -> service.resolve(9L, new ResolveForm(6L, null, null, DAY, null, null)),
                EEmployeeError.EMP_096);
        assertThat(service.changeStatus(9L, EStatus.IN_REVIEW).status()).isEqualTo(EStatus.IN_REVIEW);
        assertCode(() -> service.changeStatus(9L, EStatus.IN_PROGRESS), EEmployeeError.EMP_096);
        assertThat(service.assign(9L, new AssignForm(3L, null)).status()).isEqualTo(EStatus.ASSIGNED);
        assertThat(service.changeStatus(9L, EStatus.IN_PROGRESS).status()).isEqualTo(EStatus.IN_PROGRESS);
        assertThat(service.resolve(9L, new ResolveForm(6L, null, null, DAY, null, null)).status())
                .isEqualTo(EStatus.RESOLVED);
        // RESOLVED is final except for correcting the resolution
        assertCode(() -> service.changeStatus(9L, EStatus.IN_REVIEW), EEmployeeError.EMP_096);
        assertCode(() -> service.assign(9L, new AssignForm(3L, null)), EEmployeeError.EMP_096);
        assertThat(service.resolve(9L, new ResolveForm(6L, "fix", null, DAY, null, null)).status())
                .isEqualTo(EStatus.RESOLVED);
    }

    @Test
    void anotherCompanysComplaintIsNotFoundForStateActions() {
        service.create(form(1L, 2L, false));
        TestLogin.as(11L, "LIFECYCLE_MANAGE");
        assertCode(() -> service.changeStatus(9L, EStatus.IN_REVIEW), EEmployeeError.EMP_067);
        assertCode(() -> service.assign(9L, new AssignForm(3L, null)), EEmployeeError.EMP_067);
    }

    private static ComplaintForm form(Long complainant, Long against, boolean anonymous) {
        return new ComplaintForm(complainant, against, 5L, " Rude remarks ", DAY, null, null, anonymous);
    }

    private static Employee employee(Long id) {
        Employee e = new Employee();
        e.setId(id);
        e.setCompanyId(10L);
        e.setFullName("Test " + id);
        return e;
    }

    private static LifecycleCatalog item(Long id, ELifecycleCatalogKind kind) {
        LifecycleCatalog c = new LifecycleCatalog();
        c.setId(id);
        c.setKind(kind);
        c.setCompanyId(10L);
        c.setName(kind.label());
        return c;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
