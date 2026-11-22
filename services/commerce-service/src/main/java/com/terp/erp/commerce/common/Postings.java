package com.terp.erp.commerce.common;

import com.terp.erp.commerce.client.AccountingClient;

import java.math.BigDecimal;

/**
 * Order of a document that moves stock and is journalled: (1) accounting validates the entry and writes nothing, so
 * a closed year (ACC_070) or an unmapped account (ACC_017) refuses the document before any stock moves; (2) the
 * stock move; (3) the journal entry. Every call is idempotent on (sourceType, sourceId), and the commerce status
 * change commits only after all three, so a retry of the same document finishes what a failed attempt started.
 * TODO(assumption): residual risk — if step 3 fails for a transient reason (accounting down) after step 2, the stock
 *  has moved while the document stays DRAFT / APPROVED until it is retried; editing or deleting it then leaves the
 *  move in inventory. No inventory reverse endpoint exists for a document id that may be retried; the real fix is
 *  an outbox + broker (not chosen yet).
 */
public final class Postings {
    private Postings() {
    }

    public static void run(AccountingClient accounting, AccountingClient.Posting p, Runnable stockMove) {
        boolean journalled = positive(p.goodsAmount()) || positive(p.serviceAmount()) || positive(p.taxAmount())
                || positive(p.costAmount());
        if (journalled) {
            accounting.journal(p, true);
        }
        stockMove.run();
        if (journalled) {
            accounting.journal(p, false);
        }
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}
