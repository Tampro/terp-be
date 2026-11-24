package com.terp.erp.meeting.common;

import com.terp.erp.retention.CompanyAnonymizeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Retention for meeting-service (PO 2026-11-24: 5 years after the company was deleted): organizer and assignee
 * names/emails, meeting descriptions and agendas, action item notes, Zoom agendas, join links and passcodes, and
 * the company's Zoom credentials. Kept: titles, dates, rooms, statuses. Idempotent.
 */
@Component
@RequiredArgsConstructor
public class MeetingAnonymizer implements CompanyAnonymizeHandler {
    private final JdbcTemplate jdbc;

    @Override
    public void anonymize(long companyId) {
        jdbc.update("UPDATE meeting SET description = NULL, agenda = NULL, organizer_name = '[anonymized]', organizer_email = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE action_item SET description = NULL, assignee_name = '[anonymized]', assignee_email = NULL, progress_notes = NULL WHERE company_id = ?", companyId);
        jdbc.update("UPDATE zoom_meeting SET agenda = NULL, passcode = '[anonymized]', join_url = '[anonymized]', start_url_cipher = '[anonymized]' WHERE company_id = ?", companyId);
        jdbc.update("DELETE FROM zoom_setting WHERE company_id = ?", companyId);
        jdbc.update("UPDATE company_event SET description = NULL WHERE company_id = ?", companyId);
    }
}
