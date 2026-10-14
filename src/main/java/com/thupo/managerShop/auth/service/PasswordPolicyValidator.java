package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validator kiểm tra chính sách an toàn mật khẩu:
 * - Độ dài tối thiểu 8 ký tự, tối đa 128 ký tự
 * - Phải chứa ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số
 * - Không chứa username hoặc phần trước @ của email
 * - Không nằm trong danh sách các mật khẩu yếu/phổ biến nhất
 */
@Component
public class PasswordPolicyValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    private static final Pattern HAS_UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern HAS_LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");

    // Danh sách mẫu một số mật khẩu phổ biến và nguy hiểm nhất
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "12345678", "123456789", "1234567890", "password", "password123",
            "admin123", "manager123", "qwertyuiop", "iloveyou", "welcome123",
            "11111111", "00000000", "shop1234", "shopmanager"
    );

    /**
     * Xác thực mật khẩu mới.
     *
     * @param newPassword Mật khẩu mới dạng plain-text
     * @param username    Tên tài khoản (để kiểm tra không bị lồng vào mật khẩu)
     * @param email       Email tài khoản
     */
    public void validate(String newPassword, String username, String email) {
        if (newPassword == null || newPassword.length() < MIN_LENGTH || newPassword.length() > MAX_LENGTH) {
            throw new AuthException(ErrorCode.AUTH_PASSWORD_POLICY,
                    String.format("Mật khẩu phải có độ dài từ %d đến %d ký tự", MIN_LENGTH, MAX_LENGTH), null);
        }

        if (!HAS_UPPERCASE.matcher(newPassword).find()) {
            throw new AuthException(ErrorCode.AUTH_PASSWORD_POLICY, "Mật khẩu phải chứa ít nhất một chữ cái in hoa (A-Z)", null);
        }

        if (!HAS_LOWERCASE.matcher(newPassword).find()) {
            throw new AuthException(ErrorCode.AUTH_PASSWORD_POLICY, "Mật khẩu phải chứa ít nhất một chữ cái in thường (a-z)", null);
        }

        if (!HAS_DIGIT.matcher(newPassword).find()) {
            throw new AuthException(ErrorCode.AUTH_PASSWORD_POLICY, "Mật khẩu phải chứa ít nhất một chữ số (0-9)", null);
        }

        String lowerPassword = newPassword.toLowerCase(Locale.ROOT);

        if (COMMON_PASSWORDS.contains(lowerPassword)) {
            throw new AuthException(ErrorCode.AUTH_PASSWORD_POLICY, "Mật khẩu quá phổ biến và dễ đoán, vui lòng chọn mật khẩu phức tạp hơn", null);
        }

        if (username != null && !username.isBlank()) {
            String cleanUsername = username.trim().toLowerCase(Locale.ROOT);
            if (cleanUsername.length() >= 3 && lowerPassword.contains(cleanUsername)) {
                throw new AuthException(ErrorCode.AUTH_PASSWORD_POLICY, "Mật khẩu không được chứa tên đăng nhập của bạn", null);
            }
        }

        if (email != null && email.contains("@")) {
            String emailPrefix = email.substring(0, email.indexOf('@')).trim().toLowerCase(Locale.ROOT);
            if (emailPrefix.length() >= 3 && lowerPassword.contains(emailPrefix)) {
                throw new AuthException(ErrorCode.AUTH_PASSWORD_POLICY, "Mật khẩu không được chứa phần tên của email", null);
            }
        }
    }
}
