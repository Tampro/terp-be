package com.terp.erp.notification.event;

import java.util.Arrays;
import java.util.Optional;

/**
 * Event type (routing key) → IAM catalog codes: emailCode = ENotificationEvent (the company's email switch and the
 * template), webhookCode = EWebhookEvent (the company's webhooks), with its display name and module for the webhook
 * body. Recipients come from the payload: recipientEmail (an address) and / or recipientUserIds (IAM users, resolved
 * through IAM's internal contact endpoint). An event type not listed here is acknowledged and ignored.
 * Slack / Telegram / SMS fire for a routed event when the company turned the integration on and did not switch the
 * event off for that channel (IAM company_channel_event; no row = on). SMS also needs data.recipientPhone (E.164).
 * Google Calendar fires for a route with {@link #calendar} when the integration is on and the payload's flag is true.
 */
public enum EEventRoute {
    SALES_INVOICE_POSTED("sales.invoice.posted", "INVOICE_SENT", "NEW_SALES_INVOICE", "New Sales Invoice", "SALES",
            ECalendar.INVOICE_DUE),
    PURCHASE_INVOICE_POSTED("purchase.invoice.posted", null, "NEW_PURCHASE_INVOICE", "New Purchase Invoice",
            "PURCHASE"),
    CUSTOMER_PAYMENT_CLEARED("accounting.customer_payment.cleared", "INVOICE_PAYMENT_CREATED", null, null, null),
    SUPPORT_TICKET_CREATED("support.ticket.created", "SUPPORT_TICKET_CREATED", null, null, null),
    SUPPORT_TICKET_REPLIED("support.ticket.replied", "SUPPORT_TICKET_REPLY", null, null, null),
    APPLICATION_RECEIVED("recruitment.application.received", "APPLICATION_RECEIVED", "NEW_JOB_CANDIDATE",
            "New Job Candidate", "RECRUITMENT"),
    LEAD_ASSIGNED("crm.lead.assigned", "LEAD_ASSIGNED", null, null, null),
    LEAD_MOVED("crm.lead.moved", "LEAD_MOVED", "LEAD_MOVED", "Lead Moved", "CRM"),
    DEAL_ASSIGNED("crm.deal.assigned", "DEAL_ASSIGNED", null, null, null),
    DEAL_MOVED("crm.deal.moved", "DEAL_MOVED", "DEAL_MOVED", "Deal Moved", "CRM"),
    // feat/notification-events: one row per event of the shared contract; webhook names / modules = EWebhookEvent.
    USER_CREATED("iam.user.created", null, "NEW_USER", "New User", "GENERAL"),
    HELPDESK_TICKET_CREATED("iam.helpdesk.ticket_created", "HELPDESK_TICKET_CREATED", null, null, null),
    HELPDESK_TICKET_REPLIED("iam.helpdesk.ticket_replied", "HELPDESK_TICKET_REPLY", null, null, null),
    SALES_INVOICE_STATUS_CHANGED("sales.invoice.status_changed", null, "SALES_INVOICE_STATUS_UPDATED",
            "Sales Invoice Status Updated", "SALES"),
    SALES_INVOICE_SENT("sales.invoice.sent", "INVOICE_SENT", null, null, null),
    SALES_PROPOSAL_CREATED("sales.proposal.created", null, "NEW_SALES_PROPOSAL", "New Sales Proposal", "SALES"),
    SALES_PROPOSAL_STATUS_CHANGED("sales.proposal.status_changed", "PROPOSAL_STATUS_UPDATED",
            "SALES_PROPOSAL_STATUS_UPDATED", "Sales Proposal Status Updated", "SALES"),
    PURCHASE_INVOICE_SENT("purchase.invoice.sent", "PURCHASE_SENT", null, null, null),
    CUSTOMER_CREATED("accounting.customer.created", null, "NEW_CUSTOMER", "New Customer", "ACCOUNTING"),
    VENDOR_CREATED("accounting.vendor.created", null, "NEW_VENDOR", "New Vendor", "ACCOUNTING"),
    REVENUE_CREATED("accounting.revenue.created", null, "NEW_REVENUE", "New Revenue", "ACCOUNTING"),
    VENDOR_PAYMENT_CLEARED("accounting.vendor_payment.cleared", "PURCHASE_PAYMENT_CREATED", null, null, null),
    INVOICE_PAYMENT_REMINDER("accounting.invoice.payment_reminder", "PAYMENT_REMINDER", null, null, null),
    WAREHOUSE_CREATED("inventory.warehouse.created", null, "NEW_WAREHOUSE", "New Warehouse", "INVENTORY"),
    TRAINING_CREATED("employee.training.created", null, "NEW_TRAINING", "New Training", "TRAINING"),
    TRAINER_CREATED("employee.trainer.created", null, "NEW_TRAINER", "New Trainer", "TRAINING"),
    AWARD_CREATED("employee.award.created", null, "NEW_AWARD", "New Award", "HRM"),
    ANNOUNCEMENT_PUBLISHED("employee.announcement.published", null, "NEW_ANNOUNCEMENT", "New Announcement", "HRM"),
    HOLIDAY_CREATED("employee.holiday.created", null, "NEW_HOLIDAY", "New Holidays", "HRM", ECalendar.HOLIDAY_CREATE),
    // Google Calendar only (calendarOnly): no email, webhook, chat or SMS for these.
    HOLIDAY_UPDATED("employee.holiday.updated", null, null, null, null, ECalendar.HOLIDAY_UPDATE),
    HOLIDAY_DELETED("employee.holiday.deleted", null, null, null, null, ECalendar.HOLIDAY_DELETE),
    ZOOM_MEETING_CREATED("meeting.zoom.created", null, "NEW_ZOOM_MEETING", "New Zoom Meeting", "MEETING"),
    COMPANY_EVENT_CREATED("meeting.event.created", null, "NEW_EVENT", "New Event", "HRM"),
    JOB_POSTING_PUBLISHED("recruitment.job_posting.published", null, "NEW_JOB_POSTING", "New Job Posting",
            "RECRUITMENT"),
    // The contract names the candidate as recipient but no email code: webhook only (an email needs a code).
    INTERVIEW_SCHEDULED("recruitment.interview.scheduled", null, "NEW_INTERVIEW_SCHEDULE",
            "New Job Interview Schedule", "RECRUITMENT"),
    CANDIDATE_CONVERTED("recruitment.candidate.converted", null, "CANDIDATE_CONVERTED_TO_EMPLOYEE",
            "New Convert To Employee", "RECRUITMENT"),
    OFFER_LETTER_SENT("recruitment.offer.letter_sent", "OFFER_LETTER", null, null, null),
    PROJECT_CREATED("project.project.created", null, "NEW_PROJECT", "New Project", "PROJECT"),
    MILESTONE_CREATED("project.milestone.created", null, "NEW_MILESTONE", "New Milestone", "PROJECT"),
    TASK_CREATED("project.task.created", null, "NEW_TASK", "New Task", "PROJECT"),
    TASK_MOVED("project.task.moved", null, "TASK_STAGE_UPDATED", "Task Stage Update", "PROJECT"),
    TASK_COMMENTED("project.task.commented", null, "NEW_TASK_COMMENT", "New Task Comment", "PROJECT"),
    BUG_CREATED("project.bug.created", null, "NEW_BUG", "New Bug", "PROJECT"),
    LEAD_CREATED("crm.lead.created", null, "NEW_LEAD", "New Lead", "CRM"),
    DEAL_CREATED("crm.deal.created", null, "NEW_DEAL", "New Deal", "CRM"),
    LEAD_CONVERTED("crm.lead.converted", null, "LEAD_CONVERTED_TO_DEAL", "Convert To Deal", "CRM"),
    CONTRACT_CREATED("crm.contract.created", null, "NEW_CONTRACT", "New Contract", "CONTRACT"),
    CRM_TASK_CREATED("crm.task.created", "CRM_TASK_CREATED", null, null, null),
    LEAD_EMAILED("crm.lead.emailed", "LEAD_EMAIL", null, null, null),
    DEAL_EMAILED("crm.deal.emailed", "DEAL_EMAIL", null, null, null),
    // Express orders → the customer (recipient order:<orderId>, read from order-service). No chat, SMS or webhook.
    ORDER_PAID("order.paid", "ORDER_PAID", null, null, null),
    // The email code depends on data.status (see emailCode(JsonNode)); READY / ASSIGNED send nothing.
    DELIVERY_STATUS_CHANGED("delivery.status.changed", "ORDER_DELIVERED", null, null, null),
    // Back Office cancelled a paid order and refunded it (paid-order-cancel); the reason is never in the email.
    ORDER_REFUNDED("order.refunded", "ORDER_REFUNDED", null, null, null);

