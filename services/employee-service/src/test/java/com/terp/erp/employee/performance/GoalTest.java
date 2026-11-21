package com.terp.erp.employee.performance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EEmployeeStatus;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.lifecycle.LifecycleSupport;
import com.terp.erp.employee.performance.EmployeeGoal.EStatus;
import com.terp.erp.employee.performance.GoalService.GoalForm;
import com.terp.erp.exception.ApiException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoalTest {
    private static final LocalDate START = LocalDate.of(2026, 12, 1);

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final PerformanceCatalogRepository items = mock(PerformanceCatalogRepository.class);
    private final EmployeeGoalRepository goalRepo = mock(EmployeeGoalRepository.class);
    private final PerformanceCatalogService catalogs = new PerformanceCatalogService(items,
            mock(PerformanceIndicatorRepository.class), goalRepo);
    private final GoalService service = new GoalService(goalRepo, catalogs, items,
            new LifecycleSupport(employees, mock(MediaClient.class)));

    private Employee employee;
    private PerformanceCatalog type;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "PERFORMANCE_MANAGE");
        employee = new Employee();
        employee.setId(1L);
        employee.setCompanyId(10L);
        employee.setFullName("Test One");
        employee.setStatus(EEmployeeStatus.ACTIVE);
        when(employees.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(employee));
        when(employees.findAllById(any())).thenReturn(List.of(employee));
        type = new PerformanceCatalog();
        type.setId(2L);
        type.setCompanyId(10L);
        type.setKind(EPerformanceCatalogKind.GOAL_TYPE);
        type.setName("Sales");
        when(items.findByIdAndKindAndDeletedFalse(2L, EPerformanceCatalogKind.GOAL_TYPE)).thenReturn(Optional.of(type));
        when(items.findAllById(any())).thenReturn(List.of(type));
        when(goalRepo.save(any())).thenAnswer(i -> {
            EmployeeGoal g = i.getArgument(0);
            g.setId(9L);
            when(goalRepo.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(g));
            return g;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRules() {
        assertCode(() -> service.create(form(2L, START.minusDays(1))), EEmployeeError.EMP_052);
        // an indicator category id is not a goal type
        assertCode(() -> service.create(form(3L, START)), EEmployeeError.EMP_071);
        var g = service.create(form(2L, START.plusDays(9)));
        assertThat(g.days()).isEqualTo(10);
        assertThat(g.status()).isEqualTo(EStatus.NOT_STARTED);
        assertThat(g.goalType().name()).isEqualTo("Sales");
        employee.setStatus(EEmployeeStatus.TERMINATED);
        assertCode(() -> service.create(form(2L, START)), EEmployeeError.EMP_058);
        // editing a terminated employee's existing goal stays possible
        assertThat(service.update(9L, form(2L, START)).days()).isEqualTo(1);
    }

    @Test
    void progressMovesTheStatus() {
        service.create(form(2L, START));
        assertThat(service.updateProgress(9L, 30).status()).isEqualTo(EStatus.IN_PROGRESS);
        assertThat(service.updateProgress(9L, 100).status()).isEqualTo(EStatus.COMPLETED);
        assertThat(service.updateProgress(9L, 80).status()).isEqualTo(EStatus.IN_PROGRESS);
    }

    @Test
    void goalTypeInUseCannotBeDeleted() {
        when(goalRepo.existsByGoalTypeIdAndDeletedFalse(2L)).thenReturn(true);
        assertCode(() -> catalogs.delete(EPerformanceCatalogKind.GOAL_TYPE, 2L), EEmployeeError.EMP_010);
    }

    private static GoalForm form(Long typeId, LocalDate end) {
        return new GoalForm(1L, typeId, " Ship billing ", null, START, end, "5 projects", 0, null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
