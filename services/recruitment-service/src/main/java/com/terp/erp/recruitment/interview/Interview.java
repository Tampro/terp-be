package com.terp.erp.recruitment.interview;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.terp.erp.recruitment.common.BaseAuditableEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** An interview of a candidate (docs/analysis/06 §7.1.3): one round of their posting, on a date and time. */
@Getter
@Setter
@Entity
@Table(name = "interview")
public class Interview extends BaseAuditableEntity {
    public enum EStatus { SCHEDULED, COMPLETED, CANCELLED, NO_SHOW }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "job_posting_id", nullable = false)
    private Long jobPostingId;

    @Column(name = "round_id", nullable = false)
    private Long roundId;

    @Column(name = "interview_type_id", nullable = false)
    private Long interviewTypeId;

    @Column(name = "interview_date", nullable = false)
    private LocalDate interviewDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "meeting_link", length = 1000)
    private String meetingLink;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.SCHEDULED;

    @ElementCollection
    @CollectionTable(name = "interview_interviewer", joinColumns = @JoinColumn(name = "interview_id"))
    @OrderColumn(name = "position")
    private List<Interviewer> interviewers = new ArrayList<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    /** Minutes from midnight; an interview past midnight simply ends after 1440. */
    int startMinute() {
        return startTime.getHour() * 60 + startTime.getMinute();
    }

    int endMinute() {
        return startMinute() + durationMinutes;
    }

    boolean overlaps(Interview o) {
        return interviewDate.equals(o.interviewDate) && startMinute() < o.endMinute() && o.startMinute() < endMinute();
    }

    /** Employee-service id, with what was shown when they were assigned. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class Interviewer {
        @Column(name = "employee_id", nullable = false)
        private Long employeeId;

        @Column(name = "name", length = 200)
        private String name;

        @Column(name = "email", length = 255)
        private String email;

        @Column(name = "designation", length = 150)
        private String designation;
    }
}
