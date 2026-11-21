package com.terp.erp.iam.language;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

/** The translation of one UI label in one language. */
@Getter
@Setter
@Entity
@Table(name = "language_label")
public class LanguageLabel extends BaseAuditableEntity {
    @Column(name = "language_id", nullable = false)
    private Long languageId;

    @Column(name = "label_key", nullable = false, length = 200)
    private String labelKey;

    @Column(name = "label_value", nullable = false, columnDefinition = "TEXT")
    private String labelValue;
}
