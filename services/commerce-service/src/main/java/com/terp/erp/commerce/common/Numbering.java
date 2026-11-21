package com.terp.erp.commerce.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** Document numbers PREFIX-YYYY-MM-NNN per company (ERP manual: SP-, QT-, SI-, SR-), taken under a row lock. */
@Component
@RequiredArgsConstructor
public class Numbering {
    private final DocumentSequenceRepository sequences;

    /** Runs in the caller's transaction: a rolled-back document gives its number back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Long companyId, String prefix, LocalDate date) {
        String period = String.format("%04d-%02d", date.getYear(), date.getMonthValue());
        DocumentSequence.Key key = new DocumentSequence.Key(companyId, prefix, period);
        DocumentSequence s = sequences.lock(key).orElseGet(() -> {
            DocumentSequence fresh = new DocumentSequence();
            fresh.setKey(key);
            fresh.setNextValue(1);
            return sequences.saveAndFlush(fresh);
        });
        int n = s.getNextValue();
        s.setNextValue(n + 1);
        return String.format("%s-%s-%03d", prefix, period, n);
    }
}