    public final String eventType;
    public final String emailCode;
    public final String webhookCode;
    public final String webhookName;
    public final String webhookModule;
    /** null = no Google Calendar entry for this event. */
    public final ECalendar calendar;

    /**
     * What the Google Calendar channel does for an event, and the payload switch it needs (true, else no row):
     * holidays data.calendarSync, sales invoices data.syncToGoogleCalendar.
     */
    public enum ECalendar {
        HOLIDAY_CREATE("calendarSync"), HOLIDAY_UPDATE("calendarSync"), HOLIDAY_DELETE("calendarSync"),
        INVOICE_DUE("syncToGoogleCalendar");

        public final String flag;

        ECalendar(String flag) {
            this.flag = flag;
        }
    }

    EEventRoute(String eventType, String emailCode, String webhookCode, String webhookName, String webhookModule) {
        this(eventType, emailCode, webhookCode, webhookName, webhookModule, null);
    }

    EEventRoute(String eventType, String emailCode, String webhookCode, String webhookName, String webhookModule,
                ECalendar calendar) {
        this.eventType = eventType;
        this.emailCode = emailCode;
        this.webhookCode = webhookCode;
        this.webhookName = webhookName;
        this.webhookModule = webhookModule;
        this.calendar = calendar;
    }

    /**
     * The email code for this event: {@link #emailCode} except for a delivery status, where the status chooses it
     * (PICKED_UP / DELIVERED / CANCELLED) and any other status has none.
     */
    public String emailCode(com.fasterxml.jackson.databind.JsonNode data) {
        if (this != DELIVERY_STATUS_CHANGED) {
            return emailCode;
        }
        String status = data == null ? "" : data.path("status").asText("");
        return switch (status) {
            case "PICKED_UP" -> "ORDER_PICKED_UP";
            case "DELIVERED" -> "ORDER_DELIVERED";
            case "CANCELLED" -> "ORDER_DELIVERY_CANCELLED";
            default -> null;
        };
    }

    /** Emails to a shop customer about their order: never to the company's chat channels, SMS or webhooks. */
    public boolean customerOrder() {
        return this == ORDER_PAID || this == DELIVERY_STATUS_CHANGED || this == ORDER_REFUNDED;
    }

    /** Routed for the calendar only: the chat channels and SMS stay quiet. */
    public boolean calendarOnly() {
        return calendar != null && emailCode == null && webhookCode == null;
    }

    public static Optional<EEventRoute> of(String eventType) {
        return Arrays.stream(values()).filter(r -> r.eventType.equals(eventType)).findFirst();
    }
}
