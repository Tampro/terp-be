package com.terp.erp.project.form;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.project.form.FormConvertService.ConvertForm;
import com.terp.erp.project.form.FormConvertService.ConvertResult;
import com.terp.erp.project.form.FormService.FormDto;
import com.terp.erp.project.form.FormService.FormForm;
import com.terp.erp.project.form.FormService.FormRow;
import com.terp.erp.project.form.FormService.ResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/forms")
@Tag(name = "Forms", description = "Form builder (ERP manual 11.2): forms, public link, responses, convert")
@SecurityRequirement(name = "bearerAuth")
public class FormController {
    private final FormService service;
    private final FormConvertService convertService;

    @GetMapping
    @PreAuthorize("@PermissionChecker.has('FORM_VIEW')")
    @Operation(summary = "List forms of the company")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "PRJ_001 (super admin without companyId)")})
    public ApiMessageDto<ResponseListDto<FormRow>> list(@RequestParam(required = false) Long companyId,
                                                        @RequestParam(required = false) String keyword,
                                                        @ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.list(companyId, keyword, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@PermissionChecker.has('FORM_VIEW')")
    @Operation(summary = "Form with its fields and public token")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "FRM_001")})
    public ApiMessageDto<FormDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("@PermissionChecker.has('FORM_MANAGE')")
    @Operation(summary = "Create a form (a public token is generated)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, PRJ_001, FRM_003, FRM_018, FRM_021, FRM_022")})
    public ApiMessageDto<FormDto> create(@Valid @RequestBody FormForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@PermissionChecker.has('FORM_MANAGE')")
    @Operation(summary = "Replace a form",
            description = "Full replacement: name, active, layout and the whole field list. A field keeps its"
                    + " key (and so its answers and convert mappings) only when the key is sent back.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, FRM_003, FRM_018, FRM_021, FRM_022"),
            @ApiResponse(responseCode = "404", description = "FRM_001")})
    public ApiMessageDto<FormDto> update(@PathVariable Long id, @Valid @RequestBody FormForm form) {
        return ApiMessageDto.ok(service.update(id, form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@PermissionChecker.has('FORM_MANAGE')")
    @Operation(summary = "Delete a form (soft); its public link stops working")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "FRM_001")})
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiMessageDto.ok(null);
    }

    @PostMapping("/{id}/regenerate-token")
    @PreAuthorize("@PermissionChecker.has('FORM_MANAGE')")
    @Operation(summary = "Issue a new public token; the old link answers 404 at once")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "FRM_001")})
    public ApiMessageDto<FormRow> regenerateToken(@PathVariable Long id) {
        return ApiMessageDto.ok(service.regenerateToken(id));
    }

    @GetMapping("/{id}/responses")
    @PreAuthorize("@PermissionChecker.has('FORM_VIEW')")
    @Operation(summary = "Responses of a form, newest first by default",
            description = "from / to are inclusive days (yyyy-MM-dd, UTC). PASSWORD answers are masked.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "FRM_020"),
            @ApiResponse(responseCode = "404", description = "FRM_001")})
    public ApiMessageDto<ResponseListDto<ResponseDto>> responses(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.responses(id, from, to, pageable)));
    }

    @GetMapping("/{id}/responses/{responseId}")
    @PreAuthorize("@PermissionChecker.has('FORM_VIEW')")
    @Operation(summary = "One response (PASSWORD answers masked)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "FRM_001, FRM_014")})
    public ApiMessageDto<ResponseDto> response(@PathVariable Long id, @PathVariable Long responseId) {
        return ApiMessageDto.ok(service.response(id, responseId));
    }

    @DeleteMapping("/{id}/responses/{responseId}")
    @PreAuthorize("@PermissionChecker.has('FORM_MANAGE')")
    @Operation(summary = "Delete a response (soft)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "FRM_001, FRM_014")})
    public ApiMessageDto<Void> deleteResponse(@PathVariable Long id, @PathVariable Long responseId) {
        service.deleteResponse(id, responseId);
        return ApiMessageDto.ok(null);
    }

    @PostMapping("/{id}/responses/{responseId}/convert")
    @PreAuthorize("@PermissionChecker.has('FORM_MANAGE')")
    @Operation(summary = "Convert a response to a project, CRM lead, deal or contract (once)",
            description = "PROJECT also needs PROJECT_MANAGE. LEAD/DEAL/CONTRACT are created in crm-service with the"
                    + " caller's token: crm-service applies its own permissions and company checks, and its errors"
                    + " (CRM_xxx, CON_xxx, COMMON_xxx) are relayed as they are.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400",
                    description = "COMMON_001, FRM_015, FRM_016, FRM_017, FRM_018, FRM_023, relayed crm-service codes"),
            @ApiResponse(responseCode = "403", description = "COMMON_004 (PROJECT without PROJECT_MANAGE)"),
            @ApiResponse(responseCode = "404", description = "FRM_001, FRM_014"),
            @ApiResponse(responseCode = "503", description = "PRJ_003 (crm-service down)")})
    public ApiMessageDto<ConvertResult> convert(@PathVariable Long id, @PathVariable Long responseId,
                                                @Valid @RequestBody ConvertForm form) {
        return ApiMessageDto.ok(convertService.convert(id, responseId, form));
    }
}
