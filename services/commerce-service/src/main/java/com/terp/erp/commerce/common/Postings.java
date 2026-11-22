package com.terp.erp.commerce.common;

import com.terp.erp.commerce.client.AccountingClient;
import com.terp.erp.outbox.OutboxPublisher;
import com.terp.erp.outbox.Payload;
import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;

import java.math.BigDecimal;

/**
 * Order of a document that moves stock and is journalled: (1) accounting validates the entry and writes nothing
 * (synchronous, the caller's token), so a closed year (ACC_070) or an unmapped account (ACC_017) refuses the document
 * before any stock moves; (2) the stock move (synchronous, idempotent on (sourceType, sourceId)); (3) the journal is
 * requested through the outbox (event {@value #EVENT_JOURNAL_REQUESTED}) in the transaction of the commerce status
 * change: it is published only if the document commits, and it is not lost if accounting or the broker is down.
 * accounting-service answers with accounting.journal.posted / .failed, which set the document's journal status.
 * TODO(assumption): residual risk — the stock move (2) is still a remote call made before the commerce commit; if
 *  the commit itself fails after it, the stock has moved for a document that stays DRAFT / APPROVED until it is
 *  posted again (the move is idempotent, so the repost takes nothing twice).
 */
public final class Postings {
    public static final String EVENT_JOURNAL_REQUESTED = "commerce.journal.requested";

    private Postings() {
    }

    /** @return PENDING when a journal was requested, null when the document has nothing to journal. */
    public static EJournalStatus run(AccountingClient accounting, OutboxPublisher outbox, Long companyId,
                                     AccountingClient.Posting p, Runnable stockMove) {
        boolean journalled = journalled(p);
        if (journalled) {
            accounting.validate(p);
        }
        stockMove.run();
        if (!journalled) {
            return null;
        }
        request(outbox, companyId, p);
        return EJournalStatus.PENDING;
    }

    /**
     * Re-post of a FAILED journal: validated again first (a still-closed year answers at once and changes nothing),
     * then requested again. The journal is idempotent per (company, sourceType, sourceId) on the accounting side.
     */
    public static void retry(AccountingClient accounting, OutboxPublisher outbox, Long companyId,
                             AccountingClient.Posting p) {
        accounting.validate(p);
        request(outbox, companyId, p);
    }

    /**
     * The payload = the body of accounting's POST /journal-postings (without validateOnly) + the actor, for audit.
     * Permission (SALES_POST / PURCHASE_POST) was checked here, by the producer: accounting does not check it again.
     */
    static void request(OutboxPublisher outbox, Long companyId, AccountingClient.Posting p) {
        outbox.publish(companyId, EVENT_JOURNAL_REQUESTED, p.sourceType(), p.sourceId(), Payload.of(
                "sourceType", p.sourceType(), "sourceId", p.sourceId(), "date", p.date(), "reference", p.reference(),
                "description", p.description(), "goodsAmount", p.goodsAmount(), "serviceAmount", p.serviceAmount(),
                "taxAmount", p.taxAmount(), "costAmount", p.costAmount(),
                "actorSub", CurrentUser.find().map(ErpPrincipal::keycloakId).orElse(null)));
    }

    public static boolean journalled(AccountingClient.Posting p) {
        return positive(p.goodsAmount()) || positive(p.serviceAmount()) || positive(p.taxAmount())
                || positive(p.costAmount());
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}
