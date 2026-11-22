package com.terp.erp.employee.training;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.employee.common.BaseAuditableEntity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/** A session of a training program (docs/analysis/06 §7.4.2). */
@Getter
@Setter
@Entity
@Table(name = "training_session")
public class TrainingSession extends BaseAuditableEntity {
    /** Manual 16 "Ongoing" is IN_PROGRESS. */
    public enum EStatus { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }

    /** TODO(assumption): the spec names a Location Type dropdown without its values — confirm with BA. */
    public enum ELocationType { IN_PERSON, VIRTUAL, HYBRID }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Company wall-clock time. */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 12)
    private ELocationType locationType;

    @Column(name = "location", length = 300)
    private String location;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.SCHEDULED;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** Shared by the copies of a recurring session. */
    @Column(name = "series_id", length = 36)
    private String seriesId;

    /** Optional entry of the trainer list (FR-TRN-002), besides the employees conducting it. */
    @Column(name = "trainer_id")
    private Long trainerId;

    /** Employees conducting the session. */
    @ElementCollection
    @CollectionTable(name = "training_session_trainer", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "employee_id")
    private Set<Long> trainerIds = new HashSet<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
