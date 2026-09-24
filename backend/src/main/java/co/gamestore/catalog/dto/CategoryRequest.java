package co.gamestore.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Pattern(regexp = "^[a-z0-9-]{2,80}$", message = "must be lowercase letters, digits or dashes")
        String slug,
        @Size(max = 300) String description) {
}
