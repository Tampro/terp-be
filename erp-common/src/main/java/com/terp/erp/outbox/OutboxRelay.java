package com.terp.erp.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Polls the outbox: locks a small batch of due NEW rows (FOR UPDATE SKIP LOCKED), publishes each one and waits for
 * the broker's confirm, marks it SENT; on failure the row stays NEW with an exponential backoff
 * (initialBackoff * 2^(attempts-1), capped at maxBackoff) until maxAttempts, then FAILED with a WARN (no payload in
 * the log). At-least-once: a crash between confirm and commit republishes the row, so consumers dedupe on eventId.
 */
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    /** Batches per run when the outbox is backed up, so one run cannot hold the scheduler thread forever. */
    private static final int MAX_BATCHES_PER_RUN = 10;

    private final OutboxRepository repo;
    private final OutboxSender sender;
    private final OutboxProperties props;
    private final TransactionOperations tx;
    private final Clock clock;

    public OutboxRelay(OutboxRepository repo, OutboxSender sender, OutboxProperties props, TransactionOperations tx,
                       Clock clock) {
        this.repo = repo;
        this.sender = sender;
        this.props = props;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${erp.outbox.poll-interval-ms:2000}",
            initialDelayString = "${erp.outbox.poll-interval-ms:2000}")
    public void poll() {
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            Integer n = tx.execute(s -> relayBatch());
            if (n == null || n < props.batchSize()) {
                return;
            }
        }
    }

    /** One batch; must run inside a transaction (the row locks live until it ends). Returns the rows handled. */
    public int relayBatch() {
        Instant now = Instant.now(clock);
        List<OutboxEvent> due = repo.lockDue(now, props.batchSize());
        for (OutboxEvent e : due) {
            try {
                sender.send(e);
                e.setStatus(EOutboxStatus.SENT);
                e.setSentAt(Instant.now(clock));
                e.setLastError(null);
            } catch (RuntimeException ex) {
                failed(e, ex, now);
            }
        }
        if (!due.isEmpty()) {
            repo.saveAll(due);
        }
        return due.size();
    }

    private void failed(OutboxEvent e, RuntimeException ex, Instant now) {
        int attempts = e.getAttempts() + 1;
        e.setAttempts(attempts);
        e.setLastError(truncate(ex.getClass().getSimpleName()));
        if (attempts >= props.maxAttempts()) {
            e.setStatus(EOutboxStatus.FAILED);
            log.warn("Outbox event {} ({}) FAILED after {} attempts: {}", e.getEventId(), e.getEventType(), attempts,
                    e.getLastError());
            return;
        }
        e.setNextAttemptAt(now.plus(backoff(attempts)));
        log.info("Outbox event {} ({}) publish failed (attempt {}): {}", e.getEventId(), e.getEventType(), attempts,
                e.getLastError());
    }

    /** initialBackoff * 2^(attempts-1), capped. */
    Duration backoff(int attempts) {
        Duration d = props.initialBackoff();
        for (int i = 1; i < attempts && d.compareTo(props.maxBackoff()) < 0; i++) {
            d = d.multipliedBy(2);
        }
        return d.compareTo(props.maxBackoff()) > 0 ? props.maxBackoff() : d;
    }

    private static String truncate(String s) {
        return s.length() > 100 ? s.substring(0, 100) : s;
    }
}
