package com.terp.erp.attendance.leave;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.attendance.leave.MyLeaveService.MyLeave;
import com.terp.erp.common.ApiMessageDto;

@Tag(name = "My Attendance")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance/me")
@SecurityRequirement(name = "bearerAuth")
public class MyLeaveController {
    private final MyLeaveService service;

    @Operation(summary = "My leave", description = "My leave balances of this year (as of the last balance sync), "
            + "my pending applications count and my 5 latest applications.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Super admin (ATT_004)"),
            @ApiResponse(responseCode = "404", description = "No employee profile linked to the account (ATT_060)")})
    @PreAuthorize("@PermissionChecker.has('ATTENDANCE_SELF')")
    @GetMapping("/leave")
    public ApiMessageDto<MyLeave> leave() {
        return ApiMessageDto.ok(service.mine());
    }
}
