package com.terp.erp.recruitment.question;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.recruitment.common.BaseAuditableEntity;

/** A question a job posting may ask applicants (docs/analysis/06 §7.1.17). */
@Getter
@Setter
@Entity
@Table(name = "custom_question")
public class CustomQuestion extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "question", nullable = false, length = 1000)
    private String question;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
