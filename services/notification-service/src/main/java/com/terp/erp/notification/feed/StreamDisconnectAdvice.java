package com.terp.erp.notification.feed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * A client closing its SSE stream (tab closed, reconnect) surfaces as a failed write. That is normal, not an error:
 * without this, the shared RestExceptionHandler logs it at ERROR and then fails again trying to write a JSON body
 * into a text/event-stream response. Runs before that handler and writes nothing, since the client is gone.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StreamDisconnectAdvice {

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void clientGone(AsyncRequestNotUsableException ex) {
        log.debug("Stream client disconnected");
    }
}
