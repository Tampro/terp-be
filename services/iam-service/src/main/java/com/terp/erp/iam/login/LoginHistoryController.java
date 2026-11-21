package com.terp.erp.iam.login;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.iam.login.LoginHistoryService.LoginDto;
import com.terp.erp.iam.model.LoginHistory.EUserType;

@Tag(name = "Login history")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/iam/login-history")
@SecurityRequirement(name = "bearerAuth")
public class LoginHistoryController {
    private static final String NOT_FOUND = "No such login record in your company (IAM_019)";

    private final LoginHistoryService service;

    @Operation(summary = "Login History", description = "Newest first. A company user sees their company's logins; a "
            + "super admin every login (companyId = one company, userType = SUPER_ADMIN / COMPANY_USER). keyword = "
            + "username or IP.")
    @PreAuthorize("@PermissionChecker.has('IAM_USER_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<LoginDto>> list(
            @Parameter(description = "Super admin only: one company") @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) EUserType userType,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "loginAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.search(companyId, userId, userType, keyword, pageable)));
    }

    @Operation(summary = "Login details", description = "IP, browser, OS and the full User-Agent.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('IAM_USER_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<LoginDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Delete a login record")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('IAM_USER_UPDATE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }
}
