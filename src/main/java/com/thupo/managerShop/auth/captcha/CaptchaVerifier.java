package com.thupo.managerShop.auth.captcha;

/**
 * Interface kiểm tra CAPTCHA.
 * Được kích hoạt khi tài khoản đã thử sai >= 3 lần để chống bot brute-force tự động.
 */
public interface CaptchaVerifier {

    /**
     * Xác thực captcha token gửi từ phía client.
     *
     * @param captchaToken Token do dịch vụ captcha (Google reCAPTCHA / Cloudflare Turnstile) sinh ra
     * @param clientIp    Địa chỉ IP của client
     * @return true nếu captcha hợp lệ hoặc hệ thống chưa bật bắt buộc captcha
     */
    boolean verify(String captchaToken, String clientIp);
}
