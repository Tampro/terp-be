package com.terp.erp.iam.email;

/**
 * The super admin's Email Notifications switches (ERP manual 3.7). NEW_USER = {@link EEmailTemplate#USER_CREATE},
 * sent by IAM; the helpdesk ones are the emails to the platform team (platform.helpdesk.* events, sent by
 * notification-service to the addresses of PlatformEmailNotificationSettings.helpdeskNotifyEmails). Codes travel to
 * notification-service as the emailCode of GET /api/v1/internal/platform-notification-config: never rename one.
 */
public enum EPlatformEmail {
    NEW_USER, HELPDESK_TICKET_CREATED, HELPDESK_TICKET_REPLY
}
