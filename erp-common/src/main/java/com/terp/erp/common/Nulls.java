package com.terp.erp.common;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Objects;

/**
 * Bridges values from unannotated APIs (JDK, our records) into Spring parameters declared {@code @NonNull}. The
 * IDE's null analysis (Eclipse JDT in VS Code) cannot know that {@code Duration.ofSeconds(2)} or {@code List.of(..)}
 * never return null and warns "needs unchecked conversion"; this checks at runtime instead of hiding the warning,
 * so a real null still fails fast with an NPE at the call site.
 */
public final class Nulls {
    private Nulls() {
    }

    // The only suppression for null analysis in the code base: the requireNonNull just above makes it true.
    @SuppressWarnings("null")
    @NonNull
    public static <T> T nonNull(@Nullable T value) {
        return Objects.requireNonNull(value);
    }
}
