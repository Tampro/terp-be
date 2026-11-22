package com.terp.erp.recruitment.posting;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.terp.erp.recruitment.common.BaseAuditableEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A job opening (docs/analysis/06 §7.1.1). jobCode is generated per company. Publishing it sets status PUBLISHED; only
 * published postings will appear on the public career page.
 */
@Getter
@Setter
@Entity
@Table(name = "job_posting")
public class JobPosting extends BaseAuditableEntity {
    public enum EPriority { LOW, MEDIUM, HIGH }

    /** EXISTING = through the career page of this system; CUSTOM = elsewhere, at applicationUrl. */
    public enum EApplicationType { EXISTING, CUSTOM }

    /**
     * DRAFT ⇄ PUBLISHED → CLOSED; CLOSED → PUBLISHED (re-open) but never back to DRAFT; only PUBLISHED can close.
     * TODO(assumption): a closed posting may be re-published — confirm with BA.
     */
    public enum EStatus { DRAFT, PUBLISHED, CLOSED }

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "job_code", nullable = false, length = 20)
    private String jobCode;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "job_type_id", nullable = false)
    private Long jobTypeId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    /** employee-service ids. */
    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "department_id")
    private Long departmentId;

    /** Copied from employee-service when an admin saves or publishes (the public career page has no token). */
    @Column(name = "branch_name", length = 150)
    private String branchName;

    @Column(name = "department_name", length = 150)
    private String departmentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private EPriority priority = EPriority.MEDIUM;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_type", nullable = false, length = 10)
    private EApplicationType applicationType = EApplicationType.EXISTING;

    @Column(name = "application_url", length = 500)
    private String applicationUrl;

    @Column(name = "positions", nullable = false)
    private int positions = 1;

    @Column(name = "featured", nullable = false)
    private boolean featured;

    @Column(name = "min_experience", nullable = false)
    private int minExperience;

    @Column(name = "max_experience")
    private Integer maxExperience;

    @Column(name = "min_salary", precision = 15, scale = 2)
    private BigDecimal minSalary;

    @Column(name = "max_salary", precision = 15, scale = 2)
    private BigDecimal maxSalary;

    @Column(name = "description", columnDefinition = "MEDIUMTEXT")
    private String description;

    @Column(name = "requirements", columnDefinition = "MEDIUMTEXT")
    private String requirements;

    @Column(name = "benefits", columnDefinition = "MEDIUMTEXT")
    private String benefits;

    /** "Need to Ask?" */
    @Column(name = "ask_gender", nullable = false)
    private boolean askGender;

    @Column(name = "ask_date_of_birth", nullable = false)
    private boolean askDateOfBirth;

    /** "Need to Show Option?" */
    @Column(name = "show_cover_letter", nullable = false)
    private boolean showCoverLetter;

    @Column(name = "show_terms", nullable = false)
    private boolean showTerms;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private EStatus status = EStatus.DRAFT;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "publish_date")
    private Instant publishDate;

    @ElementCollection
    @CollectionTable(name = "job_posting_skill", joinColumns = @JoinColumn(name = "job_posting_id"))
    @OrderColumn(name = "position")
    @Column(name = "skill", nullable = false, length = 100)
    private List<String> skills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "job_posting_question", joinColumns = @JoinColumn(name = "job_posting_id"))
    @Column(name = "custom_question_id", nullable = false)
    private Set<Long> customQuestionIds = new HashSet<>();

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
