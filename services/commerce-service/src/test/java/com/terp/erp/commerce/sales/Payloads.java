package com.terp.erp.commerce.sales;

import java.util.Map;

/** Outbox payloads are maps built by Payload.of; tests read them with String keys. */
final class Payloads {
    private Payloads() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object payload) {
        return (Map<String, Object>) payload;
    }
}
