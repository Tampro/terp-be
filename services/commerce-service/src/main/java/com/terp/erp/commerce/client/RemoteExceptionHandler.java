package com.terp.erp.commerce.client;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.TraceIdFilter;

/** Relays another service's business error (e.g. INV_ or ACC_ codes) as is; runs before the common catch-all. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RemoteExceptionHandler {
    @ExceptionHandler(RemoteApiException.class)
    public ResponseEntity<ApiMessageDto<Object>> handle(RemoteApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiMessageDto.builder()
                .result(false).code(ex.getCode()).message(ex.getRemoteMessage())
                .traceId(MDC.get(TraceIdFilter.MDC_KEY)).build());
    }
}
