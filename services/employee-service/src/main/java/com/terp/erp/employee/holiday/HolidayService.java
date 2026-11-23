package com.terp.erp.employee.holiday;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.employee.holiday.HolidayDtos.CategoryDto;
import com.terp.erp.employee.holiday.HolidayDtos.CategoryForm;
import com.terp.erp.employee.holiday.HolidayDtos.Filter;
import com.terp.erp.employee.holiday.HolidayDtos.HolidayDto;
import com.terp.erp.employee.holiday.HolidayDtos.HolidayForm;
import com.terp.erp.employee.holiday.HolidayDtos.Ref;
import com.terp.erp.employee.org.Branch;
import com.terp.erp.employee.org.BranchRepository;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.exception.ApiException;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Holidays page (docs/analysis/06 §6.3.4): list, calendar, exports, CRUD, plus the company's own categories next to
 * the four system ones. A holiday without branches applies to every branch.
 */
@Service
@RequiredArgsConstructor
public class HolidayService {
    /** Calendar and export ranges: a year view plus a margin. */
    static final int MAX_RANGE_DAYS = 400;
    /** Rows of one export; a company has a few dozen holidays a year. */
    static final int MAX_EXPORT_ROWS = 1000;

    private final HolidayRepository holidays;
    private final HolidayCategoryRepository categories;
    private final BranchRepository branches;
    private final Clock clock;
    private final OutboxPublisher outbox;

    // ------------------------------------------------------------------ holidays

    @Transactional(readOnly = true)
    public Page<HolidayDto> search(Long requestedCompanyId, Filter f, Pageable pageable) {
        Long companyId = company(requestedCompanyId);
        LocalDate[] range = range(f);
        Page<Holiday> page = holidays.search(companyId, f.categoryId(), range[0], range[1], f.branchId(),
                DepartmentService.kw(f.keyword()), pageable);
        Map<Long, HolidayDto> dtos = dtos(companyId, page.getContent());
        return page.map(h -> dtos.get(h.getId()));
    }

    /** Holidays overlapping [from, to], ordered by start date: the month/week/day calendar views. */
    @Transactional(readOnly = true)
    public List<HolidayDto> calendar(Long requestedCompanyId, LocalDate from, LocalDate to, Long branchId) {
        assertRange(from, to);
        return export(requestedCompanyId, new Filter(null, null, null, from, to, branchId));
    }

    /** Same filters as the list, every matching row (up to MAX_EXPORT_ROWS), ordered by start date. */
    @Transactional(readOnly = true)
    public List<HolidayDto> export(Long requestedCompanyId, Filter f) {
        Long companyId = company(requestedCompanyId);
        LocalDate[] range = range(f);
        List<Holiday> rows = holidays.search(companyId, f.categoryId(), range[0], range[1], f.branchId(),
                DepartmentService.kw(f.keyword()),
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by("startDate", "name"))).getContent();
        Map<Long, HolidayDto> dtos = dtos(companyId, rows);
        return rows.stream().map(h -> dtos.get(h.getId())).toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf(Long requestedCompanyId, Filter f) {
        return HolidayExporter.pdf(export(requestedCompanyId, f), HolidayExporter.subtitle(f));
    }

    @Transactional(readOnly = true)
    public byte[] exportIcal(Long requestedCompanyId, Filter f) {
        return HolidayExporter.ical(export(requestedCompanyId, f), clock.instant());
    }

    @Transactional(readOnly = true)
    public HolidayDto get(Long id) {
        Holiday h = require(id);
        return dtos(h.getCompanyId(), List.of(h)).get(id);
    }

    @Transactional
    public HolidayDto create(Long requestedCompanyId, HolidayForm form) {
        Long companyId = company(requestedCompanyId);
        Holiday h = new Holiday();
        h.setCompanyId(companyId);
        apply(h, form);
        holidays.save(h);
        // webhook NEW_HOLIDAY; no recipient
        publish("employee.holiday.created", h);
        return dtos(companyId, List.of(h)).get(h.getId());
    }

    /** Full replacement: omitted description or branches are cleared (no branch = all branches). */
    @Transactional
    public HolidayDto update(Long id, HolidayForm form) {
        Holiday h = require(id);
        apply(h, form);
        publish("employee.holiday.updated", h);
        return dtos(h.getCompanyId(), List.of(h)).get(id);
    }

    @Transactional
    public void delete(Long id) {
        Holiday h = require(id);
        h.setDeleted(true);
        publish("employee.holiday.deleted", h);
    }

    /**
     * created / updated / deleted share one payload: webhook NEW_HOLIDAY (created only), no recipient; calendarSync
     * and name / startDate / endDate let notification-service create, replace or remove the Google Calendar entry.
     */
    private void publish(String eventType, Holiday h) {
        outbox.publish(h.getCompanyId(), eventType, "HOLIDAY", h.getId(), Payload.of(
                "holidayId", h.getId(), "title", h.getName(), "name", h.getName(), "categoryId", h.getCategoryId(),
                "startDate", h.getStartDate(), "endDate", h.getEndDate(), "paid", h.isPaid(),
                "branchIds", Payload.ids(h.getBranchIds()), "calendarSync", h.isCalendarSync()));
    }

