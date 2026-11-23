package com.terp.erp.project.project;

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
import com.terp.erp.project.project.ProjectStatsService.ProjectStats;

@Tag(name = "Projects")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects")
@SecurityRequirement(name = "bearerAuth")
public class ProjectStatsController {
    private final ProjectStatsService service;

    @Operation(summary = "Project dashboard figures",
            description = "Over the projects you can see: every project of the company with PROJECT_VIEW, else "
                    + "(PROJECT_VIEW_OWN) those you are on the team of or created (scope = OWN). Totals are "
                    + "all-time; monthly (tasks created vs completed) is for the year in the time zone tz.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (PRJ_001), "
            + "unknown time zone (COMMON_001)"))
    @PreAuthorize("@PermissionChecker.hasAny('PROJECT_VIEW','PROJECT_VIEW_OWN')")
    @GetMapping("/stats")
    public ApiMessageDto<ProjectStats> stats(
            @Parameter(description = "Super admin only: the company to act on")
            @RequestParam(required = false) Long companyId,
            @Parameter(description = "Year of the monthly trend; default this year")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "IANA time zone; default Asia/Ho_Chi_Minh")
            @RequestParam(required = false) String tz) {
        return ApiMessageDto.ok(service.stats(companyId, year, tz));
    }
}
