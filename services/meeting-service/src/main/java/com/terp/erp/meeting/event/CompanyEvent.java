package com.terp.erp.meeting.event;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.terp.erp.meeting.common.BaseAuditableEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** A company event with an approval status (docs/analysis/06 §9.2.1). */
@Getter
@Setter
@Entity
@Table(name = "company_event")
public class CompanyEvent extends BaseAuditableEntity {
    public enum EStatus { PENDING, APPROVED, REJECTED }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(of = "departmentId")
    @Embeddable
    public static class Department {
        @Column(name = "department_id", nullable = false)
        private Long departmentId;

        @Column(name = "name", nullable = false, length = 200)
        private String name;
    }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "event_type_id", nullable = false)
    private Long eventTypeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "location", length = 500)
    private String location;

    @Column(name = "color", nullable = false, length = 7)
    private String color = "#3B82F6";

    @Column(name = "description", length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.PENDING;

    /** Keycloak sub of who approved / rejected. */
    @Column(name = "decided_by", length = 64)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ElementCollection
    @CollectionTable(name = "company_event_department", joinColumns = @JoinColumn(name = "event_id"))
    private Set<Department> departments = new LinkedHashSet<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
