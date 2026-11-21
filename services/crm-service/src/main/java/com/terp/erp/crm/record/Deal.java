package com.terp.erp.crm.record;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;
import com.terp.erp.crm.record.CrmEnums.EDealStatus;

import java.math.BigDecimal;

/** A deal of a pipeline (ERP manual 7.3): made directly or from a lead (fromLeadId). */
@Getter
@Setter
@Entity
@Table(name = "crm_deal")
public class Deal extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "pipeline_id", nullable = false)
    private Long pipelineId;

    @Column(name = "stage_id", nullable = false)
    private Long stageId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "phone", length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EDealStatus status = EDealStatus.ACTIVE;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "from_lead_id")
    private Long fromLeadId;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
