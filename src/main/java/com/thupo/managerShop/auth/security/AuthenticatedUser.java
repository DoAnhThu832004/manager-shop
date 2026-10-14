package com.thupo.managerShop.auth.security;

import java.util.List;

/** Principal đặt vào SecurityContext sau khi xác thực JWT thành công. */
public record AuthenticatedUser(Long id, String username, List<String> roles) {
}
