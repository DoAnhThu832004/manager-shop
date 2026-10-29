package com.thupo.managerShop.giftcard.util;

import com.thupo.managerShop.common.exception.BadRequestAlertException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Tiện ích sinh mã, băm HMAC-SHA256 với secret pepper và che mờ mã thẻ quà tặng.
 * Tuyệt đối không log hoặc lưu trữ mã thẻ quà tặng gốc.
 */
public final class GiftCardCodeUtil {

    /**
     * Bộ ký tự cho phép sinh mã ngẫu nhiên: 16 ký tự a-z và 0-9, loại trừ các ký tự dễ nhầm:
     * - Loại trừ số 0 và chữ o
     * - Loại trừ số 1, chữ l và chữ i
     */
    private static final String ALLOWED_ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final int CODE_LENGTH = 16;
    private static final Pattern USER_CODE_PATTERN = Pattern.compile("^[a-z0-9]{8,20}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private GiftCardCodeUtil() {
    }

    /**
     * Sinh mã thẻ ngẫu nhiên 16 ký tự an toàn bằng SecureRandom.
     */
    public static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = SECURE_RANDOM.nextInt(ALLOWED_ALPHABET.length());
            sb.append(ALLOWED_ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Chuẩn hóa mã thẻ quà tặng (chuyển chữ thường, cắt khoảng trắng).
     * Kiểm tra định dạng: từ 8 đến 20 ký tự chữ và số.
     */
    public static String normalizeCode(String rawCode) {
        if (rawCode == null) {
            throw new BadRequestAlertException("Mã thẻ quà tặng không được để trống");
        }
        String normalized = rawCode.trim().toLowerCase();
        if (!USER_CODE_PATTERN.matcher(normalized).matches()) {
            throw new BadRequestAlertException("Mã thẻ quà tặng phải từ 8 đến 20 ký tự chữ và số (không chứa ký tự đặc biệt)");
        }
        return normalized;
    }

    /**
     * Tính toán mã băm HMAC-SHA256 từ mã thẻ đã chuẩn hóa và secret pepper.
     * Kết quả là chuỗi hex 64 ký tự chữ thường.
     */
    public static String computeHash(String normalizedCode, String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("Secret pepper cho thẻ quà tặng chưa được cấu hình!");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(normalizedCode.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Lỗi khi tính toán HMAC-SHA256 cho mã thẻ quà tặng", e);
        }
    }

    /**
     * Lấy 4 ký tự cuối của mã thẻ.
     */
    public static String extractLast4(String normalizedCode) {
        if (normalizedCode == null || normalizedCode.length() < 4) {
            throw new IllegalArgumentException("Mã thẻ không đủ 4 ký tự");
        }
        return normalizedCode.substring(normalizedCode.length() - 4);
    }

    /**
     * Che mờ mã thẻ hiển thị theo định dạng ************ab12 (12 dấu sao + 4 ký tự cuối).
     */
    public static String maskCode(String codeLast4) {
        return "************" + (codeLast4 != null ? codeLast4 : "xxxx");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
