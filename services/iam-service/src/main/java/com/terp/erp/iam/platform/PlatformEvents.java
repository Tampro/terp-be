package com.terp.erp.iam.platform;

import com.terp.erp.outbox.OutboxPublisher;

/**
 * Platform (super admin) events: published through IAM's outbox with companyId = null, the marker notification-
 * service uses to route them to the platform's webhooks and email recipients, never to a company's. The company
 * the event is about travels in the payload (companyId as a string). Payloads hold display fields only: no email
 * address, password or ticket text. Routing keys are the contract with notification-service EPlatformRoute.
 */
public final class PlatformEvents {
    public static final String COMPANY_CREATED = "platform.company.created";
    public static final String ORDER_CREATED = "platform.order.created";
    public static final String ORDER_APPROVED = "platform.order.approved";
    public static final String USER_CREATED = "platform.user.created";
    public static final String HELPDESK_TICKET_CREATED = "platform.helpdesk.ticket_created";
    public static final String HELPDESK_TICKET_REPLIED = "platform.helpdesk.ticket_replied";

    private PlatformEvents() {
    }

    /** Must run inside the business transaction (OutboxPublisher is MANDATORY). */
    public static void publish(OutboxPublisher outbox, String eventType, String aggregateType, Object aggregateId,
                               Object payload) {
        outbox.publish(null, eventType, aggregateType, aggregateId, payload);
    }
}
