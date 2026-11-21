package com.terp.erp.recruitment.posting;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.terp.erp.recruitment.posting.JobPosting.EApplicationType;
import com.terp.erp.recruitment.posting.JobPosting.EPriority;
import com.terp.erp.recruitment.posting.JobPosting.EStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class JobPostingDtos {
    private JobPostingDtos() {
    }

    public record Ref(String id, String name) {
    }

    public record QuestionRef(String id, String question, boolean required) {
    }

    /** List row; description, requirements, benefits and questions only on the detail. */
    public record PostingDto(String id, String jobCode, String title, Ref jobType, Ref location, Ref branch,
                             Ref department, EPriority priority, List<String> skills, LocalDate startDate,
                             LocalDate applicationDeadline, EApplicationType applicationType, String applicationUrl,
                             int positions, boolean featured, int minExperience, Integer maxExperience,
                             BigDecimal minSalary, BigDecimal maxSalary, String description, String requirements,
                             String benefits, List<QuestionRef> customQuestions, boolean askGender,
                             boolean askDateOfBirth, boolean showCoverLetter, boolean showTerms, EStatus status,
                             boolean published, Instant publishDate, long applications, Instant createdDate,
                             Instant updatedDate) {
    }

    public record PostingOption(String id, String jobCode, String title) {
    }

    public record PostingForm(
            @Schema(example = "Backend Developer", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200) String title,
            @Schema(description = "Job type id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long jobTypeId,
            @Schema(description = "Job location id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long locationId,
            @Schema(description = "Branch id (employee-service)", example = "1") Long branchId,
            @Schema(description = "Department id of that branch (employee-service)", example = "1") Long departmentId,
            @Schema(description = "Omitted = MEDIUM") EPriority priority,
            @Schema(example = "[\"Java\", \"Spring\"]") @Size(max = 50) List<@NotBlank @Size(max = 100) String> skills,
            @Schema(example = "2027-01-04") LocalDate startDate,
            @Schema(example = "2026-12-15") LocalDate applicationDeadline,
            @Schema(description = "EXISTING = through the career page (default), CUSTOM = at applicationUrl")
            EApplicationType applicationType,
            @Schema(example = "https://careers.example.invalid/jobs/1") @Size(max = 500) String applicationUrl,
            @Schema(example = "2", description = "Omitted = 1") @Min(1) @Max(10_000) Integer positions,
            boolean featured,
            @Schema(example = "2") @Min(0) @Max(60) int minExperience,
            @Schema(example = "5") @Min(0) @Max(60) Integer maxExperience,
            @Schema(example = "15000000") @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal minSalary,
            @Schema(example = "30000000") @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal maxSalary,
            @Schema(description = "Rich text (HTML)") @Size(max = 100_000) String description,
            @Schema(description = "Rich text (HTML)") @Size(max = 100_000) String requirements,
            @Schema(description = "Rich text (HTML)") @Size(max = 100_000) String benefits,
            @Schema(description = "Custom question ids") @Size(max = 50) List<@NotNull Long> customQuestionIds,
            boolean askGender,
            boolean askDateOfBirth,
            boolean showCoverLetter,
            boolean showTerms) {
    }
}
