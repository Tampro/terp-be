package com.terp.erp.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response envelope shared with the FE ({@code IApiResponse<T>}): { result, code, data, message }. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiMessageDto<T> {
    private Boolean result;
    private String code;
    private T data;
    private String message;
    /** Present on errors only, so a support ticket can be matched to the log line. */
    private String traceId;

    public static <T> ApiMessageDto<T> ok(T data) {
        return ApiMessageDto.<T>builder().result(true).data(data).build();
    }

    public static <T> ApiMessageDto<T> ok(T data, String message) {
        return ApiMessageDto.<T>builder().result(true).data(data).message(message).build();
    }
}
