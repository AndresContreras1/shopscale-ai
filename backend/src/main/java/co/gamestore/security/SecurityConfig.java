package co.gamestore.security;

import tools.jackson.databind.ObjectMapper;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, RateLimiter rateLimiter,
                                                   ObjectMapper objectMapper,
                                                   @Value("${app.security.rate-limit.api-per-minute:300}") int apiLimit,
                                                   @Value("${app.security.rate-limit.login-per-minute:10}") int loginLimit)
            throws Exception {
        var jwtFilter = new JwtAuthenticationFilter(jwtService);
        var rateLimitFilter = new RateLimitFilter(rateLimiter, objectMapper, apiLimit, loginLimit);

        http
                // Stateless JWT API: no cookies, so no CSRF token and no HTTP session.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
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
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
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
