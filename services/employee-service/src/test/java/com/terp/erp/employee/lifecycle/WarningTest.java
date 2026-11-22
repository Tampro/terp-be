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
import com.terp.erp.employee.lifecycle.EmployeeWarning.EStatus;
import com.terp.erp.employee.lifecycle.LifecycleCatalogService.CatalogForm;
import com.terp.erp.employee.lifecycle.WarningService.PlanForm;
import com.terp.erp.employee.lifecycle.WarningService.ResponseForm;
import com.terp.erp.employee.lifecycle.WarningService.StatusForm;
import com.terp.erp.employee.lifecycle.WarningService.WarningForm;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.exception.ApiException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WarningTest {
    /** 2026-11-19 17:30 UTC = 2026-11-20 in Vietnam. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final LifecycleCatalogRepository items = mock(LifecycleCatalogRepository.class);
    private final EmployeeWarningRepository warnings = mock(EmployeeWarningRepository.class);
    private final LifecycleSupport support = new LifecycleSupport(employees, mock(MediaClient.class));
    private final LifecycleCatalogService catalogs = new LifecycleCatalogService(items, warnings,
            mock(EmployeeTerminationRepository.class), mock(TripExpenseRepository.class),
            mock(EmployeeComplaintRepository.class));
    private final WarningService service = new WarningService(warnings, catalogs, items, support,
            Clock.fixed(Instant.parse("2026-11-19T17:30:00Z"), ZoneOffset.UTC));

    private LifecycleCatalog type;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "LIFECYCLE_MANAGE");
        when(employees.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(employee(1L, 10L)));
        when(employees.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(employee(2L, 10L)));
        when(employees.findAllById(any())).thenReturn(List.of(employee(1L, 10L), employee(2L, 10L)));
        type = item(5L, ELifecycleCatalogKind.WARNING_TYPE, 10L);
        when(items.findByIdAndKindAndDeletedFalse(5L, ELifecycleCatalogKind.WARNING_TYPE)).thenReturn(Optional.of(type));
        when(items.findByIdAndKindAndDeletedFalse(6L, ELifecycleCatalogKind.WARNING_SEVERITY))
                .thenReturn(Optional.of(item(6L, ELifecycleCatalogKind.WARNING_SEVERITY, 10L)));
        when(warnings.save(any())).thenAnswer(i -> {
            EmployeeWarning w = i.getArgument(0);
            w.setId(9L);
            return w;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRules() {
        assertCode(() -> service.create(form(1L, 5L, 6L, TODAY.minusDays(1), false)), EEmployeeError.EMP_053);
        // a severity id used as a type is not in the type list
        assertCode(() -> service.create(form(2L, 6L, 6L, null, false)), EEmployeeError.EMP_050);
        assertCode(() -> service.create(new WarningForm(1L, 2L, 5L, 6L, "Late", TODAY, null, null,
                TODAY.minusDays(1), false, null, null, null)), EEmployeeError.EMP_052);
        type.setStatus(EOrgStatus.INACTIVE);
        assertCode(() -> service.create(form(2L, 5L, 6L, null, false)), EEmployeeError.EMP_054);
        type.setStatus(EOrgStatus.ACTIVE);

        var d = service.create(form(2L, 5L, 6L, null, true));
        assertThat(d.status()).isEqualTo(EStatus.PENDING);
        assertThat(d.improvementPlan().hasImprovementPlan()).isTrue();
        assertThat(d.improvementPlan().goals()).isEqualTo("Be on time");
    }

    @Test
    void decidedOnceFromPendingAndExpiryFlaggedOnlyWhenApproved() {
        EmployeeWarning w = existing();
        w.setExpiryDate(TODAY.minusDays(1));
        assertThat(service.get(9L).expired()).isFalse();
        assertCode(() -> service.changeStatus(9L, new StatusForm(EStatus.PENDING)), EEmployeeError.EMP_096);
        assertThat(service.changeStatus(9L, new StatusForm(EStatus.APPROVED)).expired()).isTrue();
        assertCode(() -> service.changeStatus(9L, new StatusForm(EStatus.REJECTED)), EEmployeeError.EMP_096);
        assertCode(() -> service.changeStatus(9L, new StatusForm(EStatus.APPROVED)), EEmployeeError.EMP_096);
        w.setStatus(EStatus.REJECTED);
        assertCode(() -> service.changeStatus(9L, new StatusForm(EStatus.APPROVED)), EEmployeeError.EMP_096);
    }

    @Test
    void onlyTheWarnedEmployeeRespondsAndOnlyWhilePending() {
        EmployeeWarning w = existing();
        Employee me = employee(1L, 10L);
        // TestLogin user id is 5
        when(employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(me));
        TestLogin.as(10L);
        var d = service.respond(9L, new ResponseForm(" I will improve "));
        assertThat(d.employeeResponse()).isEqualTo("I will improve");
        assertThat(d.acknowledgmentDate()).isEqualTo(TODAY);

        // another employee of the company: the warning reads as not found
        when(employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(employee(2L, 10L)));
        assertCode(() -> service.respond(9L, new ResponseForm("x")), EEmployeeError.EMP_051);
        // no profile at all
        when(employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.empty());
        assertCode(() -> service.respond(9L, new ResponseForm("x")), EEmployeeError.EMP_041);

        when(employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(me));
        w.setStatus(EStatus.APPROVED);
        assertCode(() -> service.respond(9L, new ResponseForm("late")), EEmployeeError.EMP_097);
        assertThat(w.getEmployeeResponse()).isEqualTo("I will improve");
    }

    @Test
    void anotherCompanysWarningIsNotFound() {
        existing();
        TestLogin.as(11L, "LIFECYCLE_MANAGE");
        assertCode(() -> service.changeStatus(9L, new StatusForm(EStatus.APPROVED)), EEmployeeError.EMP_051);
        when(employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(11L, 5L)).thenReturn(Optional.of(employee(1L, 11L)));
        assertCode(() -> service.respond(9L, new ResponseForm("x")), EEmployeeError.EMP_051);
    }

    @Test
    void planOffClearsIt() {
        EmployeeWarning w = existing();
        service.updatePlan(9L, new PlanForm(true, "Goals", TODAY, TODAY.plusDays(30), "Half done"));
        assertThat(w.getImprovementPlanProgress()).isEqualTo("Half done");
        assertCode(() -> service.updatePlan(9L, new PlanForm(true, "Goals", TODAY, TODAY.minusDays(1), null)),
                EEmployeeError.EMP_052);
        service.updatePlan(9L, new PlanForm(false, "ignored", TODAY, TODAY, "ignored"));
        assertThat(w.isHasImprovementPlan()).isFalse();
        assertThat(w.getImprovementPlanGoals()).isNull();
        assertThat(w.getImprovementPlanProgress()).isNull();
    }

    @Test
    void listEntryInUseCannotBeDeletedAndNamesAreUniquePerList() {
        when(warnings.existsBySeverityIdAndDeletedFalse(6L)).thenReturn(true);
        when(items.findByIdAndKindAndDeletedFalse(6L, ELifecycleCatalogKind.WARNING_SEVERITY))
                .thenReturn(Optional.of(item(6L, ELifecycleCatalogKind.WARNING_SEVERITY, 10L)));
        assertCode(() -> catalogs.delete(ELifecycleCatalogKind.WARNING_SEVERITY, 6L), EEmployeeError.EMP_010);
        when(items.nameTaken(10L, ELifecycleCatalogKind.WARNING_TYPE, "Late", -1L)).thenReturn(true);
        assertCode(() -> catalogs.create(ELifecycleCatalogKind.WARNING_TYPE, null, new CatalogForm(" Late ", null,
                null)), EEmployeeError.EMP_011);
        assertCode(() -> ELifecycleCatalogKind.ofPath("nope"), EEmployeeError.EMP_050);
    }

    private EmployeeWarning existing() {
        EmployeeWarning w = new EmployeeWarning();
        w.setId(9L);
        w.setCompanyId(10L);
        w.setEmployeeId(1L);
        w.setWarningTypeId(5L);
        w.setSeverityId(6L);
        w.setSubject("Late");
        w.setWarningDate(TODAY.minusDays(10));
        when(warnings.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(w));
        return w;
    }

    private static WarningForm form(Long warnedBy, Long typeId, Long severityId, LocalDate expiry, boolean plan) {
        return new WarningForm(1L, warnedBy, typeId, severityId, " Late arrival ", TODAY, null, null, expiry, plan,
                "Be on time", TODAY, TODAY.plusDays(30));
    }

    private static Employee employee(Long id, Long companyId) {
        Employee e = new Employee();
        e.setId(id);
        e.setCompanyId(companyId);
        e.setFullName("Test " + id);
        return e;
    }

    private static LifecycleCatalog item(Long id, ELifecycleCatalogKind kind, Long companyId) {
        LifecycleCatalog c = new LifecycleCatalog();
        c.setId(id);
        c.setKind(kind);
        c.setCompanyId(companyId);
        c.setName(kind.label());
        return c;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
