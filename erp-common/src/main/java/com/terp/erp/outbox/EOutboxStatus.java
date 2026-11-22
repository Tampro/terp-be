package com.terp.erp.outbox;

/**
 * NEW = waiting to be published (first try or a retry after a failure, see next_attempt_at); SENT = confirmed by the
 * broker; FAILED = gave up after erp.outbox.max-attempts (final: the relay never picks it again).
 */
public enum EOutboxStatus { NEW, SENT, FAILED }
