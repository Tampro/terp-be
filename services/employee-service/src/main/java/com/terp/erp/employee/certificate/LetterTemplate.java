package com.terp.erp.employee.certificate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

/** A company's own text of a letter in one language (docs/analysis/06 §10.5.8-10.5.10); sanitized HTML. */
@Getter
@Setter
@Entity
@Table(name = "letter_template")
public class LetterTemplate extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "letter_type", nullable = false, length = 30)
    private ELetterType letterType;

    @Column(name = "lang", nullable = false, length = 5)
    private String lang;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;
}
