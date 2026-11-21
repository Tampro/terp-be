package com.terp.erp.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.terp.erp.common.ApiMessageDto;
import com.terp.erp.common.Nulls;
import com.terp.erp.common.TraceIdFilter;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class RestExceptionHandler {
    private final MessageSource messageSource;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiMessageDto<Object>> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getErrorCode(), null, ex.getArgs());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiMessageDto<Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, ECommonError.COMMON_001, fields);
    }

    /** Malformed JSON or a wrong type in the body is the client's mistake: 400, not 500. */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiMessageDto<Object>> handleUnreadable(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ECommonError.COMMON_001, null);
    }

    /**
     * A wrong, missing or invalid query/path parameter or multipart part (month=13, status=BAD, no year) is the
     * client's mistake: 400, not 500.
     */
    @ExceptionHandler({
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class,
            jakarta.validation.ConstraintViolationException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<ApiMessageDto<Object>> handleBadParameter(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ECommonError.COMMON_001, null);
    }

    /** No such endpoint: 404, not 500. */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiMessageDto<Object>> handleNoEndpoint(Exception ex) {
        return build(HttpStatus.NOT_FOUND, ECommonError.COMMON_005, null);
    }

    /** Wrong HTTP method or content type on an existing endpoint: 405 / 415 with the validation code. */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiMessageDto<Object>> handleMethod(Exception ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ECommonError.COMMON_001, null);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiMessageDto<Object>> handleMediaType(Exception ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ECommonError.COMMON_001, null);
    }

    // @PreAuthorize failures are thrown inside the handler, so they arrive here, not at the AccessDeniedHandler.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiMessageDto<Object>> handleDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ECommonError.COMMON_004, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiMessageDto<Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ECommonError.COMMON_002, null);
    }

    public String resolve(ErrorCode error, Object... args) {
        return messageSource.getMessage(Nulls.nonNull(error.getCode()), args, error.getMessage(), LocaleContextHolder.getLocale());
    }

    private ResponseEntity<ApiMessageDto<Object>> build(HttpStatus status, ErrorCode error, Object data, Object... args) {
        ApiMessageDto<Object> body = ApiMessageDto.builder()
                .result(false)
                .code(error.getCode())
                .message(resolve(error, args))
                .data(data)
                .traceId(MDC.get(TraceIdFilter.MDC_KEY))
                .build();
        return ResponseEntity.status(Nulls.nonNull(status)).body(body);
    }
}
