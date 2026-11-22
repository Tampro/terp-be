package com.terp.erp.outbox;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an event payload in key order, nulls allowed (Map.of refuses them). Long ids become strings, like the REST
 * DTOs, so consumers never meet a JavaScript-unsafe number. Recipient conventions read by notification-service:
 * recipientEmail (one address) and recipientUserIds (IAM user ids).
 */
public final class Payload {
    private Payload() {
    }

    public static Map<String, Object> of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("key/value pairs expected");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], value(keyValues[i + 1]));
        }
        return m;
    }

    private static Object value(Object v) {
        if (v instanceof Long l) {
            return String.valueOf(l);
        }
        if (v instanceof Collection<?> c) {
            return c.stream().map(Payload::value).toList();
        }
        return v;
    }

    /** Ids as a list of strings, nulls dropped. */
    public static List<String> ids(Collection<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
    }
}
