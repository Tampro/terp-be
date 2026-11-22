package com.terp.erp.support.contact;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A contact form message from the portal (reference support_contacts; its first/last name are merged into name, as
 * its frontend contact request takes one name). Plain text: the FE must escape it.
 */
@Getter
@Setter
@Entity
@Table(name = "contact_message")
public class ContactMessage extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "message", nullable = false, length = 5000)
    private String message;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
