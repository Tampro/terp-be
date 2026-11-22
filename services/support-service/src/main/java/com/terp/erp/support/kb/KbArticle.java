package com.terp.erp.support.kb;

import com.terp.erp.support.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A knowledge base article (reference support_ticket_knowledge_bases: title, description ≤ 500, category; content
 * from StoreKnowledgeBaseRequest). TODO(assumption): published is not in the reference — only published articles
 * show on the portal, new and imported ones start unpublished.
 */
@Getter
@Setter
@Entity
@Table(name = "kb_article")
public class KbArticle extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
