package com.terp.erp.iam.constant;

import java.util.HashSet;
import java.util.Set;

/**
 * Support and communication permissions checked by support-service (ERP manual 18); must match the Liquibase seed
 * 20261122800000_support_permission_init_data. The platform helpdesk (HELPDESK_*) stays in IAM.
 */
public final class SupportPermissions {
    /** Tickets, ticket categories, knowledge base + FAQ, contact messages, portal settings + custom pages. */
    public static final Set<String> SUPPORT = Set.of("SUPPORT_TICKET_VIEW", "SUPPORT_TICKET_MANAGE",
            "SUPPORT_KB_VIEW", "SUPPORT_KB_MANAGE", "SUPPORT_CONTACT_VIEW", "SUPPORT_CONTACT_MANAGE",
            "SUPPORT_SETTINGS_MANAGE");

    /** One-to-one chat between users of the same company. */
    public static final String MESSENGER_USE = "MESSENGER_USE";
    public static final Set<String> MESSENGER = Set.of(MESSENGER_USE);

    public static final Set<String> ALL;

    static {
        Set<String> all = new HashSet<>(SUPPORT);
        all.addAll(MESSENGER);
        ALL = Set.copyOf(all);
    }

    private SupportPermissions() {
    }
}
