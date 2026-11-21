package com.terp.erp.recruitment.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.recruitment.dashboard.RecruitmentDashboardService.RecruitmentDashboard;

@Tag(name = "Recruitment dashboard")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recruitment")
@SecurityRequirement(name = "bearerAuth")
public class RecruitmentDashboardController {
    private final RecruitmentDashboardService service;

    @Operation(summary = "Recruitment figures of the company dashboard",
            description = "Active jobs (published, deadline not passed) and their open positions, the career page "
                    + "slug (null when off) and candidates per status.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (REC_005)"))
    @PreAuthorize("@PermissionChecker.has('RECRUITMENT_VIEW')")
    @GetMapping("/dashboard")
    public ApiMessageDto<RecruitmentDashboard> dashboard(
            @Parameter(description = "Super admin only: the company to act on. Ignored for company users.")
            @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.dashboard(companyId));
    }
}
