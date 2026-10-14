package com.thupo.managerShop.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tiện ích sinh token ngẫu nhiên bằng SecureRandom và băm SHA-256.
 * Dùng cho Refresh Token và Password Reset Token để chỉ lưu trữ hash trong DB.
 */
public final class TokenHashUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TokenHashUtil() {
    }

    /**
     * Sinh chuỗi ngẫu nhiên bảo mật an toàn mã hoá (mặc định 32 bytes ~ 43 ký tự URL-safe Base64).
     */
    public static String generateRandomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Băm chuỗi bằng thuật toán SHA-256 và trả về chuỗi Hex 64 ký tự.
     */
    public static String sha256Hex(String plainText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuật toán SHA-256 không khả dụng trên JVM này", e);
        }
    }
}
