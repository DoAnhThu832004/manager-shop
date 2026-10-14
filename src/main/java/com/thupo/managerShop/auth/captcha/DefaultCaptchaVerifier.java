package com.thupo.managerShop.auth.captcha;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Implementation mẫu cho CaptchaVerifier.
 * Trong thực tế, class này sẽ gọi REST API sang Google reCAPTCHA hoặc Cloudflare Turnstile để xác minh token.
 */
@Component
@ConditionalOnMissingBean(CaptchaVerifier.class)
public class DefaultCaptchaVerifier implements CaptchaVerifier {

    @Override
    public boolean verify(String captchaToken, String clientIp) {
        // Cho mục đích kiểm thử và phát triển ban đầu, chấp nhận token nếu có độ dài tối thiểu
        return captchaToken != null && !captchaToken.trim().isEmpty();
    }
}
