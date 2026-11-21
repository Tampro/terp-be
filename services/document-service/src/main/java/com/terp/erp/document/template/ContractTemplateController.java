package com.terp.erp.document.template;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
import com.terp.erp.document.common.EStatus;
import com.terp.erp.document.template.DocTemplateService.Preview;
import com.terp.erp.document.template.DocTemplateService.StatusCounts;
import com.terp.erp.document.template.DocTemplateService.TemplateDto;
import com.terp.erp.document.template.DocTemplateService.TemplateForm;

import static com.terp.erp.document.common.Texts.COMPANY;

@Tag(name = "Contract templates")
@RestController
@RequestMapping("/api/v1/document/contract-templates")
@SecurityRequirement(name = "bearerAuth")
public class ContractTemplateController extends TemplateEndpoints {
    public ContractTemplateController(DocTemplateService service) {
        super(service, DocTemplate.EKind.CONTRACT);
    }

    @Operation(summary = "Contract templates", description = "keyword = name or description; contractTypeId; status = "
            + "the tab. variableCount / clauseCount / contentLength = the list columns.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_VIEW')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<TemplateDto>> list(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long contractTypeId,
            @RequestParam(required = false) EStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return doList(companyId, keyword, contractTypeId, status, pageable);
    }

    @Operation(summary = "Counts of the status tabs (All, Active, Inactive)")
    @ApiResponses(@ApiResponse(responseCode = "400", description = NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_VIEW')")
    @GetMapping("/status-counts")
    public ApiMessageDto<StatusCounts> counts(
            @Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long contractTypeId) {
        return doCounts(companyId, keyword, contractTypeId);
    }

    @Operation(summary = "View a template", description = "With the content and the variables.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_VIEW')")
    @GetMapping("/{id}")
    public ApiMessageDto<TemplateDto> get(@PathVariable Long id) {
        return doGet(id);
    }

    @Operation(summary = "Preview", description = "The content with [variable_name] placeholders.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_VIEW')")
    @GetMapping("/{id}/preview")
    public ApiMessageDto<Preview> preview(@PathVariable Long id) {
        return doPreview(id);
    }

    @Operation(summary = "Add Template", description = "groupId = the contract type. Variables = the comma list plus "
            + "the {{names}} of the content. Set as Default clears the other defaults of the contract type.")
    @ApiResponses(@ApiResponse(responseCode = "400", description = FORM_ERRORS + ", contract type not in the "
            + "company (DOC_005), " + NO_COMPANY))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @PostMapping
    public ApiMessageDto<TemplateDto> create(@Parameter(description = COMPANY) @RequestParam(required = false) Long companyId,
                                             @Valid @RequestBody TemplateForm form) {
        return doCreate(companyId, form);
    }

    @Operation(summary = "Edit a template", description = "Full replacement; status and default omitted = kept.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = FORM_ERRORS + ", contract type not in the company "
                    + "(DOC_005)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @PutMapping("/{id}")
    public ApiMessageDto<TemplateDto> update(@PathVariable Long id, @Valid @RequestBody TemplateForm form) {
        return doUpdate(id, form);
    }

    @Operation(summary = "Toggle Status")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @PutMapping("/{id}/status")
    public ApiMessageDto<TemplateDto> status(@PathVariable Long id, @Valid @RequestBody StatusForm form) {
        return doStatus(id, form.status());
    }

    @Operation(summary = "Delete a template")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @DeleteMapping("/{id}")
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        return doDelete(id);
    }

    @Operation(summary = "Generate Contract (PDF)", description = "Fills the variables and returns the PDF. contractId "
            + "= also upload it to the media library (needs MEDIA_UPLOAD) and attach it to that contract "
            + "(documentFileId; the id is in the X-Attached-File-Id header).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Inactive template (DOC_020), missing values (DOC_021)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND + ", no such contract (DOC_017)"),
            @ApiResponse(responseCode = "503", description = "media-service unavailable (DOC_007)")})
    @PreAuthorize("@PermissionChecker.has('CONTRACT_MANAGE')")
    @PostMapping("/{id}/generate")
    public ResponseEntity<byte[]> generate(@PathVariable Long id, @RequestParam(required = false) Long contractId,
                                           @Valid @RequestBody(required = false) GenerateForm form) {
        return doGenerate(id, form, contractId);
    }
}
