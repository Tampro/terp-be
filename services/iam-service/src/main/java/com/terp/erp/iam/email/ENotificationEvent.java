package com.terp.erp.iam.email;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Business events a company may switch email notifications on or off for (ERP manual 19 "Email notifications").
 * Names are the reference TERP Core email template names: database/seeders/EmailTemplatesSeeder (general),
 * packages/workdo/Lead (CRM), SupportTicket, Recruitment. "New User" is {@link EEmailTemplate#USER_CREATE}, which
 * the platform already sends, so it is not repeated here.
 * Dispatch: the owning services publish outbox events; notification-service maps them to these codes, reads this
 * switch through IAM's internal GET /api/v1/internal/notification-config and sends. Produced today: INVOICE_SENT (on
 * sales invoice post), INVOICE_PAYMENT_CREATED, LEAD_ASSIGNED, LEAD_MOVED, DEAL_ASSIGNED, DEAL_MOVED,
 * SUPPORT_TICKET_CREATED, SUPPORT_TICKET_REPLY, APPLICATION_RECEIVED, ORDER_* (order.paid, delivery.status.changed).
 * TODO: the others have no producer yet.
 * Codes are stored in company_email_notification.template_code: never rename one, and never reuse an
 * {@link EEmailTemplate} name.
 */
@Getter
@RequiredArgsConstructor
public enum ENotificationEvent {
    INVOICE_SENT(ENotificationModule.GENERAL, "Customer Invoice Send"),
    PAYMENT_REMINDER(ENotificationModule.GENERAL, "Payment Reminder"),
    INVOICE_PAYMENT_CREATED(ENotificationModule.GENERAL, "Invoice Payment Create"),
    // Reference "Proposal Send"; the manual calls it "proposal status updated".
    PROPOSAL_STATUS_UPDATED(ENotificationModule.GENERAL, "Proposal Send"),
    HELPDESK_TICKET_CREATED(ENotificationModule.GENERAL, "New Helpdesk Ticket"),
    HELPDESK_TICKET_REPLY(ENotificationModule.GENERAL, "New Helpdesk Ticket Reply"),
    PURCHASE_SENT(ENotificationModule.GENERAL, "Purchase Send"),
    PURCHASE_PAYMENT_CREATED(ENotificationModule.GENERAL, "Purchase Payment Create"),
    LEAD_ASSIGNED(ENotificationModule.CRM, "Lead Assign"),
    LEAD_MOVED(ENotificationModule.CRM, "Lead Move"),
    DEAL_ASSIGNED(ENotificationModule.CRM, "Deal Assign"),
    DEAL_MOVED(ENotificationModule.CRM, "Deal Move"),
    // TODO(assumption): the manual lists "new task" for CRM; the reference Lead seeder has no such template.
    CRM_TASK_CREATED(ENotificationModule.CRM, "New Task"),
    LEAD_EMAIL(ENotificationModule.CRM, "Lead Emails"),
    DEAL_EMAIL(ENotificationModule.CRM, "Deal Emails"),
    SUPPORT_TICKET_CREATED(ENotificationModule.SUPPORT, "New Ticket"),
    SUPPORT_TICKET_REPLY(ENotificationModule.SUPPORT, "New Ticket Reply"),
    APPLICATION_RECEIVED(ENotificationModule.RECRUITMENT, "Application Received"),
    OFFER_LETTER(ENotificationModule.RECRUITMENT, "Offer Letter"),
    // Express orders, to the customer (order-service email snapshot; customer-delivery-notification spec).
    ORDER_PAID(ENotificationModule.ORDER, "Order Paid"),
    ORDER_PICKED_UP(ENotificationModule.ORDER, "Order Picked Up"),
    ORDER_DELIVERED(ENotificationModule.ORDER, "Order Delivered"),
    ORDER_DELIVERY_CANCELLED(ENotificationModule.ORDER, "Order Delivery Cancelled"),
    // Back Office cancelled a paid order and refunded it (paid-order-cancel spec).
    ORDER_REFUNDED(ENotificationModule.ORDER, "Order Refunded"),
    // Back Office completed a customer return (order-return spec).
    ORDER_RETURNED(ENotificationModule.ORDER, "Order Returned");

    /** Groups of the Email Notification Settings page, in display order. */
    public enum ENotificationModule { GENERAL, CRM, SUPPORT, RECRUITMENT, SUBSCRIPTION, ORDER }

    private final ENotificationModule module;
    private final String displayName;
}
