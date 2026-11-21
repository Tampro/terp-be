package com.terp.erp.recruitment.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {
    List<InterviewFeedback> findByInterviewIdOrderByCreatedDateAsc(Long interviewId);

    boolean existsByInterviewIdAndEmployeeId(Long interviewId, Long employeeId);

    /** Interview ids among these that have at least one feedback. */
    @Query("select distinct f.interviewId from InterviewFeedback f where f.interviewId in :ids")
    List<Long> interviewsWithFeedback(Collection<Long> ids);
}
