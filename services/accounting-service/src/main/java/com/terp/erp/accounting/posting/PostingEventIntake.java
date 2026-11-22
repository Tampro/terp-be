package com.terp.erp.accounting.posting;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.accounting.common.SystemActor;
import com.terp.erp.accounting.posting.DocumentPostingService.ESource;
import com.terp.erp.accounting.posting.DocumentPostingService.PostingDto;
import com.terp.erp.accounting.posting.DocumentPostingService.PostingForm;
import com.terp.erp.outbox.EventEnvelope;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * commerce.journal.requested → the document's journal entry, once per eventId (processed_event) and once per
 * (company, sourceType, sourceId) (the journal's own idempotency, so a re-post after FAILED never doubles it). The
 * result goes back to commerce through this service's outbox, in the same transaction:
 * accounting.journal.posted, or — when accounting refuses the entry for a business reason — accounting.journal.failed
 * from {@link #fail} in a new transaction (the posting one rolled back).
 * Runs as the system: the company comes from the event, the actor (created_by) from its actorSub; no permission
 * check here — commerce enforced SALES_POST / PURCHASE_POST before publishing.
 */
@Service
@RequiredArgsConstructor
public class PostingEventIntake {
    public static final String EVENT_REQUESTED = "commerce.journal.requested";
    public static final String EVENT_POSTED = "accounting.journal.posted";
    public static final String EVENT_FAILED = "accounting.journal.failed";

    private final ProcessedEventRepository processed;
    private final DocumentPostingService postings;
    private final OutboxPublisher outbox;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isProcessed(String eventId) {
        return eventId != null && processed.existsById(eventId);
    }

    /** Business refusals (ApiException: ACC_070 closed year, ACC_017 unmapped account…) are thrown to the caller. */
    @Transactional
    public void post(EventEnvelope e) {
        PostingForm f = form(e);
        if (processed.existsById(e.eventId())) {
            return;
        }
        PostingDto r = SystemActor.as(e.data().path("actorSub").asText(null), () -> postings.postFor(e.companyId(), f));
        record(e, "POSTED");
        outbox.publish(e.companyId(), EVENT_POSTED, f.sourceType().name(), f.sourceId(), Payload.of(
                "sourceType", f.sourceType().name(), "sourceId", f.sourceId(), "journalEntryId", r.journalEntryId(),
                "journalNumber", r.journalNumber(), "requestEventId", e.eventId()));
    }

    /** The refusal, recorded and sent back once: commerce marks the document journalStatus FAILED with the code. */
    @Transactional
    public void fail(EventEnvelope e, String errorCode) {
        PostingForm f = form(e);
        if (processed.existsById(e.eventId())) {
            return;
        }
        record(e, "FAILED:" + errorCode);
        outbox.publish(e.companyId(), EVENT_FAILED, f.sourceType().name(), f.sourceId(), Payload.of(
                "sourceType", f.sourceType().name(), "sourceId", f.sourceId(), "errorCode", errorCode,
                "requestEventId", e.eventId()));
    }

    private void record(EventEnvelope e, String outcome) {
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId(e.eventId());
        pe.setCompanyId(e.companyId());
        pe.setEventType(e.eventType());
        pe.setOutcome(outcome.length() > 40 ? outcome.substring(0, 40) : outcome);
        pe.setReceivedAt(Instant.now(clock));
        processed.saveAndFlush(pe);
    }

    /** The payload = the body of POST /journal-postings; an unreadable one can never succeed: straight to the DLQ. */
    static PostingForm form(EventEnvelope e) {
        if (e == null || e.eventId() == null || e.companyId() == null || e.data() == null) {
            throw new AmqpRejectAndDontRequeueException("malformed posting event");
        }
        JsonNode d = e.data();
        try {
            ESource source = ESource.valueOf(d.path("sourceType").asText(""));
            long sourceId = Long.parseLong(d.path("sourceId").asText(""));
            LocalDate date = LocalDate.parse(d.path("date").asText(""));
            return new PostingForm(source, sourceId, date, d.path("reference").asText(null),
                    d.path("description").asText(null), amount(d.path("goodsAmount")),
                    amount(d.path("serviceAmount")), amount(d.path("taxAmount")), amount(d.path("costAmount")),
                    false);
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new AmqpRejectAndDontRequeueException("unreadable posting event " + e.eventId());
        }
    }

    private static BigDecimal amount(JsonNode n) {
        if (n.isMissingNode() || n.isNull()) {
            return null;
        }
        BigDecimal v = new BigDecimal(n.asText());
        if (v.signum() < 0) {
            throw new IllegalArgumentException("negative amount");
        }
        return v;
    }
}
