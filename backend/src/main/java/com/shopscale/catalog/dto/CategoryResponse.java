package com.shopscale.catalog.dto;

import com.shopscale.catalog.Category;
import java.io.Serializable;

public record CategoryResponse(Long id, String name, String slug, String description) implements Serializable {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription());
    }
}
