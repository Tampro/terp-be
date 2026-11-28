package com.terp.erp.order.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.terp.erp.order.order.CustomerOrder;
import com.terp.erp.order.order.CustomerOrderRepository;
import com.terp.erp.order.order.EJournalStatus;
import com.terp.erp.order.order.OrderJournal;
import com.terp.erp.outbox.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Applies accounting's journal result to a paid order, once: the processed_event row (key = eventId) and the status
 * change commit together. No user: the company comes from the envelope and the order must belong to it. Results of
 * other source types (commerce documents) share the routing keys and are ignored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingEventIntake {
    public static final String JOURNAL_POSTED = "accounting.journal.posted";
    public static final String JOURNAL_FAILED = "accounting.journal.failed";
    /** Routing keys bound to erp.order.accounting. */
    public static final List<String> ROUTING_KEYS = List.of(JOURNAL_POSTED, JOURNAL_FAILED);

    private final ProcessedEventRepository processed;
    private final CustomerOrderRepository orders;
    private final Clock clock;

    @Transactional(readOnly = true)
    public boolean isProcessed(String eventId) {
        return eventId != null && processed.existsById(eventId);
    }

    @Transactional
    public void accept(EventEnvelope e) {
        if (e == null || e.eventId() == null || e.eventType() == null || e.companyId() == null || e.data() == null) {
            throw new AmqpRejectAndDontRequeueException("malformed event");
        }
        if (!OrderJournal.SOURCE_TYPE.equals(e.data().path("sourceType").asText(""))
                || !ROUTING_KEYS.contains(e.eventType()) || processed.existsById(e.eventId())) {
            return;
        }
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId(e.eventId());
        pe.setCompanyId(e.companyId());
        pe.setEventType(e.eventType());
        pe.setReceivedAt(Instant.now(clock));
        processed.saveAndFlush(pe);
        Long orderId = id(e.data().path("sourceId"));
        CustomerOrder order = orderId == null ? null : orders.findById(orderId)
                .filter(o -> e.companyId().equals(o.getCompanyId())).orElse(null);
        if (order == null) {
            log.warn("Journal result {} for unknown order {} ignored", e.eventId(), orderId);
            return;
        }
        // POSTED is final: a late "failed" after a successful retry changes nothing.
        if (JOURNAL_POSTED.equals(e.eventType())) {
            order.setJournalStatus(EJournalStatus.POSTED);
            order.setJournalEntryNo(e.data().path("journalNumber").asText(null));
            order.setJournalError(null);
        } else if (order.getJournalStatus() != EJournalStatus.POSTED) {
            order.setJournalStatus(EJournalStatus.FAILED);
            order.setJournalError(e.data().path("errorCode").asText(null));
        }
        orders.save(order);
    }

    private static Long id(JsonNode n) {
        String v = n == null ? "" : n.asText("");
        return v.matches("[0-9]{1,19}") ? Long.valueOf(v) : null;
    }
}
