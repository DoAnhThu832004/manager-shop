package com.thupo.managerShop.auth.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Tiện ích so khớp IP với CIDR cho IPv4 và IPv6.
 *
 * <p>Chỉ phân giải chuỗi đã được kiểm tra là địa chỉ IP dạng literal, tuyệt đối không để
 * {@link InetAddress#getByName(String)} nhận tên miền từ header do client gửi (tránh DNS lookup bị lợi dụng).
 */
public final class CidrMatcher {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");
    private static final int MAX_IP_LENGTH = 45;

    private CidrMatcher() {
    }

    /** Chuỗi có phải IPv4/IPv6 literal hợp lệ về mặt cú pháp hay không. */
    public static boolean isIpLiteral(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_IP_LENGTH) {
            return false;
        }
        if (value.indexOf(':') >= 0) {
            return IPV6_CHARS.matcher(value).matches() && toBytes(value) != null;
        }
        if (!IPV4.matcher(value).matches()) {
            return false;
        }
        for (String part : value.split("\\.")) {
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    /** Chuẩn hoá IP về một dạng duy nhất (để dùng làm khoá đếm/chặn); chuỗi không hợp lệ giữ nguyên. */
    public static String normalize(String ip) {
        byte[] bytes = isIpLiteral(ip) ? toBytes(ip) : null;
        if (bytes == null) {
            return ip;
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            return ip;
        }
    }

    /**
     * Kiểm tra {@code ip} có nằm trong {@code cidrOrIp} hay không.
     *
     * @param cidrOrIp một IP đơn lẻ hoặc dải dạng {@code 10.0.0.0/8}
     * @param ip       IP cần kiểm tra
     */
    public static boolean matches(String cidrOrIp, String ip) {
        if (cidrOrIp == null || ip == null) {
            return false;
        }
        String[] parts = cidrOrIp.trim().split("/", 2);
        byte[] network = isIpLiteral(parts[0]) ? toBytes(parts[0]) : null;
        byte[] target = isIpLiteral(ip) ? toBytes(ip) : null;
        if (network == null || target == null || network.length != target.length) {
            return false;
        }
        int totalBits = network.length * 8;
        int prefix = totalBits;
        if (parts.length == 2) {
            try {
                prefix = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                return false;
            }
            if (prefix < 0 || prefix > totalBits) {
                return false;
            }
        }
        return prefixEquals(network, target, prefix);
    }

    private static boolean prefixEquals(byte[] a, byte[] b, int prefixBits) {
        int fullBytes = prefixBits / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        int remainingBits = prefixBits % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return (a[fullBytes] & mask) == (b[fullBytes] & mask);
    }

    private static byte[] toBytes(String ip) {
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
