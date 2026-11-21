package com.terp.erp.meeting.meeting;

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
import com.terp.erp.meeting.common.BaseAuditableEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** One meeting, or one occurrence of a recurring series (docs/analysis/06 §9.1.1). */
@Getter
@Setter
@Entity
@Table(name = "meeting")
public class Meeting extends BaseAuditableEntity {
    /** COMPLETED is shown as "Held" on the list. */
    public enum EStatus { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }

    public enum ERecurrence { NONE, DAILY, WEEKLY, MONTHLY }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** The id of the first occurrence; null for a one-off meeting. */
    @Column(name = "series_id")
    private Long seriesId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "agenda", length = 4000)
    private String agenda;

    @Column(name = "meeting_type_id")
    private Long meetingTypeId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(name = "organizer_name", length = 200)
    private String organizerName;

    @Column(name = "organizer_email", length = 255)
    private String organizerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence", nullable = false, length = 10)
    private ERecurrence recurrence = ERecurrence.NONE;

    @Column(name = "recurrence_end_date")
    private LocalDate recurrenceEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.SCHEDULED;

    @ElementCollection
    @CollectionTable(name = "meeting_attendee", joinColumns = @JoinColumn(name = "meeting_id"))
    @Column(name = "employee_id", nullable = false)
    private Set<Long> attendeeIds = new LinkedHashSet<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
