package com.terp.erp.order.ret;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.order.ret.ReturnDtos.ApproveForm;
import com.terp.erp.order.ret.ReturnDtos.PolicyDto;
import com.terp.erp.order.ret.ReturnDtos.PolicyForm;
import com.terp.erp.order.ret.ReturnDtos.RejectForm;
import com.terp.erp.order.ret.ReturnDtos.ReturnDto;
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
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Back Office: review, approve / reject and complete customer returns; the return policy. */
@Tag(name = "Returns (Back Office)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/order")
@SecurityRequirement(name = "bearerAuth")
public class ReturnController {
    private static final String COMPANY = "Super admin only: the company to act on";
    private static final String NOT_FOUND = "No such return in your company (RET_007)";
    private final ReturnService service;

    @Operation(summary = "Returns of the company", description = "Newest first; filters optional.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (RET_008)"))
    @PreAuthorize("@PermissionChecker.has('ORDER_RETURN_VIEW')")
    @GetMapping("/returns")
    public ApiMessageDto<ResponseListDto<ReturnDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) EReturnStatus status, @RequestParam(required = false) Long storeId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(companyId, status, storeId, pageable)));
    }

    @Operation(summary = "A return of the company")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('ORDER_RETURN_VIEW')")
    @GetMapping("/returns/{id}")
    public ApiMessageDto<ReturnDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @Operation(summary = "Approve a requested return", description = "REQUESTED -> APPROVED. A new reason group may be "
            + "given; the pick-up fee and refund are recomputed.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not REQUESTED (RET_006), nothing left after the fee (RET_004)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('ORDER_RETURN_MANAGE')")
    @PostMapping("/returns/{id}/approve")
    public ApiMessageDto<ReturnDto> approve(@PathVariable Long id, @RequestBody(required = false) ApproveForm form) {
        return ApiMessageDto.ok(service.approve(id, form == null ? null : form.reason()));
    }

    @Operation(summary = "Reject a requested return", description = "REQUESTED -> REJECTED; its items can be asked again.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not REQUESTED (RET_006), note missing (COMMON_001)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('ORDER_RETURN_MANAGE')")
    @PostMapping("/returns/{id}/reject")
    public ApiMessageDto<ReturnDto> reject(@PathVariable Long id, @Valid @RequestBody RejectForm form) {
        return ApiMessageDto.ok(service.reject(id, form.note()));
    }

    @Operation(summary = "Complete an approved return (goods back)", description = "Stock goes back, the refund is paid "
            + "(sandbox), points spent come back and points earned are taken back, a reversal journal (SHOP_ORDER_RETURN) "
            + "is requested and the customer is emailed (ORDER_RETURNED). A failure leaves it APPROVED; retrying is safe.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Not APPROVED (RET_006)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND),
            @ApiResponse(responseCode = "503", description = "inventory unavailable (ORD_007), nothing changed")})
    @PreAuthorize("@PermissionChecker.has('ORDER_RETURN_MANAGE')")
    @PostMapping("/returns/{id}/complete")
    public ApiMessageDto<ReturnDto> complete(@PathVariable Long id) {
        return ApiMessageDto.ok(service.complete(id));
    }

    @Operation(summary = "The company's return policy", description = "saved = false: the default pick-up fee (3,000 won). "
            + "The window is 30 days for every item.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (RET_008)"))
    @PreAuthorize("@PermissionChecker.has('ORDER_RETURN_VIEW')")
    @GetMapping("/return-policy")
    public ApiMessageDto<PolicyDto> policy(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId) {
        return ApiMessageDto.ok(service.policyDto(companyId));
    }

    @Operation(summary = "Replace the return policy", description = "PUT is full replacement.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Super admin without companyId (RET_008), fee out of "
            + "0–100,000 or not whole won (COMMON_001)"))
    @PreAuthorize("@PermissionChecker.has('ORDER_RETURN_MANAGE')")
    @PutMapping("/return-policy")
    public ApiMessageDto<PolicyDto> savePolicy(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                               @Valid @RequestBody PolicyForm form) {
        return ApiMessageDto.ok(service.savePolicy(companyId, form.pickupFee()));
    }
}
