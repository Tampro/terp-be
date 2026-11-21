package com.terp.erp.recruitment.offertemplate;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.recruitment.common.BaseAuditableEntity;
import com.terp.erp.recruitment.common.EStatus;

import java.util.ArrayList;
import java.util.List;

/** Reusable offer letter (docs/analysis/06 §7.1.16): sanitized HTML with {{variable}} placeholders. */
@Getter
@Setter
@Entity
@Table(name = "offer_template")
public class OfferTemplate extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @ElementCollection
    @CollectionTable(name = "offer_template_variable", joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "position")
    @Column(name = "name", nullable = false, length = 64)
    private List<String> variables = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
