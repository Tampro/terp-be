package com.terp.erp.employee.training;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.lifecycle.LifecycleSupport;
import com.terp.erp.employee.org.BranchRepository;
import com.terp.erp.employee.org.DepartmentRepository;
import com.terp.erp.employee.training.TrainingAssessment.EType;
import com.terp.erp.employee.training.TrainingAssessmentService.AssessmentForm;
import com.terp.erp.employee.training.TrainingAssessmentService.ResultForm;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingAssessmentTest {
    private final TrainingAssessmentRepository assessmentRepo = mock(TrainingAssessmentRepository.class);
    private final AssessmentResultRepository resultRepo = mock(AssessmentResultRepository.class);
    private final EmployeeTrainingRepository trainingRepo = mock(EmployeeTrainingRepository.class);
    private final TrainingProgramRepository programRepo = mock(TrainingProgramRepository.class);
    private final TrainingTypeRepository typeRepo = mock(TrainingTypeRepository.class);
    private final LifecycleSupport support = new LifecycleSupport(mock(EmployeeRepository.class),
            mock(MediaClient.class));
    private final TrainingProgramService programs = new TrainingProgramService(programRepo, typeRepo,
            new TrainingTypeService(typeRepo, programRepo, mock(BranchRepository.class),
                    mock(DepartmentRepository.class)), support, trainingRepo, mock(TrainingSessionRepository.class),
            assessmentRepo);
    private final TrainingAssessmentService service = new TrainingAssessmentService(assessmentRepo, resultRepo,
            trainingRepo, programRepo, programs, support);

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "TRAINING_MANAGE");
        TrainingProgram p = new TrainingProgram();
        p.setId(5L);
        p.setCompanyId(10L);
        p.setName("Basics");
        when(programRepo.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(p));
        when(programRepo.findById(5L)).thenReturn(Optional.of(p));
        when(assessmentRepo.save(any())).thenAnswer(i -> {
            TrainingAssessment a = i.getArgument(0);
            a.setId(7L);
            when(assessmentRepo.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(a));
            return a;
        });
        EmployeeTraining mine = new EmployeeTraining();
        mine.setId(20L);
        mine.setProgramId(5L);
        mine.setEmployeeId(1L);
        EmployeeTraining other = new EmployeeTraining();
        other.setId(21L);
        other.setProgramId(6L);
        when(trainingRepo.findByIdAndDeletedFalse(20L)).thenReturn(Optional.of(mine));
        when(trainingRepo.findByIdAndDeletedFalse(21L)).thenReturn(Optional.of(other));
        when(trainingRepo.findAllById(any())).thenReturn(List.of(mine));
        when(resultRepo.findByAssessmentIdOrderByScoreDescIdAsc(anyLong())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resultsMustBelongToTheProgramOnce() {
        var a = service.create(new AssessmentForm(5L, " Final quiz ", null, EType.QUIZ, new BigDecimal("70"), null));
        assertThat(a.name()).isEqualTo("Final quiz");
        assertThat(a.statistics().totalResults()).isZero();
        assertThat(a.statistics().averageScore()).isNull();
        assertCode(() -> service.addResult(7L, new ResultForm(21L, BigDecimal.TEN, null, null)),
                EEmployeeError.EMP_090);
        when(resultRepo.existsByAssessmentIdAndEmployeeTrainingIdAndIdNot(eq(7L), eq(20L), any())).thenReturn(true);
        assertCode(() -> service.addResult(7L, new ResultForm(20L, BigDecimal.TEN, null, null)),
                EEmployeeError.EMP_091);
    }

    @Test
    void statisticsAndRegrading() {
        TrainingAssessment a = new TrainingAssessment();
        a.setPassingScore(new BigDecimal("70"));
        List<AssessmentResult> list = List.of(result("85"), result("70"), result("40.5"));
        var s = TrainingAssessmentService.statistics(list, a);
        assertThat(s.passed()).isEqualTo(2);
        assertThat(s.failed()).isEqualTo(1);
        assertThat(s.passRate()).isEqualTo(67);
        assertThat(s.averageScore()).isEqualByComparingTo("65.2");
        a.setPassingScore(new BigDecimal("90"));
        assertThat(TrainingAssessmentService.statistics(list, a).passed()).isZero();
    }

    @Test
    void deleteGuards() {
        TrainingAssessment a = new TrainingAssessment();
        a.setId(7L);
        a.setCompanyId(10L);
        a.setProgramId(5L);
        when(assessmentRepo.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(a));
        when(resultRepo.existsByAssessmentId(7L)).thenReturn(true);
        assertCode(() -> service.delete(7L), EEmployeeError.EMP_010);
        assertCode(() -> service.update(7L, new AssessmentForm(6L, "X", null, EType.QUIZ, BigDecimal.ONE, null)),
                EEmployeeError.EMP_010);
        when(assessmentRepo.existsByProgramIdAndDeletedFalse(5L)).thenReturn(true);
        assertCode(() -> programs.delete(5L), EEmployeeError.EMP_010);
    }

    private static AssessmentResult result(String score) {
        AssessmentResult r = new AssessmentResult();
        r.setScore(new BigDecimal(score));
        return r;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
