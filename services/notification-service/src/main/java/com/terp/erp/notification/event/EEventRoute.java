package com.terp.erp.notification.event;

import java.util.Arrays;
import java.util.Optional;

/**
 * Event type (routing key) → IAM catalog codes: emailCode = ENotificationEvent (the company's email switch and the
 * template), webhookCode = EWebhookEvent (the company's webhooks), with its display name and module for the webhook
 * body. Recipients come from the payload: recipientEmail (an address) and / or recipientUserIds (IAM users, resolved
 * through IAM's internal contact endpoint). An event type not listed here is acknowledged and ignored.
 * TODO(assumption): every routed event also goes to Slack / Telegram when the company turned that integration on
 * (IAM keeps no per-event switch for them).
 */
public enum EEventRoute {
    SALES_INVOICE_POSTED("sales.invoice.posted", "INVOICE_SENT", "NEW_SALES_INVOICE", "New Sales Invoice", "SALES"),
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
    DEAL_MOVED("crm.deal.moved", "DEAL_MOVED", "DEAL_MOVED", "Deal Moved", "CRM");

    public final String eventType;
    public final String emailCode;
    public final String webhookCode;
    public final String webhookName;
    public final String webhookModule;

    EEventRoute(String eventType, String emailCode, String webhookCode, String webhookName, String webhookModule) {
        this.eventType = eventType;
        this.emailCode = emailCode;
        this.webhookCode = webhookCode;
        this.webhookName = webhookName;
        this.webhookModule = webhookModule;
    }

    public static Optional<EEventRoute> of(String eventType) {
        return Arrays.stream(values()).filter(r -> r.eventType.equals(eventType)).findFirst();
    }
}
