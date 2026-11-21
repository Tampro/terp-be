package com.terp.erp.iam.site;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.iam.model.BaseAuditableEntity;

/** One section of the landing page (docs/analysis/06 §3.2.7) as the FE's JSON document. */
@Getter
@Setter
@Entity
@Table(name = "landing_section")
public class LandingSection extends BaseAuditableEntity {
    @Column(name = "section_key", nullable = false, length = 30)
    private String sectionKey;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;
}
