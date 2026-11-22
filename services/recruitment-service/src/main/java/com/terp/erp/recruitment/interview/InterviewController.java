package com.terp.erp.recruitment.interview;

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
import com.terp.erp.recruitment.interview.InterviewDtos.FeedbackForm;
import com.terp.erp.recruitment.interview.InterviewDtos.InterviewDetail;
import com.terp.erp.recruitment.interview.InterviewDtos.InterviewForm;
import com.terp.erp.recruitment.interview.InterviewDtos.InterviewRow;
import com.terp.erp.recruitment.interview.InterviewDtos.InterviewSummary;
import com.terp.erp.recruitment.interview.InterviewDtos.KanbanColumn;
import com.terp.erp.recruitment.interview.InterviewDtos.StatusForm;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Interviews")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment/interviews")
@SecurityRequirement(name = "bearerAuth")
public class InterviewController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String KEYWORD = "Matches the candidate's full name or email";
    private static final String FROM = "First date, inclusive (calendar week / month, a day, tomorrow)";
    private static final String TO = "Last date, inclusive";

    private final InterviewService service;

    @Operation(summary = "Interviews of the company (calendar, day table, Tomorrow's Interviews)",
            description = "candidateId = Candidate filter, status = the tab, from / to = the dates shown. Sorted by "
                    + "date and time by default.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<InterviewRow>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long candidateId,
            @Parameter(description = FROM) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Interview.EStatus status,
            @ParameterObject @PageableDefault(size = 50, sort = {"interviewDate", "startTime"}) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, candidateId, from, to, status, pageable)));
    }

    @Operation(summary = "Summary cards, status tab counts and Month Summary",
            description = "total, byStatus (Scheduled, Completed, Cancelled, No-show) and pendingFeedback (completed "
                    + "without feedback) under the list's filters; pass the month as from / to for the Month Summary.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/summary")
    public ApiMessageDto<InterviewSummary> summary(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long candidateId,
            @Parameter(description = FROM) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.summary(companyId, keyword, candidateId, from, to));
    }

    @Operation(summary = "Kanban: Scheduled, Completed, Cancelled, No-show with counts and up to 50 cards each")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/kanban")
    public ApiMessageDto<List<KanbanColumn>> kanban(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long candidateId,
            @Parameter(description = FROM) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = TO) @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.kanban(companyId, keyword, candidateId, from, to));
    }

    @Operation(summary = "Interview details", description = "Candidate panel, interviewers, feedback summary "
            + "(averages, recommendation counts) and every feedback.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such interview in your company (REC_023)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<InterviewDetail> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Schedule an interview (status SCHEDULED)",
            description = "The round must belong to the candidate's job posting. A NEW or SCREENING candidate moves to "
                    + "INTERVIEW. Interviewers are employees (needs EMPLOYEE_VIEW in employee-service).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Round of another posting (REC_024), hired/rejected "
                    + "candidate (REC_025), interviewer busy (REC_026), candidate busy (REC_027), inactive round or "
                    + "type (REC_014)"),
            @ApiResponse(responseCode = "404", description = "No such candidate (REC_015), round (REC_007), type "
                    + "(REC_001), employee (EMP_001, relayed)"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (REC_012)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping
    public ApiMessageDto<InterviewDetail> create(@Valid @RequestBody InterviewForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit an interview", description = "Full replacement of the form; the status is kept. "
            + "Interviewers already assigned keep the name copied then.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "REC_014, REC_024, REC_025, REC_026, REC_027"),
            @ApiResponse(responseCode = "404", description = "REC_023, REC_015, REC_007, REC_001, EMP_001"),
            @ApiResponse(responseCode = "503", description = "employee-service unavailable (REC_012)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<InterviewDetail> update(@PathVariable Long id, @Valid @RequestBody InterviewForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Update Status (Scheduled, Completed, Cancelled, No-show)",
            description = "Back to Scheduled checks the time conflicts again. Completed moves the candidate from "
                    + "INTERVIEW to OFFER (any other candidate status is left as is).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Interviewer busy (REC_026), candidate busy (REC_027)"),
            @ApiResponse(responseCode = "404", description = "No such interview in your company (REC_023)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<InterviewDetail> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return ApiMessageDto.ok(service.changeStatus(id, form.status()));
    }

    @Operation(summary = "Delete an interview")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such interview in your company (REC_023)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Add feedback for one of the interviewers",
            description = "Only for a completed interview; one feedback per interviewer. Returns the interview.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not completed (REC_028), not an interviewer (REC_029), "
                    + "already given (REC_030)"),
            @ApiResponse(responseCode = "404", description = "No such interview in your company (REC_023)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PostMapping("/{id}/feedback")
    public ApiMessageDto<InterviewDetail> addFeedback(@PathVariable Long id, @Valid @RequestBody FeedbackForm form) {
        return ApiMessageDto.ok(service.addFeedback(id, form));
    }

    @Operation(summary = "Edit a feedback", description = "Full replacement of scores and texts; employeeId must stay "
            + "the same.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Another interviewer (REC_029)"),
            @ApiResponse(responseCode = "404", description = "No such interview (REC_023) or feedback (REC_031)")})
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @PutMapping("/{id}/feedback/{feedbackId}")
    public ApiMessageDto<InterviewDetail> updateFeedback(@PathVariable Long id, @PathVariable Long feedbackId,
                                                         @Valid @RequestBody FeedbackForm form) {
        return ApiMessageDto.ok(service.updateFeedback(id, feedbackId, form));
    }

    @Operation(summary = "Delete a feedback", description = "Returns the interview.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such interview (REC_023) or feedback (REC_031)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_MANAGE')")
    @DeleteMapping("/{id}/feedback/{feedbackId}")
    public ApiMessageDto<InterviewDetail> deleteFeedback(@PathVariable Long id, @PathVariable Long feedbackId) {
        return ApiMessageDto.ok(service.deleteFeedback(id, feedbackId));
    }
}
