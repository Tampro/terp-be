package com.terp.erp.employee.lifecycle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.client.IamClient;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EEmployeeStatus;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.lifecycle.ResignationService.ResignationForm;
import com.terp.erp.employee.lifecycle.TerminationService.TerminationForm;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeparationTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final IamClient iam = mock(IamClient.class);
    private final LifecycleCatalogRepository items = mock(LifecycleCatalogRepository.class);
    private final EmployeeResignationRepository resignations = mock(EmployeeResignationRepository.class);
    private final EmployeeTerminationRepository terminations = mock(EmployeeTerminationRepository.class);
    private final LifecycleSupport support = new LifecycleSupport(employees, mock(MediaClient.class));
    private final EmployeeExit exit = new EmployeeExit(iam);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneOffset.UTC);
    private final LifecycleCatalogService catalogs = new LifecycleCatalogService(items,
            mock(EmployeeWarningRepository.class), terminations, mock(TripExpenseRepository.class),
            mock(EmployeeComplaintRepository.class));
    private final ResignationService resignationService = new ResignationService(resignations, support, exit, clock);
    private final TerminationService terminationService = new TerminationService(terminations, catalogs, items,
            support, exit, clock);

    private Employee employee;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "LIFECYCLE_MANAGE");
        employee = new Employee();
        employee.setId(1L);
        employee.setCompanyId(10L);
        employee.setFullName("Test One");
        employee.setStatus(EEmployeeStatus.ACTIVE);
        employee.setUserId(77L);
        employee.setLoginEnabled(true);
        when(employees.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(employee));
        when(employees.findAllById(any())).thenReturn(List.of(employee));
        when(resignations.save(any())).thenAnswer(i -> {
            EmployeeResignation r = i.getArgument(0);
            r.setId(9L);
            return r;
        });
        LifecycleCatalog type = new LifecycleCatalog();
        type.setId(5L);
        type.setCompanyId(10L);
        type.setKind(ELifecycleCatalogKind.TERMINATION_TYPE);
        type.setName("Layoff");
        when(items.findByIdAndKindAndDeletedFalse(5L, ELifecycleCatalogKind.TERMINATION_TYPE))
                .thenReturn(Optional.of(type));
        when(items.findAllById(any())).thenReturn(List.of(type));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void completingTerminatesTheEmployeeAndLocksTheRecord() {
        EmployeeResignation r = existingResignation();
        var done = resignationService.changeStatus(9L, new ResignationService.StatusForm(
                EmployeeResignation.EStatus.COMPLETED, true, DAY, " Good team "));
        verify(iam).setLoginEnabled(77L, false);
        assertThat(employee.getStatus()).isEqualTo(EEmployeeStatus.TERMINATED);
        assertThat(employee.isLoginEnabled()).isFalse();
        assertThat(done.completedAt()).isEqualTo(clock.instant());
        assertThat(done.exitInterview().feedback()).isEqualTo("Good team");
        assertCode(() -> resignationService.delete(9L), EEmployeeError.EMP_057);
        assertCode(() -> resignationService.changeStatus(9L, new ResignationService.StatusForm(
                EmployeeResignation.EStatus.PENDING, false, null, null)), EEmployeeError.EMP_057);
        assertThat(r.isDeleted()).isFalse();
        // a new record for someone who has left is refused
        assertCode(() -> resignationService.create(new ResignationForm(1L, DAY, null, null, null, null, null)),
                EEmployeeError.EMP_058);
    }

    @Test
    void iamFailureLeavesTheEmployeeEmployed() {
        existingResignation();
        doThrow(new IllegalStateException("IAM down")).when(iam).setLoginEnabled(anyLong(), anyBoolean());
        assertThatThrownBy(() -> resignationService.changeStatus(9L, new ResignationService.StatusForm(
                EmployeeResignation.EStatus.COMPLETED, false, null, null))).isInstanceOf(IllegalStateException.class);
        assertThat(employee.getStatus()).isEqualTo(EEmployeeStatus.ACTIVE);
    }

    @Test
    void otherStatusesAndNoLoginDoNotCallIam() {
        EmployeeResignation r = existingResignation();
        var approved = resignationService.changeStatus(9L, new ResignationService.StatusForm(
                EmployeeResignation.EStatus.APPROVED, false, DAY, "dropped"));
        assertThat(approved.exitInterview().date()).isNull();
        assertThat(approved.exitInterview().feedback()).isNull();
        employee.setUserId(null);
        resignationService.changeStatus(9L, new ResignationService.StatusForm(
                EmployeeResignation.EStatus.COMPLETED, false, null, null));
        verify(iam, never()).setLoginEnabled(anyLong(), anyBoolean());
        assertThat(r.getStatus()).isEqualTo(EmployeeResignation.EStatus.COMPLETED);
    }

    @Test
    void formRules() {
        assertCode(() -> resignationService.create(new ResignationForm(1L, DAY, DAY.minusDays(1), null, null, null,
                null)), EEmployeeError.EMP_052);
        assertCode(() -> terminationService.create(new TerminationForm(1L, 5L, DAY, DAY.minusDays(1), null, null,
                null, null)), EEmployeeError.EMP_052);
        assertCode(() -> terminationService.create(new TerminationForm(1L, 6L, null, DAY, null, null, null, null)),
                EEmployeeError.EMP_050);
        when(terminations.save(any())).thenAnswer(i -> {
            EmployeeTermination t = i.getArgument(0);
            t.setId(8L);
            return t;
        });
        var t = terminationService.create(new TerminationForm(1L, 5L, DAY, DAY.plusDays(30), " 30 days ", null, null,
                null));
        assertThat(t.status()).isEqualTo(EmployeeTermination.EStatus.PLANNED);
        assertThat(t.terminationType().name()).isEqualTo("Layoff");
        assertThat(t.noticePeriod()).isEqualTo("30 days");
    }

    @Test
    void terminationTypeInUseCannotBeDeleted() {
        when(terminations.existsByTerminationTypeIdAndDeletedFalse(5L)).thenReturn(true);
        assertCode(() -> catalogs.delete(ELifecycleCatalogKind.TERMINATION_TYPE, 5L), EEmployeeError.EMP_010);
    }

    private EmployeeResignation existingResignation() {
        EmployeeResignation r = new EmployeeResignation();
        r.setId(9L);
        r.setCompanyId(10L);
        r.setEmployeeId(1L);
        r.setResignationDate(DAY);
        when(resignations.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(r));
        return r;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
