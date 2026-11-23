package com.terp.erp.crm.portal;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.crm.contract.ContractPortalService;
import com.terp.erp.crm.contract.ContractPortalService.PortalContract;
import com.terp.erp.crm.contract.ContractPortalService.PortalContractDetail;
import com.terp.erp.crm.contract.ContractSignature.ESignerType;
import com.terp.erp.crm.contract.ContractSignatureController;
import com.terp.erp.crm.contract.ContractSignatureService;
import com.terp.erp.crm.contract.ContractSignatureService.PortalSignature;
import com.terp.erp.crm.contract.ContractSignatureService.SignForm;
import com.terp.erp.crm.record.DealPortalService;
import com.terp.erp.crm.record.DealPortalService.PortalDeal;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Client portal: contracts (read, sign, PDF) and deals (read) of the customer linked to my account; no party id is taken in. */
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
    private final ContractSignatureService signatures;

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

    @Operation(summary = "Sign one of my contracts", description = ContractSignatureController.NOT_QUALIFIED
            + " Signs for the client side; the printed name and e-mail come from my token (body signerName is "
            + "ignored). IP and user agent are recorded but never shown on the portal. When both sides have signed "
            + "the contract becomes ACTIVE.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = ContractSignatureController.SIGN_ERRORS),
            @ApiResponse(responseCode = "404", description = "Not a contract of my customer (CTR_002); " + NOT_LINKED),
            @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @PostMapping("/contracts/{id}/sign")
    public ApiMessageDto<List<PortalSignature>> sign(@PathVariable Long id, @Valid @RequestBody SignForm form,
                                                     HttpServletRequest request) {
        return ApiMessageDto.ok(signatures.signAsClient(id, form, ContractSignatureService.Origin.of(request)));
    }

    @Operation(summary = "Signatures of one of my contracts", description = "Side, name, time and whether the "
            + "contract still matches what was signed (contentMatches).")
    @ApiResponses({@ApiResponse(responseCode = "404", description = "Not a contract of my customer (CTR_002); "
            + NOT_LINKED), @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping("/contracts/{id}/signatures")
    public ApiMessageDto<List<PortalSignature>> contractSignatures(@PathVariable Long id) {
        return ApiMessageDto.ok(signatures.listForClient(id));
    }

    @Operation(summary = "Signature image of one of my contracts (image/png)", description = "type = COMPANY or CLIENT.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = "Not a contract of my customer (CTR_002), side "
            + "not signed (CTR_019); " + NOT_LINKED), @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping(value = "/contracts/{id}/signatures/{type}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> signatureImage(@PathVariable Long id, @PathVariable ESignerType type) {
        return ContractSignatureController.png(signatures.imageForClient(id, type));
    }

    @Operation(summary = "PDF of one of my contracts", description = ContractSignatureController.NOT_QUALIFIED)
    @ApiResponses({@ApiResponse(responseCode = "404", description = "Not a contract of my customer (CTR_002); "
            + NOT_LINKED), @ApiResponse(responseCode = "503", description = UNAVAILABLE)})
    @PreAuthorize("@PermissionChecker.has('PORTAL_CLIENT')")
    @GetMapping(value = "/contracts/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> contractPdf(@PathVariable Long id) {
        return ContractSignatureController.pdf(signatures.pdfForClient(id));
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
