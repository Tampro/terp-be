package com.terp.erp.accounting.customer;

import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.security.TenantGuard;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/**
 * Customers (ERP manual 12.1): code CUST-0001… issued per company and never reused; an optional CLIENT user account
 * is linked to at most one customer of the company (ACC_003).
 */
@Service
@RequiredArgsConstructor
public class CustomerService {
    static final String CODE_PREFIX = "CUST-";
    private static final AddressForm EMPTY = new AddressForm(null, null, null, null, null, null, null);

    private final CustomerRepository customers;
    private final CustomerCodeSequenceRepository sequences;
    private final OutboxPublisher outbox;

    public record AddressForm(
            @Schema(example = "Example Trading Co.") @Size(max = 255) String name,
            @Schema(example = "1 Example Street") @Size(max = 255) String address,
            @Schema(example = "Floor 2") @Size(max = 255) String address2,
            @Schema(example = "Testville") @Size(max = 100) String city,
            @Schema(example = "Test State") @Size(max = 100) String state,
            @Schema(example = "Testland") @Size(max = 100) String country,
            @Schema(example = "00000") @Size(max = 20) String zip) {
    }

    public record CustomerForm(
            @Schema(example = "Example Trading Co.", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String companyName,
            @Schema(example = "Test Contact", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
            @Size(max = 255) String contactPerson,
            @Schema(example = "buyer@example.com", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email
            @Size(max = 255) String email,
            @Schema(description = "Digits, spaces and ( ) - .", example = "(000) 000-0000") @Size(max = 30)
            @Pattern(regexp = "^[0-9()\\-. ]*$") String mobile,
            @Schema(example = "0000000000") @Size(max = 50) String taxNumber,
            @Schema(example = "Net 30") @Size(max = 100) String paymentTerms,
            @Schema(description = "ERP account id of a CLIENT user, optional; one customer per account",
                    example = "1") @Positive Long userId,
            @Schema(description = "Billing address") @Valid AddressForm billing,
            @Schema(description = "true = shipping is copied from billing (the shipping field is ignored)")
            boolean shippingSameAsBilling,
            @Schema(description = "Shipping address; ignored when shippingSameAsBilling") @Valid AddressForm shipping,
            @Schema(example = "Fake customer for testing") @Size(max = 10000) String notes) {
    }

    public record AddressDto(String name, String address, String address2, String city, String state,
                             String country, String zip) {
    }

    public record CustomerDto(String id, String companyId, String customerCode, String companyName,
                              String contactPerson, String email, String mobile, String taxNumber,
                              String paymentTerms, String userId, AddressDto billing, boolean shippingSameAsBilling,
                              AddressDto shipping, String notes, Instant createdDate, Instant updatedDate) {
    }

    @Transactional(readOnly = true)
    public Page<CustomerDto> search(Long requestedCompanyId, String keyword, Pageable pageable) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        return customers.search(companyId, kw(keyword), pageable).map(CustomerService::dto);
    }

    @Transactional(readOnly = true)
    public CustomerDto get(Long id) {
        return dto(require(id));
    }

    @Transactional
    public CustomerDto create(Long requestedCompanyId, CustomerForm form) {
        Long companyId = TenantGuard.resolveCompany(requestedCompanyId, EAccountingError.ACC_001);
        Customer c = new Customer();
        c.setCompanyId(companyId);
        apply(c, form);
        c.setCustomerCode(nextCode(companyId));
        Customer saved = customers.save(c);
        // webhook NEW_CUSTOMER; no recipient (nobody is emailed about a new customer)
        outbox.publish(companyId, "accounting.customer.created", "CUSTOMER", saved.getId(), Payload.of(
                "customerId", saved.getId(), "title", saved.getCustomerCode() + " - " + saved.getCompanyName(),
                "customerCode", saved.getCustomerCode(), "companyName", saved.getCompanyName(),
                "contactPerson", saved.getContactPerson(), "email", saved.getEmail()));
        return dto(saved);
    }

    /** Full replacement; the code never changes. */
    @Transactional
    public CustomerDto update(Long id, CustomerForm form) {
        Customer c = require(id);
        apply(c, form);
        return dto(c);
    }

    @Transactional
    public void delete(Long id) {
        Customer c = require(id);
        // TODO(assumption): refuse when quotations/invoices reference the customer once those documents exist;
        // they live in commerce-service (another database), so the check needs a cross-service call.
        c.setDeleted(true);
    }

    /** A customer of the caller's company (ACC_002 otherwise, 404 on direct access). */
    public Customer require(Long id) {
        Customer c = customers.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ApiException.notFound(EAccountingError.ACC_002));
        TenantGuard.assertSameCompany(c.getCompanyId(), EAccountingError.ACC_002);
        return c;
    }

    private String nextCode(Long companyId) {
        sequences.ensureRow(companyId);
        CustomerCodeSequence seq = sequences.lockByCompanyId(companyId).orElseThrow();
        seq.setLastNumber(seq.getLastNumber() + 1);
        // TODO(assumption): 4 digits as in the manual's CUST-0001; past 9999 the number simply grows wider.
        return CODE_PREFIX + String.format(Locale.ROOT, "%04d", seq.getLastNumber());
    }

    private void apply(Customer c, CustomerForm f) {
        if (f.userId() != null && customers.existsByCompanyIdAndUserIdAndDeletedFalseAndIdNot(c.getCompanyId(),
                f.userId(), c.getId() == null ? -1L : c.getId())) {
            throw ApiException.badRequest(EAccountingError.ACC_003);
        }
        // TODO(assumption): userId is stored as given; that it is a CLIENT user of the company is not checked in IAM.
        c.setCompanyName(f.companyName().trim());
        c.setContactPerson(f.contactPerson().trim());
        c.setEmail(f.email().trim());
        c.setMobile(blankToNull(f.mobile()));
        c.setTaxNumber(blankToNull(f.taxNumber()));
        c.setPaymentTerms(blankToNull(f.paymentTerms()));
        c.setUserId(f.userId());
        c.setNotes(blankToNull(f.notes()));
        AddressForm b = f.billing() == null ? EMPTY : f.billing();
        AddressForm s = f.shippingSameAsBilling() ? b : (f.shipping() == null ? EMPTY : f.shipping());
        c.setBillingName(blankToNull(b.name()));
        c.setBillingAddress(blankToNull(b.address()));
        c.setBillingAddress2(blankToNull(b.address2()));
        c.setBillingCity(blankToNull(b.city()));
        c.setBillingState(blankToNull(b.state()));
        c.setBillingCountry(blankToNull(b.country()));
        c.setBillingZip(blankToNull(b.zip()));
        c.setShippingSameAsBilling(f.shippingSameAsBilling());
        c.setShippingName(blankToNull(s.name()));
        c.setShippingAddress(blankToNull(s.address()));
        c.setShippingAddress2(blankToNull(s.address2()));
        c.setShippingCity(blankToNull(s.city()));
        c.setShippingState(blankToNull(s.state()));
        c.setShippingCountry(blankToNull(s.country()));
        c.setShippingZip(blankToNull(s.zip()));
    }

    private static CustomerDto dto(Customer c) {
        return new CustomerDto(c.getId().toString(), c.getCompanyId().toString(), c.getCustomerCode(),
                c.getCompanyName(), c.getContactPerson(), c.getEmail(), c.getMobile(), c.getTaxNumber(),
                c.getPaymentTerms(), c.getUserId() == null ? null : c.getUserId().toString(),
                new AddressDto(c.getBillingName(), c.getBillingAddress(), c.getBillingAddress2(), c.getBillingCity(),
                        c.getBillingState(), c.getBillingCountry(), c.getBillingZip()),
                c.isShippingSameAsBilling(),
                new AddressDto(c.getShippingName(), c.getShippingAddress(), c.getShippingAddress2(),
                        c.getShippingCity(), c.getShippingState(), c.getShippingCountry(), c.getShippingZip()),
                c.getNotes(), c.getCreatedDate(), c.getUpdatedDate());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** LIKE pattern for a keyword, '' when none; ! escapes % and _. */
    private static String kw(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + k + "%";
    }
}
