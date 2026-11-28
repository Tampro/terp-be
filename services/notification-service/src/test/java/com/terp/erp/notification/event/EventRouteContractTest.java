package com.terp.erp.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.notification.channel.MessageTemplates;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The route table against the shared event contract of feat/notification-events (eventType, webhook, email). */
class EventRouteContractTest {
    /** eventType | webhook code | email code ("-" = none): the contract table, then the earlier routes. */
    static final List<String[]> CONTRACT = List.of(
            row("iam.user.created", "NEW_USER", "-"),
            row("iam.helpdesk.ticket_created", "-", "HELPDESK_TICKET_CREATED"),
            row("iam.helpdesk.ticket_replied", "-", "HELPDESK_TICKET_REPLY"),
            row("sales.invoice.status_changed", "SALES_INVOICE_STATUS_UPDATED", "-"),
            row("sales.invoice.sent", "-", "INVOICE_SENT"),
            row("sales.proposal.created", "NEW_SALES_PROPOSAL", "-"),
            row("sales.proposal.status_changed", "SALES_PROPOSAL_STATUS_UPDATED", "PROPOSAL_STATUS_UPDATED"),
            row("purchase.invoice.sent", "-", "PURCHASE_SENT"),
            row("accounting.customer.created", "NEW_CUSTOMER", "-"),
            row("accounting.vendor.created", "NEW_VENDOR", "-"),
            row("accounting.revenue.created", "NEW_REVENUE", "-"),
            row("accounting.vendor_payment.cleared", "-", "PURCHASE_PAYMENT_CREATED"),
            row("accounting.invoice.payment_reminder", "-", "PAYMENT_REMINDER"),
            row("inventory.warehouse.created", "NEW_WAREHOUSE", "-"),
            row("employee.training.created", "NEW_TRAINING", "-"),
            row("employee.trainer.created", "NEW_TRAINER", "-"),
            row("employee.award.created", "NEW_AWARD", "-"),
            row("employee.announcement.published", "NEW_ANNOUNCEMENT", "-"),
            row("employee.holiday.created", "NEW_HOLIDAY", "-"),
            row("meeting.zoom.created", "NEW_ZOOM_MEETING", "-"),
            row("meeting.event.created", "NEW_EVENT", "-"),
            row("recruitment.job_posting.published", "NEW_JOB_POSTING", "-"),
            row("recruitment.interview.scheduled", "NEW_INTERVIEW_SCHEDULE", "-"),
            row("recruitment.candidate.converted", "CANDIDATE_CONVERTED_TO_EMPLOYEE", "-"),
            row("recruitment.offer.letter_sent", "-", "OFFER_LETTER"),
            row("project.project.created", "NEW_PROJECT", "-"),
            row("project.milestone.created", "NEW_MILESTONE", "-"),
            row("project.task.created", "NEW_TASK", "-"),
            row("project.task.moved", "TASK_STAGE_UPDATED", "-"),
            row("project.task.commented", "NEW_TASK_COMMENT", "-"),
            row("project.bug.created", "NEW_BUG", "-"),
            row("crm.lead.created", "NEW_LEAD", "-"),
            row("crm.deal.created", "NEW_DEAL", "-"),
            row("crm.lead.converted", "LEAD_CONVERTED_TO_DEAL", "-"),
            row("crm.contract.created", "NEW_CONTRACT", "-"),
            row("crm.task.created", "-", "CRM_TASK_CREATED"),
            row("crm.lead.emailed", "-", "LEAD_EMAIL"),
            row("crm.deal.emailed", "-", "DEAL_EMAIL"),
            // routed before this branch, unchanged
            row("sales.invoice.posted", "NEW_SALES_INVOICE", "INVOICE_SENT"),
            row("purchase.invoice.posted", "NEW_PURCHASE_INVOICE", "-"),
            row("accounting.customer_payment.cleared", "-", "INVOICE_PAYMENT_CREATED"),
            row("support.ticket.created", "-", "SUPPORT_TICKET_CREATED"),
            row("support.ticket.replied", "-", "SUPPORT_TICKET_REPLY"),
            row("recruitment.application.received", "NEW_JOB_CANDIDATE", "APPLICATION_RECEIVED"),
            row("crm.lead.assigned", "-", "LEAD_ASSIGNED"),
            row("crm.lead.moved", "LEAD_MOVED", "LEAD_MOVED"),
            row("crm.deal.assigned", "-", "DEAL_ASSIGNED"),
            row("crm.deal.moved", "DEAL_MOVED", "DEAL_MOVED"),
            // feat/google-calendar: Google Calendar only
            row("employee.holiday.updated", "-", "-"),
            row("employee.holiday.deleted", "-", "-"),
            // customer-delivery-notification: Express customer emails (delivery: code chosen by data.status)
            row("order.paid", "-", "ORDER_PAID"),
            row("delivery.status.changed", "-", "ORDER_DELIVERED"));

