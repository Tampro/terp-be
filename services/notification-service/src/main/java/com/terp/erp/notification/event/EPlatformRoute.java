package com.terp.erp.notification.event;

import java.util.Arrays;
import java.util.Optional;

/**
 * Platform events (IAM PlatformEvents, published with companyId = null) → IAM platform catalog codes: emailCode =
 * EPlatformEmail (the super admin's switch; recipients = the platform team addresses), templateKey = the built-in
 * text (MessageTemplates), webhookCode = EPlatformWebhookEvent (the super admin's webhooks). Their deliveries carry
 * companyId null and are listed to super admins only. Never routed to a company's channels.
 */
public enum EPlatformRoute {
    COMPANY_CREATED("platform.company.created", null, null, "NEW_COMPANY", "New Company"),
    ORDER_CREATED("platform.order.created", null, null, "NEW_ORDER", "New Plan Order"),
    ORDER_APPROVED("platform.order.approved", null, null, "ORDER_APPROVED", "Plan Order Approved"),
    USER_CREATED("platform.user.created", null, null, "NEW_USER", "New User"),
    HELPDESK_TICKET_CREATED("platform.helpdesk.ticket_created", "HELPDESK_TICKET_CREATED",
            "PLATFORM_HELPDESK_TICKET_CREATED", "NEW_HELPDESK_TICKET", "New Helpdesk Ticket"),
    HELPDESK_TICKET_REPLIED("platform.helpdesk.ticket_replied", "HELPDESK_TICKET_REPLY",
            "PLATFORM_HELPDESK_TICKET_REPLY", "NEW_HELPDESK_TICKET_REPLY", "New Helpdesk Ticket Reply");

    public static final String PREFIX = "platform.";
    public static final String MODULE = "PLATFORM";

    public final String eventType;
    public final String emailCode;
    public final String templateKey;
    public final String webhookCode;
    public final String webhookName;

    EPlatformRoute(String eventType, String emailCode, String templateKey, String webhookCode, String webhookName) {
        this.eventType = eventType;
        this.emailCode = emailCode;
        this.templateKey = templateKey;
        this.webhookCode = webhookCode;
        this.webhookName = webhookName;
    }

    public static Optional<EPlatformRoute> of(String eventType) {
        return Arrays.stream(values()).filter(r -> r.eventType.equals(eventType)).findFirst();
    }
}
