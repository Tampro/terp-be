package com.terp.erp.meeting.zoom;

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

/** A meeting created on Zoom for the company (docs/analysis/06 §9.3). */
@Getter
@Setter
@Entity
@Table(name = "zoom_meeting")
public class ZoomMeeting extends BaseAuditableEntity {
    public enum EStatus { SCHEDULED, STARTED, ENDED, CANCELLED }

    /** Zoom's auto_recording values. */
    public enum ERecording { NONE, LOCAL, CLOUD }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "zoom_meeting_id", nullable = false)
    private Long zoomMeetingId;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "agenda", length = 2000)
    private String agenda;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "passcode", nullable = false, length = 10)
    private String passcode;

    @Column(name = "join_url", nullable = false, length = 1000)
    private String joinUrl;

    @Column(name = "start_url_cipher", nullable = false, length = 4000)
    private String startUrlCipher;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.SCHEDULED;

    @ElementCollection
    @CollectionTable(name = "zoom_meeting_invitee", joinColumns = @JoinColumn(name = "zoom_meeting_id"))
    @Column(name = "employee_id", nullable = false)
    private Set<Long> inviteeIds = new LinkedHashSet<>();

    @Column(name = "host_video", nullable = false)
    private boolean hostVideo;

    @Column(name = "participant_video", nullable = false)
    private boolean participantVideo;

    @Column(name = "waiting_room", nullable = false)
    private boolean waitingRoom;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_recording", nullable = false, length = 10)
    private ERecording autoRecording = ERecording.NONE;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