    private static String[] row(String type, String webhook, String email) {
        return new String[]{type, webhook, email};
    }

    private static String code(String v) {
        return "-".equals(v) ? null : v;
    }

    @Test
    void everyContractEventIsRoutedExactlyAsTheTableSays() {
        for (String[] r : CONTRACT) {
            EEventRoute route = EEventRoute.of(r[0]).orElseThrow(() -> new AssertionError("not routed: " + r[0]));
            assertThat(route.webhookCode).as(r[0] + " webhook").isEqualTo(code(r[1]));
            assertThat(route.emailCode).as(r[0] + " email").isEqualTo(code(r[2]));
            if (route.webhookCode != null) {
                assertThat(route.webhookName).as(r[0] + " webhook name").isNotBlank();
                assertThat(route.webhookModule).as(r[0] + " webhook module").isNotBlank();
            }
        }
        assertThat(EEventRoute.values()).hasSize(CONTRACT.size());
        assertThat(Arrays.stream(EEventRoute.values()).map(r -> r.eventType).distinct()).hasSize(CONTRACT.size());
    }

    @Test
    void everyEmailCodeHasATemplateInBothLanguagesAndEveryRouteAChatLine() {
        var data = new ObjectMapper().createObjectNode().put("title", "DOC-1").put("number", "DOC-1")
                .put("ticketNumber", "00000001").put("subject", "Test subject").put("body", "Test body")
                .put("letterText", "Dear Test Candidate, ...");
        for (EEventRoute r : EEventRoute.values()) {
            if (r.emailCode != null) {
                assertThat(MessageTemplates.hasTemplate(r.emailCode)).as(r.emailCode).isTrue();
                for (String lang : List.of("en", "vi")) {
                    MessageTemplates.Message m = MessageTemplates.email(r.emailCode, lang, data);
                    assertThat(m.subject()).as(r.emailCode + " " + lang).isNotBlank();
                    assertThat(m.body()).as(r.emailCode + " " + lang).isNotBlank();
                }
            }
            assertThat(MessageTemplates.chat(r, "en", data)).as(r.eventType).startsWith("[ERP] ")
                    .doesNotContain("\n");
        }
    }

    @Test
    void anOfferLetterBodyIsTheRenderedLetterAndPrivateEmailsStayOutOfChat() {
        var data = new ObjectMapper().createObjectNode().put("title", "Offer OF-1")
                .put("letterText", "Dear Test Candidate,\nwe are pleased to offer you the position.")
                .put("subject", "Private subject").put("body", "Private body");
        assertThat(MessageTemplates.email("OFFER_LETTER", "en", data).body())
                .isEqualTo("Dear Test Candidate,\nwe are pleased to offer you the position.");
        assertThat(MessageTemplates.chat(EEventRoute.LEAD_EMAILED, "en", data)).isEqualTo("[ERP] Lead email sent Offer OF-1");
    }
}
