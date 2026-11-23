package com.terp.erp.employee.announcement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import com.terp.erp.employee.TestLogin;
import com.terp.erp.employee.announcement.AnnouncementDtos.AnnouncementForm;
import com.terp.erp.employee.announcement.AnnouncementDtos.Stats;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.org.Branch;
import com.terp.erp.employee.org.BranchRepository;
import com.terp.erp.employee.org.DepartmentRepository;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.exception.ApiException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnnouncementServiceTest {
    /** 2026-11-19 20:00 UTC is already 2026-11-20 in Vietnam. */
    private static final Instant NOW = Instant.parse("2026-11-19T20:00:00Z");
    private static final LocalDate TODAY_VN = LocalDate.of(2026, 11, 20);

    private final AnnouncementRepository announcements = mock(AnnouncementRepository.class);
    private final AnnouncementCategoryRepository categories = mock(AnnouncementCategoryRepository.class);
    private final AnnouncementViewRepository views = mock(AnnouncementViewRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final MediaClient media = mock(MediaClient.class);
    private final com.terp.erp.outbox.OutboxPublisher outbox = org.mockito.Mockito.mock(com.terp.erp.outbox.OutboxPublisher.class);
    private final AnnouncementService service = new AnnouncementService(announcements, categories, views, branches,
            departments, employees, media, Clock.fixed(NOW, ZoneOffset.UTC), outbox);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSanitizesContentAndCompanyWideIgnoresTargets() {
        TestLogin.as(10L, "ANNOUNCEMENT_MANAGE");
        stubCategory();
        when(announcements.save(any())).thenAnswer(i -> {
            Announcement a = i.getArgument(0);
            a.setId(5L);
            return a;
        });
        when(announcements.countAudience(eq(10L), eq(true), anyCollection(), anyCollection())).thenReturn(8L);
        var d = service.create(null, form("<p onclick=\"x()\">Hi<script>alert(1)</script></p>", true, List.of(1L),
                List.of()));
        {
            var ev = org.mockito.ArgumentCaptor.forClass(Object.class);
            org.mockito.Mockito.verify(outbox, org.mockito.Mockito.atLeastOnce()).publish(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq("employee.announcement.published"), org.mockito.ArgumentMatchers.eq("ANNOUNCEMENT"),
                    org.mockito.ArgumentMatchers.any(), ev.capture());
            @SuppressWarnings("unchecked") var p = (java.util.Map<String, Object>) ev.getValue();
            org.assertj.core.api.Assertions.assertThat(p).containsKeys("announcementId", "title", "startDate", "companyWide");
            org.assertj.core.api.Assertions.assertThat(p).doesNotContainKeys("content");
        }
        assertThat(d.content()).isEqualTo("<p>Hi</p>");
        assertThat(d.companyWide()).isTrue();
        assertThat(d.branches()).isEmpty();
        assertThat(d.stats().totalEmployees()).isEqualTo(8);
        assertThat(d.stats().views()).isZero();
    }

    @Test
    void noAudienceIsRefused() {
        TestLogin.as(10L, "ANNOUNCEMENT_MANAGE");
        stubCategory();
        assertError(() -> service.create(null, form("<p>x</p>", false, List.of(), List.of())), EEmployeeError.EMP_040);
    }

    @Test
    void contentWithOnlyAScriptIsRefused() {
        TestLogin.as(10L, "ANNOUNCEMENT_MANAGE");
        stubCategory();
        assertThatThrownBy(() -> service.create(null, form("<script>alert(1)</script>", true, null, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void branchOfAnotherCompanyIsRefused() {
        TestLogin.as(10L, "ANNOUNCEMENT_MANAGE");
        stubCategory();
        when(branches.findByCompanyIdAndIdInAndDeletedFalse(eq(10L), anyCollection())).thenReturn(List.of());
        assertError(() -> service.create(null, form("<p>x</p>", false, List.of(99L), List.of())), EEmployeeError.EMP_002);
    }

    @Test
    void inactiveBranchCannotBeNewlyTargeted() {
        TestLogin.as(10L, "ANNOUNCEMENT_MANAGE");
        stubCategory();
        Branch b = new Branch();
        b.setId(1L);
        b.setCompanyId(10L);
        b.setStatus(EOrgStatus.INACTIVE);
        when(branches.findByCompanyIdAndIdInAndDeletedFalse(eq(10L), anyCollection())).thenReturn(List.of(b));
        assertError(() -> service.create(null, form("<p>x</p>", false, List.of(1L), List.of())), EEmployeeError.EMP_029);
    }

    @Test
    void categoryOfAnotherCompanyIsRefused() {
        TestLogin.as(10L, "ANNOUNCEMENT_MANAGE");
        AnnouncementCategory c = new AnnouncementCategory();
        c.setId(1L);
        c.setCompanyId(11L);
        when(categories.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(c));
        assertError(() -> service.create(null, form("<p>x</p>", true, null, null)), EEmployeeError.EMP_038);
    }

    @Test
    void readCountsTheEmployeeOnceAndOnlyWhenAddressed() {
        TestLogin.as(10L);
        Employee me = new Employee();
        me.setId(7L);
        me.setCompanyId(10L);
        me.setBranchId(1L);
        me.setDepartmentId(3L);
        when(employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(me));
        Announcement toDept = announcement(TODAY_VN, TODAY_VN);
        toDept.getDepartmentIds().add(3L);
        when(announcements.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(toDept));
        stubCategory();
        service.read(5L);
        verify(views).recordFirstView(eq(5L), eq(7L), any());

        Announcement toOtherBranch = announcement(TODAY_VN, TODAY_VN);
        toOtherBranch.setId(6L);
        toOtherBranch.getBranchIds().add(2L);
        when(announcements.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(toOtherBranch));
        assertError(() -> service.read(6L), EEmployeeError.EMP_037);
        verify(views, never()).recordFirstView(eq(6L), anyLong(), any());
    }

    @Test
    void employeeReadingGetsAttachmentLinksThroughTheServiceAccount() {
        TestLogin.as(10L);
        Employee me = new Employee();
        me.setId(7L);
        me.setCompanyId(10L);
        when(employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(me));
        Announcement a = announcement(TODAY_VN, TODAY_VN);
        a.setCompanyWide(true);
        a.getAttachments().add(new Announcement.Attachment(42L, "policy.pdf", "application/pdf", 9L));
        when(announcements.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(a));
        stubCategory();
        when(media.linksAsService(42L, 10L)).thenReturn(new MediaClient.MediaFile("42", "10", "policy.pdf", null,
                "application/pdf", "DOCUMENT", 9L, "https://v", "https://d"));
        var d = service.read(5L);
        assertThat(d.attachments().get(0).downloadUrl()).isEqualTo("https://d");
        verify(media, never()).find(any());
    }

    @Test
    void adminListNeverCallsTheServiceAccount() {
        TestLogin.as(10L, "ANNOUNCEMENT_VIEW");
        Announcement a = announcement(TODAY_VN, TODAY_VN);
        a.getAttachments().add(new Announcement.Attachment(42L, "policy.pdf", "application/pdf", 9L));
        when(announcements.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(a));
        stubCategory();
        var d = service.get(5L);
        assertThat(d.attachments().get(0).downloadUrl()).isNull();
        verify(media, never()).linksAsService(anyLong(), anyLong());
    }

    @Test
    void readOfAnUpcomingAnnouncementIsNotFound() {
        TestLogin.as(10L);
        Employee me = new Employee();
        me.setId(7L);
        me.setCompanyId(10L);
        when(employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.of(me));
        Announcement a = announcement(TODAY_VN.plusDays(1), TODAY_VN.plusDays(5));
        a.setCompanyWide(true);
        when(announcements.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(a));
        assertError(() -> service.read(5L), EEmployeeError.EMP_037);
    }

    @Test
    void feedWithoutEmployeeProfileIsForbidden() {
        TestLogin.as(10L);
        when(employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(10L, 5L)).thenReturn(Optional.empty());
        assertError(() -> service.feed(null, null, org.springframework.data.domain.Pageable.unpaged()),
                EEmployeeError.EMP_041);
    }

    @Test
    void otherCompanyAnnouncementIsNotFound() {
        TestLogin.as(11L, "ANNOUNCEMENT_VIEW");
        when(announcements.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(announcement(TODAY_VN, TODAY_VN)));
        assertError(() -> service.get(5L), EEmployeeError.EMP_037);
    }

    @Test
    void stateUsesVietnamDate() {
        assertThat(EAnnouncementState.of(TODAY_VN, TODAY_VN, TODAY_VN)).isEqualTo(EAnnouncementState.ACTIVE);
        assertThat(EAnnouncementState.of(TODAY_VN.plusDays(1), TODAY_VN.plusDays(2), TODAY_VN))
                .isEqualTo(EAnnouncementState.UPCOMING);
        assertThat(EAnnouncementState.of(TODAY_VN.minusDays(2), TODAY_VN.minusDays(1), TODAY_VN))
                .isEqualTo(EAnnouncementState.EXPIRED);
    }

    @Test
    void viewRateIsAPercentWithOneDecimal() {
        assertThat(Stats.of(3, 1).viewRate()).isEqualTo(33.3);
        assertThat(Stats.of(0, 0).viewRate()).isZero();
    }

    @Test
    void systemCategoryIsReadOnly() {
        TestLogin.as(10L, "ANNOUNCEMENT_MANAGE");
        stubCategory();
        assertError(() -> service.deleteCategory(1L), EEmployeeError.EMP_039);
    }

    @Test
    void sanitizerKeepsFormattingAndDropsDangerousLinks() {
        String html = HtmlSanitizer.clean("<h2>T</h2><p><b>b</b> <a href=\"javascript:alert(1)\">x</a> "
                + "<a href=\"https://example.invalid\">y</a></p><iframe src=\"https://evil.invalid\"></iframe>"
                + "<img src=\"x\" onerror=\"alert(1)\">");
        assertThat(html).contains("<h2>T</h2>", "<b>b</b>", "rel=\"noopener noreferrer nofollow\"")
                .doesNotContain("javascript:", "iframe", "onerror");
        assertThat(HtmlSanitizer.preview("<p>Hello <b>world</b></p>", 5)).isEqualTo("Hello…");
    }

    private void stubCategory() {
        AnnouncementCategory c = new AnnouncementCategory();
        c.setId(1L);
        c.setName("General");
        c.setColor("#546E7A");
        when(categories.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(c));
        when(categories.findAllById(any())).thenReturn(List.of(c));
        when(announcements.countAudience(anyLong(), anyBoolean(), anyCollection(), anyCollection())).thenReturn(0L);
    }

    private static AnnouncementForm form(String content, boolean companyWide, List<Long> branchIds,
                                         List<Long> departmentIds) {
        return new AnnouncementForm(" Party ", 1L, null, content, TODAY_VN, TODAY_VN.plusDays(3), null, true, false,
                companyWide, branchIds, departmentIds, null);
    }

    private static Announcement announcement(LocalDate start, LocalDate end) {
        Announcement a = new Announcement();
        a.setId(5L);
        a.setCompanyId(10L);
        a.setTitle("T");
        a.setCategoryId(1L);
        a.setContent("<p>x</p>");
        a.setStartDate(start);
        a.setEndDate(end);
        return a;
    }

    private static void assertError(Runnable call, EEmployeeError code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(code);
    }
}
