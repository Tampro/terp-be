package com.terp.erp.employee.lifecycle;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.terp.erp.employee.client.IamClient;
import com.terp.erp.employee.common.EEmployeeError;
import com.terp.erp.employee.employee.EEmployeeStatus;
import com.terp.erp.employee.employee.Employee;
import com.terp.erp.exception.ApiException;

import java.time.LocalDate;

/**
 * Accepting a resignation or approving a termination ends the employment at once, whatever the dates (PO, 2026-11-20,
 * kept when the statuses were aligned with the manual): the employee becomes TERMINATED and an enabled login is
 * locked in IAM (Keycloak disabled, sessions ended).
 */
@Component
@RequiredArgsConstructor
public class EmployeeExit {
    private final IamClient iam;

    public record ExitInterview(boolean conducted, LocalDate date, String feedback) {
        static ExitInterview of(SeparationRecord r) {
            return new ExitInterview(r.isExitInterviewConducted(), r.getExitInterviewDate(), r.getExitFeedback());
        }
    }

    /** The Change Status modal's exit interview part; not conducted clears the date and the feedback. */
    static void interview(SeparationRecord r, boolean conducted, LocalDate date, String feedback) {
        r.setExitInterviewConducted(conducted);
        r.setExitInterviewDate(conducted ? date : null);
        r.setExitFeedback(conducted ? AwardService.blankToNull(feedback) : null);
    }

    /** Decided records are kept as they were decided (EMP_046); only the exit interview stays editable. */
    static void assertOpen(boolean decided) {
        if (decided) {
            throw ApiException.badRequest(EEmployeeError.EMP_046);
        }
    }

    /** No new resignation / termination for an employee who has already left (EMP_058). */
    public static void assertEmployed(Employee e) {
        if (e.getStatus() == EEmployeeStatus.TERMINATED) {
            throw ApiException.badRequest(EEmployeeError.EMP_058);
        }
    }

    /** IAM first: when it fails the transaction rolls back, so the employee never shows as gone with a live login. */
    void terminate(Employee e) {
        if (e.getUserId() != null && e.isLoginEnabled()) {
            iam.setLoginEnabled(e.getUserId(), false);
        }
        e.setLoginEnabled(false);
        e.setStatus(EEmployeeStatus.TERMINATED);
    }
}
