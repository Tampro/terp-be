package com.terp.erp.recruitment.onboarding;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.exception.ApiException;
import com.terp.erp.recruitment.TestLogin;
import com.terp.erp.recruitment.candidate.Candidate;
import com.terp.erp.recruitment.candidate.CandidateRepository;
import com.terp.erp.recruitment.candidate.CandidateService;
import com.terp.erp.recruitment.client.EmployeeClient;
import com.terp.erp.recruitment.common.ERecruitmentError;
import com.terp.erp.recruitment.common.EStatus;
import com.terp.erp.recruitment.onboarding.OnboardingService.OnboardingForm;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingServiceTest {
    private static final LocalDate START = LocalDate.of(2027, 1, 4);

    private final CandidateOnboardingRepository onboardings = mock(CandidateOnboardingRepository.class);
    private final OnboardingChecklistRepository checklists = mock(OnboardingChecklistRepository.class);
    private final ChecklistItemRepository items = mock(ChecklistItemRepository.class);
    private final CandidateService candidateService = mock(CandidateService.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final ChecklistService checklistService = new ChecklistService(checklists, items, onboardings);
    private final OnboardingService service = new OnboardingService(onboardings, checklistService, checklists, items,
            candidateService, candidates, employees);

    private OnboardingChecklist checklist;
    private Candidate candidate;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "RECRUITMENT_MANAGE");
        checklist = new OnboardingChecklist();
        checklist.setId(1L);
        checklist.setCompanyId(10L);
        checklist.setName("Engineering");
        when(checklists.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(checklist));
        when(checklists.findById(1L)).thenReturn(Optional.of(checklist));
        when(items.activeOf(1L)).thenReturn(List.of(item(1L, 0), item(2L, 3), item(3L, null)));
        when(employees.employee(50L)).thenReturn(new EmployeeClient.Employee("50", "10", "Test Newhire",
                "newhire@example.invalid", null));
        when(employees.employee(51L)).thenReturn(new EmployeeClient.Employee("51", "10", "Test Buddy", null, null));
        candidate = new Candidate();
        candidate.setId(7L);
        candidate.setCompanyId(10L);
        candidate.setStatus(Candidate.EStatus.HIRED);
        when(candidateService.require(7L)).thenReturn(candidate);
        when(candidates.findById(7L)).thenReturn(Optional.of(candidate));
        when(onboardings.save(any())).thenAnswer(i -> {
            CandidateOnboarding o = i.getArgument(0);
            o.setId(100L);
            return o;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void startCopiesNamesLinksTheCandidateAndDatesTheTasks() {
        var d = service.create(new OnboardingForm(50L, 1L, START, 51L, 7L));
        assertThat(d.status()).isEqualTo(CandidateOnboarding.EStatus.PENDING);
        assertThat(d.employee().name()).isEqualTo("Test Newhire");
        assertThat(d.employee().email()).isEqualTo("newhire@example.invalid");
        assertThat(d.buddy().name()).isEqualTo("Test Buddy");
        assertThat(d.checklist().items()).extracting(OnboardingService.TaskDto::dueDate)
                .containsExactly(START, START.plusDays(3), null);
        assertThat(candidate.getEmployeeId()).isEqualTo(50L);
    }

    @Test
    void rules() {
        assertCode(() -> service.create(new OnboardingForm(50L, 1L, START, 50L, null)), ERecruitmentError.REC_042);
        when(onboardings.employeeTaken(eq(10L), eq(50L), anyLong())).thenReturn(true);
        assertCode(() -> service.create(new OnboardingForm(50L, 1L, START, null, null)), ERecruitmentError.REC_043);
        when(onboardings.employeeTaken(eq(10L), eq(50L), anyLong())).thenReturn(false);
        candidate.setStatus(Candidate.EStatus.OFFER);
        assertCode(() -> service.create(new OnboardingForm(50L, 1L, START, null, 7L)), ERecruitmentError.REC_044);
        candidate.setStatus(Candidate.EStatus.HIRED);
        candidate.setEmployeeId(99L);
        assertCode(() -> service.create(new OnboardingForm(50L, 1L, START, null, 7L)), ERecruitmentError.REC_045);
        checklist.setStatus(EStatus.INACTIVE);
        assertCode(() -> service.create(new OnboardingForm(50L, 1L, START, null, null)), ERecruitmentError.REC_014);
    }

    @Test
    void deleteReleasesTheCandidate() {
        CandidateOnboarding o = new CandidateOnboarding();
        o.setId(100L);
        o.setCompanyId(10L);
        o.setEmployeeId(50L);
        o.setCandidateId(7L);
        o.setChecklistId(1L);
        candidate.setEmployeeId(50L);
        when(onboardings.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(o));
        service.delete(100L);
        assertThat(o.isDeleted()).isTrue();
        assertThat(candidate.getEmployeeId()).isNull();
    }

    @Test
    void checklistInUseCannotBeDeletedAndDefaultIsUnique() {
        when(onboardings.existsByChecklistIdAndDeletedFalse(1L)).thenReturn(true);
        assertCode(() -> checklistService.delete(1L), ERecruitmentError.REC_013);
        checklistService.update(1L, new ChecklistService.ChecklistForm("Engineering", null, true, null));
        verify(checklists).clearDefault(10L, 1L);
        checklist.setCompanyId(11L);
        assertCode(() -> checklistService.get(1L), ERecruitmentError.REC_038);
    }

    private static ChecklistItem item(Long id, Integer dueDay) {
        ChecklistItem i = new ChecklistItem();
        i.setId(id);
        i.setTaskName("Task " + id);
        i.setCategory(ChecklistItem.ECategory.IT_SETUP);
        i.setDueDay(dueDay);
        return i;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ERecruitmentError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
