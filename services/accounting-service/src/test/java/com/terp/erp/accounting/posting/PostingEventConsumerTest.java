package com.terp.erp.accounting.posting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.terp.erp.accounting.common.EAccountingError;
import com.terp.erp.accounting.common.SystemActor;
import com.terp.erp.accounting.ledger.AccountMapping;
import com.terp.erp.accounting.ledger.ESystemAccount;
import com.terp.erp.accounting.ledger.JournalEntryRepository;
import com.terp.erp.accounting.ledger.JournalService;
import com.terp.erp.exception.ApiException;
import com.terp.erp.outbox.EventEnvelope;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** commerce.journal.requested consumed as the system: no SecurityContext, company from the envelope. */
class PostingEventConsumerTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 22);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final JournalService journal = mock(JournalService.class);
    private final JournalEntryRepository entries = mock(JournalEntryRepository.class);
    private final AccountMapping mapping = mock(AccountMapping.class);
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final PostingEventIntake intake = new PostingEventIntake(processed,
            new DocumentPostingService(journal, entries, mapping), outbox, clock);
    private final PostingEventConsumer consumer = new PostingEventConsumer(intake);
    private final Set<String> seen = new HashSet<>();
    private final List<String> actors = new ArrayList<>();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        for (ESystemAccount k : ESystemAccount.values()) {
            when(mapping.require(10L, k)).thenReturn((long) k.ordinal() + 100);
        }
        when(entries.findByCompanyIdAndSourceTypeAndSourceId(any(), any(), any())).thenReturn(Optional.empty());
        when(journal.post(eq(10L), any(), any(), any(), any(), any(), anyList())).thenAnswer(inv -> {
            actors.add(SystemActor.current().orElse("system"));
            return new JournalService.JournalEntryDto("50", "10", "JE-2026-001", DAY, null, null, null, null, null,
                    null, null, null, List.of(), null);
        });
        when(processed.existsById(any())).thenAnswer(inv -> seen.contains(inv.<String>getArgument(0)));
        when(processed.saveAndFlush(any(ProcessedEvent.class))).thenAnswer(inv -> {
            seen.add(inv.<ProcessedEvent>getArgument(0).getEventId());
            return inv.getArgument(0);
        });
    }

    private EventEnvelope request(String eventId, Long company) {
        Map<String, Object> data = Payload.of("sourceType", "SALES_INVOICE", "sourceId", 7L, "date", DAY,
                "reference", "SI-2026-11-007", "description", "Sales invoice SI-2026-11-007 - Test Customer",
                "goodsAmount", new BigDecimal("200"), "serviceAmount", new BigDecimal("100"),
                "taxAmount", new BigDecimal("20"), "costAmount", new BigDecimal("120"), "actorSub", "kc-5");
        return new EventEnvelope(eventId, PostingEventIntake.EVENT_REQUESTED, company, "SALES_INVOICE", "7",
                Instant.now(clock), "trace-1", mapper.valueToTree(data));
    }

    @Test
    void aDuplicateEventIdPostsTheJournalOnceAndAnswersPostedOnce() {
        consumer.onEvent(request("e1", 10L));
        consumer.onEvent(request("e1", 10L));

        verify(journal, times(1)).post(eq(10L), eq(DAY), eq("SALES_INVOICE"), eq(7L), eq("SI-2026-11-007"),
                anyString(), anyList());
        verify(outbox, times(1)).publish(eq(10L), eq("accounting.journal.posted"), eq("SALES_INVOICE"), eq(7L),
                org.mockito.ArgumentMatchers.<Object>argThat(p -> "JE-2026-001".equals(
                        ((Map<?, ?>) p).get("journalNumber")) && "7".equals(((Map<?, ?>) p).get("sourceId"))));
        // created_by of the entry = the user who posted the invoice in commerce
        assertThat(actors).containsExactly("kc-5");
        assertThat(SystemActor.current()).isEmpty();
    }

    @Test
    void aClosedYearPublishesJournalFailedWithTheCodeAndAcknowledges() {
        doThrow(ApiException.badRequest(EAccountingError.ACC_070, "2026-12-31")).when(journal).assertOpen(10L, DAY);

        consumer.onEvent(request("e1", 10L));

        verify(journal, never()).post(any(), any(), any(), any(), any(), any(), anyList());
        verify(outbox).publish(eq(10L), eq("accounting.journal.failed"), eq("SALES_INVOICE"), eq(7L),
                org.mockito.ArgumentMatchers.<Object>argThat(p -> "ACC_070".equals(((Map<?, ?>) p).get("errorCode"))));
        verify(outbox, never()).publish(any(), eq("accounting.journal.posted"), any(), any(), any());
        // redelivered: answered once
        consumer.onEvent(request("e1", 10L));
        verify(outbox, times(1)).publish(any(), eq("accounting.journal.failed"), any(), any(), any());
    }

    @Test
    void anUnmappedAccountIsAPermanentFailureToo() {
        when(mapping.require(10L, ESystemAccount.COGS)).thenThrow(ApiException.badRequest(EAccountingError.ACC_017,
                "COGS"));

        consumer.onEvent(request("e1", 10L));

        verify(outbox).publish(eq(10L), eq("accounting.journal.failed"), eq("SALES_INVOICE"), eq(7L),
                org.mockito.ArgumentMatchers.<Object>argThat(p -> "ACC_017".equals(((Map<?, ?>) p).get("errorCode"))));
    }

    @Test
    void aTransientFailureIsThrownForTheRetryAndPublishesNothing() {
        when(journal.post(eq(10L), any(), any(), any(), any(), any(), anyList()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> consumer.onEvent(request("e1", 10L)))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        verify(outbox, never()).publish(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void aRepostAfterFailureReusesTheExistingEntry() {
        var existing = new com.terp.erp.accounting.ledger.JournalEntry();
        existing.setId(50L);
        when(entries.findByCompanyIdAndSourceTypeAndSourceId(10L, "SALES_INVOICE", 7L))
                .thenReturn(Optional.of(existing));
        when(journal.get(50L)).thenReturn(new JournalService.JournalEntryDto("50", "10", "JE-2026-001", DAY, null,
                null, null, null, null, null, null, null, List.of(), null));

        consumer.onEvent(request("e2", 10L));

        verify(journal, never()).post(any(), any(), any(), any(), any(), any(), anyList());
        verify(outbox).publish(eq(10L), eq("accounting.journal.posted"), eq("SALES_INVOICE"), eq(7L), any());
    }

    @Test
    void anUnreadableEventGoesToTheDeadLetterQueue() {
        EventEnvelope bad = new EventEnvelope("e3", PostingEventIntake.EVENT_REQUESTED, 10L, "X", "1",
                Instant.now(clock), null, mapper.valueToTree(Map.of("sourceType", "NOPE")));
        assertThatThrownBy(() -> consumer.onEvent(bad)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(outbox, never()).publish(any(), anyString(), anyString(), any(), any());
    }
}
