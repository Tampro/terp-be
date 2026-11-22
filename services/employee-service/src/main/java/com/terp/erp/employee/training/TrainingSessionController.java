package com.terp.erp.employee.training;

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
import com.terp.erp.employee.training.TrainingSessionService.AttendanceForm;
import com.terp.erp.employee.training.TrainingSessionService.CalendarItem;
import com.terp.erp.employee.training.TrainingSessionService.CreateResult;
import com.terp.erp.employee.training.TrainingSessionService.SessionDto;
import com.terp.erp.employee.training.TrainingSessionService.SessionForm;
import com.terp.erp.employee.training.TrainingSessionService.StatusCounts;

import java.time.LocalDate;
import java.util.List;

import static com.terp.erp.employee.training.TrainingSupport.COMPANY;

@Tag(name = "Training sessions")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/training/sessions")
@SecurityRequirement(name = "bearerAuth")
public class TrainingSessionController {
    private static final String KEYWORD = "Matches the session or program name or the location";
    private static final String NOT_FOUND = "No such session in your company (EMP_086)";

    private final TrainingSessionService service;

    @Operation(summary = "Training sessions of the company", description = "from / to = Date From / To on the start "
            + "(dates inclusive); status = the tab; attendees = trainees marked Present.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<SessionDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) TrainingSession.ELocationType locationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TrainingSession.EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "startAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(
                service.search(companyId, keyword, programId, locationType, from, to, status, pageable)));
    }

    @Operation(summary = "Counts of the status tabs (All, Scheduled, In Progress, Completed, Cancelled)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> statusCounts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @Parameter(description = KEYWORD) @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) TrainingSession.ELocationType locationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiMessageDto.ok(service.statusCounts(companyId, keyword, programId, locationType, from, to));
    }

    @Operation(summary = "Calendar View", description = "Sessions overlapping [from, to] (dates inclusive, at most "
            + TrainingSessionService.MAX_CALENDAR_DAYS + " days), by start; Program and Status filters.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "to before from (EMP_052), range too long "
            + "(EMP_088), super admin without companyId (EMP_019)"))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/calendar")
    public ApiMessageDto<List<CalendarItem>> calendar(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) TrainingSession.EStatus status) {
        return ApiMessageDto.ok(service.calendar(companyId, from, to, programId, status));
    }

    @Operation(summary = "Session details", description = "Session details, program details, trainers and the "
            + "attendance of the program's trainees (status null = not marked).")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('TRAINING_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<SessionDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Add a session", description = "recurrence = Recurring Session: that many copies "
            + "(daily / weekly / monthly) sharing a seriesId, each edited on its own.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Program completed or cancelled (EMP_083), end not "
                    + "after start (EMP_052), trainer terminated (EMP_058), trainerId not an active trainer of the company "
                    + "(EMP_101)"),
            @ApiResponse(responseCode = "404", description = "No such program (EMP_081) or trainer (EMP_001) in "
                    + "your company")})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PostMapping
    public ApiMessageDto<CreateResult> create(@Valid @RequestBody SessionForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Edit a session", description = "Full replacement of this session only; status omitted = "
            + "kept; recurrence ignored.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "EMP_081, EMP_083, EMP_052, EMP_058, EMP_101"),
            @ApiResponse(responseCode = "404", description = "No such session (EMP_086) or trainer (EMP_001)")})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<SessionDto> update(@PathVariable Long id, @Valid @RequestBody SessionForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @Operation(summary = "Manage Attendance", description = "Marks the listed trainees Present / Absent / Excused "
            + "(status null clears the mark); others are left as they are.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not a trainee of the program (EMP_087)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @PutMapping("/{id}/attendance")
    public ApiMessageDto<SessionDto> attendance(@PathVariable Long id, @Valid @RequestBody AttendanceForm form) {
        return ApiMessageDto.ok(service.updateAttendance(id, form.items()));
    }

    @Operation(summary = "Delete a session")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('TRAINING_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
