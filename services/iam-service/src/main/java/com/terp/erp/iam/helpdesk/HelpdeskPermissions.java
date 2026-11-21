package com.terp.erp.iam.helpdesk;

import java.util.Set;

/**
 * Company-side permissions of the platform helpdesk; must match the Liquibase seed. Managing categories, every
 * company's tickets, status/priority and internal replies is super-admin only and has no code.
 */
public final class HelpdeskPermissions {
    /** See the company's own tickets and their conversation (internal replies excluded). */
    public static final String VIEW = "HELPDESK_VIEW";
    /** Open a ticket for the own company and reply to it. */
    public static final String CREATE = "HELPDESK_CREATE";

    public static final Set<String> ALL = Set.of(VIEW, CREATE);

    private HelpdeskPermissions() {
    }
}
