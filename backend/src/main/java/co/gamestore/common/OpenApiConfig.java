package co.gamestore.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gameStoreOpenApi(
            BrandProperties brand,
            @org.springframework.beans.factory.annotation.Value(
                    "${server.servlet.session.cookie.name:__Host-SID}") String sessionCookieName) {
        return new OpenAPI()
                // Authentication is a session cookie, not a bearer token: sign in through
                // /api/auth/login and the browser carries it from there.
                .components(new Components().addSecuritySchemes("session",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE).name(sessionCookieName)))
                .addSecurityItem(new SecurityRequirement().addList("session"))
                .info(new Info()
                .title(brand.name() + " API")
                .version("0.1.0")
                .description("Console store and repair workshop: catalog, inventory, orders and AI reports"));
    }
}
