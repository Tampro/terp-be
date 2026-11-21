package com.terp.erp.employee.training;

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
import com.terp.erp.employee.org.BranchRepository;
import com.terp.erp.employee.org.DepartmentRepository;
import com.terp.erp.employee.org.DesignationRepository;
import com.terp.erp.employee.training.EmployeeTraining.EStatus;
import com.terp.erp.employee.training.EmployeeTrainingService.BulkForm;
import com.terp.erp.employee.training.EmployeeTrainingService.TrainingForm;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeTrainingTest {
    /** 2026-11-30 18:00 UTC = 2026-12-01 in Vietnam. */
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 1);

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final EmployeeTrainingRepository trainingRepo = mock(EmployeeTrainingRepository.class);
    private final TrainingProgramRepository programRepo = mock(TrainingProgramRepository.class);
    private final TrainingTypeRepository typeRepo = mock(TrainingTypeRepository.class);
    private final LifecycleSupport support = new LifecycleSupport(employees, mock(MediaClient.class));
    private final TrainingProgramService programs = new TrainingProgramService(programRepo, typeRepo,
            new TrainingTypeService(typeRepo, programRepo, mock(BranchRepository.class),
                    mock(DepartmentRepository.class)), support, trainingRepo, mock(TrainingSessionRepository.class),
            mock(TrainingAssessmentRepository.class));
    private final EmployeeTrainingService service = new EmployeeTrainingService(trainingRepo, programRepo, typeRepo,
            programs, employees, mock(DepartmentRepository.class), mock(DesignationRepository.class), support,
            Clock.fixed(Instant.parse("2026-11-30T18:00:00Z"), ZoneOffset.UTC), mock(AssessmentResultRepository.class),
            mock(TrainingAssessmentService.class));

    private TrainingProgram program;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "TRAINING_MANAGE");
        for (long id = 1; id <= 3; id++) {
            Employee e = new Employee();
            e.setId(id);
            e.setCompanyId(10L);
            e.setFullName("Test " + id);
            e.setStatus(id == 3 ? EEmployeeStatus.TERMINATED : EEmployeeStatus.ACTIVE);
            when(employees.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(e));
        }
        when(employees.findAllById(any())).thenReturn(List.of());
        program = new TrainingProgram();
        program.setId(5L);
        program.setCompanyId(10L);
        program.setTrainingTypeId(4L);
        program.setName("Basics");
        program.setCapacity(2);
        when(programRepo.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(program));
        when(programRepo.findAllById(any())).thenReturn(List.of(program));
        when(typeRepo.findAllById(any())).thenReturn(List.of());
        when(trainingRepo.save(any())).thenAnswer(i -> {
            EmployeeTraining t = i.getArgument(0);
            t.setId(9L);
            when(trainingRepo.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(t));
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignRules() {
        assertCode(() -> service.assign(form(3L, null, null)), EEmployeeError.EMP_058);
        assertCode(() -> service.assign(form(1L, null, TODAY.minusDays(1))), EEmployeeError.EMP_052);
        when(trainingRepo.existsByProgramIdAndEmployeeIdAndDeletedFalseAndIdNot(5L, 1L, -1L)).thenReturn(true);
        assertCode(() -> service.assign(form(1L, null, null)), EEmployeeError.EMP_085);
        when(trainingRepo.seatsTaken(5L, -1L)).thenReturn(2L);
        assertCode(() -> service.assign(form(2L, null, null)), EEmployeeError.EMP_084);
        when(trainingRepo.seatsTaken(5L, -1L)).thenReturn(1L);
        program.setStatus(TrainingProgram.EStatus.CANCELLED);
        assertCode(() -> service.assign(form(2L, null, null)), EEmployeeError.EMP_083);
        program.setStatus(TrainingProgram.EStatus.ACTIVE);

        var t = service.assign(form(2L, EStatus.COMPLETED, null));
        assertThat(t.completionDate()).isEqualTo(TODAY);
        assertThat(t.result()).isEqualTo(EmployeeTrainingService.EResult.PASSED);
        // the existing assignment of a now-cancelled program can still be edited
        program.setStatus(TrainingProgram.EStatus.CANCELLED);
        assertThat(service.update(9L, form(2L, null, null)).status()).isEqualTo(EStatus.COMPLETED);
    }

    @Test
    void bulkSkipsAlreadyAssignedAndChecksSeats() {
        EmployeeTraining existing = new EmployeeTraining();
        existing.setEmployeeId(1L);
        when(trainingRepo.findByProgramIdAndEmployeeIdInAndDeletedFalse(eq(5L), any())).thenReturn(List.of(existing));
        when(trainingRepo.seatsTaken(5L, -1L)).thenReturn(1L);
        var r = service.bulkAssign(new BulkForm(Set.of(1L, 2L), 5L, TODAY, " Q1 "));
        assertThat(r.created()).isEqualTo(1);
        assertThat(r.skippedEmployeeIds()).containsExactly("1");
        verify(trainingRepo, times(1)).save(any());
        when(trainingRepo.seatsTaken(anyLong(), anyLong())).thenReturn(2L);
        assertCode(() -> service.bulkAssign(new BulkForm(Set.of(1L, 2L), 5L, TODAY, null)), EEmployeeError.EMP_084);
        assertCode(() -> service.bulkAssign(new BulkForm(Set.of(3L), 5L, TODAY, null)), EEmployeeError.EMP_058);
    }

    @Test
    void ratesAndProgramDeleteGuard() {
        assertThat(EmployeeTrainingService.rate(1, 3)).isEqualTo(33);
        assertThat(EmployeeTrainingService.rate(2, 3)).isEqualTo(67);
        assertThat(EmployeeTrainingService.rate(0, 0)).isZero();
        when(trainingRepo.existsByProgramIdAndDeletedFalse(5L)).thenReturn(true);
        assertCode(() -> programs.delete(5L), EEmployeeError.EMP_010);
    }

    private static TrainingForm form(Long employeeId, EStatus status, LocalDate completion) {
        return new TrainingForm(employeeId, 5L, status, TODAY, completion, null, new BigDecimal("85"), true, null,
                null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
