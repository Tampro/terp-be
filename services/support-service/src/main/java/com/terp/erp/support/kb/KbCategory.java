package com.terp.erp.support.kb;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A knowledge base category (reference knowledge_base_categories: title). */
@Getter
@Setter
@Entity
@Table(name = "kb_category")
public class KbCategory extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
