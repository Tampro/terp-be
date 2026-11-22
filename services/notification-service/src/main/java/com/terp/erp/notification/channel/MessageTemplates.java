package com.terp.erp.notification.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.notification.event.EEventRoute;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain-text subject + body per email code, in English and Vietnamese, with {key} filled from the event's data.
 * Language: the company's default language when it is en or vi; vi when none; en for any other language.
 * TODO(assumption): short built-in texts; the company-editable templates of IAM (EmailTemplate) cover only the
 * platform emails today — use them here once BA writes the business event templates.
 */
public final class MessageTemplates {
    public record Message(String subject, String body) {
    }

    private record Text(String subjectEn, String bodyEn, String subjectVi, String bodyVi) {
    }

    private static final Pattern KEY = Pattern.compile("\\{([A-Za-z]+)}");

    private static final Map<String, Text> TEXTS = Map.of(
            "INVOICE_SENT", new Text("Invoice {number}",
                    "Invoice {number} for {customerName} has been issued.\nTotal: {total}\nDue date: {dueDate}",
                    "Hóa đơn {number}",
                    "Hóa đơn {number} cho {customerName} đã được phát hành.\nTổng tiền: {total}\nHạn thanh toán: {dueDate}"),
            "INVOICE_PAYMENT_CREATED", new Text("Payment {number} received",
                    "Payment {number} from {customerName} has been recorded.\nAmount: {amount}\nDate: {paymentDate}",
                    "Đã nhận thanh toán {number}",
                    "Đã ghi nhận thanh toán {number} của {customerName}.\nSố tiền: {amount}\nNgày: {paymentDate}"),
            "SUPPORT_TICKET_CREATED", new Text("Ticket {ticketNumber}: {subject}",
                    "Hello {name},\nyour support ticket {ticketNumber} \"{subject}\" has been received.",
                    "Phiếu hỗ trợ {ticketNumber}: {subject}",
                    "Xin chào {name},\nphiếu hỗ trợ {ticketNumber} \"{subject}\" đã được tiếp nhận."),
            "SUPPORT_TICKET_REPLY", new Text("New reply on ticket {ticketNumber}",
                    "There is a new reply on your support ticket {ticketNumber} \"{subject}\".",
                    "Phản hồi mới cho phiếu {ticketNumber}",
                    "Phiếu hỗ trợ {ticketNumber} \"{subject}\" có phản hồi mới."),
            "APPLICATION_RECEIVED", new Text("Application received: {jobTitle}",
                    "Hello {name},\nyour application for {jobTitle} has been received.\nTracking id: {trackingId}",
                    "Đã nhận hồ sơ ứng tuyển: {jobTitle}",
                    "Xin chào {name},\nhồ sơ ứng tuyển vị trí {jobTitle} đã được tiếp nhận.\nMã theo dõi: {trackingId}"),
            "LEAD_ASSIGNED", new Text("Lead assigned: {name}",
                    "The lead {name} ({subject}) has been assigned to you.",
                    "Được giao khách hàng tiềm năng: {name}",
                    "Khách hàng tiềm năng {name} ({subject}) đã được giao cho bạn."),
            "LEAD_MOVED", new Text("Lead {name} moved to {toStage}",
                    "The lead {name} moved from {fromStage} to {toStage}.",
                    "Khách hàng tiềm năng {name} chuyển sang {toStage}",
                    "Khách hàng tiềm năng {name} đã chuyển từ {fromStage} sang {toStage}."),
            "DEAL_ASSIGNED", new Text("Deal assigned: {name}",
                    "The deal {name} has been assigned to you.",
                    "Được giao cơ hội: {name}",
                    "Cơ hội {name} đã được giao cho bạn."),
            "DEAL_MOVED", new Text("Deal {name} moved to {toStage}",
                    "The deal {name} moved from {fromStage} to {toStage}.",
                    "Cơ hội {name} chuyển sang {toStage}",
                    "Cơ hội {name} đã chuyển từ {fromStage} sang {toStage}."));

    private MessageTemplates() {
    }

    public static boolean vietnamese(String language) {
        return language == null || language.isBlank() || language.toLowerCase().startsWith("vi");
    }

    public static Message email(String emailCode, String language, JsonNode data) {
        Text t = TEXTS.get(emailCode);
        if (t == null) {
            return new Message(emailCode, "");
        }
        boolean vi = vietnamese(language);
        return new Message(oneLine(fill(vi ? t.subjectVi() : t.subjectEn(), data)),
                fill(vi ? t.bodyVi() : t.bodyEn(), data));
    }

    /** One line for Slack / Telegram: the email subject, else the webhook event name with the document number. */
    public static String chat(EEventRoute route, String language, JsonNode data) {
        if (route.emailCode != null && TEXTS.containsKey(route.emailCode)) {
            return "[ERP] " + email(route.emailCode, language, data).subject();
        }
        String number = data == null ? "" : data.path("number").asText("");
        return oneLine("[ERP] " + (route.webhookName == null ? route.eventType : route.webhookName)
                + (number.isEmpty() ? "" : " " + number));
    }

    static String fill(String template, JsonNode data) {
        Matcher m = KEY.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = data == null ? "" : data.path(m.group(1)).asText("");
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** A value can never add a header line or break the one-line chat text. */
    private static String oneLine(String s) {
        return s.replaceAll("[\\r\\n]+", " ").trim();
    }
}
