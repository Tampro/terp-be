package com.terp.erp.payroll.client;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.TraceIdFilter;
import com.terp.erp.exception.ApiException;
import com.terp.erp.exception.RestExceptionHandler;
import com.terp.erp.payroll.common.EPayrollError;

/** Relays another service's business error (e.g. EMP_002) as is; runs before the common catch-all. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@lombok.RequiredArgsConstructor
public class RemoteExceptionHandler {
    private final RestExceptionHandler common;

    /** An import file over the multipart limit is the client's mistake, not a 500. */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiMessageDto<Object>> tooLarge() {
        return common.handleApi(ApiException.badRequest(EPayrollError.PAY_022, "5 MB"));
    }

    @ExceptionHandler(RemoteApiException.class)
    public ResponseEntity<ApiMessageDto<Object>> handle(RemoteApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiMessageDto.builder()
                .result(false).code(ex.getCode()).message(ex.getRemoteMessage())
                .traceId(MDC.get(TraceIdFilter.MDC_KEY)).build());
    }
}
