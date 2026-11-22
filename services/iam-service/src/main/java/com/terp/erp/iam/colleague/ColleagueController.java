package com.terp.erp.iam.colleague;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.ResponseListDto;
import com.terp.erp.iam.colleague.ColleagueService.ColleagueDto;

/**
 * Colleagues for the messenger: active users of the caller's own company, id and name only. Unlike /iam/users it
 * needs no IAM_USER_VIEW, so it shows nothing an admin screen would (email, mobile, roles, status of others).
 */
@Tag(name = "Colleagues")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/iam/colleagues")
@SecurityRequirement(name = "bearerAuth")
public class ColleagueController {
    private final ColleagueService colleagues;

    @Operation(summary = "Active users of my company (id, username, full name), the caller excluded",
            description = "keyword matches username or full name.")
    @ApiResponses(@ApiResponse(responseCode = "403", description = "No company (IAM_003 as 400) or no MESSENGER_USE"))
    @PreAuthorize("@PermissionChecker.has('MESSENGER_USE')")
    @GetMapping
    public ApiMessageDto<ResponseListDto<ColleagueDto>> list(@RequestParam(required = false) String keyword,
                                                             @ParameterObject Pageable pageable) {
        return ApiMessageDto.ok(ResponseListDto.of(colleagues.search(keyword, pageable)));
    }

    @Operation(summary = "One active colleague of my company")
    @ApiResponses(@ApiResponse(responseCode = "404", description = "Not an active user of my company (IAM_009)"))
    @PreAuthorize("@PermissionChecker.has('MESSENGER_USE')")
    @GetMapping("/{id}")
    public ApiMessageDto<ColleagueDto> get(@PathVariable Long id) {
        return ApiMessageDto.ok(colleagues.get(id));
    }
}
