package com.terp.erp.crm.record;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.crm.common.BaseAuditableEntity;

import java.time.LocalDate;

/** A lead of a pipeline (ERP manual 7.2). Converted once into a deal; then convertedDealId is set. */
@Getter
@Setter
@Entity
@Table(name = "crm_lead")
public class Lead extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "pipeline_id", nullable = false)
    private Long pipelineId;

    @Column(name = "stage_id", nullable = false)
    private Long stageId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    /** The assigned user (IAM account id). */
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "converted_deal_id")
    private Long convertedDealId;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
