package com.terp.erp.employee.performance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.lifecycle.EmployeeExit;
import com.terp.erp.employee.lifecycle.LifecycleSupport;
import com.terp.erp.employee.lifecycle.LifecycleSupport.EmployeeRef;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.employee.performance.EmployeeReview.EStatus;
import com.terp.erp.employee.performance.PerformanceSupport.Ref;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Employee Reviews page and Conduct Review (docs/analysis/06 §7.3.1). The first conduct copies the company's active
 * indicators into the review; each is rated 1 (Poor) – 5 (Excellent) and the overall rating is their weighted
 * average, 1 decimal (PO, 2026-11-20). Saving keeps the review IN_PROGRESS; submitting (or Update Status to
 * COMPLETED) needs every indicator rated (EMP_079). Status moves PENDING -> IN_PROGRESS -> COMPLETED (EMP_096 for
 * other steps back); the first saved conduct starts a PENDING review. A COMPLETED review is read-only (EMP_077) until
 * Update Status reopens it to IN_PROGRESS; then edit and conduct work again — aligned with reference demo 2026-11-24
 * (PO). PENDING or IN_PROGRESS can be CANCELLED (final, read-only like COMPLETED, still deletable) — reference product +
 * PO 2026-11-24.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {
    private final EmployeeReviewRepository reviews;
    private final ReviewRatingRepository ratings;
    private final PerformanceIndicatorRepository indicators;
    private final PerformanceCatalogRepository catalogItems;
    private final ReviewCycleRepository cycleRepo;
    private final ReviewCycleService cycles;
    private final LifecycleSupport support;
    private final Clock clock;

    public record RatingDto(String indicatorId, String indicatorName, String description, String measurementUnit,
                            String targetValue, BigDecimal weight, Integer rating, String comments) {
    }

    /** Performance Ratings, grouped by indicator category. */
    public record CategoryRatings(String category, List<RatingDto> indicators) {
    }

    /**
     * ratingsSaved = false: the review has not been conducted yet and the groups preview the company's active
     * indicators (copied on the first save). groups only on the details.
     */
    public record ReviewDto(String id, EmployeeRef employee, EmployeeRef reviewer, Ref reviewCycle,
                            LocalDate reviewDate, EStatus status, BigDecimal overallRating, String overallComments,
                            String pros, String cons, Instant completedAt, boolean ratingsSaved, List<CategoryRatings> groups,
                            Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record ScheduleForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(description = "Employee conducting it", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long reviewerId,
            @Schema(description = "Entry of /performance/review-cycles", example = "3",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long reviewCycleId,
            @Schema(example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate reviewDate,
            @Schema(description = "PENDING or IN_PROGRESS; omitted = PENDING on create, kept on edit; never moves back. "
                    + "CANCELLED only through Update Status")
            EStatus status) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    public record RatingForm(
            @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long indicatorId,
            @Schema(description = "1 (Poor) – 5 (Excellent); null = not rated yet", example = "4") @Min(1) @Max(5)
            Integer rating,
            @Size(max = 5000) String comments) {
    }

    public record ConductForm(
            @Schema(description = "Indicators not listed keep what they have") List<@Valid @NotNull RatingForm> ratings,
            @Size(max = 5000) String overallComments,
            @Schema(description = "Strengths seen in the period", example = "Delivers on time") @Size(max = 5000)
            String pros,
            @Schema(description = "Points to improve", example = "Documentation") @Size(max = 5000) String cons,
            @Schema(description = "true = complete the review (every indicator rated)") boolean submit) {
    }

    @Transactional(readOnly = true)
    public Page<ReviewDto> search(Long requestedCompanyId, String keyword, Long cycleId, LocalDate from,
                                  LocalDate to, EStatus status, Pageable pageable) {
        Page<EmployeeReview> page = reviews.search(PerformanceSupport.company(requestedCompanyId), cycleId, from, to,
                status, DepartmentService.kw(keyword), pageable);
        Set<Long> people = new HashSet<>();
        page.forEach(r -> {
            people.add(r.getEmployeeId());
            people.add(r.getReviewerId());
        });
        Map<Long, EmployeeRef> refs = support.refs(people);
        Map<Long, String> cycleNames = cycleRepo.findAllById(page.map(EmployeeReview::getReviewCycleId).toSet())
                .stream().collect(Collectors.toMap(ReviewCycle::getId, ReviewCycle::getName));
        return page.map(r -> dto(r, refs, cycleNames.get(r.getReviewCycleId()), null, false));
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts(Long requestedCompanyId, String keyword, Long cycleId, LocalDate from,
                                     LocalDate to) {
        Map<EStatus, Long> by = new EnumMap<>(EStatus.class);
        for (EStatus s : EStatus.values()) {
            by.put(s, 0L);
        }
        for (Object[] r : reviews.countByStatus(PerformanceSupport.company(requestedCompanyId), cycleId, from, to,
                DepartmentService.kw(keyword))) {
            by.put((EStatus) r[0], (Long) r[1]);
        }
        return new StatusCounts(by.values().stream().mapToLong(Long::longValue).sum(), by);
    }

    /** View and the Conduct Review page. */
    @Transactional(readOnly = true)
    public ReviewDto get(Long id) {
        return detail(require(id));
    }

    @Transactional
    public ReviewDto schedule(ScheduleForm form) {
        EmployeeReview r = new EmployeeReview();
        apply(r, form);
        return detail(reviews.save(r));
    }

    /** Full replacement of the schedule while not completed/cancelled (reopen first); status omitted = kept. */
    @Transactional
    public ReviewDto update(Long id, ScheduleForm form) {
        EmployeeReview r = requireOpen(id);
        apply(r, form);
        return detail(r);
    }

    /** Update Status: forward, or COMPLETED back to IN_PROGRESS (reopen); COMPLETED needs every indicator rated. */
    @Transactional
    public ReviewDto changeStatus(Long id, EStatus status) {
        EmployeeReview r = require(id);
        assertTransition(r.getStatus(), status);
        if (status == EStatus.COMPLETED) {
            complete(r, ratings.findByReviewIdOrderByCategoryNameAscIndicatorNameAsc(r.getId()));
        } else {
            if (r.getStatus() == EStatus.COMPLETED) {
                // reopened: completedAt is set again by the next completion
                r.setCompletedAt(null);
            }
            r.setStatus(status);
        }
        return detail(r);
    }

    /**
     * Forward, plus COMPLETED -> IN_PROGRESS (reopen) — aligned with reference demo 2026-11-24 (PO); PENDING or
     * IN_PROGRESS -> CANCELLED, nothing out of CANCELLED (reference product + PO 2026-11-24).
     * TODO(assumption): no step back to PENDING (from IN_PROGRESS or COMPLETED) — confirm with BA.
     */
    static void assertTransition(EStatus from, EStatus to) {
        if (from == to || (from == EStatus.COMPLETED && to == EStatus.IN_PROGRESS)) {
            return;
        }
        // reference product + PO 2026-11-24: only an open review can be cancelled, and a cancelled one is final
        if (from == EStatus.CANCELLED || to == EStatus.CANCELLED) {
            if (from == EStatus.CANCELLED || from == EStatus.COMPLETED) {
                throw ApiException.badRequest(EEmployeeError.EMP_096, from, to);
            }
            return;
        }
        if (to.ordinal() < from.ordinal()) {
            throw ApiException.badRequest(EEmployeeError.EMP_096, from, to);
        }
    }

    /** Conduct Review: saves the ratings and comments; submit completes it. */
    @Transactional
    public ReviewDto conduct(Long id, ConductForm f) {
        EmployeeReview r = requireOpen(id);
        List<ReviewRating> rows = ratings.findByReviewIdOrderByCategoryNameAscIndicatorNameAsc(r.getId());
        if (rows.isEmpty()) {
            rows = ratings.saveAll(snapshot(r));
        }
        Map<Long, ReviewRating> byIndicator = rows.stream()
                .collect(Collectors.toMap(ReviewRating::getIndicatorId, Function.identity()));
        for (RatingForm x : f.ratings() == null ? List.<RatingForm>of() : f.ratings()) {
            ReviewRating row = byIndicator.get(x.indicatorId());
            if (row == null) {
                throw ApiException.badRequest(EEmployeeError.EMP_078);
            }
            row.setRating(x.rating());
            row.setComments(PerformanceSupport.blankToNull(x.comments()));
        }
        r.setOverallComments(PerformanceSupport.blankToNull(f.overallComments()));
        r.setPros(PerformanceSupport.blankToNull(f.pros()));
        r.setCons(PerformanceSupport.blankToNull(f.cons()));
        r.setOverallRating(overall(rows));
        if (f.submit()) {
            complete(r, rows);
        } else {
            r.setStatus(EStatus.IN_PROGRESS);
        }
        return detail(r);
    }

    /**
     * Completed reviews keep only View and Update Status (which can reopen them). A cancelled review is deletable
     * like a pending one: it was never completed, so nothing is lost (reference product + PO 2026-11-24).
     */
    @Transactional
    public void delete(Long id) {
        EmployeeReview r = require(id);
        if (r.getStatus() == EStatus.COMPLETED) {
            throw ApiException.badRequest(EEmployeeError.EMP_077);
        }
        r.setDeleted(true);
    }

    private void complete(EmployeeReview r, List<ReviewRating> rows) {
        if (rows.isEmpty() || rows.stream().anyMatch(x -> x.getRating() == null)) {
            throw ApiException.badRequest(EEmployeeError.EMP_079);
        }
        r.setOverallRating(overall(rows));
        if (r.getStatus() != EStatus.COMPLETED) {
            r.setStatus(EStatus.COMPLETED);
            r.setCompletedAt(clock.instant());
        }
    }

    /** Weighted average of the rated rows, 1 decimal half-up; null when nothing is rated. */
    static BigDecimal overall(List<ReviewRating> rows) {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal weights = BigDecimal.ZERO;
        for (ReviewRating x : rows) {
            if (x.getRating() != null) {
                sum = sum.add(x.getWeight().multiply(BigDecimal.valueOf(x.getRating())));
                weights = weights.add(x.getWeight());
            }
        }
        return weights.signum() == 0 ? null : sum.divide(weights, 1, RoundingMode.HALF_UP);
    }

    private List<ReviewRating> snapshot(EmployeeReview r) {
        List<PerformanceIndicator> active = indicators.findByCompanyIdAndStatusAndDeletedFalse(r.getCompanyId(),
                EOrgStatus.ACTIVE);
        Map<Long, String> categories = categoryNames(active);
        List<ReviewRating> rows = new ArrayList<>();
        for (PerformanceIndicator i : active) {
            ReviewRating x = new ReviewRating();
            x.setReviewId(r.getId());
            x.setIndicatorId(i.getId());
            x.setCategoryName(categories.get(i.getCategoryId()));
            x.setIndicatorName(i.getName());
            x.setIndicatorDescription(i.getDescription());
            x.setMeasurementUnit(i.getMeasurementUnit());
            x.setTargetValue(i.getTargetValue());
            x.setWeight(i.getWeight());
            rows.add(x);
        }
        return rows;
    }

    private Map<Long, String> categoryNames(List<PerformanceIndicator> list) {
        return catalogItems.findAllById(list.stream().map(PerformanceIndicator::getCategoryId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PerformanceCatalog::getId, PerformanceCatalog::getName));
    }

    private void apply(EmployeeReview r, ScheduleForm f) {
        Employee e = support.employee(f.employeeId());
        if (r.getId() == null || !e.getId().equals(r.getEmployeeId())) {
            EmployeeExit.assertEmployed(e);
        }
        if (f.reviewerId().equals(e.getId())) {
            throw ApiException.badRequest(EEmployeeError.EMP_076);
        }
        Employee reviewer = support.employee(f.reviewerId());
        if (!Objects.equals(reviewer.getCompanyId(), e.getCompanyId())) {
            throw ApiException.notFound(EEmployeeError.EMP_001);
        }
        if (f.status() == EStatus.COMPLETED) {
            throw ApiException.badRequest(EEmployeeError.EMP_079);
        }
        if (f.status() == EStatus.CANCELLED) {
            // cancelling is an Update Status action, not part of scheduling
            throw ApiException.badRequest(EEmployeeError.EMP_096,
                    r.getStatus() == null ? EStatus.PENDING : r.getStatus(), f.status());
        }
        r.setReviewCycleId(cycles.usable(f.reviewCycleId(), e.getCompanyId(), r.getReviewCycleId()).getId());
        r.setCompanyId(e.getCompanyId());
        r.setEmployeeId(e.getId());
        r.setReviewerId(reviewer.getId());
        r.setReviewDate(f.reviewDate());
        if (f.status() != null) {
            if (r.getId() != null) {
                assertTransition(r.getStatus(), f.status());
            }
            r.setStatus(f.status());
        }
    }

    private ReviewDto detail(EmployeeReview r) {
        Map<Long, EmployeeRef> refs = support.refs(List.of(r.getEmployeeId(), r.getReviewerId()));
        String cycle = cycleRepo.findById(r.getReviewCycleId()).map(ReviewCycle::getName).orElse(null);
        List<ReviewRating> rows = ratings.findByReviewIdOrderByCategoryNameAscIndicatorNameAsc(r.getId());
        boolean saved = !rows.isEmpty();
        List<RatingDto> list;
        Map<Long, String> categoryOf = new HashMap<>();
        if (saved) {
            list = rows.stream().map(x -> {
                categoryOf.put(x.getIndicatorId(), x.getCategoryName());
                return new RatingDto(x.getIndicatorId().toString(), x.getIndicatorName(),
                        x.getIndicatorDescription(), x.getMeasurementUnit(), x.getTargetValue(), x.getWeight(),
                        x.getRating(), x.getComments());
            }).toList();
        } else {
            List<PerformanceIndicator> active = indicators.findByCompanyIdAndStatusAndDeletedFalse(r.getCompanyId(),
                    EOrgStatus.ACTIVE);
            Map<Long, String> names = categoryNames(active);
            list = active.stream().sorted(Comparator.comparing(PerformanceIndicator::getName)).map(i -> {
                categoryOf.put(i.getId(), names.get(i.getCategoryId()));
                return new RatingDto(i.getId().toString(), i.getName(), i.getDescription(), i.getMeasurementUnit(),
                        i.getTargetValue(), i.getWeight(), null, null);
            }).toList();
        }
        Map<String, List<RatingDto>> grouped = new LinkedHashMap<>();
        list.stream().sorted(Comparator.comparing((RatingDto x) ->
                        Objects.requireNonNullElse(categoryOf.get(Long.valueOf(x.indicatorId())), "")))
                .forEach(x -> grouped.computeIfAbsent(Objects.requireNonNullElse(
                        categoryOf.get(Long.valueOf(x.indicatorId())), ""), k -> new ArrayList<>()).add(x));
        List<CategoryRatings> groups = grouped.entrySet().stream()
                .map(en -> new CategoryRatings(en.getKey().isEmpty() ? null : en.getKey(), en.getValue())).toList();
        return dto(r, refs, cycle, groups, saved);
    }

    private static ReviewDto dto(EmployeeReview r, Map<Long, EmployeeRef> refs, String cycle,
                                 List<CategoryRatings> groups, boolean saved) {
        return new ReviewDto(r.getId().toString(), refs.get(r.getEmployeeId()), refs.get(r.getReviewerId()),
                new Ref(r.getReviewCycleId().toString(), cycle), r.getReviewDate(), r.getStatus(),
                r.getOverallRating(), r.getOverallComments(), r.getPros(), r.getCons(), r.getCompletedAt(), saved, groups, r.getCreatedDate(),
                r.getUpdatedDate());
    }

    private EmployeeReview requireOpen(Long id) {
        EmployeeReview r = require(id);
        if (r.getStatus() == EStatus.COMPLETED || r.getStatus() == EStatus.CANCELLED) {
            throw ApiException.badRequest(EEmployeeError.EMP_077);
        }
        return r;
    }

    private EmployeeReview require(Long id) {
        EmployeeReview r = reviews.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_075));
        TenantGuard.assertSameCompany(r.getCompanyId(), EEmployeeError.EMP_075);
        return r;
    }
}
