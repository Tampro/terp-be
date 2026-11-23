package com.terp.erp.crm.portal;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.crm.contract.ContractPortalService;
import com.terp.erp.crm.contract.ContractPortalService.PortalContract;
import com.terp.erp.crm.contract.ContractPortalService.PortalContractDetail;
import com.terp.erp.crm.record.DealPortalService;
import com.terp.erp.crm.record.DealPortalService.PortalDeal;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Client portal (read-only): contracts and deals of the customer linked to my account; no party id is taken in. */
@Tag(name = "Client portal — CRM")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/portal/client")
@SecurityRequirement(name = "bearerAuth")
public class ClientPortalController {
    static final String NOT_LINKED = "Account not linked to a customer (ACC_098 relayed from accounting, or CRM_022)";
    static final String UNAVAILABLE = "Accounting service unavailable (CRM_015)";
    private final ContractPortalService contracts;
    private final DealPortalService deals;

    @Operation(summary = "My contracts", description = "Contracts whose customer is the customer linked to my account.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = NOT_LINKED),
            @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping("/contracts")
    public ApiMessageDto<ResponseListDto<PortalContract>> contracts(
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(contracts.list(pageable)));
    }

    @Operation(summary = "One of my contracts", description = "With its renewals; comments, notes and attachments "
            + "are internal and not shown.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = "Not a contract of my customer (CTR_002); "
            + NOT_LINKED), @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping("/contracts/{id}")
    public ApiMessageDto<PortalContractDetail> contract(@PathVariable Long id) {
        return ApiMessageDto.ok(contracts.get(id));
    }

    @Operation(summary = "My deals", description = "Deals with my customer as a client.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = NOT_LINKED),
            @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping("/deals")
    public ApiMessageDto<ResponseListDto<PortalDeal>> deals(
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(deals.list(pageable)));
    }

    @Operation(summary = "One of my deals", description = "Name, price, status and stage only.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = "Not a deal of my customer (CRM_007); "
            + NOT_LINKED), @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping("/deals/{id}")
    public ApiMessageDto<PortalDeal> deal(@PathVariable Long id) {
        return ApiMessageDto.ok(deals.get(id));
    }
}
