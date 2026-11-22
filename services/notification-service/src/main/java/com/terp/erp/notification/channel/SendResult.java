package com.terp.erp.notification.channel;

/**
 * Outcome of one send. code: null when sent; otherwise a short code stored on the delivery (never a server's text,
 * which could quote an address or a secret). retryable: worth another try later (timeout, 5xx, SMTP down).
 */
public record SendResult(boolean sent, String code, boolean retryable) {
    public static SendResult ok() {
        return new SendResult(true, null, false);
    }

    public static SendResult retry(String code) {
        return new SendResult(false, code, true);
    }

    public static SendResult fail(String code) {
        return new SendResult(false, code, false);
    }

    /** OutboundHttp's answer: 2xx sent; 408 / 429 / 5xx / no answer retried; other 4xx final. */
    public static SendResult ofHttp(com.terp.erp.net.OutboundHttp.Result r) {
        if (r.delivered()) {
            return ok();
        }
        if (r.statusCode() == null) {
            return retry(r.failure() == null ? "CONNECTION_ERROR" : r.failure());
        }
        int s = r.statusCode();
        String code = "HTTP_" + s;
        return s == 408 || s == 429 || s >= 500 ? retry(code) : fail(code);
    }
}
