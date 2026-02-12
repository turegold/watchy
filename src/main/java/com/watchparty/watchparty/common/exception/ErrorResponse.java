package com.watchparty.watchparty.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final boolean success;
    private final String code;
    private final String message;
    private final List<FieldErrorDetail> errors;
    private final LocalDateTime timestamp;
    private final String path;

    private ErrorResponse(boolean success, String code, String message,
                          List<FieldErrorDetail> errors, LocalDateTime timestamp, String path) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.errors = errors;
        this.timestamp = timestamp;
        this.path = path;
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(
                false,
                errorCode.name(),
                message,
                null,
                LocalDateTime.now(),
                path
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, String message,
                                   List<FieldErrorDetail> errors, String path) {
        return new ErrorResponse(
                false,
                errorCode.name(),
                message,
                errors,
                LocalDateTime.now(),
                path
        );
    }

    @Getter
    public static class FieldErrorDetail {
        private final String field;
        private final String reason;

        public FieldErrorDetail(String field, String reason) {
            this.field = field;
            this.reason = reason;
        }
    }
}
