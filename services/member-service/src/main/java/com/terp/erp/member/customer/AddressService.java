package com.terp.erp.member.customer;

import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.ECommonError;
import com.terp.erp.member.client.StorefrontClient;
import com.terp.erp.member.client.StorefrontClient.ServingStore;
import com.terp.erp.member.common.EMemberError;
import com.terp.erp.member.common.FieldCipher;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Delivery addresses of the caller's own membership (shop company + token subject): at most 10, exactly one default
 * while any exist. The phone is encrypted at rest (FieldCipher) and shown only to its owner.
 */
@Service
@RequiredArgsConstructor
public class AddressService {
    static final int MAX = 10;

    private final MemberService members;
    private final CustomerAddressRepository addresses;
    private final FieldCipher cipher;
    private final StorefrontClient storefront;

    public record AddressForm(
            @Schema(example = "Home", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 30) String label,
            @Schema(example = "Hong Gildong", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 50) String recipientName,
            @Schema(description = "9-11 digits; '-' and spaces are ignored", example = "010-1234-5678",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 20) String phone,
            @Schema(example = "08502", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Pattern(regexp = "\\d{5}") String postcode,
            @Schema(example = "106 Gasan-ro, Geumcheon-gu, Seoul", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 200) String address1,
            @Schema(example = "Apt 101-1203") @Size(max = 200) String address2,
            @Schema(description = "From the FE's geocoding", example = "37.4752", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
            @Schema(example = "126.8940", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal lon,
            @Schema(example = "Leave at the door") @Size(max = 200) String deliveryNote,
            @Schema(description = "The first address is always default") Boolean isDefault) {
    }

    /** servingStore null: no store of the shop delivers there (or store-service did not answer). */
    public record AddressDto(String id, String label, String recipientName, String phone, String postcode, String address1,
                             String address2, BigDecimal lat, BigDecimal lon, String deliveryNote, boolean isDefault,
                             ServingStore servingStore) {
    }

    @Transactional(readOnly = true)
    public List<AddressDto> list(String slug, String sub) {
        Customer c = members.requireActive(slug, sub);
        return addresses.ofCustomer(c.getId()).stream().map(a -> dto(a, slug)).toList();
    }

    @Transactional
    public AddressDto create(String slug, String sub, AddressForm f) {
        Customer c = members.requireActive(slug, sub);
        long count = addresses.countByCustomerId(c.getId());
        if (count >= MAX) {
            throw ApiException.badRequest(EMemberError.MEM_008, MAX);
        }
        CustomerAddress a = new CustomerAddress();
        a.setCompanyId(c.getCompanyId());
        a.setCustomerId(c.getId());
        apply(a, f);
        boolean makeDefault = count == 0 || Boolean.TRUE.equals(f.isDefault());
        if (makeDefault) {
            clearDefault(c.getId());
        }
        a.setDefault(makeDefault);
        return dto(addresses.saveAndFlush(a), slug);
    }

    /** Full replacement; isDefault=true moves the default here, false leaves it where it is (a default stays default). */
    @Transactional
    public AddressDto replace(String slug, String sub, Long id, AddressForm f) {
        Customer c = members.requireActive(slug, sub);
        CustomerAddress a = require(c, id);
        apply(a, f);
        if (Boolean.TRUE.equals(f.isDefault()) && !a.isDefault()) {
            clearDefault(c.getId());
            a.setDefault(true);
        }
        return dto(addresses.saveAndFlush(a), slug);
    }

    @Transactional
    public AddressDto makeDefault(String slug, String sub, Long id) {
        Customer c = members.requireActive(slug, sub);
        CustomerAddress a = require(c, id);
        clearDefault(c.getId());
        a.setDefault(true);
        return dto(addresses.saveAndFlush(a), slug);
    }

    /** Hard delete; deleting the default makes the newest remaining address default. */
    @Transactional
    public void delete(String slug, String sub, Long id) {
        Customer c = members.requireActive(slug, sub);
        CustomerAddress a = require(c, id);
        boolean wasDefault = a.isDefault();
        addresses.delete(a);
        addresses.flush();
        if (wasDefault) {
            addresses.ofCustomer(c.getId()).stream().findFirst().ifPresent(next -> {
                next.setDefault(true);
                addresses.save(next);
            });
        }
    }

    // ------------------------------------------------------------------ helpers

    private CustomerAddress require(Customer c, Long id) {
        return addresses.findByIdAndCustomerId(id, c.getId()).orElseThrow(() -> ApiException.notFound(EMemberError.MEM_007));
    }

    private void clearDefault(Long customerId) {
        for (CustomerAddress other : addresses.ofCustomer(customerId)) {
            if (other.isDefault()) {
                other.setDefault(false);
                addresses.save(other);
            }
        }
        addresses.flush();
    }

    private void apply(CustomerAddress a, AddressForm f) {
        String phone = f.phone().replaceAll("[^0-9]", "");
        if (!phone.matches("\\d{9,11}")) {
            throw ApiException.badRequest(ECommonError.COMMON_001);
        }
        a.setLabel(f.label().trim());
        a.setRecipientName(f.recipientName().trim());
        a.setPhoneEnc(cipher.encrypt(phone));
        a.setPostcode(f.postcode());
        a.setAddress1(f.address1().trim());
        a.setAddress2(blankToNull(f.address2()));
        a.setLat(f.lat());
        a.setLon(f.lon());
        a.setDeliveryNote(blankToNull(f.deliveryNote()));
    }

    private AddressDto dto(CustomerAddress a, String slug) {
        ServingStore store = storefront.servingStore(slug.trim().toLowerCase(java.util.Locale.ROOT), a.getLat(), a.getLon())
                .orElse(null);
        return new AddressDto(String.valueOf(a.getId()), a.getLabel(), a.getRecipientName(), cipher.decrypt(a.getPhoneEnc()),
                a.getPostcode(), a.getAddress1(), a.getAddress2(), a.getLat(), a.getLon(), a.getDeliveryNote(), a.isDefault(),
                store);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
