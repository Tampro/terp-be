package com.terp.erp.accounting.vendor;

import com.terp.erp.accounting.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A vendor of the company. */
@Getter
@Setter
@Entity
@Table(name = "vendor")
public class Vendor extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "vendor_code", nullable = false, updatable = false, length = 20)
    private String vendorCode;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "contact_person", nullable = false)
    private String contactPerson;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "mobile", length = 30)
    private String mobile;

    @Column(name = "tax_number", length = 50)
    private String taxNumber;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "billing_name")
    private String billingName;
    @Column(name = "billing_address")
    private String billingAddress;
    @Column(name = "billing_address2")
    private String billingAddress2;
    @Column(name = "billing_city", length = 100)
    private String billingCity;
    @Column(name = "billing_state", length = 100)
    private String billingState;
    @Column(name = "billing_country", length = 100)
    private String billingCountry;
    @Column(name = "billing_zip", length = 20)
    private String billingZip;

    @Column(name = "shipping_same_as_billing", nullable = false)
    private boolean shippingSameAsBilling;

    @Column(name = "shipping_name")
    private String shippingName;
    @Column(name = "shipping_address")
    private String shippingAddress;
    @Column(name = "shipping_address2")
    private String shippingAddress2;
    @Column(name = "shipping_city", length = 100)
    private String shippingCity;
    @Column(name = "shipping_state", length = 100)
    private String shippingState;
    @Column(name = "shipping_country", length = 100)
    private String shippingCountry;
    @Column(name = "shipping_zip", length = 20)
    private String shippingZip;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
