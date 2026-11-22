package com.terp.erp.employee.announcement;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.terp.erp.employee.announcement.AnnouncementDtos.AnnouncementDto;
import com.terp.erp.employee.announcement.AnnouncementDtos.AnnouncementForm;
import com.terp.erp.employee.announcement.AnnouncementDtos.AttachmentDto;
import com.terp.erp.employee.announcement.AnnouncementDtos.CategoryDto;
import com.terp.erp.employee.announcement.AnnouncementDtos.CategoryForm;
import com.terp.erp.employee.announcement.AnnouncementDtos.ETab;
import com.terp.erp.employee.announcement.AnnouncementDtos.Filter;
import com.terp.erp.employee.announcement.AnnouncementDtos.Ref;
import com.terp.erp.employee.announcement.AnnouncementDtos.Stats;
import com.terp.erp.employee.announcement.AnnouncementDtos.TabCounts;
import com.terp.erp.employee.client.MediaClient;
import com.terp.erp.employee.client.MediaClient.MediaFile;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.employee.employee.EmployeeRepository;
import com.terp.erp.employee.org.Branch;
import com.terp.erp.employee.org.BranchRepository;
import com.terp.erp.employee.org.Department;
import com.terp.erp.employee.org.DepartmentRepository;
import com.terp.erp.employee.org.DepartmentService;
import com.terp.erp.employee.org.EOrgStatus;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.TenantGuard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Announcements page (docs/analysis/06 §6.3.5): admin list, dashboard tabs, details with statistics, CRUD and
 * categories; plus the employee feed whose opens are the views of the statistics.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {
    /** TODO(assumption): "today" (active, upcoming) is the Vietnam date until companies carry their own time zone. */
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int PREVIEW_CHARS = 300;
    private static final List<Long> NONE = List.of(-1L);

    private final AnnouncementRepository announcements;
    private final AnnouncementCategoryRepository categories;
    private final AnnouncementViewRepository views;
    private final BranchRepository branches;
    private final DepartmentRepository departments;
    private final EmployeeRepository employees;
    private final MediaClient media;
    private final Clock clock;

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public Page<AnnouncementDto> search(Long requestedCompanyId, Filter f, Pageable pageable) {
        Long companyId = company(requestedCompanyId);
        return rows(companyId, query(companyId, f, f.tab(), pageable));
    }

    /** Counts of the dashboard tabs under the same filters (the tab itself excluded). */
    @Transactional(readOnly = true)
    public TabCounts tabCounts(Long requestedCompanyId, Filter f) {
        Long companyId = company(requestedCompanyId);
        Pageable one = PageRequest.of(0, 1);
        return new TabCounts(query(companyId, f, ETab.ALL, one).getTotalElements(),
                query(companyId, f, ETab.HIGH_PRIORITY, one).getTotalElements(),
                query(companyId, f, ETab.FEATURED, one).getTotalElements(),
                query(companyId, f, ETab.UPCOMING, one).getTotalElements());
    }

    @Transactional(readOnly = true)
    public AnnouncementDto get(Long id) {
        Announcement a = require(id);
        return detail(a, stats(a));
    }

    @Transactional(readOnly = true)
    public AnnouncementDto statistics(Long id) {
        return get(id);
    }

    @Transactional
    public AnnouncementDto create(Long requestedCompanyId, AnnouncementForm form) {
        Long companyId = company(requestedCompanyId);
        Announcement a = new Announcement();
        a.setCompanyId(companyId);
        a.setStatus(form.status() == null ? EAnnouncementStatus.ACTIVE : form.status());
        apply(a, form);
        announcements.save(a);
        return detail(a, stats(a));
    }

    /** Full replacement: omitted lists are cleared, omitted flags are false. */
    @Transactional
    public AnnouncementDto update(Long id, AnnouncementForm form) {
        Announcement a = require(id);
        apply(a, form);
        return detail(a, stats(a));
    }

    @Transactional
    public void delete(Long id) {
        require(id).setDeleted(true);
    }

    /** DRAFT or INACTIVE becomes ACTIVE (shown to employees inside its dates); already ACTIVE = no change. */
    @Transactional
    public AnnouncementDto activate(Long id) {
        Announcement a = require(id);
        a.setStatus(EAnnouncementStatus.ACTIVE);
        return detail(a, stats(a));
    }

    /** Hidden from employees at once; views are kept. */
    @Transactional
    public AnnouncementDto deactivate(Long id) {
        Announcement a = require(id);
        a.setStatus(EAnnouncementStatus.INACTIVE);
        return detail(a, stats(a));
    }

    // ------------------------------------------------------------------ employee feed

    /** Active announcements reaching the caller's own employee profile. */
    @Transactional(readOnly = true)
    public Page<AnnouncementDto> feed(String keyword, ETab tab, Pageable pageable) {
        Employee me = me();
        Page<Announcement> page = announcements.feed(me.getCompanyId(), me.getBranchId(), me.getDepartmentId(),
                today(), tab == ETab.FEATURED ? Boolean.TRUE : null, tab == ETab.HIGH_PRIORITY ? Boolean.TRUE : null,
                DepartmentService.kw(keyword), pageable);
        return rows(me.getCompanyId(), page);
    }

    /** Opens one announcement of the feed and counts the caller as a viewer (first open only). */
    @Transactional
    public AnnouncementDto read(Long id) {
        Employee me = me();
        Announcement a = announcements.findByIdAndDeletedFalse(id)
                .filter(x -> Objects.equals(x.getCompanyId(), me.getCompanyId()) && reaches(x, me)
                        && x.getStatus() == EAnnouncementStatus.ACTIVE
                        && EAnnouncementState.of(x.getStartDate(), x.getEndDate(), today()) == EAnnouncementState.ACTIVE)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_037));
        views.recordFirstView(a.getId(), me.getId(), clock.instant());
        return detailForAudience(a);
    }

    static boolean reaches(Announcement a, Employee e) {
        return a.isCompanyWide()
                || (e.getBranchId() != null && a.getBranchIds().contains(e.getBranchId()))
                || (e.getDepartmentId() != null && a.getDepartmentIds().contains(e.getDepartmentId()));
    }

    private Employee me() {
        ErpPrincipal p = CurrentUser.get();
        if (p.companyId() == null || p.userId() == null) {
            throw ApiException.forbidden(EEmployeeError.EMP_041);
        }
        return employees.findFirstByCompanyIdAndUserIdAndDeletedFalse(p.companyId(), p.userId())
                .orElseThrow(() -> ApiException.forbidden(EEmployeeError.EMP_041));
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
        AnnouncementCategory c = new AnnouncementCategory();
        c.setCompanyId(companyId);
        c.setName(name);
        c.setColor(form.color().toUpperCase());
        return CategoryDto.of(categories.save(c));
    }

    @Transactional
    public CategoryDto updateCategory(Long id, CategoryForm form) {
        AnnouncementCategory c = requireOwnCategory(id);
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
        AnnouncementCategory c = requireOwnCategory(id);
        if (announcements.existsByCategoryIdAndDeletedFalse(id)) {
            throw ApiException.badRequest(EEmployeeError.EMP_010);
        }
        c.setDeleted(true);
    }

    // ------------------------------------------------------------------ helpers

    private Page<Announcement> query(Long companyId, Filter f, ETab tab, Pageable pageable) {
        ETab t = tab == null ? ETab.ALL : tab;
        return announcements.search(companyId, f.categoryId(), f.branchId(), f.departmentId(), f.dateFrom(),
                f.dateTo(), t == ETab.FEATURED ? Boolean.TRUE : null, t == ETab.HIGH_PRIORITY ? Boolean.TRUE : null,
                t == ETab.UPCOMING ? today() : null, f.status(), DepartmentService.kw(f.keyword()), pageable);
    }

    private void apply(Announcement a, AnnouncementForm f) {
        if (f.endDate().isBefore(f.startDate())) {
            throw ApiException.badRequest(EEmployeeError.EMP_034);
        }
        AnnouncementCategory c = usableCategory(a.getCompanyId(), f.categoryId());
        Set<Long> branchIds = f.companyWide() || f.branchIds() == null ? Set.of() : new LinkedHashSet<>(f.branchIds());
        Set<Long> departmentIds = f.companyWide() || f.departmentIds() == null ? Set.of()
                : new LinkedHashSet<>(f.departmentIds());
        if (!f.companyWide() && branchIds.isEmpty() && departmentIds.isEmpty()) {
            throw ApiException.badRequest(EEmployeeError.EMP_040);
        }
        assertBranches(a, branchIds);
        assertDepartments(a, departmentIds);
        String content = HtmlSanitizer.clean(f.content());
        if (HtmlSanitizer.preview(content, 1).isEmpty() && !content.contains("<img")) {
            throw ApiException.badRequest(ECommonError.COMMON_001);
        }
        a.setAttachments(attachments(a, f.attachmentIds()));
        a.setTitle(f.title().trim());
        a.setCategoryId(c.getId());
        a.setShortDescription(f.shortDescription() == null || f.shortDescription().isBlank() ? null
                : f.shortDescription().trim());
        a.setContent(content);
        a.setStartDate(f.startDate());
        a.setEndDate(f.endDate());
        a.setFeatured(f.featured());
        a.setHighPriority(f.highPriority());
        a.setCompanyWide(f.companyWide());
        a.getBranchIds().clear();
        a.getBranchIds().addAll(branchIds);
        a.getDepartmentIds().clear();
        a.getDepartmentIds().addAll(departmentIds);
    }

    /** Targets of the company; an inactive one cannot be newly chosen (EMP_029 / EMP_030) but stays when kept. */
    private void assertBranches(Announcement a, Set<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<Branch> found = branches.findByCompanyIdAndIdInAndDeletedFalse(a.getCompanyId(), ids);
        if (found.size() != ids.size()) {
            throw ApiException.badRequest(EEmployeeError.EMP_002);
        }
        if (found.stream().anyMatch(b -> b.getStatus() == EOrgStatus.INACTIVE && !a.getBranchIds().contains(b.getId()))) {
            throw ApiException.badRequest(EEmployeeError.EMP_029);
        }
    }

    private void assertDepartments(Announcement a, Set<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<Department> found = departments.findByCompanyIdAndIdInAndDeletedFalse(a.getCompanyId(), ids);
        if (found.size() != ids.size()) {
            throw ApiException.badRequest(EEmployeeError.EMP_003);
        }
        if (found.stream().anyMatch(d -> d.getStatus() == EOrgStatus.INACTIVE
                && !a.getDepartmentIds().contains(d.getId()))) {
            throw ApiException.badRequest(EEmployeeError.EMP_030);
        }
    }

    /** Files already attached keep their stored name; new ones must be files of the company (EMP_012). */
    private List<Announcement.Attachment> attachments(Announcement a, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Announcement.Attachment> kept = a.getAttachments().stream()
                .collect(Collectors.toMap(Announcement.Attachment::getMediaFileId, Function.identity(), (x, y) -> x));
        List<Announcement.Attachment> out = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            Announcement.Attachment old = kept.get(id);
            if (old != null) {
                out.add(new Announcement.Attachment(id, old.getFileName(), old.getMimeType(), old.getSizeBytes()));
                continue;
            }
            MediaFile m = media.require(id, a.getCompanyId());
            String name = m.displayName() != null && !m.displayName().isBlank() ? m.displayName() : m.originalName();
            out.add(new Announcement.Attachment(id, name, m.mimeType(), m.sizeBytes()));
        }
        return out;
    }

    private Stats stats(Announcement a) {
        long total = announcements.countAudience(a.getCompanyId(), a.isCompanyWide(), orNone(a.getBranchIds()),
                orNone(a.getDepartmentIds()));
        return Stats.of(total, a.getId() == null ? 0 : views.countByAnnouncementId(a.getId()));
    }

    /** `in ()` is not valid SQL: an empty target list becomes an id that cannot exist. */
    private static Collection<Long> orNone(Set<Long> ids) {
        return ids.isEmpty() ? NONE : ids;
    }

    private Page<AnnouncementDto> rows(Long companyId, Page<Announcement> page) {
        Names n = names(companyId, page.getContent());
        return page.map(a -> dto(a, n, false, null, false));
    }

    private AnnouncementDto detail(Announcement a, Stats stats) {
        return dto(a, names(a.getCompanyId(), List.of(a)), true, stats, false);
    }

    /** Detail for an employee of the audience: attachment links through the service account. */
    private AnnouncementDto detailForAudience(Announcement a) {
        return dto(a, names(a.getCompanyId(), List.of(a)), true, null, true);
    }

    private AnnouncementDto dto(Announcement a, Names n, boolean detail, Stats stats, boolean asService) {
        AnnouncementCategory c = n.categories().get(a.getCategoryId());
        return new AnnouncementDto(a.getId().toString(), a.getCompanyId().toString(), a.getTitle(),
                c == null ? null : CategoryDto.of(c), a.getShortDescription(),
                HtmlSanitizer.preview(a.getContent(), PREVIEW_CHARS), detail ? a.getContent() : null,
                a.getStartDate(), a.getEndDate(), a.getStatus(), EAnnouncementState.of(a.getStartDate(), a.getEndDate(), today()),
                a.isFeatured(), a.isHighPriority(), a.isCompanyWide(), refs(a.getBranchIds(), n.branches()),
                refs(a.getDepartmentIds(), n.departments()), attachmentDtos(a, detail, asService), stats, a.getCreatedDate(),
                a.getUpdatedDate());
    }

    /**
     * Signed links on the detail only. Callers with MEDIA_VIEW get them as themselves; an employee reading the feed
     * (audience already checked in read) gets them through the service account.
     */
    private List<AttachmentDto> attachmentDtos(Announcement a, boolean detail, boolean asService) {
        boolean asCaller = detail && !asService && CurrentUser.find().map(p -> p.has("MEDIA_VIEW")).orElse(false);
        return a.getAttachments().stream().map(x -> {
            MediaFile m = asCaller ? media.find(x.getMediaFileId())
                    : detail && asService ? media.linksAsService(x.getMediaFileId(), a.getCompanyId()) : null;
            return new AttachmentDto(x.getMediaFileId().toString(), x.getFileName(), x.getMimeType(), x.getSizeBytes(),
                    m == null ? null : m.viewUrl(), m == null ? null : m.downloadUrl());
        }).toList();
    }

    private static List<Ref> refs(Set<Long> ids, Map<Long, String> names) {
        return ids.stream().filter(names::containsKey).map(id -> new Ref(id.toString(), names.get(id)))
                .sorted(Comparator.comparing(Ref::name)).toList();
    }

    private record Names(Map<Long, AnnouncementCategory> categories, Map<Long, String> branches,
                         Map<Long, String> departments) {
    }

    private Names names(Long companyId, Collection<Announcement> rows) {
        Map<Long, AnnouncementCategory> cats = categories.findAllById(
                        rows.stream().map(Announcement::getCategoryId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AnnouncementCategory::getId, Function.identity()));
        Set<Long> b = rows.stream().flatMap(a -> a.getBranchIds().stream()).collect(Collectors.toSet());
        Set<Long> d = rows.stream().flatMap(a -> a.getDepartmentIds().stream()).collect(Collectors.toSet());
        Map<Long, String> bn = b.isEmpty() ? new HashMap<>() : branches.findByCompanyIdAndIdInAndDeletedFalse(companyId, b)
                .stream().collect(Collectors.toMap(Branch::getId, Branch::getName));
        Map<Long, String> dn = d.isEmpty() ? new HashMap<>()
                : departments.findByCompanyIdAndIdInAndDeletedFalse(companyId, d).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName));
        return new Names(cats, bn, dn);
    }

    private Announcement require(Long id) {
        Announcement a = announcements.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_037));
        TenantGuard.assertSameCompany(a.getCompanyId(), EEmployeeError.EMP_037);
        return a;
    }

    private AnnouncementCategory usableCategory(Long companyId, Long id) {
        return categories.findByIdAndDeletedFalse(id)
                .filter(c -> c.getCompanyId() == null || Objects.equals(c.getCompanyId(), companyId))
                .orElseThrow(() -> ApiException.badRequest(EEmployeeError.EMP_038));
    }

    /** System categories are seeded and read-only for everyone, the super admin included. */
    private AnnouncementCategory requireOwnCategory(Long id) {
        AnnouncementCategory c = categories.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EEmployeeError.EMP_038));
        if (c.getCompanyId() == null) {
            throw ApiException.badRequest(EEmployeeError.EMP_039);
        }
        TenantGuard.assertSameCompany(c.getCompanyId(), EEmployeeError.EMP_038);
        return c;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private static Long company(Long requested) {
        return TenantGuard.resolveCompany(requested, EEmployeeError.EMP_019);
    }
}
