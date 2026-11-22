package com.terp.erp.iam.integration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Events a company webhook can subscribe to: the reference TERP Core packages/workdo/Webhook/src/config/
 * webhook-events.php (module + action), grouped by the modules of ERP manual 19. Reference module "general" is split
 * into SALES / PURCHASE / INVENTORY / GENERAL, "Account" is ACCOUNTING, "Taskly" PROJECT, "Lead" CRM.
 * TODO: nothing publishes these yet; dispatch (sign, POST, retry with back-off, delivery log) needs a broker.
 * Codes are stored in company_webhook.event_code: never rename one.
 */
@Getter
@RequiredArgsConstructor
public enum EWebhookEvent {
    NEW_USER(EModule.GENERAL, "New User"),
    NEW_SALES_INVOICE(EModule.SALES, "New Sales Invoice"),
    SALES_INVOICE_STATUS_UPDATED(EModule.SALES, "Sales Invoice Status Updated"),
    NEW_SALES_PROPOSAL(EModule.SALES, "New Sales Proposal"),
    SALES_PROPOSAL_STATUS_UPDATED(EModule.SALES, "Sales Proposal Status Updated"),
    NEW_PURCHASE_INVOICE(EModule.PURCHASE, "New Purchase Invoice"),
    NEW_WAREHOUSE(EModule.INVENTORY, "New Warehouse"),
    NEW_CUSTOMER(EModule.ACCOUNTING, "New Customer"),
    NEW_VENDOR(EModule.ACCOUNTING, "New Vendor"),
    NEW_REVENUE(EModule.ACCOUNTING, "New Revenue"),
    NEW_JOB_POSTING(EModule.RECRUITMENT, "New Job Posting"),
    NEW_JOB_CANDIDATE(EModule.RECRUITMENT, "New Job Candidate"),
    NEW_INTERVIEW_SCHEDULE(EModule.RECRUITMENT, "New Job Interview Schedule"),
    CANDIDATE_CONVERTED_TO_EMPLOYEE(EModule.RECRUITMENT, "New Convert To Employee"),
    NEW_TRAINING(EModule.TRAINING, "New Training"),
    NEW_TRAINER(EModule.TRAINING, "New Trainer"),
    NEW_ZOOM_MEETING(EModule.MEETING, "New Zoom Meeting"),
    NEW_PROJECT(EModule.PROJECT, "New Project"),
    NEW_MILESTONE(EModule.PROJECT, "New Milestone"),
    NEW_TASK(EModule.PROJECT, "New Task"),
    TASK_STAGE_UPDATED(EModule.PROJECT, "Task Stage Update"),
    NEW_TASK_COMMENT(EModule.PROJECT, "New Task Comment"),
    NEW_BUG(EModule.PROJECT, "New Bug"),
    NEW_LEAD(EModule.CRM, "New Lead"),
    NEW_DEAL(EModule.CRM, "New Deal"),
    LEAD_MOVED(EModule.CRM, "Lead Moved"),
    DEAL_MOVED(EModule.CRM, "Deal Moved"),
    LEAD_CONVERTED_TO_DEAL(EModule.CRM, "Convert To Deal"),
    NEW_CONTRACT(EModule.CONTRACT, "New Contract"),
    NEW_AWARD(EModule.HRM, "New Award"),
    NEW_ANNOUNCEMENT(EModule.HRM, "New Announcement"),
    NEW_HOLIDAY(EModule.HRM, "New Holidays"),
    NEW_EVENT(EModule.HRM, "New Event");

    public enum EModule { GENERAL, SALES, PURCHASE, INVENTORY, ACCOUNTING, RECRUITMENT, TRAINING, MEETING, PROJECT, CRM,
        CONTRACT, HRM }

    private final EModule module;
    private final String displayName;
}
