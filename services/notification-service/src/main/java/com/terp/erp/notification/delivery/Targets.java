package com.terp.erp.notification.delivery;

/** What the delivery log may show of a target: never a full address or URL (they are PII or carry secrets). */
public final class Targets {
    private Targets() {
    }

    /** "*@example.com"; null when it is not an address. */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.lastIndexOf('@');
        return at < 0 || at == email.length() - 1 ? null : "*@" + email.substring(at + 1).trim().toLowerCase();
    }
}
