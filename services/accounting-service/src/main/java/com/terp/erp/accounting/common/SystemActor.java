package com.terp.erp.accounting.common;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Who a write made outside a request (an event consumer) is attributed to: the producer's actor (the Keycloak sub
 * carried in the event), so created_by of a journal entry posted from commerce.journal.requested names the user who
 * posted the invoice. Read by AuditingConfig after the request's own user; "system" when neither is known.
 */
public final class SystemActor {
    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();

    private SystemActor() {
    }

    public static Optional<String> current() {
        return Optional.ofNullable(ACTOR.get());
    }

    public static <T> T as(String actorSub, Supplier<T> work) {
        String previous = ACTOR.get();
        if (actorSub == null || actorSub.isBlank() || actorSub.length() > 64) {
            ACTOR.remove();
        } else {
            ACTOR.set(actorSub);
        }
        try {
            return work.get();
        } finally {
            if (previous == null) {
                ACTOR.remove();
            } else {
                ACTOR.set(previous);
            }
        }
    }
}
