package com.terp.erp.member.customer;

import com.terp.erp.common.ApiMessageDto;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only (SecurityConfig internalChain, erp.member.internal.client-ids): the ACTIVE membership of a
 * customer-realm subject in a shop, for order-service's carts. Shop unavailable or not a member → 404 (MEM_001 / MEM_004).
 */
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/member/shops/{slug}/members")
public class InternalMemberController {
    private final MemberService members;

    public record MembershipDto(String customerId, String companyId) {
    }

    @GetMapping("/{sub}")
    public ApiMessageDto<MembershipDto> membership(@PathVariable String slug, @PathVariable String sub) {
        Customer c = members.requireActive(slug, sub);
        return ApiMessageDto.ok(new MembershipDto(String.valueOf(c.getId()), String.valueOf(c.getCompanyId())));
    }
}
