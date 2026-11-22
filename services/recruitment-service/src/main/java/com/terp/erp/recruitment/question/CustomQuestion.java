package com.terp.erp.recruitment.question;

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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** A question a job posting may ask applicants (docs/analysis/06 §7.1.17). */
@Getter
@Setter
@Entity
@Table(name = "custom_question")
public class CustomQuestion extends BaseAuditableEntity {
    /** The 7 types of TERP Core custom_questions.type. */
    public enum EType { TEXT, TEXTAREA, NUMBER, DATE, SELECT, RADIO, CHECKBOX }

    /** Types answered by picking from {@link #options}. */
    public static final Set<EType> CHOICE = EnumSet.of(EType.SELECT, EType.RADIO, EType.CHECKBOX);

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "question", nullable = false, length = 1000)
    private String question;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private EType type = EType.TEXT;

    /** Choice types only. */
    @ElementCollection
    @CollectionTable(name = "custom_question_option", joinColumns = @JoinColumn(name = "custom_question_id"))
    @OrderColumn(name = "position")
    @Column(name = "option_value", nullable = false, length = 255)
    private List<String> options = new ArrayList<>();

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
