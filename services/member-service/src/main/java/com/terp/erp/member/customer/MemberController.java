package com.terp.erp.member.customer;

import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.member.customer.MemberService.JoinForm;
import com.terp.erp.member.customer.MemberService.MemberDto;
import com.terp.erp.security.CustomerPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in customer's own membership of a shop. Customer tokens only (realm erp-customer, SecurityConfig shopChain);
 * no permission code: the resource is the caller's own row, picked by the token's subject.
 */
@Tag(name = "Shop membership (customer)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shop/{slug}/me")
@SecurityRequirement(name = "bearerAuth")
public class MemberController {
    private static final String SHOP = "Unknown shop, shop disabled or company not active (MEM_001)";
    private static final String DOWN = "store-service unavailable (MEM_006)";

    private final MemberService service;

    @Operation(summary = "My membership of this shop")
    @ApiResponses({@ApiResponse(responseCode = "401", description = "No customer token (COMMON_003)"),
            @ApiResponse(responseCode = "404", description = SHOP + ", not a member (MEM_004)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @GetMapping
    public ApiMessageDto<MemberDto> get(@Parameter(example = "homeplus-express") @PathVariable String slug,
                                        @AuthenticationPrincipal CustomerPrincipal me) {
        return ApiMessageDto.ok(service.get(slug, me.sub()));
    }

    @Operation(summary = "Join this shop", description = "Terms of use and privacy policy must be accepted; their versions "
            + "and the time are stored. A customer who left less than 5 days ago cannot join again yet.")
    @ApiResponses({@ApiResponse(responseCode = "400", description = "Already a member (MEM_002), left less than 5 days ago "
            + "(MEM_003), required consent missing (MEM_005), invalid field (COMMON_001)"),
            @ApiResponse(responseCode = "401", description = "No customer token (COMMON_003)"),
            @ApiResponse(responseCode = "404", description = SHOP),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @PostMapping
    public ApiMessageDto<MemberDto> join(@PathVariable String slug, @AuthenticationPrincipal CustomerPrincipal me,
                                         @Valid @RequestBody JoinForm form) {
        return ApiMessageDto.ok(service.join(slug, me.sub(), form));
    }

    @Operation(summary = "Leave this shop", description = "The membership is kept as LEFT (consent history); the account "
            + "itself is not deleted.")
    @ApiResponses({@ApiResponse(responseCode = "404", description = SHOP + ", not a member (MEM_004)"),
            @ApiResponse(responseCode = "503", description = DOWN)})
    @DeleteMapping
    public ApiMessageDto<Void> leave(@PathVariable String slug, @AuthenticationPrincipal CustomerPrincipal me) {
        service.leave(slug, me.sub());
        return ApiMessageDto.ok(null);
    }
}
