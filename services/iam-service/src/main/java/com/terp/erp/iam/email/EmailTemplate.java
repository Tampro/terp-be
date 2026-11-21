package com.terp.erp.iam.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

@Getter
@Setter
@Entity
@Table(name = "email_template")
public class EmailTemplate extends BaseAuditableEntity {
    /** EEmailTemplate name; fixed. */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Sender name shown in the inbox; placeholders allowed. */
    @Column(name = "from_name", length = 100)
    private String fromName;
}
