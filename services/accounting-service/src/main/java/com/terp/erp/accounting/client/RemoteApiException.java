package com.terp.erp.accounting.client;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/**
 * A business error answered by inventory- or accounting-service (e.g. SAL_ or PUR_ codes). Relayed to the caller with the
 * other service's code and message, already in the caller's language (Accept-Language is forwarded).
 */
@Getter
public class RemoteApiException extends RuntimeException {
    private final HttpStatusCode status;
    private final String code;
    private final String remoteMessage;

    public RemoteApiException(HttpStatusCode status, String code, String remoteMessage) {
        super(code);
        this.status = status;
        this.code = code;
        this.remoteMessage = remoteMessage;
    }
}
