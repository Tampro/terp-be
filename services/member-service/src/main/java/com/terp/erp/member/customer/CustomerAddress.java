package com.terp.erp.member.customer;

import com.terp.erp.member.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** A delivery address of one membership. phoneEnc = FieldCipher output, never the plain phone. */
@Getter
@Setter
@Entity
@Table(name = "customer_address")
public class CustomerAddress extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "label", nullable = false, length = 30)
    private String label;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(name = "phone_enc", nullable = false, length = 255)
    private String phoneEnc;

    @Column(name = "postcode", nullable = false, length = 5)
    private String postcode;

    @Column(name = "address1", nullable = false, length = 200)
    private String address1;

    @Column(name = "address2", length = 200)
    private String address2;

    @Column(name = "lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lon", nullable = false, precision = 9, scale = 6)
    private BigDecimal lon;

    @Column(name = "delivery_note", length = 200)
    private String deliveryNote;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
