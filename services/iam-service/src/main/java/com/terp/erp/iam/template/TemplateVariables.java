package com.terp.erp.iam.template;

import com.terp.erp.iam.email.ENotificationEvent;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {key} placeholders a company template may use: the event payload keys notification-service's built-in texts
 * (MessageTemplates) read today, i.e. the contract with the producing services. Keep in step with MessageTemplates
 * and EEventRoute of notification-service.
 */
public final class TemplateVariables {
    /** Same syntax as notification-service MessageTemplates: letters only, no expressions. */
    public static final Pattern KEY = Pattern.compile("\\{([A-Za-z]+)}");

    private static final Map<ENotificationEvent, List<String>> EMAIL = Map.ofEntries(
            Map.entry(ENotificationEvent.INVOICE_SENT, List.of("number", "customerName", "total", "dueDate")),
            Map.entry(ENotificationEvent.INVOICE_PAYMENT_CREATED,
                    List.of("number", "customerName", "amount", "paymentDate")),
            Map.entry(ENotificationEvent.SUPPORT_TICKET_CREATED, List.of("name", "ticketNumber", "subject")),
            Map.entry(ENotificationEvent.SUPPORT_TICKET_REPLY, List.of("ticketNumber", "subject")),
            Map.entry(ENotificationEvent.APPLICATION_RECEIVED, List.of("name", "jobTitle", "trackingId")),
            Map.entry(ENotificationEvent.LEAD_ASSIGNED, List.of("name", "subject")),
            Map.entry(ENotificationEvent.LEAD_MOVED, List.of("name", "fromStage", "toStage")),
            Map.entry(ENotificationEvent.DEAL_ASSIGNED, List.of("name")),
            Map.entry(ENotificationEvent.DEAL_MOVED, List.of("name", "fromStage", "toStage")),
            Map.entry(ENotificationEvent.HELPDESK_TICKET_CREATED, List.of("ticketNumber", "subject")),
            Map.entry(ENotificationEvent.HELPDESK_TICKET_REPLY, List.of("ticketNumber", "subject")),
            Map.entry(ENotificationEvent.PROPOSAL_STATUS_UPDATED, List.of("title", "status")),
            Map.entry(ENotificationEvent.PURCHASE_SENT, List.of("title", "vendorName", "total")),
            Map.entry(ENotificationEvent.PURCHASE_PAYMENT_CREATED,
                    List.of("number", "vendorName", "amount", "paymentDate")),
            Map.entry(ENotificationEvent.PAYMENT_REMINDER, List.of("title", "customerName", "balance", "dueDate")),
            Map.entry(ENotificationEvent.OFFER_LETTER, List.of("title", "letterText")),
            Map.entry(ENotificationEvent.CRM_TASK_CREATED, List.of("title", "date")),
            Map.entry(ENotificationEvent.LEAD_EMAIL, List.of("subject", "body")),
            Map.entry(ENotificationEvent.DEAL_EMAIL, List.of("subject", "body")),
            Map.entry(ENotificationEvent.ORDER_PAID, List.of("orderNo", "storeName", "recipientName", "total")),
            Map.entry(ENotificationEvent.ORDER_PICKED_UP, List.of("orderNo", "storeName", "recipientName")),
            Map.entry(ENotificationEvent.ORDER_DELIVERED, List.of("orderNo", "storeName", "recipientName")),
            Map.entry(ENotificationEvent.ORDER_DELIVERY_CANCELLED, List.of("orderNo", "storeName", "recipientName")),
            Map.entry(ENotificationEvent.ORDER_REFUNDED, List.of("orderNo", "storeName", "recipientName", "total")));
    // ORDER_* are not in EVENT_EMAIL: customer emails never go to the company's chat channels.

    /** Routing key to email code (notification-service EEventRoute.emailCode), for the chat variables. */
    private static final Map<String, ENotificationEvent> EVENT_EMAIL = Map.ofEntries(
            Map.entry("sales.invoice.posted", ENotificationEvent.INVOICE_SENT),
            Map.entry("sales.invoice.sent", ENotificationEvent.INVOICE_SENT),
            Map.entry("accounting.customer_payment.cleared", ENotificationEvent.INVOICE_PAYMENT_CREATED),
            Map.entry("support.ticket.created", ENotificationEvent.SUPPORT_TICKET_CREATED),
            Map.entry("support.ticket.replied", ENotificationEvent.SUPPORT_TICKET_REPLY),
            Map.entry("recruitment.application.received", ENotificationEvent.APPLICATION_RECEIVED),
            Map.entry("crm.lead.assigned", ENotificationEvent.LEAD_ASSIGNED),
            Map.entry("crm.lead.moved", ENotificationEvent.LEAD_MOVED),
            Map.entry("crm.deal.assigned", ENotificationEvent.DEAL_ASSIGNED),
            Map.entry("crm.deal.moved", ENotificationEvent.DEAL_MOVED),
            Map.entry("iam.helpdesk.ticket_created", ENotificationEvent.HELPDESK_TICKET_CREATED),
            Map.entry("iam.helpdesk.ticket_replied", ENotificationEvent.HELPDESK_TICKET_REPLY),
            Map.entry("sales.proposal.status_changed", ENotificationEvent.PROPOSAL_STATUS_UPDATED),
            Map.entry("purchase.invoice.sent", ENotificationEvent.PURCHASE_SENT),
            Map.entry("accounting.vendor_payment.cleared", ENotificationEvent.PURCHASE_PAYMENT_CREATED),
            Map.entry("accounting.invoice.payment_reminder", ENotificationEvent.PAYMENT_REMINDER),
            Map.entry("recruitment.offer.letter_sent", ENotificationEvent.OFFER_LETTER),
            Map.entry("crm.task.created", ENotificationEvent.CRM_TASK_CREATED));

    /** Never offered to a chat channel: the offer letter stays in the email. */
    private static final Set<String> CHAT_PRIVATE = Set.of("letterText");

    private TemplateVariables() {
    }

    public static List<String> email(ENotificationEvent code) {
        return EMAIL.getOrDefault(code, List.of());
    }

    /**
     * number / title (the built-in chat line's label) plus the event's email keys. Lead / deal emails offer no more:
     * their subject and body are the user's own text and never go to a chat channel.
     */
    public static List<String> chat(String eventType) {
        Set<String> out = new LinkedHashSet<>(List.of("number", "title"));
        Optional.ofNullable(EVENT_EMAIL.get(eventType))
                .ifPresent(e -> email(e).stream().filter(k -> !CHAT_PRIVATE.contains(k)).forEach(out::add));
        return new ArrayList<>(out);
    }

    /** The first placeholder of the text that is not in the allowed list, if any. */
    public static Optional<String> unknown(String text, List<String> allowed) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = KEY.matcher(text);
        while (m.find()) {
            if (!allowed.contains(m.group(1))) {
                return Optional.of(m.group(1));
            }
        }
        return Optional.empty();
    }

    /**
     * {key} to value, a key without a value becomes empty (same rule as notification-service). HTML bodies get the
     * value HTML-escaped; plain text gets it on one line so a value cannot add lines.
     */
    public static String fill(String template, Map<String, String> values, boolean html) {
        if (template == null) {
            return "";
        }
        Matcher m = KEY.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = values.getOrDefault(m.group(1), "");
            v = html ? HtmlUtils.htmlEscape(com.terp.erp.common.Nulls.nonNull(v)): v.replaceAll("[\\r\\n]+", " ");
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Map<String, String> samples(List<String> keys) {
        Map<String, String> m = new java.util.HashMap<>();
        keys.forEach(k -> m.put(k, "[" + k + "]"));
        return m;
    }
}
