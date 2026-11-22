package com.terp.erp.recruitment.candidate;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A person who applied for a job posting (docs/analysis/06 §7.1.2), moving through the pipeline New → Screening →
 * Interview → Offer → Hired, or Rejected. employeeId is set once onboarding converts them into an employee.
 */
@Getter
@Setter
@Entity
@Table(name = "candidate")
public class Candidate extends BaseAuditableEntity {
    public enum EStatus { NEW, SCREENING, INTERVIEW, OFFER, HIRED, REJECTED }

    public enum EGender { MALE, FEMALE, OTHER }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "job_posting_id", nullable = false)
    private Long jobPostingId;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "current_company", length = 150)
    private String currentCompany;

    @Column(name = "current_position", length = 150)
    private String currentPosition;

    @Column(name = "experience_years", nullable = false)
    private int experienceYears;

    @Column(name = "current_salary", precision = 15, scale = 2)
    private BigDecimal currentSalary;

    @Column(name = "expected_salary", precision = 15, scale = 2)
    private BigDecimal expectedSalary;

    @Column(name = "notice_period", length = 50)
    private String noticePeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private EGender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "cover_letter_message", length = 5000)
    private String coverLetterMessage;

    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private EStatus status = EStatus.NEW;

    @Column(name = "application_date", nullable = false)
    private LocalDate applicationDate;

    /** Given to the applicant to track the application on the career page; null for candidates saved before it. */
    @Column(name = "tracking_id", length = 20, unique = true)
    private String trackingId;

    /** employee-service id once converted (offer Convert to Employee, or onboarding). */
    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "resume_key", length = 64)
    private String resumeKey;

    @Column(name = "resume_name", length = 255)
    private String resumeName;

    @Column(name = "resume_mime", length = 127)
    private String resumeMime;

    @Column(name = "resume_size")
    private Long resumeSize;

    @Column(name = "cover_letter_key", length = 64)
    private String coverLetterKey;

    @Column(name = "cover_letter_name", length = 255)
    private String coverLetterName;

    @Column(name = "cover_letter_mime", length = 127)
    private String coverLetterMime;

    @Column(name = "cover_letter_size")
    private Long coverLetterSize;

    @ElementCollection
    @CollectionTable(name = "candidate_answer", joinColumns = @JoinColumn(name = "candidate_id"))
    @OrderColumn(name = "position")
    private List<Answer> answers = new ArrayList<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class Answer {
        @Column(name = "custom_question_id", nullable = false)
        private Long customQuestionId;

        /** Copied from the question when answered. */
        @Column(name = "question", nullable = false, length = 1000)
        private String question;

        @Column(name = "answer", length = 5000)
        private String answer;
    }
}
