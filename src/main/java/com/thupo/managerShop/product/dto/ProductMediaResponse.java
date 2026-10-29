package com.thupo.managerShop.product.dto;

import com.thupo.managerShop.product.entity.MediaType;

public record ProductMediaResponse(
        Long id,
        String url,
        MediaType mediaType,
        String altText,
        int position
) {}
