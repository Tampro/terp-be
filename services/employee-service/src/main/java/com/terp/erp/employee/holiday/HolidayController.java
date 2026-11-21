package com.terp.erp.employee.holiday;

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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.terp.erp.employee.holiday.HolidayDtos.CategoryDto;
import com.terp.erp.employee.holiday.HolidayDtos.CategoryForm;
import com.terp.erp.employee.holiday.HolidayDtos.Filter;
import com.terp.erp.employee.holiday.HolidayDtos.HolidayDto;
import com.terp.erp.employee.holiday.HolidayDtos.HolidayForm;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Holidays")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/org")
@SecurityRequirement(name = "bearerAuth")
public class HolidayController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String FILTERS = "keyword matches name or description; categoryId; year (calendar year); "
            + "dateFrom/dateTo keep holidays overlapping the range; branchId keeps that branch's and the all-branch ones.";
    private static final MediaType ICAL = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final HolidayService service;

    @Operation(summary = "Holidays of the company (list view)",
            description = FILTERS + " Sort by name, startDate, createdDate.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping("/holidays")
    public ApiMessageDto<ResponseListDto<HolidayDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long branchId,
            @ParameterObject @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.ASC)
            Pageable pageable) {
        Filter f = new Filter(keyword, categoryId, year, dateFrom, dateTo, branchId);
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, f, pageable)));
    }

    @Operation(summary = "Holidays overlapping [from, to] for the calendar (month, week, day views)",
            description = "Not paged, ordered by start date. Range at most 400 days.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "to before from (EMP_034), range too long "
            + "(EMP_036), super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping("/holidays/calendar")
    public ApiMessageDto<List<HolidayDto>> calendar(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId) {
        return ApiMessageDto.ok(service.calendar(companyId, from, to, branchId));
    }

    @Operation(summary = "Export the holidays as PDF", description = "Same filters as the list; up to 1000 rows.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping("/holidays/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long branchId) {
        Filter f = new Filter(keyword, categoryId, year, dateFrom, dateTo, branchId);
        byte[] pdf = service.exportPdf(companyId, f);
        return file(pdf, MediaType.APPLICATION_PDF, HolidayExporter.fileName(f, "pdf"));
    }

    @Operation(summary = "Export the holidays as iCalendar (.ics)",
            description = "Same filters as the list; all-day events, importable into Google Calendar or Outlook.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping("/holidays/export/ical")
    public ResponseEntity<byte[]> exportIcal(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long branchId) {
        Filter f = new Filter(keyword, categoryId, year, dateFrom, dateTo, branchId);
        byte[] ics = service.exportIcal(companyId, f);
        return file(ics, ICAL, HolidayExporter.fileName(f, "ics"));
    }

    @Operation(summary = "Holiday details")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such holiday in your company (EMP_032)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping("/holidays/{id}")
    public ApiMessageDto<HolidayDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a holiday", description = "No branchIds = all branches.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "End before start (EMP_034), longer than 400 days "
            + "(EMP_036), unknown category (EMP_033), branch not in the company (EMP_002), inactive branch (EMP_029), "
            + "no company (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PostMapping("/holidays")
    public ApiMessageDto<HolidayDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                            @Valid @RequestBody HolidayForm form) {
        return ApiMessageDto.ok(service.create(companyId, form));
    }

    @Operation(summary = "Edit a holiday (full replacement)",
            description = "Every field is replaced: omitted description is cleared, omitted branchIds = all branches.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "End before start (EMP_034), longer than 400 days "
                    + "(EMP_036), unknown category (EMP_033), branch not in the company (EMP_002), inactive branch (EMP_029)"),
            @ApiResponse(responseCode = "404", description = "No such holiday in your company (EMP_032)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PutMapping("/holidays/{id}")
    public ApiMessageDto<HolidayDto> update(@PathVariable Long id, @Valid @RequestBody HolidayForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Delete a holiday")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such holiday in your company (EMP_032)"))
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @DeleteMapping("/holidays/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    // ------------------------------------------------------------------ categories

    @Operation(summary = "Holiday categories: the four system ones and the company's own",
            description = "color is the calendar legend colour.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_VIEW')")
    @GetMapping("/holiday-categories")
    public ApiMessageDto<List<CategoryDto>> categories(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.categories(companyId));
    }

    @Operation(summary = "Add a holiday category of the company")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Name taken (EMP_011), no company (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PostMapping("/holiday-categories")
    public ApiMessageDto<CategoryDto> createCategory(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.createCategory(companyId, form));
    }

    @Operation(summary = "Edit a holiday category of the company (full replacement)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Name taken (EMP_011), system category (EMP_035)"),
            @ApiResponse(responseCode = "404", description = "No such category in your company (EMP_033)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @PutMapping("/holiday-categories/{id}")
    public ApiMessageDto<CategoryDto> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryForm form) {
        return ApiMessageDto.ok(service.updateCategory(id, form));
    }

    @Operation(summary = "Delete a holiday category of the company", description = "Refused while a holiday uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Still in use (EMP_010), system category (EMP_035)"),
            @ApiResponse(responseCode = "404", description = "No such category in your company (EMP_033)")})
    @PreAuthorize("@PermissionChecker.has('ORG_MANAGE')")
    @DeleteMapping("/holiday-categories/{id}")
    public ApiMessageDto<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ApiMessageDto.ok(null);
    }

    private static ResponseEntity<byte[]> file(byte[] body, MediaType type, String name) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
