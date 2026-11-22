package com.terp.erp.attendance.record;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.attendance.record.ClockService.ClockForm;
import com.terp.erp.attendance.record.ClockService.TodayDto;
import com.terp.erp.attendance.record.RecordDtos.RecordDto;
import com.terp.erp.common.ApiMessageDto;

@Tag(name = "My Attendance")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/me")
@SecurityRequirement(name = "bearerAuth")
public class ClockController {
    /** Set by the gateway's ClientIpGlobalFilter from the connection; a client-sent value is replaced there. */
    static final String CLIENT_IP_HEADER = "X-Client-IP";

    private final ClockService service;

    @Operation(summary = "My attendance today", description = "Today's record (or last night's open night-shift "
            + "record), my shift, and whether I can clock in / out now.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Super admin (ATT_004)"),
            @ApiResponse(responseCode = "404", description = "No employee profile linked to the account (ATT_060)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_SELF')")
    @GetMapping("/today")
    public ApiMessageDto<TodayDto> today() {
        return ApiMessageDto.ok(service.today());
    }

    @Operation(summary = "Clock in", description = "Once per day, at the current time of the company. Late is "
            + "computed from my shift and attendance policy.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Already clocked in today (ATT_055), super admin (ATT_004)"),
            @ApiResponse(responseCode = "403", description = "IP address not allowed (ATT_054)"),
            @ApiResponse(responseCode = "404", description = "No employee profile linked to the account (ATT_060)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_SELF')")
    @PostMapping("/clock-in")
    public ApiMessageDto<RecordDto> clockIn(@Valid @RequestBody(required = false) ClockForm form,
                                            HttpServletRequest request) {
        return ApiMessageDto.ok(service.clockIn(form, clientIp(request)));
    }

    @Operation(summary = "Clock out", description = "Closes today's open clock-in (or last night's on a night shift). "
            + "Early departure and overtime are computed; notes, when given, replace the clock-in notes.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "No open clock-in (ATT_056), super admin (ATT_004)"),
            @ApiResponse(responseCode = "403", description = "IP address not allowed (ATT_054)"),
            @ApiResponse(responseCode = "404", description = "No employee profile linked to the account (ATT_060)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_SELF')")
    @PostMapping("/clock-out")
    public ApiMessageDto<RecordDto> clockOut(@Valid @RequestBody(required = false) ClockForm form,
                                             HttpServletRequest request) {
        return ApiMessageDto.ok(service.clockOut(form, clientIp(request)));
    }

    /**
     * X-Client-IP of the gateway, else the connection address (a call that did not come through the gateway).
     * X-Forwarded-For is never read: the gateway passes it on as the client sent it.
     */
    static String clientIp(HttpServletRequest request) {
        String h = request.getHeader(CLIENT_IP_HEADER);
        return h != null && !h.isBlank() ? h.trim() : request.getRemoteAddr();
    }
}
