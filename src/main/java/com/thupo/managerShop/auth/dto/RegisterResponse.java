package com.thupo.managerShop.auth.dto;

import com.thupo.managerShop.auth.entity.Role;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.entity.UserStatus;
import java.time.Instant;
import java.util.List;

/**
 * DTO phản hồi thông tin sau khi đăng ký tài khoản thành công.
 */
public record RegisterResponse(
        Long id,
        String username,
        String email,
        String fullName,
        UserStatus status,
        List<String> roles,
        Instant createdAt,
        String message
) {
    public static RegisterResponse of(User user, String message) {
        List<String> roles = user.getRoles() != null
                ? user.getRoles().stream().map(Role::getName).map(Enum::name).sorted().toList()
                : List.of();
        return new RegisterResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roles,
                user.getCreatedAt(),
                message
        );
    }
}
