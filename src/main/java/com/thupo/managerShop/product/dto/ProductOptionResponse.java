package com.thupo.managerShop.product.dto;

import java.util.List;

public record ProductOptionResponse(
        Long id,
        String name,
        int position,
        List<String> values
) {}
