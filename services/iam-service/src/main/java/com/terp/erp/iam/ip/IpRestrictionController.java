package com.terp.erp.iam.ip;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import com.terp.erp.iam.ip.IpRestrictionService.IpDto;
import com.terp.erp.iam.ip.IpRestrictionService.IpForm;
import com.terp.erp.iam.ip.IpRestrictionService.Status;
import com.terp.erp.iam.ip.IpRestrictionService.SwitchForm;
import com.terp.erp.iam.login.LoginHistoryService;

@Tag(name = "IP restriction")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/iam/ip-restriction")
@SecurityRequirement(name = "bearerAuth")
public class IpRestrictionController {
    private static final String COMPANY = "Super admin only: the company to act on. Ignored for company users.";
    private static final String NOT_FOUND = "No such IP address entry in your company (IAM_023)";
    private static final String LOCKOUT = "would block your own IP address (IAM_021)";

    private final IpRestrictionService service;

    @Operation(summary = "IP restriction status", description = "enabled = the company switch; platformEnabled = the "
            + "System Settings switch; enforced = login is checked now (both on and the list not empty); yourIp = "
            + "your address as seen through the gateway.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (IAM_003)"))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping
    public ApiMessageDto<Status> status(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                        HttpServletRequest request) {
        return ApiMessageDto.ok(service.status(companyId, ip(request)));
    }

    @Operation(summary = "Turn the company's IP restriction on or off", description = "Turning it on is refused when "
            + "the list is not empty and does not contain your own address.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "On while the list " + LOCKOUT
            + ", super admin without companyId (IAM_003)"))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping
    public ApiMessageDto<Status> setEnabled(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                            @RequestBody SwitchForm form, HttpServletRequest request) {
        return ApiMessageDto.ok(service.setEnabled(companyId, form.enabled(), ip(request)));
    }

    @Operation(summary = "Allowed IP addresses", description = "Sortable by ipAddress.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (IAM_003)"))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @GetMapping("/ips")
    public ApiMessageDto<ResponseListDto<IpDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @ParameterObject @PageableDefault(size = 50, sort = "ipAddress", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(companyId, pageable)));
    }

    @Operation(summary = "Add IP Address", description = "An IPv4 / IPv6 address or a CIDR range (stored normalized).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Not an IP or CIDR (IAM_020), already listed "
            + "(IAM_022), super admin without companyId (IAM_003)"))
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PostMapping("/ips")
    public ApiMessageDto<IpDto> add(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                    @Valid @RequestBody IpForm form) {
        return ApiMessageDto.ok(service.add(companyId, form));
    }

    @Operation(summary = "Edit an IP address")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Not an IP or CIDR (IAM_020), already listed (IAM_022), "
                    + "the enforced list " + LOCKOUT),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @PutMapping("/ips/{id}")
    public ApiMessageDto<IpDto> update(@PathVariable Long id, @Valid @RequestBody IpForm form,
                                       HttpServletRequest request) {
        return ApiMessageDto.ok(service.update(id, form, ip(request)));
    }

    @Operation(summary = "Delete an IP address")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The enforced list " + LOCKOUT),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('IAM_SETTINGS_MANAGE')")
    @DeleteMapping("/ips/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        service.delete(id, ip(request));
        return ApiMessageDto.ok(null);
    }

    private static String ip(HttpServletRequest request) {
        return LoginHistoryService.clientIp(request.getHeader("X-Client-IP"), request.getRemoteAddr());
    }
}
