package com.thupo.managerShop.auth.exception;

/** Exception nghiệp vụ của module auth, được {@link GlobalExceptionHandler} chuyển thành response chuẩn. */
public class AuthException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Long retryAfterSeconds;
    private final Boolean captchaRequired;

    public AuthException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null, null);
    }

    public AuthException(ErrorCode errorCode, Long retryAfterSeconds) {
        this(errorCode, errorCode.getDefaultMessage(), retryAfterSeconds, null);
    }

    public AuthException(ErrorCode errorCode, String message, Long retryAfterSeconds) {
        this(errorCode, message, retryAfterSeconds, null);
    }

    public AuthException(ErrorCode errorCode, String message, Long retryAfterSeconds, Boolean captchaRequired) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
        this.captchaRequired = captchaRequired;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Boolean getCaptchaRequired() {
        return captchaRequired;
    }
}
