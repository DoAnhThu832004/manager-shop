package com.thupo.managerShop.auth.exception;

/** Exception nghiệp vụ của module auth, được {@link GlobalExceptionHandler} chuyển thành response chuẩn. */
public class AuthException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Long retryAfterSeconds;

    public AuthException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null);
    }

    public AuthException(ErrorCode errorCode, Long retryAfterSeconds) {
        this(errorCode, errorCode.getDefaultMessage(), retryAfterSeconds);
    }

    public AuthException(ErrorCode errorCode, String message, Long retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
