package com.watchparty.watchparty.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;
    private final String path;

    private ApiResponse(boolean success, String code, String message, T data,
                        LocalDateTime timestamp, String path) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
        this.path = path;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "요청이 성공했습니다.", data, null, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, "OK", message, data, null, null);
    }

    public static ApiResponse<Void> okMessage(String message) {
        return new ApiResponse<>(true, "OK", message, null, null, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data, boolean includeTimestamp, boolean includePath,
                                        String path) {
        return new ApiResponse<>(
                true,
                "OK",
                message,
                data,
                includeTimestamp ? LocalDateTime.now() : null,
                includePath ? path : null
        );
    }
}
