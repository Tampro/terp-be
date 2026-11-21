package com.terp.erp.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base of every business exception; subclasses only fix the HTTP status. */
@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final ErrorCode errorCode;
    private final transient Object[] args;

    public ApiException(HttpStatus status, ErrorCode errorCode, Object... args) {
        super(errorCode.getCode());
        this.status = status;
        this.errorCode = errorCode;
        this.args = args;
    }

    public static ApiException badRequest(ErrorCode error, Object... args) {
        return new ApiException(HttpStatus.BAD_REQUEST, error, args);
    }

    /** Also used for rows of another company: 404 rather than 403 does not confirm the id exists. */
    public static ApiException notFound(ErrorCode error, Object... args) {
        return new ApiException(HttpStatus.NOT_FOUND, error, args);
    }

    public static ApiException forbidden(ErrorCode error, Object... args) {
        return new ApiException(HttpStatus.FORBIDDEN, error, args);
    }

    public static ApiException unauthorized(ErrorCode error, Object... args) {
        return new ApiException(HttpStatus.UNAUTHORIZED, error, args);
    }
}
