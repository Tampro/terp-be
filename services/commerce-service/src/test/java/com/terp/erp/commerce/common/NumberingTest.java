package com.terp.erp.commerce.common;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NumberingTest {
    private final DocumentSequenceRepository sequences = mock(DocumentSequenceRepository.class);
    private final Numbering numbering = new Numbering(sequences);

    @Test
    void theFirstNumberOfAMonthInsertsIgnoringARacingInsertThenLocksTheRow() {
        DocumentSequence.Key key = new DocumentSequence.Key(10L, "SI", "2026-11");
        DocumentSequence row = new DocumentSequence();
        row.setKey(key);
        row.setNextValue(1);
        when(sequences.lock(key)).thenReturn(Optional.of(row));

        assertThat(numbering.next(10L, "SI", LocalDate.of(2026, 11, 3))).isEqualTo("SI-2026-11-001");
        assertThat(numbering.next(10L, "SI", LocalDate.of(2026, 11, 4))).isEqualTo("SI-2026-11-002");

        InOrder order = inOrder(sequences);
        order.verify(sequences).ensureRow(10L, "SI", "2026-11");
        order.verify(sequences).lock(key);
        // the row is never created through save, which raced on the primary key
        verify(sequences, never()).saveAndFlush(any());
        assertThat(row.getNextValue()).isEqualTo(3);
    }
}
