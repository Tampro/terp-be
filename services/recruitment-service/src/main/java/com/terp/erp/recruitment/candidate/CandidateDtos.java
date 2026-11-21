package com.terp.erp.recruitment.candidate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import com.terp.erp.recruitment.candidate.Candidate.EGender;
import com.terp.erp.recruitment.candidate.Candidate.EStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class CandidateDtos {
    private CandidateDtos() {
    }

    public record Ref(String id, String name) {
    }

    public record JobRef(String id, String jobCode, String title) {
    }

    /** List row and kanban card. employeeConverted = onboarding made them an employee. */
    public record CandidateRow(String id, String fullName, String email, JobRef job, Ref source, int experienceYears,
                               BigDecimal expectedSalary, EStatus status, boolean employeeConverted,
                               LocalDate applicationDate, Instant updatedDate) {
    }

    public record AnswerDto(String questionId, String question, String answer) {
    }

    /** resumeUrl / coverLetterUrl = the admin download endpoints (need a token), null without a file. */
    public record CandidateDetail(String id, String firstName, String lastName, String email, String phone,
                                  String address, String city, String state, String country, String zipCode,
                                  JobRef job, Ref branch, Ref location, Ref jobType, Ref department, Ref source,
                                  String currentCompany, String currentPosition, int experienceYears,
                                  BigDecimal currentSalary, BigDecimal expectedSalary, String noticePeriod,
                                  EGender gender, LocalDate dateOfBirth, String coverLetterMessage,
                                  String resumeName, Long resumeSize, String resumeUrl, String coverLetterName,
                                  String coverLetterUrl, List<AnswerDto> answers,
                                  boolean termsAccepted, EStatus status, String employeeId, LocalDate applicationDate,
                                  Instant createdDate, Instant updatedDate) {
    }

    public record StatusCounts(long all, Map<EStatus, Long> byStatus) {
    }

    public record KanbanColumn(EStatus status, long count, List<CandidateRow> cards) {
    }

    public record AnswerForm(
            @Schema(description = "Custom question id of the posting", example = "1",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long questionId,
            @Schema(example = "I like the product") @Size(max = 5000) String answer) {
    }

    /** Admin entry (e.g. a LinkedIn applicant); the public career page sends the same fields. */
    public record CandidateForm(
            @Schema(description = "Job posting id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long jobPostingId,
            @Schema(description = "Candidate source id", example = "2") Long sourceId,
            @Schema(example = "Test", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100)
            String firstName,
            @Schema(example = "Candidate", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100)
            String lastName,
            @Schema(example = "candidate@example.invalid", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 255) String email,
            @Schema(example = "0900000000") @Size(max = 30) String phone,
            @Size(max = 150) String currentCompany,
            @Size(max = 150) String currentPosition,
            @Schema(example = "3") @Min(0) @Max(60) int experienceYears,
            @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal currentSalary,
            @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal expectedSalary,
            @Schema(example = "30 days") @Size(max = 50) String noticePeriod,
            EGender gender,
            @Past LocalDate dateOfBirth,
            @Size(max = 255) String address,
            @Size(max = 100) String city,
            @Size(max = 100) String state,
            @Size(max = 20) String zipCode,
            @Size(max = 100) String country,
            @Size(max = 5000) String coverLetterMessage,
            boolean termsAccepted,
            @Schema(description = "Omitted = today") LocalDate applicationDate,
            @Size(max = 50) List<@Valid AnswerForm> answers) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }
}
