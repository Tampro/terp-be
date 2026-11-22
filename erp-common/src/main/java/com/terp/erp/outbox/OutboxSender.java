package com.terp.erp.outbox;

/** Publishes one row to the broker; returns only when the broker confirmed it, throws otherwise. */
@FunctionalInterface
public interface OutboxSender {
    void send(OutboxEvent event);
}
