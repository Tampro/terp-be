package com.terp.erp.recruitment.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.recruitment.common.BaseAuditableEntity;

/** One interviewer's evaluation of an interview; scores 1-5. */
@Getter
@Setter
@Entity
@Table(name = "interview_feedback")
public class InterviewFeedback extends BaseAuditableEntity {
    public enum ERecommendation { STRONG_HIRE, HIRE, MAYBE, REJECT, STRONG_REJECT }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "interview_id", nullable = false)
    private Long interviewId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "interviewer_name", length = 200)
    private String interviewerName;

    @Column(name = "interviewer_role", length = 150)
    private String interviewerRole;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Column(name = "technical_score", nullable = false)
    private int technicalScore;

    @Column(name = "communication_score", nullable = false)
    private int communicationScore;

    @Column(name = "cultural_fit_score", nullable = false)
    private int culturalFitScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", nullable = false, length = 14)
    private ERecommendation recommendation;

    @Column(name = "strengths", length = 5000)
    private String strengths;

    @Column(name = "weaknesses", length = 5000)
    private String weaknesses;

    @Column(name = "comments", length = 5000)
    private String comments;
}
