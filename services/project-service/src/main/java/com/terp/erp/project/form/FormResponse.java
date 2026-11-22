package com.terp.erp.project.form;

import com.terp.erp.project.common.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "form_response")
public class FormResponse extends BaseAuditableEntity {
    public enum ETarget { PROJECT, CONTRACT, LEAD, DEAL }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "form_id", nullable = false)
    private Long formId;

    /** JSON snapshot [{key,label,type,value}]. */
    @Column(name = "answers", nullable = false)
    private String answers;

    @Enumerated(EnumType.STRING)
    @Column(name = "converted_target_type")
    private ETarget convertedTargetType;

    @Column(name = "converted_target_id")
    private String convertedTargetId;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
