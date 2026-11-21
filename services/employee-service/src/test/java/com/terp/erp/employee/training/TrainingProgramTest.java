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
import com.terp.erp.employee.org.Branch;
import com.terp.erp.employee.org.BranchRepository;
import com.terp.erp.employee.org.Department;
import com.terp.erp.employee.org.DepartmentRepository;
import com.terp.erp.employee.training.TrainingProgramService.ProgramForm;
import com.terp.erp.employee.training.TrainingTypeService.TypeForm;
import com.terp.erp.exception.ApiException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainingProgramTest {
    private final TrainingTypeRepository typeRepo = mock(TrainingTypeRepository.class);
    private final TrainingProgramRepository programRepo = mock(TrainingProgramRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final TrainingTypeService types = new TrainingTypeService(typeRepo, programRepo, branches, departments);
    private final TrainingProgramService programs = new TrainingProgramService(programRepo, typeRepo, types,
            new LifecycleSupport(mock(EmployeeRepository.class), mock(MediaClient.class)),
            mock(EmployeeTrainingRepository.class), mock(TrainingSessionRepository.class),
            mock(TrainingAssessmentRepository.class));

    private TrainingType type;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "TRAINING_MANAGE");
        Branch hq = new Branch();
        hq.setId(1L);
        hq.setCompanyId(10L);
        hq.setName("HQ");
        when(branches.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(hq));
        when(branches.findAllById(any())).thenReturn(List.of(hq));
        when(departments.findByCompanyIdAndIdInAndDeletedFalse(eq(10L), any())).thenAnswer(i -> {
            java.util.Collection<Long> ids = i.getArgument(1);
            return ids.stream().filter(id -> id == 2L || id == 3L).map(id -> dept(id, id == 2L ? 1L : 4L)).toList();
        });
        when(departments.findAllById(any())).thenReturn(List.of(dept(2L, 1L)));
        type = new TrainingType();
        type.setId(5L);
        type.setCompanyId(10L);
        type.setName("Service");
        when(typeRepo.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(type));
        when(typeRepo.findAllById(any())).thenReturn(List.of(type));
        when(typeRepo.save(any())).thenAnswer(i -> {
            TrainingType t = i.getArgument(0);
            t.setId(6L);
            return t;
        });
        when(programRepo.save(any())).thenAnswer(i -> {
            TrainingProgram p = i.getArgument(0);
            p.setId(7L);
            return p;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void departmentsMustFitTheBranch() {
        type.setBranchId(1L);
        // department 3 is in branch 4, 9 does not exist
        assertCode(() -> types.assignDepartments(5L, Set.of(3L)), EEmployeeError.EMP_003);
        assertCode(() -> types.assignDepartments(5L, Set.of(9L)), EEmployeeError.EMP_003);
        var t = types.assignDepartments(5L, Set.of(2L));
        assertThat(t.departments()).extracting(TrainingTypeService.DepartmentRef::name).containsExactly("Dept 2");
        assertThat(t.departments().get(0).branch().name()).isEqualTo("HQ");
        assertCode(() -> types.create(null, new TypeForm("X", null, 99L)), EEmployeeError.EMP_002);
    }

    @Test
    void typeWithProgramsCannotBeDeleted() {
        when(programRepo.existsByTrainingTypeIdAndDeletedFalse(5L)).thenReturn(true);
        assertCode(() -> types.delete(5L), EEmployeeError.EMP_010);
    }

    @Test
    void programRules() {
        assertCode(() -> programs.create(null, form(8L)), EEmployeeError.EMP_080);
        var p = programs.create(null, form(5L));
        assertThat(p.status()).isEqualTo(TrainingProgram.EStatus.DRAFT);
        assertThat(p.trainingType().name()).isEqualTo("Service");
        assertThat(p.name()).isEqualTo("Basics");
        when(programRepo.nameTaken(eq(10L), eq("Basics"), any())).thenReturn(true);
        assertCode(() -> programs.create(null, form(5L)), EEmployeeError.EMP_011);
    }

    private static ProgramForm form(Long typeId) {
        return new ProgramForm(" Basics ", typeId, null, null, null, 20, null, null, null, true, false);
    }

    private static Department dept(Long id, Long branchId) {
        Department d = new Department();
        d.setId(id);
        d.setCompanyId(10L);
        d.setBranchId(branchId);
        d.setName("Dept " + id);
        return d;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
