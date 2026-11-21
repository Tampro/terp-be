package com.terp.erp.iam.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

/** Subject and HTML body of one template in one language. */
@Getter
@Setter
@Entity
@Table(name = "email_template_content")
public class EmailTemplateContent extends BaseAuditableEntity {
    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "lang", nullable = false, length = 5)
    private String lang;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
}
