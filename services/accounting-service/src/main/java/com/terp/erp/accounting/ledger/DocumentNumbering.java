package com.terp.erp.accounting.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Per-company document numbers: PREFIX-period-NNN; a rolled-back document gives its number back. */
@Service
@RequiredArgsConstructor
public class DocumentNumbering {
    private final DocumentSequenceRepository sequences;

    /** period is "2026" or "2026-11"; numbers restart for each new period. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Long companyId, String prefix, String period) {
        String key = prefix + "-" + period;
        sequences.ensureRow(companyId, key);
        DocumentSequence s = sequences.lock(companyId, key).orElseThrow();
        long n = s.getLastNumber() + 1;
        s.setLastNumber(n);
        return String.format("%s-%03d", key, n);
    }
}
