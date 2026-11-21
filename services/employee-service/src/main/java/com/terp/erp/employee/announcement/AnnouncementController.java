package com.terp.erp.employee.announcement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.employee.announcement.AnnouncementDtos.AnnouncementDto;
import com.terp.erp.employee.announcement.AnnouncementDtos.AnnouncementForm;
import com.terp.erp.employee.announcement.AnnouncementDtos.CategoryDto;
import com.terp.erp.employee.announcement.AnnouncementDtos.CategoryForm;
import com.terp.erp.employee.announcement.AnnouncementDtos.ETab;
import com.terp.erp.employee.announcement.AnnouncementDtos.Filter;
import com.terp.erp.employee.announcement.AnnouncementDtos.TabCounts;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Announcements")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/org")
@SecurityRequirement(name = "bearerAuth")
public class AnnouncementController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String FILTERS = "keyword matches title or short description; branchId / departmentId keep "
            + "the announcements reaching it (targeted or company-wide); dateFrom/dateTo keep the ones overlapping the "
            + "range; tab = ALL, HIGH_PRIORITY, FEATURED or UPCOMING (dashboard tabs).";
    private static final String TARGETS = "Branch not in the company (EMP_002), department not in the company "
            + "(EMP_003), inactive branch (EMP_029) or department (EMP_030), no audience (EMP_040), unknown category "
            + "(EMP_038), end before start (EMP_034), attachment not in the media library (EMP_012), empty content "
            + "(COMMON_001)";

    private final AnnouncementService service;

    @Operation(summary = "Announcements of the company (list and dashboard view)",
            description = FILTERS + " Each row has a plain-text contentPreview and its state (UPCOMING, ACTIVE, "
                    + "EXPIRED). Sort by startDate, endDate, title, createdDate.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ANNOUNCEMENT_VIEW')")
    @GetMapping("/announcements")
    public ApiMessageDto<ResponseListDto<AnnouncementDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) ETab tab,
            @ParameterObject @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Filter f = new Filter(keyword, categoryId, branchId, departmentId, dateFrom, dateTo, tab);
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, f, pageable)));
    }

    @Operation(summary = "Counts of the dashboard tabs (All, High Priority, Featured, Upcoming)",
            description = "Same filters as the list, without tab.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ANNOUNCEMENT_VIEW')")
    @GetMapping("/announcements/tab-counts")
    public ApiMessageDto<TabCounts> tabCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        Filter f = new Filter(keyword, categoryId, branchId, departmentId, dateFrom, dateTo, null);
        return ApiMessageDto.ok(service.tabCounts(companyId, f));
    }

    @Operation(summary = "My announcements: active ones reaching my branch or department, or company-wide",
            description = "For every employee with a profile; no permission needed. tab = ALL, HIGH_PRIORITY or FEATURED.")
    @ApiResponses(@ApiResponse(responseCode = "403", description = "No employee profile in the company (EMP_041)"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/announcements/my")
    public ApiMessageDto<ResponseListDto<AnnouncementDto>> feed(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ETab tab,
            @ParameterObject @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.feed(keyword, tab, pageable)));
    }

    @Operation(summary = "Read one of my announcements (counts me as a viewer)",
            description = "The first open of each employee is one view of the statistics.")
    @ApiResponses({
            @ApiResponse(responseCode = "403", description = "No employee profile in the company (EMP_041)"),
            @ApiResponse(responseCode = "404", description = "Not active or not addressed to you (EMP_037)")})
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/announcements/my/{id}")
    public ApiMessageDto<AnnouncementDto> read(@PathVariable Long id) {
        return ApiMessageDto.ok(service.read(id));
    }

    @Operation(summary = "Announcement details: content, attachments and engagement statistics",
            description = "Attachment links are given only to callers with MEDIA_VIEW.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such announcement in your company (EMP_037)"))
    @PreAuthorize("@PermissionChecker.has('ANNOUNCEMENT_VIEW')")
    @GetMapping("/announcements/{id}")
    public ApiMessageDto<AnnouncementDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Announcement statistics: summary, Total Employees, Views, View Rate",
            description = "Total Employees = employees reached today (not terminated); Views = unique employees who "
                    + "opened it; View Rate = Views / Total Employees in percent.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such announcement in your company (EMP_037)"))
    @PreAuthorize("@PermissionChecker.has('ANNOUNCEMENT_VIEW')")
    @GetMapping("/announcements/{id}/statistics")
    public ApiMessageDto<AnnouncementDto> statistics(@PathVariable Long id) {
        return ApiMessageDto.ok(service.statistics(id));
    }

    @Operation(summary = "Add an announcement",
            description = "content is HTML from the rich-text editor; scripts, styles, event handlers and javascript: "
                    + "links are removed. companyWide = every employee, else at least one target branch or department.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = TARGETS + ", no company (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ANNOUNCEMENT_MANAGE')")
    @PostMapping("/announcements")
    public ApiMessageDto<AnnouncementDto> create(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody AnnouncementForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit an announcement (full replacement)",
            description = "Every field is replaced: omitted lists are cleared, omitted flags are false. Views are kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = TARGETS),
            @ApiResponse(responseCode = "404", description = "No such announcement in your company (EMP_037)")})
    @PreAuthorize("@PermissionChecker.has('ANNOUNCEMENT_MANAGE')")
    @PutMapping("/announcements/{id}")
    public ApiMessageDto<AnnouncementDto> update(@PathVariable Long id, @Valid @RequestBody AnnouncementForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete an announcement")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such announcement in your company (EMP_037)"))
    @PreAuthorize("@PermissionChecker.has('ANNOUNCEMENT_MANAGE')")
    @DeleteMapping("/announcements/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    // ------------------------------------------------------------------ categories

    @Operation(summary = "Announcement categories: the system ones and the company's own")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ANNOUNCEMENT_VIEW')")
    @GetMapping("/announcement-categories")
    public ApiMessageDto<List<CategoryDto>> categories(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.categories(companyId));
    }

    @Operation(summary = "Add an announcement category of the company")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (EMP_011), no company (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ANNOUNCEMENT_MANAGE')")
    @PostMapping("/announcement-categories")
    public ApiMessageDto<CategoryDto> createCategory(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.createCategory(companyId, form));
    }

    @Operation(summary = "Edit an announcement category of the company (full replacement)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (EMP_011), system category (EMP_039)"),
            @ApiResponse(responseCode = "404", description = "No such category in your company (EMP_038)")})
    @PreAuthorize("@PermissionChecker.has('ANNOUNCEMENT_MANAGE')")
    @PutMapping("/announcement-categories/{id}")
    public ApiMessageDto<CategoryDto> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.updateCategory(id, form));
    }

    @Operation(summary = "Delete an announcement category of the company",
            description = "Refused while an announcement uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Still in use (EMP_010), system category (EMP_039)"),
            @ApiResponse(responseCode = "404", description = "No such category in your company (EMP_038)")})
    @PreAuthorize("@PermissionChecker.has('ANNOUNCEMENT_MANAGE')")
    @DeleteMapping("/announcement-categories/{id}")
    public ApiMessageDto<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ApiMessageDto.ok(null);
    }
}
