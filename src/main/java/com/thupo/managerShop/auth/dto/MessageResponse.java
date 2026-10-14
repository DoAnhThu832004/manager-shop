package com.thupo.managerShop.auth.dto;

/**
 * DTO phản hồi thông báo chung từ API.
 */
public record MessageResponse(String message) {
    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
