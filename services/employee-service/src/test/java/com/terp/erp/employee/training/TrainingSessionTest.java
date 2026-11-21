package com.terp.erp.employee.training;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import com.terp.erp.employee.training.TrainingSession.ELocationType;
import com.terp.erp.employee.training.TrainingSessionService.AttendanceItem;
import com.terp.erp.employee.training.TrainingSessionService.EFrequency;
import com.terp.erp.employee.training.TrainingSessionService.Recurrence;
import com.terp.erp.employee.training.TrainingSessionService.SessionForm;
import com.terp.erp.exception.ApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingSessionTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 31, 9, 0);

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final TrainingSessionRepository sessionRepo = mock(TrainingSessionRepository.class);
    private final TrainingAttendanceRepository attendanceRepo = mock(TrainingAttendanceRepository.class);
    private final EmployeeTrainingRepository trainingRepo = mock(EmployeeTrainingRepository.class);
    private final TrainingProgramRepository programRepo = mock(TrainingProgramRepository.class);
    private final TrainingTypeRepository typeRepo = mock(TrainingTypeRepository.class);
    private final LifecycleSupport support = new LifecycleSupport(employees, mock(MediaClient.class));
    private final TrainingProgramService programs = new TrainingProgramService(programRepo, typeRepo,
            new TrainingTypeService(typeRepo, programRepo, mock(BranchRepository.class),
                    mock(DepartmentRepository.class)), support, trainingRepo, sessionRepo, mock(TrainingAssessmentRepository.class));
    private final TrainingSessionService service = new TrainingSessionService(sessionRepo, attendanceRepo,
            trainingRepo, programRepo, programs, support);

    private TrainingProgram program;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "TRAINING_MANAGE");
        for (long id = 1; id <= 2; id++) {
            Employee e = new Employee();
            e.setId(id);
            e.setCompanyId(10L);
            e.setFullName("Test " + id);
            e.setStatus(id == 2 ? EEmployeeStatus.TERMINATED : EEmployeeStatus.ACTIVE);
            when(employees.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(e));
        }
        when(employees.findAllById(any())).thenReturn(List.of());
        program = new TrainingProgram();
        program.setId(5L);
        program.setCompanyId(10L);
        program.setName("Basics");
        program.setStatus(TrainingProgram.EStatus.ACTIVE);
        when(programRepo.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(program));
        when(programRepo.findAllById(any())).thenReturn(List.of(program));
        when(sessionRepo.save(any())).thenAnswer(i -> {
            TrainingSession s = i.getArgument(0);
            if (s.getId() == null) {
                s.setId(9L);
            }
            when(sessionRepo.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(s));
            return s;
        });
        EmployeeTraining trainee = new EmployeeTraining();
        trainee.setEmployeeId(1L);
        when(trainingRepo.trainees(5L)).thenReturn(List.of(trainee));
        when(attendanceRepo.findBySessionId(anyLong())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRules() {
        assertCode(() -> service.create(form(START, null, null)), EEmployeeError.EMP_052);
        assertCode(() -> service.create(form(START.plusHours(3), Set.of(2L), null)), EEmployeeError.EMP_058);
        program.setStatus(TrainingProgram.EStatus.CANCELLED);
        assertCode(() -> service.create(form(START.plusHours(3), null, null)), EEmployeeError.EMP_083);
        program.setStatus(TrainingProgram.EStatus.ACTIVE);
        var r = service.create(form(START.plusHours(3), Set.of(1L), null));
        assertThat(r.created()).isEqualTo(1);
        assertThat(r.session().durationMinutes()).isEqualTo(180);
        assertThat(r.session().attendance()).singleElement().satisfies(a -> assertThat(a.status()).isNull());
    }

    @Test
    void recurringCreatesCopies() {
        var r = service.create(form(START.plusHours(3), null, new Recurrence(EFrequency.MONTHLY, 3)));
        assertThat(r.created()).isEqualTo(3);
        assertThat(r.seriesId()).isNotNull();
        ArgumentCaptor<TrainingSession> saved = ArgumentCaptor.forClass(TrainingSession.class);
        verify(sessionRepo, times(3)).save(saved.capture());
        // Jan 31 monthly: Feb 28, Mar 31 (end of month is kept by plusMonths per step)
        assertThat(saved.getAllValues()).extracting(s -> s.getStartAt().toLocalDate())
                .containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
        assertThat(saved.getAllValues()).extracting(TrainingSession::getSeriesId).containsOnly(r.seriesId());
        assertThat(TrainingSessionService.shift(START, EFrequency.WEEKLY, 2)).isEqualTo(START.plusWeeks(2));
    }

    @Test
    void attendanceOnlyForTrainees() {
        service.create(form(START.plusHours(1), null, null));
        assertCode(() -> service.updateAttendance(9L, List.of(new AttendanceItem(2L,
                TrainingAttendance.EStatus.PRESENT, null))), EEmployeeError.EMP_087);
        service.updateAttendance(9L, List.of(new AttendanceItem(1L, TrainingAttendance.EStatus.PRESENT, " On time ")));
        verify(attendanceRepo).save(any());
    }

    @Test
    void calendarRangeAndProgramDeleteGuard() {
        assertCode(() -> service.calendar(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30), null, null),
                EEmployeeError.EMP_088);
        assertCode(() -> service.calendar(null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, null),
                EEmployeeError.EMP_052);
        when(sessionRepo.existsByProgramIdAndDeletedFalse(5L)).thenReturn(true);
        assertCode(() -> programs.delete(5L), EEmployeeError.EMP_010);
    }

    private static SessionForm form(LocalDateTime end, Set<Long> trainers, Recurrence rec) {
        return new SessionForm(5L, " Module 1 ", START, end, ELocationType.IN_PERSON, "Room 3", null, null, null,
                trainers, rec);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
