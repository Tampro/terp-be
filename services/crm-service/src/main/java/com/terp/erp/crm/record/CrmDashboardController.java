package com.terp.erp.crm.record;

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
import com.terp.erp.crm.record.CrmDashboardService.CrmDashboard;

@Tag(name = "CRM dashboard")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/crm/dashboard")
@SecurityRequirement(name = "bearerAuth")
public class CrmDashboardController {
    private final CrmDashboardService service;

    @Operation(summary = "CRM dashboard figures",
            description = "Deals, leads, users (lead owners + deal members), clients, calls, the next 10 open lead "
                    + "tasks from today, deals by stage, the 5 newest deals and leads. CRM_VIEW_OWN (or own=true) "
                    + "counts only my leads / deals.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (CRM_001), "
            + "unknown time zone (COMMON_001)"))
    @PreAuthorize("@PermissionChecker.hasAny('CRM_VIEW','CRM_VIEW_OWN')")
    @GetMapping
    public ApiMessageDto<CrmDashboard> dashboard(
            @Parameter(description = "Super admin only: the company to act on")
            @RequestParam(required = false) Long companyId,
            @Parameter(description = "IANA time zone of today; default Asia/Ho_Chi_Minh")
            @RequestParam(required = false) String tz,
            @Parameter(description = "true = only my leads / deals, also with CRM_VIEW")
            @RequestParam(defaultValue = "false") boolean own) {
        return ApiMessageDto.ok(service.dashboard(companyId, tz, own));
    }
}
