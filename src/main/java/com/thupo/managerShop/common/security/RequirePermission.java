package com.thupo.managerShop.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation đánh dấu quyền nghiệp vụ bắt buộc (RBAC).
 * Ví dụ: MANAGE_PRODUCTS, MANAGE_INVENTORY, MANAGE_GIFT_CARDS.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String[] value();
}
