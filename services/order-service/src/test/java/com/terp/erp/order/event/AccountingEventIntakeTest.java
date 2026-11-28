package com.terp.erp.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terp.erp.order.order.CustomerOrder;
import com.terp.erp.order.order.CustomerOrderRepository;
import com.terp.erp.order.order.EJournalStatus;
import com.terp.erp.outbox.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountingEventIntakeTest {
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final AccountingEventIntake intake = new AccountingEventIntake(processed, orders, Clock.systemUTC());
    private final ObjectMapper mapper = new ObjectMapper();
    private final CustomerOrder order = new CustomerOrder();

    @BeforeEach
    void setUp() {
        order.setId(100L);
        order.setCompanyId(10L);
        order.setJournalStatus(EJournalStatus.PENDING);
        when(orders.findById(100L)).thenReturn(Optional.of(order));
    }

    private EventEnvelope event(String id, String type, Map<String, Object> data) {
        return new EventEnvelope(id, type, 10L, "SHOP_ORDER", "100", Instant.now(), null, mapper.valueToTree(data));
    }

    @Test
    void postedSetsTheEntryNumberAndAFailureCannotUndoIt() {
        intake.accept(event("e1", AccountingEventIntake.JOURNAL_POSTED,
                Map.of("sourceType", "SHOP_ORDER", "sourceId", "100", "journalNumber", "JE-2026-000042")));
        assertThat(order.getJournalStatus()).isEqualTo(EJournalStatus.POSTED);
        assertThat(order.getJournalEntryNo()).isEqualTo("JE-2026-000042");
        intake.accept(event("e2", AccountingEventIntake.JOURNAL_FAILED,
                Map.of("sourceType", "SHOP_ORDER", "sourceId", "100", "errorCode", "ACC_070")));
        assertThat(order.getJournalStatus()).isEqualTo(EJournalStatus.POSTED);
    }

    @Test
    void failedKeepsTheCode() {
        intake.accept(event("e3", AccountingEventIntake.JOURNAL_FAILED,
                Map.of("sourceType", "SHOP_ORDER", "sourceId", "100", "errorCode", "ACC_070")));
        assertThat(order.getJournalStatus()).isEqualTo(EJournalStatus.FAILED);
        assertThat(order.getJournalError()).isEqualTo("ACC_070");
    }

    @Test
    void otherSourceTypesDuplicatesAndOtherCompaniesChangeNothing() {
        intake.accept(event("e4", AccountingEventIntake.JOURNAL_POSTED,
                Map.of("sourceType", "SALES_INVOICE", "sourceId", "100", "journalNumber", "JE-1")));
        when(processed.existsById("e5")).thenReturn(true);
        intake.accept(event("e5", AccountingEventIntake.JOURNAL_POSTED,
                Map.of("sourceType", "SHOP_ORDER", "sourceId", "100", "journalNumber", "JE-2")));
        order.setCompanyId(11L);
        intake.accept(event("e6", AccountingEventIntake.JOURNAL_POSTED,
                Map.of("sourceType", "SHOP_ORDER", "sourceId", "100", "journalNumber", "JE-3")));
        assertThat(order.getJournalStatus()).isEqualTo(EJournalStatus.PENDING);
        verify(orders, never()).save(any());
    }
}
