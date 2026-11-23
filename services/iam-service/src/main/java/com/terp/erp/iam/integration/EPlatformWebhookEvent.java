package com.terp.erp.iam.integration;

import com.terp.erp.iam.platform.PlatformEvents;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Events a platform (super admin) webhook can subscribe to. The reference TERP Core webhook-events.php has one
 * "super admin" event, New User; the others are Proposal (ERP manual 3.7 names no list). Stored in
 * platform_webhook.event_code: never rename one. eventType = the outbox routing key IAM publishes.
 */
@Getter
@RequiredArgsConstructor
public enum EPlatformWebhookEvent {
    NEW_COMPANY("New Company", PlatformEvents.COMPANY_CREATED),
    NEW_ORDER("New Plan Order", PlatformEvents.ORDER_CREATED),
    ORDER_APPROVED("Plan Order Approved", PlatformEvents.ORDER_APPROVED),
    NEW_USER("New User", PlatformEvents.USER_CREATED),
    NEW_HELPDESK_TICKET("New Helpdesk Ticket", PlatformEvents.HELPDESK_TICKET_CREATED),
    NEW_HELPDESK_TICKET_REPLY("New Helpdesk Ticket Reply", PlatformEvents.HELPDESK_TICKET_REPLIED);

    private final String displayName;
    private final String eventType;
}
