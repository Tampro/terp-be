package com.terp.erp.iam.language;

import io.swagger.v3.oas.annotations.Operation;
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
import com.terp.erp.iam.language.LanguageService.ImportResult;
import com.terp.erp.iam.language.LanguageService.LabelRow;
import com.terp.erp.iam.language.LanguageService.LanguageDto;
import com.terp.erp.iam.language.LanguageService.LanguageForm;
import com.terp.erp.iam.language.LanguageService.LanguageUpdateForm;

import java.util.List;
import java.util.Map;

@Tag(name = "Languages (super admin)", description = "Create Language and Manage Language")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/settings/admin/languages")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("@PermissionChecker.isSuperAdmin()")
public class LanguageController {
    private static final String NOT_FOUND = "No such language (IAM_027)";

    private final LanguageService service;

    @Operation(summary = "All languages, enabled or not")
    @GetMapping
    public ApiMessageDto<List<LanguageDto>> all() {
        return ApiMessageDto.ok(service.all());
    }

    @Operation(summary = "Create Language", description = "Starts as a copy of the default language's labels, to "
            + "translate. Translates the UI only: API messages and emails stay in vi/en/ko (en for this language).")
    @ApiResponses(@ApiResponse(responseCode = "400", description = "Code already exists (IAM_026), validation"))
    @PostMapping
    public ApiMessageDto<LanguageDto> create(@Valid @RequestBody LanguageForm form) {
        return ApiMessageDto.ok(service.create(form));
    }

    @Operation(summary = "Rename, change the flag, enable or disable", description = "Full replacement. Disabled "
            + "languages are hidden from the language selector.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "The default language cannot be disabled (IAM_029)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PutMapping("/{code}")
    public ApiMessageDto<LanguageDto> update(@PathVariable String code, @Valid @RequestBody LanguageUpdateForm form) {
        return ApiMessageDto.ok(service.update(code, form));
    }

    @Operation(summary = "Delete a language you created", description = "With all its labels.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "vi/en/ko cannot be deleted (IAM_028), nor the default "
                    + "language (IAM_029)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @DeleteMapping("/{code}")
    public ApiMessageDto<Void> delete(@PathVariable String code) {
        service.delete(code);
        return ApiMessageDto.ok(null);
    }

    @Operation(summary = "Manage Language: labels with their texts", description = "Every key of the default "
            + "language plus this language's own; keyword searches key and text. translated = false: the default "
            + "language's text is shown.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = NOT_FOUND))
    @GetMapping("/{code}/labels")
    public ApiMessageDto<ResponseListDto<LabelRow>> labels(@PathVariable String code,
                                                           @RequestParam(required = false) String keyword,
                                                           @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(service.labels(code, keyword, pageable)));
    }

    @Operation(summary = "Save Changes", description = "{key: text} of existing keys; an empty text removes this "
            + "language's own text (the default language's is shown).")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Unknown or invalid key, text over 2000 characters "
                    + "(IAM_030)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PutMapping("/{code}/labels")
    public ApiMessageDto<ImportResult> save(@PathVariable String code, @RequestBody Map<String, String> changes) {
        return ApiMessageDto.ok(service.save(code, changes));
    }

    @Operation(summary = "Import the FE label file", description = "{key: text}, at most 10000 keys; adds new keys "
            + "and replaces existing texts. Import into the default language to define the key set.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Empty, too many keys, invalid key or text (IAM_030)"),
            @ApiResponse(responseCode = "404", description = NOT_FOUND)})
    @PostMapping("/{code}/labels/import")
    public ApiMessageDto<ImportResult> importLabels(@PathVariable String code, @RequestBody Map<String, String> file) {
        return ApiMessageDto.ok(service.importLabels(code, file));
    }
}
