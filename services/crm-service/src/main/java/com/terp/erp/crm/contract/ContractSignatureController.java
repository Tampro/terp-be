package com.terp.erp.crm.contract;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.crm.contract.ContractSignature.ESignerType;
import com.terp.erp.crm.contract.ContractSignatureService.Pdf;
import com.terp.erp.crm.contract.ContractSignatureService.SignForm;
import com.terp.erp.crm.contract.ContractSignatureService.SignatureDto;

import java.util.List;

/** Company side of contract signing, signature images and the contract PDF. */
@Tag(name = "CRM — contract signatures")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/crm/contracts/{id}")
@SecurityRequirement(name = "bearerAuth")
public class ContractSignatureController {
    /** Shared with the client portal's Swagger text. */
    public static final String NOT_QUALIFIED = "A hand-drawn signature is an electronic acceptance record, NOT a "
            + "qualified (CA-certified) digital signature.";
    public static final String SIGN_ERRORS = "Not a PNG data URL / bad base64 / bad header (CTR_013), larger than "
            + "200 KB (CTR_014), larger than 2000 x 1000 px (CTR_015), this side already signed (CTR_016), contract "
            + "not PENDING (CTR_017), validation (COMMON_001)";
    private static final String NOT_FOUND = "No such contract in your company / not yours with _OWN (CTR_002)";
    private static final String VIEW = "@PermissionChecker.hasAny('CRM_CONTRACT_VIEW','CRM_CONTRACT_VIEW_OWN',"
            + "'CRM_CONTRACT_MANAGE','CRM_CONTRACT_MANAGE_OWN')";
    private static final String MANAGE = "@PermissionChecker.hasAny('CRM_CONTRACT_MANAGE','CRM_CONTRACT_MANAGE_OWN')";
    private final ContractSignatureService service;

    @Operation(summary = "Sign as the company", description = NOT_QUALIFIED + " Records signer, e-mail (token), "
            + "IP (gateway X-Client-IP), user agent and the SHA-256 of the signed content. Either side may sign "
            + "first; when both have signed the contract becomes ACTIVE. After the first signature the contract can "
            + "no longer be edited or deleted (CTR_018).")
    @ApiResponses({@ApiResponse(responseCode = "400", description = SIGN_ERRORS),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize(MANAGE)
    @PostMapping("/signatures")
    public ApiMessageDto<List<SignatureDto>> sign(@PathVariable Long id, @Valid @RequestBody SignForm form,
                                                  HttpServletRequest request) {
        return ApiMessageDto.ok(service.signAsCompany(id, form, ContractSignatureService.Origin.of(request)));
    }

    @Operation(summary = "Signatures of a contract", description = "contentMatches = the contract's current content "
            + "hash equals the hash stored when that side signed.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping("/signatures")
    public ApiMessageDto<List<SignatureDto>> list(@PathVariable Long id) {
        return ApiMessageDto.ok(service.listForCompany(id));
    }

    @Operation(summary = "Signature image (image/png)", description = "type = COMPANY or CLIENT.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND + ", side not signed (CTR_019)"))
    @PreAuthorize(VIEW)
    @GetMapping(value = "/signatures/{type}/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable Long id, @PathVariable ESignerType type) {
        return png(service.imageForCompany(id, type));
    }

    @Operation(summary = "Contract PDF with signatures", description = NOT_QUALIFIED)
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize(VIEW)
    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return pdf(service.pdfForCompany(id));
    }

    public static ResponseEntity<byte[]> png(byte[] body) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(body);
    }

    public static ResponseEntity<byte[]> pdf(Pdf pdf) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }
}
