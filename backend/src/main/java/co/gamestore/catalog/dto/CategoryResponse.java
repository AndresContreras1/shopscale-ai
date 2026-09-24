package co.gamestore.catalog.dto;

import co.gamestore.catalog.Category;
import java.io.Serializable;

public record CategoryResponse(Long id, String name, String slug, String description) implements Serializable {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription());
    }
}
