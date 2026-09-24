package co.gamestore.security;

import co.gamestore.common.ProblemType;
import co.gamestore.security.ratelimit.RateLimitFilter;
import co.gamestore.security.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimiter rateLimiter,
                                                   ObjectMapper objectMapper,
                                                   @Value("${app.security.rate-limit.api-per-minute:300}") int apiLimit,
                                                   @Value("${app.security.rate-limit.login-per-minute:10}") int loginLimit)
            throws Exception {
        var rateLimitFilter = new RateLimitFilter(rateLimiter, objectMapper, apiLimit, loginLimit);

        http
                // The session id travels in a HttpOnly cookie, which JavaScript cannot read, so an XSS
                // cannot steal it. Cookies are sent by the browser on any request, which reintroduces
                // CSRF, so spa() turns the protection back on with the double-submit token an Angular
                // client reads from the XSRF-TOKEN cookie and echoes in the X-XSRF-TOKEN header.
                .csrf(csrf -> csrf.spa())
                .cors(cors -> {
                })
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        // A new id on login, so a session id planted before signing in is worthless.
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/products/import/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/inventory/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers("/api/products/**", "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers("/api/audit/**", "/api/admin/**", "/api/ai/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeProblem(res, objectMapper, ProblemType.UNAUTHENTICATED,
                                        "Authentication required", req.getRequestURI()))
                        .accessDeniedHandler((req, res, e) ->
                                writeProblem(res, objectMapper, ProblemType.FORBIDDEN, null, req.getRequestURI())))
                .logout(logout -> logout
                        // Spring Security invalidates the session and clears the context; the browser
                        // keeps a cookie that no longer resolves to anything.
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public RestClient hibpRestClient(
            @Value("${app.security.password.hibp-url:https://api.pwnedpasswords.com}") String baseUrl) {
        return HibpBreachedPasswordChecker.restClient(baseUrl);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins:http://localhost:4200}") List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("X-RateLimit-Remaining", "X-Served-By"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * The filter chain rejects a request before any controller runs, so the RFC 9457 body is written
     * here by hand. Same shape as every other error, otherwise clients need two parsers.
     */
    private static void writeProblem(HttpServletResponse res, ObjectMapper mapper, ProblemType type,
                                     String detail, String path) throws IOException {
        res.setStatus(type.status().value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), type.toProblem(detail, path));
    }
}
