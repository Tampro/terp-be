package com.terp.erp.recruitment.interview;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.terp.erp.recruitment.candidate.Candidate;
import com.terp.erp.recruitment.interview.Interview.EStatus;
import com.terp.erp.recruitment.interview.InterviewFeedback.ERecommendation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class InterviewDtos {
    private InterviewDtos() {
    }

    public record Ref(String id, String name) {
    }

    public record JobRef(String id, String jobCode, String title) {
    }

    /** Name and applied position, for rows and cards. */
    public record CandidateRef(String id, String fullName, String jobTitle) {
    }

    public record InterviewerDto(String employeeId, String name, String email, String designation) {
    }

    public record InterviewRow(String id, CandidateRef candidate, Ref round, Ref interviewType, LocalDate date,
                               LocalTime startTime, int durationMinutes, String location, String meetingLink,
                               EStatus status, boolean feedbackSubmitted, List<InterviewerDto> interviewers) {
    }

    /** Candidate Details panel. */
    public record CandidatePanel(String id, String fullName, String email, String phone, String currentPosition,
                                 String currentCompany, int experienceYears, String noticePeriod,
                                 Candidate.EStatus status) {
    }

    public record FeedbackDto(String id, String employeeId, String interviewerName, String interviewerRole,
                              int overallScore, int technicalScore, int communicationScore, int culturalFitScore,
                              ERecommendation recommendation, String strengths, String weaknesses, String comments,
                              Instant createdDate, Instant updatedDate) {
    }

    /** Averages are null without feedback; the FE derives percentages from the counts. */
    public record FeedbackSummary(int count, Double overall, Double technical, Double communication,
                                  Double culturalFit, Map<ERecommendation, Long> recommendations) {
    }

    public record InterviewDetail(String id, CandidatePanel candidate, JobRef job, Ref round, Ref interviewType,
                                  LocalDate date, LocalTime startTime, int durationMinutes, String location,
                                  String meetingLink, EStatus status, boolean feedbackSubmitted,
                                  List<InterviewerDto> interviewers, FeedbackSummary feedbackSummary,
                                  List<FeedbackDto> feedback, Instant createdDate, Instant updatedDate) {
    }

    /** Total + per status + Pending Feedback (completed without any feedback), under the same filters. */
    public record InterviewSummary(long total, Map<EStatus, Long> byStatus, long pendingFeedback) {
    }

    public record KanbanColumn(EStatus status, long count, List<InterviewRow> cards) {
    }

    public record InterviewForm(
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long candidateId,
            @Schema(description = "A round of the candidate's job posting", example = "1",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long roundId,
            @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long interviewTypeId,
            @Schema(example = "2026-12-01", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate date,
            @Schema(type = "string", example = "10:00", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
            LocalTime startTime,
            @Schema(example = "60", requiredMode = Schema.RequiredMode.REQUIRED) @Min(5) @Max(720) int durationMinutes,
            @Schema(example = "Conference Room A, Floor 2") @Size(max = 255) String location,
            @Schema(example = "https://meet.example.invalid/abc") @Size(max = 1000) String meetingLink,
            @Schema(description = "Employee ids", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty @Size(max = 20) List<@NotNull Long> interviewerIds) {
    }

    public record StatusForm(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull EStatus status) {
    }

    public record FeedbackForm(
            @Schema(description = "One of the interview's interviewers (employee id)", example = "1",
                    requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(example = "4") @Min(1) @Max(5) int overallScore,
            @Schema(example = "4") @Min(1) @Max(5) int technicalScore,
            @Schema(example = "5") @Min(1) @Max(5) int communicationScore,
            @Schema(example = "4") @Min(1) @Max(5) int culturalFitScore,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ERecommendation recommendation,
            @Size(max = 5000) String strengths,
            @Size(max = 5000) String weaknesses,
            @Size(max = 5000) String comments) {
    }
}
