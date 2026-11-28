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
    private final CustomerAddressRepository addresses;
    private final com.terp.erp.member.common.FieldCipher cipher;

    public record MembershipDto(String customerId, String companyId) {
    }

    /** The delivery address an order is placed to, with the plain phone (service accounts only; the order encrypts it again). */
    public record AddressDto(String recipientName, String phone, String postcode, String address1, String address2,
                             java.math.BigDecimal lat, java.math.BigDecimal lon, String deliveryNote) {
    }

    @GetMapping("/{sub}/addresses/{id}")
    public ApiMessageDto<AddressDto> address(@PathVariable String slug, @PathVariable String sub, @PathVariable Long id) {
        Customer c = members.requireActive(slug, sub);
        CustomerAddress a = addresses.findByIdAndCustomerId(id, c.getId())
                .orElseThrow(() -> com.terp.erp.exception.ApiException.notFound(com.terp.erp.member.common.EMemberError.MEM_007));
        return ApiMessageDto.ok(new AddressDto(a.getRecipientName(), cipher.decrypt(a.getPhoneEnc()), a.getPostcode(),
                a.getAddress1(), a.getAddress2(), a.getLat(), a.getLon(), a.getDeliveryNote()));
    }

    @GetMapping("/{sub}")
    public ApiMessageDto<MembershipDto> membership(@PathVariable String slug, @PathVariable String sub) {
        Customer c = members.requireActive(slug, sub);
        return ApiMessageDto.ok(new MembershipDto(String.valueOf(c.getId()), String.valueOf(c.getCompanyId())));
    }
}
