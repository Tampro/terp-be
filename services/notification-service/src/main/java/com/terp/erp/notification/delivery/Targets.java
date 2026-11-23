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

    /** "*123" (last 3 digits); null when it has fewer than 3 digits. */
    public static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.length() < 3 ? null : "*" + digits.substring(digits.length() - 3);
    }

    /** "abcd…@…": enough to tell two calendars apart, not the whole id (it names a person or a team). */
    public static String maskCalendarId(String calendarId) {
        if (calendarId == null || calendarId.isBlank()) {
            return null;
        }
        String id = calendarId.trim();
        return id.substring(0, Math.min(4, id.length())) + "…@…";
    }
}
