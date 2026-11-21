package com.terp.erp.employee.performance;

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
import com.terp.erp.employee.performance.ReviewService.ConductForm;
import com.terp.erp.employee.performance.ReviewService.ReviewDto;
import com.terp.erp.employee.performance.ReviewService.ScheduleForm;
import com.terp.erp.employee.performance.ReviewService.StatusCounts;
import com.terp.erp.employee.performance.ReviewService.StatusForm;

import java.time.LocalDate;

import static com.terp.erp.employee.performance.PerformanceSupport.COMPANY;

@Tag(name = "Employee reviews")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/performance/reviews")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {
    private static final String KEYWORD = "Matches the employee's or the reviewer's name or email";
    private static final String NOT_FOUND = "No such review in your company (EMP_075)";

    private final ReviewService service;

    @Operation(summary = "Reviews of the company", description = "cycleId = the Review Cycle filter; from / to = on "
            + "the review date; status = the tab; rating = overallRating.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ReviewDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) EmployeeReview.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "reviewDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, cycleId, from, to, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Scheduled, In Progress, Completed)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cycleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, cycleId, from, to));
    }

    @Operation(summary = "Review details (View and the Conduct Review page)", description = "groups = Performance "
            + "Ratings by indicator category. Before the first conduct (ratingsSaved = false) they preview the "
            + "company's active indicators, which are copied into the review when it is first saved.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<ReviewDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Schedule a review (SCHEDULED or IN_PROGRESS)")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Reviewer = employee (EMP_076), employee terminated "
                    + "(EMP_058), cycle not in the company (EMP_073) or inactive (EMP_054), COMPLETED given "
                    + "(EMP_079)"),
            @ApiResponse(responseCode = "404", description = "No such employee in your company (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PostMapping
    public ApiMessageDto<ReviewDto> schedule(@Valid @RequestBody ScheduleForm form) {
        return ApiMessageDto.ok(service.schedule(form));
    }

    @Operation(summary = "Edit the schedule", description = "Full replacement while not completed; status omitted = "
            + "kept. Ratings are kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Completed (EMP_077), EMP_076, EMP_058, EMP_073, "
                    + "EMP_054, EMP_079"),
            @ApiResponse(responseCode = "404", description = "No such review (EMP_075) or employee (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<ReviewDto> update(@PathVariable Long id, @Valid @RequestBody ScheduleForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Conduct Review", description = "Saves ratings (1 Poor – 5 Excellent) and comments of the "
            + "listed indicators plus the overall comments; the overall rating is the weighted average of the rated "
            + "indicators. submit = false keeps the review IN_PROGRESS; true completes it.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Completed (EMP_077), indicator not in the review "
                    + "(EMP_078), submit with an unrated indicator or none at all (EMP_079)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PutMapping("/{id}/conduct")
    public ApiMessageDto<ReviewDto> conduct(@PathVariable Long id, @Valid @RequestBody ConductForm form) {
        return ApiMessageDto.ok(service.conduct(id, form));
    }

    @Operation(summary = "Update Status", description = "COMPLETED needs every indicator rated; another status "
            + "reopens a completed review for conducting.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not every indicator rated (EMP_079)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<ReviewDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete a review", description = "Not once completed.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Completed (EMP_077)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('PERFORMANCE_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
