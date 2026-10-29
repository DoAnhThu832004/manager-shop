package com.thupo.managerShop.auth.exception;

import org.springframework.http.HttpStatus;

/** Mã lỗi thống nhất của module auth. Tên enum chính là trường {@code code} trả về cho client. */
public enum ErrorCode {

    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng"),
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để thực hiện thao tác này"),
    AUTH_ACCOUNT_LOCKED(HttpStatus.LOCKED, "Tài khoản tạm thời bị khoá, vui lòng thử lại sau"),
    AUTH_ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Tài khoản không khả dụng, vui lòng liên hệ quản trị viên"),
    AUTH_ACCOUNT_PENDING_APPROVAL(HttpStatus.FORBIDDEN, "Tài khoản của bạn đang chờ quản trị viên phê duyệt và phân bổ chi nhánh"),
    AUTH_IP_BLOCKED(HttpStatus.TOO_MANY_REQUESTS, "Địa chỉ IP của bạn tạm thời bị chặn"),
    AUTH_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh, vui lòng thử lại sau"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn"),
    AUTH_REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không còn hiệu lực, vui lòng đăng nhập lại"),
    AUTH_RESET_TOKEN_INVALID(HttpStatus.GONE, "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"),
    AUTH_PASSWORD_POLICY(HttpStatus.BAD_REQUEST, "Mật khẩu không đáp ứng yêu cầu bảo mật"),
    AUTH_PASSWORD_REUSED(HttpStatus.BAD_REQUEST, "Mật khẩu mới không được trùng với các mật khẩu gần đây"),
    AUTH_USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "Tên đăng nhập hoặc email đã tồn tại trong hệ thống"),
    AUTH_REGISTRATION_DISABLED(HttpStatus.FORBIDDEN, "Tính năng đăng ký tài khoản công khai tạm thời bị vô hiệu hoá"),
    USER_ALREADY_HAS_BRANCH(HttpStatus.CONFLICT, "Tài khoản của bạn đã được liên kết với chi nhánh khác"),
    BRANCH_ALREADY_EXISTS(HttpStatus.CONFLICT, "Mã chi nhánh đã tồn tại trong hệ thống"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị trùng lặp hoặc vi phạm ràng buộc toàn vẹn của hệ thống"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu yêu cầu"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống gặp sự cố, vui lòng thử lại sau");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
