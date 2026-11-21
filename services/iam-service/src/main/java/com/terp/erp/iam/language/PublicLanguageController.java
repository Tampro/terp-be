package com.terp.erp.iam.language;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.iam.language.LanguageService.LanguageDto;

import java.util.List;
import java.util.Map;

/** The language selector and the UI labels: public, the login page needs them before anyone signs in. */
@Tag(name = "Languages")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/languages")
public class PublicLanguageController {
    private final LanguageService service;

    @Operation(summary = "Enabled languages (language selector)", description = "No token needed.")
    @GetMapping
    public ApiMessageDto<List<LanguageDto>> enabled() {
        return ApiMessageDto.ok(service.enabled());
    }

    @Operation(summary = "UI labels of a language", description = "{key: text}: every key of the default language, "
            + "in this language where translated. No token needed.")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "No such enabled language (IAM_027)"))
    @GetMapping("/{code}/labels")
    public ApiMessageDto<Map<String, String>> labels(@PathVariable String code) {
        return ApiMessageDto.ok(service.published(code));
    }
}
