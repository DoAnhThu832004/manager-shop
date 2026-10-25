package com.thupo.managerShop.auth.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchSecurityExpressionTest {

    private BranchSecurityExpression expression;

    @BeforeEach
    void setUp() {
        expression = new BranchSecurityExpression();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("OWNER có toàn quyền truy cập bất kỳ chi nhánh nào")
    void ownerCanAccessAnyBranch() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "owner_user", List.of("OWNER"), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

        assertTrue(expression.canAccess(1L));
        assertTrue(expression.canAccess(999L));
    }

    @Test
    @DisplayName("CASHIER chỉ được truy cập đúng chi nhánh trực thuộc của mình")
    void cashierCanOnlyAccessOwnBranch() {
        AuthenticatedUser user = new AuthenticatedUser(2L, "cashier_q1", List.of("CASHIER"), 1L, List.of(1L));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

        assertTrue(expression.canAccess(1L));
        assertFalse(expression.canAccess(2L));
        assertFalse(expression.canAccess(null));
    }

    @Test
    @DisplayName("MANAGER được truy cập danh sách các chi nhánh được phân quyền")
    void managerCanAccessAssignedBranches() {
        AuthenticatedUser user = new AuthenticatedUser(3L, "manager_multi", List.of("MANAGER"), 1L, List.of(1L, 2L));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

        assertTrue(expression.canAccess(1L));
        assertTrue(expression.canAccess(2L));
        assertFalse(expression.canAccess(3L));
    }
}
