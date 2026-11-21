package com.terp.erp.iam.impersonation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.iam.impersonation.ImpersonationService.SessionDto;
import com.terp.erp.iam.impersonation.ImpersonationService.StartedDto;
import com.terp.erp.iam.login.LoginHistoryService;
import com.terp.erp.security.CurrentUser;

@Tag(name = "Login as Company", description = "Super admin acts as a company's administrator for a limited time")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/iam")
@SecurityRequirement(name = "bearerAuth")
public class ImpersonationController {
    private final ImpersonationService service;

    public record EndForm(@NotBlank @Size(max = 200) String sessionKey) {
    }

    @Operation(summary = "Login as Company: start a session",
            description = "Call without " + ImpersonationService.HEADER + ". Returns a session key valid 1 hour; "
                    + "send it as " + ImpersonationService.HEADER + " on every request (all services) to act as the "
                    + "company's administrator. Changes made meanwhile are recorded under the super admin.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such company (IAM_001)"))
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PostMapping("/companies/{id}/impersonation")
    public ApiMessageDto<StartedDto> start(@PathVariable Long id, HttpServletRequest request) {
        return ApiMessageDto.ok(service.start(id, LoginHistoryService.clientIp(request.getHeader("X-Client-IP"),
                request.getRemoteAddr())));
    }

    @Operation(summary = "Back to super admin: end the session",
            description = "Call without " + ImpersonationService.HEADER + ". Always 200.")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @PostMapping("/impersonation/end")
    public ApiMessageDto<Void> end(@Valid @RequestBody EndForm form) {
        service.end(form.sessionKey(), CurrentUser.get().keycloakId());
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Login as Company sessions (audit)", description = "Newest first; companyId filters.")
    @PreAuthorize("@PermissionChecker.isSuperAdmin()")
    @GetMapping("/impersonations")
    public ApiMessageDto<ResponseListDto<SessionDto>> history(
            @Parameter(description = "Only this company") @RequestParam(required = false) Long companyId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.history(companyId, pageable)));
    }
}
