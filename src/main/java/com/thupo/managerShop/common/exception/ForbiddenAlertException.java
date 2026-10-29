package com.thupo.managerShop.common.exception;

import java.util.Collections;
import java.util.List;

public class ForbiddenAlertException extends RuntimeException {

    private final List<String> requiredPermissions;

    public ForbiddenAlertException(String message, List<String> requiredPermissions) {
        super(message);
        this.requiredPermissions = requiredPermissions != null ? requiredPermissions : Collections.emptyList();
    }

    public List<String> getRequiredPermissions() {
        return requiredPermissions;
    }
}
