package com.terp.erp.employee.training;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.org.Branch;
import com.terp.erp.employee.org.BranchRepository;
import com.terp.erp.employee.org.Department;
import com.terp.erp.employee.org.DepartmentRepository;
import com.terp.erp.employee.training.TrainerService.TrainerForm;
import com.terp.erp.exception.ApiException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrainerTest {
    private final TrainerRepository trainerRepo = mock(TrainerRepository.class);
    private final TrainingSessionRepository sessionRepo = mock(TrainingSessionRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final TrainerService service = new TrainerService(trainerRepo, sessionRepo, branches, departments);

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "TRAINING_MANAGE");
        Branch b = new Branch();
        b.setId(1L);
        b.setCompanyId(10L);
        b.setName("HQ");
        when(branches.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(b));
        when(branches.findAllById(any())).thenReturn(List.of(b));
        Department d = new Department();
        d.setId(2L);
        d.setCompanyId(10L);
        d.setBranchId(1L);
        d.setName("Sales");
        Department elsewhere = new Department();
        elsewhere.setId(3L);
        elsewhere.setCompanyId(10L);
        elsewhere.setBranchId(8L);
        elsewhere.setName("Ops");
        when(departments.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(d));
        when(departments.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(elsewhere));
        when(departments.findAllById(any())).thenReturn(List.of(d));
        when(trainerRepo.save(any())).thenAnswer(i -> {
            Trainer t = i.getArgument(0);
            t.setId(4L);
            when(trainerRepo.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(t));
            return t;
        });
        Trainer foreign = new Trainer();
        foreign.setId(6L);
        foreign.setCompanyId(20L);
        foreign.setName("Other");
        when(trainerRepo.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(foreign));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void crud() {
        var t = service.create(null, form(1L, 2L, null));
        assertThat(t.name()).isEqualTo("Jane Trainer");
        assertThat(t.branch().name()).isEqualTo("HQ");
        assertThat(t.department().name()).isEqualTo("Sales");
        assertThat(t.experienceYears()).isEqualTo(5);
        assertThat(t.active()).isTrue();
        assertThat(service.update(4L, form(1L, null, false)).active()).isFalse();
        assertThat(service.get(4L).department()).isNull();
        when(sessionRepo.existsByTrainerIdAndDeletedFalse(4L)).thenReturn(true);
        assertCode(() -> service.delete(4L), EEmployeeError.EMP_010);
        when(sessionRepo.existsByTrainerIdAndDeletedFalse(4L)).thenReturn(false);
        service.delete(4L);
        assertThat(trainerRepo.findByIdAndDeletedFalse(4L).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void departmentMustBelongToTheBranch() {
        assertCode(() -> service.create(null, form(1L, 3L, null)), EEmployeeError.EMP_003);
        assertCode(() -> service.create(null, form(null, 2L, null)), EEmployeeError.EMP_003);
        assertCode(() -> service.create(null, form(9L, null, null)), EEmployeeError.EMP_002);
    }

    @Test
    void anotherCompanysTrainerIsNotFound() {
        assertCode(() -> service.get(6L), EEmployeeError.EMP_100);
        assertCode(() -> service.update(6L, form(null, null, null)), EEmployeeError.EMP_100);
        assertCode(() -> service.delete(6L), EEmployeeError.EMP_100);
        assertThatThrownBy(() -> service.get(6L)).satisfies(e ->
                assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    private static TrainerForm form(Long branchId, Long departmentId, Boolean active) {
        return new TrainerForm(" Jane Trainer ", "0900000000", "trainer@example.invalid", 5, branchId, departmentId,
                "Leadership", "MBA", active);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
