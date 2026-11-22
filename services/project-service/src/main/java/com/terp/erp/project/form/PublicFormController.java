package com.terp.erp.project.form;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.exception.ApiException;
import com.terp.erp.project.common.EFormError;
import com.terp.erp.project.form.FormService.PublicFormDto;
import com.terp.erp.project.form.FormService.SubmitForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No token: the form token in the path is the only key. The gateway permits exactly these and rate-limits per IP. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/forms/{token}")
@Tag(name = "Public forms", description = "Public form link (no login)")
public class PublicFormController {
    private final FormService service;

    @GetMapping
    @Operation(summary = "Definition of an active form: name, layout, fields")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "FRM_002 (unknown, deleted or inactive: not told apart)")})
    public ApiMessageDto<PublicFormDto> get(@PathVariable String token) {
        return ApiMessageDto.ok(service.publicForm(token));
    }

    @PostMapping("/responses")
    @Operation(summary = "Submit answers keyed by field key")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "COMMON_001, FRM_004..FRM_013, FRM_018, FRM_019"),
            @ApiResponse(responseCode = "404", description = "FRM_002")})
    public ApiMessageDto<Void> submit(@PathVariable String token, @Valid @RequestBody SubmitForm form,
                                      HttpServletRequest request) {
        // Declared length only (a chunked body is bounded by the per-field limits and the field count).
        if (request.getContentLengthLong() > FormAnswerValidator.MAX_BODY_BYTES) {
            throw ApiException.badRequest(EFormError.FRM_019);
        }
        service.submit(token, form);
        return ApiMessageDto.ok(null);
    }
}
