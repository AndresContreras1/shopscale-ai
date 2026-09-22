package com.shopscale.catalog.dto;

import com.shopscale.catalog.Category;

public record CategoryResponse(Long id, String name, String slug, String description) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription());
    }
}