    private void apply(Holiday h, HolidayForm f) {
        if (f.endDate().isBefore(f.startDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_034);
        }
        if (ChronoUnit.DAYS.between(f.startDate(), f.endDate()) + 1 > MAX_RANGE_DAYS) {
            throw ApiException.badRequest(EEmployeeError.EMP_036, MAX_RANGE_DAYS);
        }
        HolidayCategory c = usableCategory(h.getCompanyId(), f.categoryId());
        Set<Long> wanted = f.branchIds() == null ? Set.of() : new HashSet<>(f.branchIds());
        if (!wanted.isEmpty()) {
            List<Branch> found = branches.findByCompanyIdAndIdInAndDeletedFalse(h.getCompanyId(), wanted);
            if (found.size() != wanted.size()) {
                throw ApiException.badRequest(EEmployeeError.EMP_002);
            }
            // TODO(assumption): like employees, an inactive branch cannot be newly chosen but stays when kept.
            boolean newInactive = found.stream().anyMatch(b -> b.getStatus() == EOrgStatus.INACTIVE
                    && !h.getBranchIds().contains(b.getId()));
            if (newInactive) {
                throw ApiException.badRequest(EEmployeeError.EMP_029);
            }
        }
        h.setName(f.name().trim());
        h.setCategoryId(c.getId());
        h.setStartDate(f.startDate());
        h.setEndDate(f.endDate());
        h.setDescription(f.description() == null || f.description().isBlank() ? null : f.description().trim());
        h.setPaid(f.isPaid() == null || f.isPaid());
        h.setCalendarSync(Boolean.TRUE.equals(f.calendarSync()));
        h.getBranchIds().clear();
        h.getBranchIds().addAll(wanted);
    }

    private Holiday require(Long id) {
        Holiday h = holidays.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_032));
        TenantGuard.assertSameCompany(h.getCompanyId(), EEmployeeError.EMP_032);
        return h;
    }

    private Map<Long, HolidayDto> dtos(Long companyId, Collection<Holiday> rows) {
        Map<Long, HolidayCategory> cats = categories.findAllById(
                        rows.stream().map(Holiday::getCategoryId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(HolidayCategory::getId, Function.identity()));
        Set<Long> branchIds = rows.stream().flatMap(h -> h.getBranchIds().stream()).collect(Collectors.toSet());
        Map<Long, String> names = branchIds.isEmpty() ? Map.of()
                : branches.findByCompanyIdAndIdInAndDeletedFalse(companyId, branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName));
        return rows.stream().collect(Collectors.toMap(Holiday::getId, h -> {
            HolidayCategory c = cats.get(h.getCategoryId());
            List<Ref> refs = h.getBranchIds().stream().filter(names::containsKey)
                    .map(b -> new Ref(b.toString(), names.get(b)))
                    .sorted(Comparator.comparing(Ref::name)).toList();
            return new HolidayDto(h.getId().toString(), h.getCompanyId().toString(), h.getName(),
                    c == null ? null : CategoryDto.of(c), h.getStartDate(), h.getEndDate(),
                    ChronoUnit.DAYS.between(h.getStartDate(), h.getEndDate()) + 1, h.getDescription(),
                    h.isPaid(), h.getBranchIds().isEmpty(), refs, h.getCreatedDate(), h.getUpdatedDate(),
                    h.isCalendarSync());
        }));
    }

    /** year narrows dateFrom/dateTo to that calendar year; both bounds are optional. */
    static LocalDate[] range(Filter f) {
        LocalDate from = f.dateFrom();
        LocalDate to = f.dateTo();
        if (f.year() != null) {
            LocalDate jan1 = LocalDate.of(f.year(), 1, 1);
            LocalDate dec31 = LocalDate.of(f.year(), 12, 31);
            from = from == null || from.isBefore(jan1) ? jan1 : from;
            to = to == null || to.isAfter(dec31) ? dec31 : to;
        }
        return new LocalDate[]{from, to};
    }

    private static void assertRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest(EEmployeeError.EMP_034);
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw ApiException.badRequest(EEmployeeError.EMP_036, MAX_RANGE_DAYS);
        }
    }

    // ------------------------------------------------------------------ categories

    @Transactional(readOnly = true)
    public List<CategoryDto> categories(Long requestedCompanyId) {
        return categories.visibleTo(company(requestedCompanyId)).stream().map(CategoryDto::of).toList();
    }

    @Transactional
    public CategoryDto createCategory(Long requestedCompanyId, CategoryForm form) {
        Long companyId = company(requestedCompanyId);
        String name = form.name().trim();
        if (categories.nameTaken(companyId, name, -1L)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        HolidayCategory c = new HolidayCategory();
        c.setCompanyId(companyId);
        c.setName(name);
        c.setColor(form.color().toUpperCase());
        return CategoryDto.of(categories.save(c));
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryForm form) {
        HolidayCategory c = requireOwnCategory(id);
        String name = form.name().trim();
        if (categories.nameTaken(c.getCompanyId(), name, id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_011);
        }
        c.setName(name);
        c.setColor(form.color().toUpperCase());
        return CategoryDto.of(c);
    }

    @Transactional
    public void deleteCategory(Long id) {
        HolidayCategory c = requireOwnCategory(id);
        if (holidays.existsByCategoryIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        c.setDeleted(true);
    }

    /** System categories or the company's own. */
    private HolidayCategory usableCategory(Long companyId, Long id) {
        return categories.findByIdAndDeletedFalse(id)
                .filter(c -> c.getCompanyId() == null || Objects.equals(c.getCompanyId(), companyId))
                .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_033));
    }

    /** System categories are seeded and read-only for everyone, the super admin included. */
    private HolidayCategory requireOwnCategory(Long id) {
        HolidayCategory c = categories.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_033));
        if (c.getCompanyId() == null) {
            throw ApiException.badRequest(EEmployeeError.EMP_035);
        }
        TenantGuard.assertSameCompany(c.getCompanyId(), EEmployeeError.EMP_033);
        return c;
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }
}
