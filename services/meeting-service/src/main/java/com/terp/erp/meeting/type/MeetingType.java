package com.terp.erp.meeting.type;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.meeting.common.BaseAuditableEntity;
import com.terp.erp.meeting.common.EStatus;

/** A category of meetings (docs/analysis/06 §9.1.3). */
@Getter
@Setter
@Entity
@Table(name = "meeting_type")
public class MeetingType extends BaseAuditableEntity {
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "color", nullable = false, length = 7)
    private String color = "#3B82F6";

    @Column(name = "default_duration_minutes", nullable = false)
    private int defaultDurationMinutes = 60;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.ACTIVE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
