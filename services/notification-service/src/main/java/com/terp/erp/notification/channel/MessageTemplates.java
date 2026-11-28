package com.terp.erp.notification.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.notification.event.EEventRoute;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain-text subject + body per email code, in English and Vietnamese, with {key} filled from the event's data.
 * Language: the company's default language when it is en or vi; vi when none; en for any other language.
 * These are the defaults: a company's own texts (IAM company_notification_template, sent in the notification-config
 * answer) replace them through customEmail / customChat. TODO(assumption): short built-in texts until BA writes them.
 */
public final class MessageTemplates {
    public record Message(String subject, String body) {
    }

    private record Text(String subjectEn, String bodyEn, String subjectVi, String bodyVi) {
    }

    private static final Pattern KEY = Pattern.compile("\\{([A-Za-z]+)}");

    private static final Map<String, Text> TEXTS = Map.ofEntries(
            Map.entry("INVOICE_SENT", new Text("Invoice {number}",
                    "Invoice {number} for {customerName} has been issued.\nTotal: {total}\nDue date: {dueDate}",
                    "Hóa đơn {number}",
                    "Hóa đơn {number} cho {customerName} đã được phát hành.\nTổng tiền: {total}\nHạn thanh toán: {dueDate}")),
            Map.entry("INVOICE_PAYMENT_CREATED", new Text("Payment {number} received",
                    "Payment {number} from {customerName} has been recorded.\nAmount: {amount}\nDate: {paymentDate}",
                    "Đã nhận thanh toán {number}",
                    "Đã ghi nhận thanh toán {number} của {customerName}.\nSố tiền: {amount}\nNgày: {paymentDate}")),
            Map.entry("SUPPORT_TICKET_CREATED", new Text("Ticket {ticketNumber}: {subject}",
                    "Hello {name},\nyour support ticket {ticketNumber} \"{subject}\" has been received.",
                    "Phiếu hỗ trợ {ticketNumber}: {subject}",
                    "Xin chào {name},\nphiếu hỗ trợ {ticketNumber} \"{subject}\" đã được tiếp nhận.")),
            Map.entry("SUPPORT_TICKET_REPLY", new Text("New reply on ticket {ticketNumber}",
                    "There is a new reply on your support ticket {ticketNumber} \"{subject}\".",
                    "Phản hồi mới cho phiếu {ticketNumber}",
                    "Phiếu hỗ trợ {ticketNumber} \"{subject}\" có phản hồi mới.")),
            Map.entry("APPLICATION_RECEIVED", new Text("Application received: {jobTitle}",
                    "Hello {name},\nyour application for {jobTitle} has been received.\nTracking id: {trackingId}",
                    "Đã nhận hồ sơ ứng tuyển: {jobTitle}",
                    "Xin chào {name},\nhồ sơ ứng tuyển vị trí {jobTitle} đã được tiếp nhận.\nMã theo dõi: {trackingId}")),
            Map.entry("LEAD_ASSIGNED", new Text("Lead assigned: {name}",
                    "The lead {name} ({subject}) has been assigned to you.",
                    "Được giao khách hàng tiềm năng: {name}",
                    "Khách hàng tiềm năng {name} ({subject}) đã được giao cho bạn.")),
            Map.entry("LEAD_MOVED", new Text("Lead {name} moved to {toStage}",
                    "The lead {name} moved from {fromStage} to {toStage}.",
                    "Khách hàng tiềm năng {name} chuyển sang {toStage}",
                    "Khách hàng tiềm năng {name} đã chuyển từ {fromStage} sang {toStage}.")),
            Map.entry("DEAL_ASSIGNED", new Text("Deal assigned: {name}",
                    "The deal {name} has been assigned to you.",
                    "Được giao cơ hội: {name}",
                    "Cơ hội {name} đã được giao cho bạn.")),
            Map.entry("DEAL_MOVED", new Text("Deal {name} moved to {toStage}",
                    "The deal {name} moved from {fromStage} to {toStage}.",
                    "Cơ hội {name} chuyển sang {toStage}",
                    "Cơ hội {name} đã chuyển từ {fromStage} sang {toStage}.")),
            Map.entry("HELPDESK_TICKET_CREATED", new Text("Helpdesk ticket {ticketNumber}: {subject}",
                    "Your helpdesk ticket {ticketNumber} \"{subject}\" has been received by the platform team.",
                    "Phiếu hỗ trợ nền tảng {ticketNumber}: {subject}",
                    "Phiếu hỗ trợ {ticketNumber} \"{subject}\" đã được đội ngũ nền tảng tiếp nhận.")),
            Map.entry("HELPDESK_TICKET_REPLY", new Text("New reply on helpdesk ticket {ticketNumber}",
                    "There is a new reply on the helpdesk ticket {ticketNumber} \"{subject}\".",
                    "Phản hồi mới cho phiếu hỗ trợ {ticketNumber}",
                    "Phiếu hỗ trợ {ticketNumber} \"{subject}\" có phản hồi mới.")),
            // To the platform team (helpdeskNotifyEmails), not to the company: platform.helpdesk.* events.
            Map.entry("PLATFORM_HELPDESK_TICKET_CREATED", new Text("New helpdesk ticket {ticketNumber}: {subject}",
                    "{companyName} opened the helpdesk ticket {ticketNumber} \"{subject}\".",
                    "Phiếu hỗ trợ mới {ticketNumber}: {subject}",
                    "{companyName} đã mở phiếu hỗ trợ {ticketNumber} \"{subject}\".")),
            Map.entry("PLATFORM_HELPDESK_TICKET_REPLY", new Text("New reply on helpdesk ticket {ticketNumber}",
                    "{companyName} replied on the helpdesk ticket {ticketNumber} \"{subject}\".",
                    "Phản hồi mới cho phiếu hỗ trợ {ticketNumber}",
                    "{companyName} đã phản hồi phiếu hỗ trợ {ticketNumber} \"{subject}\".")),
            Map.entry("PROPOSAL_STATUS_UPDATED", new Text("Proposal {title}: {status}",
                    "The proposal {title} is now {status}.",
                    "Đề xuất {title}: {status}",
                    "Đề xuất {title} đã chuyển sang trạng thái {status}.")),
            Map.entry("PURCHASE_SENT", new Text("Purchase invoice {title}",
                    "Purchase invoice {title} has been sent to {vendorName}.\nTotal: {total}",
                    "Hóa đơn mua hàng {title}",
                    "Hóa đơn mua hàng {title} đã được gửi tới {vendorName}.\nTổng tiền: {total}")),
            Map.entry("PURCHASE_PAYMENT_CREATED", new Text("Payment {number} sent",
                    "Payment {number} to {vendorName} has been recorded.\nAmount: {amount}\nDate: {paymentDate}",
                    "Đã thanh toán {number}",
                    "Đã ghi nhận khoản thanh toán {number} cho {vendorName}.\nSố tiền: {amount}\nNgày: {paymentDate}")),
            Map.entry("PAYMENT_REMINDER", new Text("Payment reminder: invoice {title}",
                    "Invoice {title} for {customerName} is overdue.\nBalance due: {balance}\nDue date: {dueDate}",
                    "Nhắc thanh toán hóa đơn {title}",
                    "Hóa đơn {title} của {customerName} đã quá hạn.\nSố tiền còn lại: {balance}\nHạn thanh toán: {dueDate}")),
            // The body is the letter the recruiter rendered from the offer template (payload letterText).
            Map.entry("OFFER_LETTER", new Text("Offer letter: {title}", "{letterText}",
                    "Thư mời nhận việc: {title}", "{letterText}")),
            Map.entry("CRM_TASK_CREATED", new Text("New CRM task: {title}",
                    "The CRM task {title} has been assigned to you.\nDue date: {date}",
                    "Công việc CRM mới: {title}",
                    "Công việc CRM {title} đã được giao cho bạn.\nHạn: {date}")),
            Map.entry("LEAD_EMAIL", new Text("{subject}", "{body}", "{subject}", "{body}")),
            Map.entry("DEAL_EMAIL", new Text("{subject}", "{body}", "{subject}", "{body}")),
            // Express customer emails. TODO(assumption): no Korean built-in text yet (the table is en + vi); a company
            // writes its own ko template on the template screen.
            Map.entry("ORDER_PAID", new Text("Order {orderNo} confirmed",
                    "Hello {recipientName},\nthank you for your order {orderNo} at {storeName}.\nTotal: {total}",
                    "Đã xác nhận đơn {orderNo}",
                    "Xin chào {recipientName},\ncảm ơn bạn đã đặt đơn {orderNo} tại {storeName}.\nTổng tiền: {total}")),
            Map.entry("ORDER_PICKED_UP", new Text("Order {orderNo} is on its way",
                    "Hello {recipientName},\nyour order {orderNo} has left {storeName} and is on its way.",
                    "Đơn {orderNo} đang được giao",
                    "Xin chào {recipientName},\nđơn {orderNo} đã rời {storeName} và đang trên đường giao.")),
            Map.entry("ORDER_DELIVERED", new Text("Order {orderNo} delivered",
                    "Hello {recipientName},\nyour order {orderNo} from {storeName} has been delivered.",
                    "Đơn {orderNo} đã giao",
                    "Xin chào {recipientName},\nđơn {orderNo} từ {storeName} đã được giao.")),
            Map.entry("ORDER_DELIVERY_CANCELLED", new Text("Delivery of order {orderNo} cancelled",
                    "Hello {recipientName},\nthe delivery of your order {orderNo} from {storeName} has been cancelled. "
                            + "The store will contact you.",
                    "Đã huỷ giao đơn {orderNo}",
                    "Xin chào {recipientName},\nviệc giao đơn {orderNo} từ {storeName} đã bị huỷ. Cửa hàng sẽ liên hệ với bạn.")),
            Map.entry("ORDER_REFUNDED", new Text("Order {orderNo} cancelled and refunded",
                    "Hello {recipientName},\nyour order {orderNo} from {storeName} has been cancelled.\nRefunded: {total}",
                    "Đã huỷ và hoàn tiền đơn {orderNo}",
                    "Xin chào {recipientName},\nđơn {orderNo} từ {storeName} đã bị huỷ.\nSố tiền hoàn: {total}")));

