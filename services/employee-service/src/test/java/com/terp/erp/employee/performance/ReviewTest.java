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
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.employee.performance.EmployeeReview.EStatus;
import com.terp.erp.employee.performance.ReviewService.ConductForm;
import com.terp.erp.employee.performance.ReviewService.RatingForm;
import com.terp.erp.employee.performance.ReviewService.ScheduleForm;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewTest {
    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final EmployeeReviewRepository reviewRepo = mock(EmployeeReviewRepository.class);
    private final ReviewRatingRepository ratingRepo = mock(ReviewRatingRepository.class);
    private final PerformanceIndicatorRepository indicatorRepo = mock(PerformanceIndicatorRepository.class);
    private final PerformanceCatalogRepository items = mock(PerformanceCatalogRepository.class);
    private final ReviewCycleRepository cycleRepo = mock(ReviewCycleRepository.class);
    private final ReviewCycleService cycles = new ReviewCycleService(cycleRepo, reviewRepo);
    private final ReviewService service = new ReviewService(reviewRepo, ratingRepo, indicatorRepo, items, cycleRepo,
            cycles, new LifecycleSupport(employees, mock(MediaClient.class)),
            Clock.fixed(Instant.parse("2026-12-01T03:00:00Z"), ZoneOffset.UTC));

    private final List<ReviewRating> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "PERFORMANCE_MANAGE");
        for (long id = 1; id <= 2; id++) {
            Employee e = new Employee();
            e.setId(id);
            e.setCompanyId(10L);
            e.setFullName("Test " + id);
            e.setStatus(EEmployeeStatus.ACTIVE);
            when(employees.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(e));
        }
        when(employees.findAllById(any())).thenReturn(List.of());
        ReviewCycle cycle = new ReviewCycle();
        cycle.setId(3L);
        cycle.setCompanyId(10L);
        cycle.setName("Monthly");
        when(cycleRepo.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(cycle));
        when(cycleRepo.findById(3L)).thenReturn(Optional.of(cycle));
        when(indicatorRepo.findByCompanyIdAndStatusAndDeletedFalse(10L, EOrgStatus.ACTIVE))
                .thenReturn(List.of(indicator(7L, "2"), indicator(8L, "1")));
        when(items.findAllById(any())).thenReturn(List.of());
        when(reviewRepo.save(any())).thenAnswer(i -> {
            EmployeeReview r = i.getArgument(0);
            r.setId(9L);
            when(reviewRepo.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(r));
            return r;
        });
        when(ratingRepo.findByReviewIdOrderByCategoryNameAscIndicatorNameAsc(anyLong())).thenAnswer(i -> saved);
        when(ratingRepo.saveAll(any())).thenAnswer(i -> {
            Iterable<ReviewRating> rows = i.getArgument(0);
            rows.forEach(saved::add);
            return saved;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void scheduleRules() {
        assertCode(() -> service.schedule(new ScheduleForm(1L, 1L, 3L, DAY, null)), EEmployeeError.EMP_076);
        assertCode(() -> service.schedule(new ScheduleForm(1L, 2L, 3L, DAY, EStatus.COMPLETED)),
                EEmployeeError.EMP_079);
        var r = service.schedule(new ScheduleForm(1L, 2L, 3L, DAY, null));
        assertThat(r.status()).isEqualTo(EStatus.PENDING);
        assertThat(r.ratingsSaved()).isFalse();
        assertThat(r.groups()).flatExtracting(ReviewService.CategoryRatings::indicators).hasSize(2);
    }

    @Test
    void conductSnapshotsAndWeightsTheRating() {
        service.schedule(new ScheduleForm(1L, 2L, 3L, DAY, null));
        var draft = service.conduct(9L, new ConductForm(List.of(new RatingForm(7L, 5, " Great ")), "So far", null, null, false));
        assertThat(draft.status()).isEqualTo(EStatus.IN_PROGRESS);
        assertThat(draft.ratingsSaved()).isTrue();
        assertThat(draft.overallRating()).isEqualByComparingTo("5.0");
        assertCode(() -> service.conduct(9L, new ConductForm(List.of(), null, null, null, true)), EEmployeeError.EMP_079);
        assertCode(() -> service.conduct(9L, new ConductForm(List.of(new RatingForm(99L, 3, null)), null, null, null, false)),
                EEmployeeError.EMP_078);
        // weights 2 and 1: (5*2 + 2*1) / 3 = 4.0
        var done = service.conduct(9L, new ConductForm(List.of(new RatingForm(8L, 2, null)), "Solid", " Fast ", " Docs ", true));
        assertThat(done.status()).isEqualTo(EStatus.COMPLETED);
        assertThat(done.overallRating()).isEqualByComparingTo("4.0");
        assertThat(done.completedAt()).isNotNull();
        assertCode(() -> service.conduct(9L, new ConductForm(List.of(), null, null, null, false)), EEmployeeError.EMP_077);
        assertCode(() -> service.delete(9L), EEmployeeError.EMP_077);
        assertThat(done.pros()).isEqualTo("Fast");
        assertThat(done.cons()).isEqualTo("Docs");
        // completed is read-only: no reopen
        assertCode(() -> service.changeStatus(9L, EStatus.IN_PROGRESS), EEmployeeError.EMP_077);
        assertThat(service.changeStatus(9L, EStatus.COMPLETED).status()).isEqualTo(EStatus.COMPLETED);
    }

    @Test
    void statusMovesForwardOnly() {
        service.schedule(new ScheduleForm(1L, 2L, 3L, DAY, null));
        assertThat(service.changeStatus(9L, EStatus.IN_PROGRESS).status()).isEqualTo(EStatus.IN_PROGRESS);
        assertCode(() -> service.changeStatus(9L, EStatus.PENDING), EEmployeeError.EMP_096);
        assertCode(() -> service.update(9L, new ScheduleForm(1L, 2L, 3L, DAY, EStatus.PENDING)),
                EEmployeeError.EMP_096);
        // nothing rated yet
        assertCode(() -> service.changeStatus(9L, EStatus.COMPLETED), EEmployeeError.EMP_079);
        ReviewService.assertTransition(EStatus.PENDING, EStatus.IN_PROGRESS);
        assertCode(() -> ReviewService.assertTransition(EStatus.COMPLETED, EStatus.PENDING), EEmployeeError.EMP_077);
    }

    @Test
    void firstConductStartsAPendingReview() {
        service.schedule(new ScheduleForm(1L, 2L, 3L, DAY, null));
        var r = service.conduct(9L, new ConductForm(List.of(new RatingForm(7L, 3, null)), null, "Kind", " ", false));
        assertThat(r.status()).isEqualTo(EStatus.IN_PROGRESS);
        assertThat(r.pros()).isEqualTo("Kind");
        assertThat(r.cons()).isNull();
    }

    @Test
    void ratingOutsideOneToFiveIsRefused() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var v = factory.getValidator();
            assertThat(v.validate(new RatingForm(7L, 0, null))).isNotEmpty();
            assertThat(v.validate(new RatingForm(7L, 6, null))).isNotEmpty();
            assertThat(v.validate(new RatingForm(7L, 5, null))).isEmpty();
            assertThat(v.validate(new RatingForm(7L, null, null))).isEmpty();
        }
    }

    @Test
    void statusMigrationMapsScheduledToPending() throws Exception {
        String sql;
        try (var in = getClass().getResourceAsStream(
                "/db/changelog/changes/20261122800100_review_status_data_changelog.mysql.sql")) {
            sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertThat(sql).contains("UPDATE employee_review SET status = 'PENDING' WHERE status = 'SCHEDULED';");
        assertThat(EStatus.valueOf("PENDING")).isNotNull();
        assertThat(java.util.Arrays.stream(EStatus.values()).map(Enum::name)).doesNotContain("SCHEDULED");
    }

    @Test
    void weightedAverageRoundsHalfUp() {
        assertThat(ReviewService.overall(List.of(row("1", 4), row("1", 5), row("1", null)))).isEqualByComparingTo("4.5");
        assertThat(ReviewService.overall(List.of(row("1", 4), row("2", 5)))).isEqualByComparingTo("4.7");
        assertThat(ReviewService.overall(List.of(row("1", null)))).isNull();
    }

    @Test
    void cycleInUseCannotBeDeleted() {
        when(reviewRepo.existsByReviewCycleIdAndDeletedFalse(3L)).thenReturn(true);
        assertCode(() -> cycles.delete(3L), EEmployeeError.EMP_010);
    }

    private static PerformanceIndicator indicator(Long id, String weight) {
        PerformanceIndicator i = new PerformanceIndicator();
        i.setId(id);
        i.setCompanyId(10L);
        i.setCategoryId(5L);
        i.setName("Indicator " + id);
        i.setWeight(new BigDecimal(weight));
        return i;
    }

    private static ReviewRating row(String weight, Integer rating) {
        ReviewRating r = new ReviewRating();
        r.setWeight(new BigDecimal(weight));
        r.setRating(rating);
        return r;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
