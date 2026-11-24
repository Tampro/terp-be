package com.terp.erp.recruitment.common;

import com.terp.erp.recruitment.storage.FileStore;
import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Retention for recruitment-service (PO 2026-11-24: 5 years after the company was deleted, then anonymised):
 * candidates' identity, contact, address, salary expectations, cover letter text and answers; interviewers'
 * names/emails, interview locations/links and feedback text; offer approver names and benefits text. The resume and
 * cover-letter files are deleted from the volume after the commit. Kept: statuses, dates, scores, offer amounts,
 * postings (the company's own text). Idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecruitmentAnonymizer implements CompanyAnonymizeHandler {
    static final String ANON = "[anonymized]";

    private final JdbcTemplate jdbc;
    private final FileStore files;

    @Override
    public void anonymize(long companyId) {
        List<String> keys = jdbc.queryForList("SELECT resume_key FROM candidate WHERE company_id = ? AND resume_key "
                + "IS NOT NULL UNION SELECT cover_letter_key FROM candidate WHERE company_id = ? AND cover_letter_key "
                + "IS NOT NULL", String.class, companyId, companyId);
        jdbc.update("UPDATE candidate SET first_name = ?, last_name = ?, email = CONCAT('anon-', id, "
                + "'@anonymized.invalid'), phone = NULL, current_company = NULL, current_position = NULL, "
                + "current_salary = NULL, expected_salary = NULL, gender = NULL, date_of_birth = NULL, address = NULL, "
                + "city = NULL, state = NULL, zip_code = NULL, country = NULL, cover_letter_message = NULL, "
                + "resume_key = NULL, resume_name = NULL, resume_mime = NULL, resume_size = NULL, "
                + "cover_letter_key = NULL, cover_letter_name = NULL, cover_letter_mime = NULL, "
                + "cover_letter_size = NULL WHERE company_id = ?", "Anonymized", "candidate", companyId);
        jdbc.update("UPDATE candidate_answer a JOIN candidate c ON c.id = a.candidate_id SET a.answer = NULL "
                + "WHERE c.company_id = ?", companyId);
        jdbc.update("UPDATE interview SET location = NULL, meeting_link = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE interview_interviewer i JOIN interview v ON v.id = i.interview_id SET i.name = ?, "
                + "i.email = NULL, i.designation = NULL WHERE v.company_id = ?", ANON, companyId);
        jdbc.update("UPDATE interview_feedback SET interviewer_name = NULL, interviewer_role = NULL, strengths = NULL, "
                + "weaknesses = NULL, comments = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE offer SET approved_by_name = NULL, benefits = NULL WHERE company_id = ?", companyId);
        deleteFilesAfterCommit(keys);
    }

    private void deleteFilesAfterCommit(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        Runnable delete = () -> keys.forEach(k -> {
            try {
                files.delete(k);
            } catch (RuntimeException ex) {
                log.warn("Retention: a candidate file could not be deleted: {}", ex.getClass().getSimpleName());
            }
        });
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete.run();
            }
        });
    }
}
