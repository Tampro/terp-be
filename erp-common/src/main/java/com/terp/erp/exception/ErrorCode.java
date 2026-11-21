package com.terp.erp.exception;

/** A stable business error. {@link #getCode()} is the enum constant name and the i18n message key. */
public interface ErrorCode {
    String name();

    /** English fallback text. */
    String getMessage();

    default String getCode() {
        return name();
    }
}
