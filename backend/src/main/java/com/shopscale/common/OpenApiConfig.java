package com.shopscale.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shopScaleOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ShopScale AI API")
                .version("0.1.0")
                .description("Scalable e-commerce backend: catalog, inventory, orders and AI reports"));
    }
}
