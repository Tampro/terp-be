package com.terp.erp.employee.performance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.employee.performance.IndicatorService.IndicatorForm;
import com.terp.erp.employee.performance.PerformanceCatalogService.CatalogForm;
import com.terp.erp.employee.performance.ReviewCycleService.CycleForm;
import com.terp.erp.exception.ApiException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerformanceSetupTest {
    private final PerformanceCatalogRepository items = mock(PerformanceCatalogRepository.class);
    private final PerformanceIndicatorRepository indicatorRepo = mock(PerformanceIndicatorRepository.class);
    private final ReviewCycleRepository cycleRepo = mock(ReviewCycleRepository.class);
    private final PerformanceCatalogService catalogs = new PerformanceCatalogService(items, indicatorRepo,
            mock(EmployeeGoalRepository.class));
    private final IndicatorService indicators = new IndicatorService(indicatorRepo, items, catalogs);
    private final ReviewCycleService cycles = new ReviewCycleService(cycleRepo,
            mock(EmployeeReviewRepository.class));

    private PerformanceCatalog category;

    @BeforeEach
    void setUp() {
        TestLogin.as(10L, "PERFORMANCE_MANAGE");
        category = new PerformanceCatalog();
        category.setId(3L);
        category.setCompanyId(10L);
        category.setKind(EPerformanceCatalogKind.INDICATOR_CATEGORY);
        category.setName("Job Performance");
        when(items.findByIdAndKindAndDeletedFalse(3L, EPerformanceCatalogKind.INDICATOR_CATEGORY))
                .thenReturn(Optional.of(category));
        when(items.findAllById(any())).thenReturn(List.of(category));
        when(indicatorRepo.save(any())).thenAnswer(i -> {
            PerformanceIndicator x = i.getArgument(0);
            x.setId(7L);
            return x;
        });
        when(cycleRepo.save(any())).thenAnswer(i -> {
            ReviewCycle x = i.getArgument(0);
            x.setId(8L);
            return x;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void indicatorRules() {
        var i = indicators.create(null, form(3L, null));
        assertThat(i.weight()).isEqualByComparingTo("1");
        assertThat(i.category().name()).isEqualTo("Job Performance");
        assertThat(i.name()).isEqualTo("Code quality");
        // a goal type id is not an indicator category
        assertCode(() -> indicators.create(null, form(4L, null)), EEmployeeError.EMP_071);
        category.setStatus(EOrgStatus.INACTIVE);
        assertCode(() -> indicators.create(null, form(3L, null)), EEmployeeError.EMP_054);
        when(indicatorRepo.nameTaken(eq(10L), anyString(), anyLong())).thenReturn(true);
        assertCode(() -> indicators.create(null, form(3L, BigDecimal.TEN)), EEmployeeError.EMP_011);
    }

    @Test
    void categoryInUseCannotBeDeleted() {
        when(indicatorRepo.existsByCategoryIdAndDeletedFalse(3L)).thenReturn(true);
        assertCode(() -> catalogs.delete(EPerformanceCatalogKind.INDICATOR_CATEGORY, 3L), EEmployeeError.EMP_010);
        assertCode(() -> EPerformanceCatalogKind.ofPath("indicators"), EEmployeeError.EMP_071);
        when(items.nameTaken(10L, EPerformanceCatalogKind.GOAL_TYPE, "Sales", -1L)).thenReturn(true);
        assertCode(() -> catalogs.create(EPerformanceCatalogKind.GOAL_TYPE, null, new CatalogForm(" Sales ", null,
                null)), EEmployeeError.EMP_011);
    }

    @Test
    void cycleUsableOnlyWhenActiveOrAlreadyChosen() {
        var c = cycles.create(null, new CycleForm(" Monthly ", ReviewCycle.EFrequency.MONTHLY, null,
                EOrgStatus.INACTIVE));
        assertThat(c.name()).isEqualTo("Monthly");
        ReviewCycle saved = new ReviewCycle();
        saved.setId(8L);
        saved.setCompanyId(10L);
        saved.setStatus(EOrgStatus.INACTIVE);
        when(cycleRepo.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(saved));
        assertCode(() -> cycles.usable(8L, 10L, null), EEmployeeError.EMP_054);
        assertThat(cycles.usable(8L, 10L, 8L)).isSameAs(saved);
        assertCode(() -> cycles.usable(8L, 11L, 8L), EEmployeeError.EMP_073);
    }

    private static IndicatorForm form(Long categoryId, BigDecimal weight) {
        return new IndicatorForm(" Code quality ", categoryId, null, "Percentage", "95%", weight, null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, EEmployeeError code) {
        assertThatThrownBy(call).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(code));
    }
}
