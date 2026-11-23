package com.terp.erp.accounting.portal;

import com.terp.erp.accounting.portal.PortalPartyService.PartyDto;
import com.terp.erp.accounting.portal.PortalPartyService.PortalNoteDto;
import com.terp.erp.accounting.portal.PortalPartyService.PortalPaymentDto;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Client / vendor portal: only rows of the customer / vendor linked to the caller (no party id in the request). */
public final class PortalControllers {
    private static final String NOT_LINKED = "Your account is not linked to a customer / vendor (ACC_098)";

    private PortalControllers() {
    }

    @Tag(name = "Portal party")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/accounting/me/party")
    @SecurityRequirement(name = "bearerAuth")
    public static class MyPartyController {
        private final PortalPartyService service;

        @Operation(summary = "The customer and / or vendor linked to my account",
                description = "Resolved by Customer.userId / Vendor.userId = my IAM user id in my company. Used by "
                        + "the portal and by other services (called with the caller's token).")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_LINKED))
        @PreAuthorize("@PermissionChecker.hasAny('PORTAL_CLIENT','PORTAL_VENDOR')")
        @GetMapping
        public ApiMessageDto<PartyDto> me() {
            return ApiMessageDto.ok(service.me());
        }
    }

    @Tag(name = "Client portal")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/portal/client")
    @SecurityRequirement(name = "bearerAuth")
    public static class ClientPortalController {
        private final PortalPartyService service;

        @Operation(summary = "My payments (as a customer)", description = "Customer payments CP- of my customer.")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_LINKED))
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/payments")
        public ApiMessageDto<ResponseListDto<PortalPaymentDto>> payments(
                @ParameterObject @PageableDefault(size = 20, sort = "paymentDate", direction = Sort.Direction.DESC)
                Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.customerPayments(pageable)));
        }

        @Operation(summary = "My credit notes", description = "Credit notes CN- issued to my customer (no drafts).")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_LINKED))
        @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
        @GetMapping("/credit-notes")
        public ApiMessageDto<ResponseListDto<PortalNoteDto>> creditNotes(
                @ParameterObject @PageableDefault(size = 20, sort = "noteDate", direction = Sort.Direction.DESC)
                Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.creditNotes(pageable)));
        }
    }

    @Tag(name = "Vendor portal")
    @RestController
    @RequiredArgsConstructor
    @RequestMapping("/api/v1/portal/vendor")
    @SecurityRequirement(name = "bearerAuth")
    public static class VendorPortalController {
        private final PortalPartyService service;

        @Operation(summary = "My payments (as a vendor)", description = "Vendor payments VP- to my vendor.")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_LINKED))
        @PreAuthorize("@PermissionChecker.has('PORTAL_VENDOR')")
        @GetMapping("/payments")
        public ApiMessageDto<ResponseListDto<PortalPaymentDto>> payments(
                @ParameterObject @PageableDefault(size = 20, sort = "paymentDate", direction = Sort.Direction.DESC)
                Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.vendorPayments(pageable)));
        }

        @Operation(summary = "My debit notes", description = "Debit notes DN- issued to my vendor (no drafts).")
        @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_LINKED))
        @PreAuthorize("@PermissionChecker.has('PORTAL_VENDOR')")
        @GetMapping("/debit-notes")
        public ApiMessageDto<ResponseListDto<PortalNoteDto>> debitNotes(
                @ParameterObject @PageableDefault(size = 20, sort = "noteDate", direction = Sort.Direction.DESC)
                Pageable pageable) {
            return ApiMessageDto.ok(ResponseListDto.of(service.debitNotes(pageable)));
        }
    }
}