    private MessageTemplates() {
    }

    /** A built-in text exists for this email code (en + vi). */
    public static boolean hasTemplate(String emailCode) {
        return TEXTS.containsKey(emailCode);
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

    /**
     * One line for Slack / Telegram / SMS: the email subject, else the webhook event name with the document number
     * (or the payload title). LEAD_EMAIL / DEAL_EMAIL never put the email's own subject in a chat channel.
     */
    public static String chat(EEventRoute route, String language, JsonNode data) {
        if (route.emailCode != null && TEXTS.containsKey(route.emailCode) && !CHAT_PRIVATE.contains(route.emailCode)) {
            String subject = email(route.emailCode, language, data).subject();
            if (!subject.isBlank()) {
                return "[ERP] " + subject;
            }
        }
        String label = data == null ? "" : data.path("number").asText("");
        if (label.isBlank() && data != null) {
            label = data.path("title").asText("");
        }
        String name = route.webhookName != null ? route.webhookName
                : CHAT_NAMES.getOrDefault(route.eventType, route.eventType);
        return oneLine("[ERP] " + name + (label.isBlank() ? "" : " " + label));
    }

    /** Email codes whose subject is the user's own text: the chat line uses a neutral name instead. */
    private static final java.util.Set<String> CHAT_PRIVATE = java.util.Set.of("LEAD_EMAIL", "DEAL_EMAIL");

    /** Chat names of routes without a webhook name (email-only events whose subject cannot be used). */
    private static final Map<String, String> CHAT_NAMES = Map.of(
            "crm.lead.emailed", "Lead email sent",
            "crm.deal.emailed", "Deal email sent");

    /**
     * A company's own email (IAM company_notification_template, already sanitised HTML there): {key} from the event
     * data, a missing key = empty, values HTML-escaped in the body and kept on one line in the subject. No
     * expressions: a value is never read as a placeholder or markup.
     */
    public static Message customEmail(String subject, String htmlBody, JsonNode data) {
        return new Message(oneLine(fill(subject == null ? "" : subject, data)), fillHtml(htmlBody, data));
    }

    /** A company's own Slack / Telegram / SMS text: values on one line, the text's own line breaks kept. */
    public static String customChat(String text, JsonNode data) {
        Matcher m = KEY.matcher(text == null ? "" : text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = data == null ? "" : data.path(m.group(1)).asText("");
            m.appendReplacement(sb, Matcher.quoteReplacement(v.replaceAll("[\\r\\n]+", " ")));
        }
        m.appendTail(sb);
        return sb.toString().trim();
    }

    static String fillHtml(String template, JsonNode data) {
        Matcher m = KEY.matcher(template == null ? "" : template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = data == null ? "" : data.path(m.group(1)).asText("");
            m.appendReplacement(sb, Matcher.quoteReplacement(HtmlUtils.htmlEscape(v)));
        }
        m.appendTail(sb);
        return sb.toString();
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
