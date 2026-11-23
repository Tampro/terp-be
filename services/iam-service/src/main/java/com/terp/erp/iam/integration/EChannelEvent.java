package com.terp.erp.iam.integration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Business events notification-service routes (its EEventRoute, keyed by the broker routing key), as the list a
 * company switches Slack / Telegram / SMS on or off for, per event (company_channel_event.event_type). Keep in step
 * with notification-service EEventRoute: an event missing here is always on for chat channels; a code here that is
 * never produced is harmless. Codes are stored: never rename one.
 */
@Getter
@RequiredArgsConstructor
public enum EChannelEvent {
    USER_CREATED("iam.user.created", EModule.GENERAL, "New user"),
    HELPDESK_TICKET_CREATED("iam.helpdesk.ticket_created", EModule.HELPDESK, "New helpdesk ticket"),
    HELPDESK_TICKET_REPLIED("iam.helpdesk.ticket_replied", EModule.HELPDESK, "New helpdesk ticket reply"),
    SALES_INVOICE_POSTED("sales.invoice.posted", EModule.SALES, "New sales invoice"),
    SALES_INVOICE_STATUS_CHANGED("sales.invoice.status_changed", EModule.SALES, "Sales invoice status updated"),
    SALES_INVOICE_SENT("sales.invoice.sent", EModule.SALES, "Sales invoice sent"),
    SALES_PROPOSAL_CREATED("sales.proposal.created", EModule.SALES, "New sales proposal"),
    SALES_PROPOSAL_STATUS_CHANGED("sales.proposal.status_changed", EModule.SALES, "Sales proposal status updated"),
    PURCHASE_INVOICE_POSTED("purchase.invoice.posted", EModule.PURCHASE, "New purchase invoice"),
    PURCHASE_INVOICE_SENT("purchase.invoice.sent", EModule.PURCHASE, "Purchase invoice sent"),
    CUSTOMER_CREATED("accounting.customer.created", EModule.ACCOUNTING, "New customer"),
    VENDOR_CREATED("accounting.vendor.created", EModule.ACCOUNTING, "New vendor"),
    REVENUE_CREATED("accounting.revenue.created", EModule.ACCOUNTING, "New revenue"),
    CUSTOMER_PAYMENT_CLEARED("accounting.customer_payment.cleared", EModule.ACCOUNTING, "Invoice payment received"),
    VENDOR_PAYMENT_CLEARED("accounting.vendor_payment.cleared", EModule.ACCOUNTING, "Purchase payment made"),
    INVOICE_PAYMENT_REMINDER("accounting.invoice.payment_reminder", EModule.ACCOUNTING, "Payment reminder"),
    WAREHOUSE_CREATED("inventory.warehouse.created", EModule.INVENTORY, "New warehouse"),
    TRAINING_CREATED("employee.training.created", EModule.TRAINING, "New training"),
    TRAINER_CREATED("employee.trainer.created", EModule.TRAINING, "New trainer"),
    AWARD_CREATED("employee.award.created", EModule.HRM, "New award"),
    ANNOUNCEMENT_PUBLISHED("employee.announcement.published", EModule.HRM, "New announcement"),
    HOLIDAY_CREATED("employee.holiday.created", EModule.HRM, "New holiday"),
    ZOOM_MEETING_CREATED("meeting.zoom.created", EModule.MEETING, "New Zoom meeting"),
    COMPANY_EVENT_CREATED("meeting.event.created", EModule.MEETING, "New event"),
    JOB_POSTING_PUBLISHED("recruitment.job_posting.published", EModule.RECRUITMENT, "New job posting"),
    APPLICATION_RECEIVED("recruitment.application.received", EModule.RECRUITMENT, "New job candidate"),
    INTERVIEW_SCHEDULED("recruitment.interview.scheduled", EModule.RECRUITMENT, "New interview schedule"),
    CANDIDATE_CONVERTED("recruitment.candidate.converted", EModule.RECRUITMENT, "Candidate converted to employee"),
    OFFER_LETTER_SENT("recruitment.offer.letter_sent", EModule.RECRUITMENT, "Offer letter sent"),
    PROJECT_CREATED("project.project.created", EModule.PROJECT, "New project"),
    MILESTONE_CREATED("project.milestone.created", EModule.PROJECT, "New milestone"),
    TASK_CREATED("project.task.created", EModule.PROJECT, "New task"),
    TASK_MOVED("project.task.moved", EModule.PROJECT, "Task stage updated"),
    TASK_COMMENTED("project.task.commented", EModule.PROJECT, "New task comment"),
    BUG_CREATED("project.bug.created", EModule.PROJECT, "New bug"),
    LEAD_CREATED("crm.lead.created", EModule.CRM, "New lead"),
    LEAD_ASSIGNED("crm.lead.assigned", EModule.CRM, "Lead assigned"),
    LEAD_MOVED("crm.lead.moved", EModule.CRM, "Lead moved"),
    LEAD_CONVERTED("crm.lead.converted", EModule.CRM, "Lead converted to deal"),
    LEAD_EMAILED("crm.lead.emailed", EModule.CRM, "Lead email"),
    DEAL_CREATED("crm.deal.created", EModule.CRM, "New deal"),
    DEAL_ASSIGNED("crm.deal.assigned", EModule.CRM, "Deal assigned"),
    DEAL_MOVED("crm.deal.moved", EModule.CRM, "Deal moved"),
    DEAL_EMAILED("crm.deal.emailed", EModule.CRM, "Deal email"),
    CRM_TASK_CREATED("crm.task.created", EModule.CRM, "New CRM task"),
    CONTRACT_CREATED("crm.contract.created", EModule.CONTRACT, "New contract"),
    SUPPORT_TICKET_CREATED("support.ticket.created", EModule.SUPPORT, "New support ticket"),
    SUPPORT_TICKET_REPLIED("support.ticket.replied", EModule.SUPPORT, "New support ticket reply");

    /** Groups of the per-event switch page, in display order. */
    public enum EModule { GENERAL, HELPDESK, SALES, PURCHASE, ACCOUNTING, INVENTORY, HRM, TRAINING, MEETING,
        RECRUITMENT, PROJECT, CRM, CONTRACT, SUPPORT }

    private final String eventType;
    private final EModule module;
    private final String displayName;

    public static Optional<EChannelEvent> ofEventType(String eventType) {
        return Arrays.stream(values()).filter(e -> e.eventType.equals(eventType)).findFirst();
    }
}
