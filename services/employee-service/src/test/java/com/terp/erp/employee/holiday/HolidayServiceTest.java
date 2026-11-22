package com.terp.erp.employee.holiday;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.holiday.HolidayDtos.CategoryForm;
import com.terp.erp.employee.holiday.HolidayDtos.Filter;
import com.terp.erp.employee.holiday.HolidayDtos.HolidayDto;
import com.terp.erp.employee.holiday.HolidayDtos.HolidayForm;
import com.terp.erp.employee.org.Branch;
import com.terp.erp.employee.org.BranchRepository;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HolidayServiceTest {
    private static final LocalDate XMAS = LocalDate.of(2026, 12, 25);

    private final HolidayRepository holidays = mock(HolidayRepository.class);
    private final HolidayCategoryRepository categories = mock(HolidayCategoryRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final HolidayService service = new HolidayService(holidays, categories, branches,
            Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createWithoutBranchesAppliesToAll() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(categories.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(category(1L, null)));
        when(categories.findAllById(any())).thenReturn(List.of(category(1L, null)));
        when(holidays.save(any())).thenAnswer(i -> {
            Holiday h = i.getArgument(0);
            h.setId(9L);
            return h;
        });
        HolidayDto d = service.create(null, new HolidayForm(" Christmas ", 1L, XMAS, XMAS.plusDays(1), " ", null, null));
        assertThat(d.name()).isEqualTo("Christmas");
        assertThat(d.allBranches()).isTrue();
        assertThat(d.days()).isEqualTo(2);
        assertThat(d.description()).isNull();
        assertThat(d.category().system()).isTrue();
    }

    @Test
    void endBeforeStartIsRefused() {
        TestLogin.as(10L, "ORG_MANAGE");
        assertError(() -> service.create(null, new HolidayForm("X", 1L, XMAS, XMAS.minusDays(1), null, null, null)),
                EEmployeeError.EMP_034);
    }

    @Test
    void categoryOfAnotherCompanyIsRefused() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(categories.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(category(7L, 11L)));
        assertError(() -> service.create(null, new HolidayForm("X", 7L, XMAS, XMAS, null, null, null)), EEmployeeError.EMP_033);
    }

    @Test
    void branchOfAnotherCompanyIsRefused() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(categories.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(category(1L, null)));
        when(branches.findByCompanyIdAndIdInAndDeletedFalse(eq(10L), anyCollection()))
                .thenReturn(List.of(branch(1L, EOrgStatus.ACTIVE)));
        assertError(() -> service.create(null, new HolidayForm("X", 1L, XMAS, XMAS, null, List.of(1L, 99L), null)),
                EEmployeeError.EMP_002);
    }

    @Test
    void inactiveBranchCannotBeNewlyChosenButIsKept() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(categories.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(category(1L, null)));
        when(categories.findAllById(any())).thenReturn(List.of(category(1L, null)));
        when(branches.findByCompanyIdAndIdInAndDeletedFalse(eq(10L), anyCollection()))
                .thenReturn(List.of(branch(2L, EOrgStatus.INACTIVE)));
        assertError(() -> service.create(null, new HolidayForm("X", 1L, XMAS, XMAS, null, List.of(2L), null)),
                EEmployeeError.EMP_029);
        Holiday h = holiday();
        h.getBranchIds().add(2L);
        when(holidays.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(h));
        HolidayDto d = service.update(9L, new HolidayForm("Y", 1L, XMAS, XMAS, null, List.of(2L), null));
        assertThat(d.branches()).extracting(HolidayDtos.Ref::name).containsExactly("B2");
    }

    @Test
    void otherCompanyHolidayIsNotFound() {
        TestLogin.as(11L, "ORG_VIEW");
        when(holidays.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(holiday()));
        assertError(() -> service.get(9L), EEmployeeError.EMP_032);
    }

    @Test
    void yearNarrowsTheDateRange() {
        LocalDate[] r = HolidayService.range(new Filter(null, null, 2026, LocalDate.of(2025, 6, 1), null, null));
        assertThat(r[0]).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(r[1]).isEqualTo(LocalDate.of(2026, 12, 31));
        r = HolidayService.range(new Filter(null, null, 2026, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 1), null));
        assertThat(r[0]).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(r[1]).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void calendarRangeIsBounded() {
        TestLogin.as(10L, "ORG_VIEW");
        assertError(() -> service.calendar(null, XMAS, XMAS.plusDays(500), null), EEmployeeError.EMP_036);
        assertError(() -> service.calendar(null, XMAS, XMAS.minusDays(1), null), EEmployeeError.EMP_034);
    }

    @Test
    void systemCategoryIsReadOnly() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(categories.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(category(1L, null)));
        assertError(() -> service.updateCategory(1L, new CategoryForm("X", "#000000")), EEmployeeError.EMP_035);
        assertError(() -> service.deleteCategory(1L), EEmployeeError.EMP_035);
    }

    @Test
    void categoryInUseCannotBeDeleted() {
        TestLogin.as(10L, "ORG_MANAGE");
        when(categories.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(category(7L, 10L)));
        when(holidays.existsByCategoryIdAndDeletedFalse(7L)).thenReturn(true);
        assertError(() -> service.deleteCategory(7L), EEmployeeError.EMP_010);
    }

    @Test
    void icalIsAllDayWithExclusiveEndEscapedAndFolded() {
        HolidayDto d = new HolidayDto("9", "10", "Tết; Lunar New Year, " + "x".repeat(80),
                new HolidayDtos.CategoryDto("1", "National", "#E53935", true), LocalDate.of(2027, 2, 5),
                LocalDate.of(2027, 2, 9), 5, null, true, true, List.of(), null, null);
        String ics = new String(HolidayExporter.ical(List.of(d), Instant.parse("2026-11-19T00:00:00Z")),
                StandardCharsets.UTF_8);
        assertThat(ics).contains("DTSTART;VALUE=DATE:20270205\r\n", "DTEND;VALUE=DATE:20270210\r\n",
                "SUMMARY:Tết\\; Lunar New Year\\, ", "\r\n x", "DESCRIPTION:Branches: All branches");
        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
    }

    @Test
    void pdfRendersVietnamese() {
        HolidayDto d = new HolidayDto("9", "10", "Quốc khánh", null, LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 2), 1, null, true, true, List.of(), null, null);
        byte[] pdf = HolidayExporter.pdf(List.of(d), "2026");
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    private static HolidayCategory category(Long id, Long companyId) {
        HolidayCategory c = new HolidayCategory();
        c.setId(id);
        c.setCompanyId(companyId);
        c.setName("C" + id);
        c.setColor("#E53935");
        return c;
    }

    private static Branch branch(Long id, EOrgStatus status) {
        Branch b = new Branch();
        b.setId(id);
        b.setCompanyId(10L);
        b.setName("B" + id);
        b.setStatus(status);
        return b;
    }

    private static Holiday holiday() {
        Holiday h = new Holiday();
        h.setId(9L);
        h.setCompanyId(10L);
        h.setName("Christmas");
        h.setCategoryId(1L);
        h.setStartDate(XMAS);
        h.setEndDate(XMAS);
        return h;
    }

    private static void assertError(Runnable call, EEmployeeError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
