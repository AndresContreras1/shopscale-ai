package co.gamestore.security;

import co.gamestore.common.ProblemType;
import co.gamestore.security.ratelimit.RateLimitFilter;
import co.gamestore.security.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimiter rateLimiter,
                                                   ObjectMapper objectMapper,
                                                   @Value("${app.security.rate-limit.api-per-minute:300}") int apiLimit,
                                                   @Value("${app.security.rate-limit.login-per-minute:10}") int loginLimit)
            throws Exception {
        var rateLimitFilter = new RateLimitFilter(rateLimiter, objectMapper, apiLimit, loginLimit);

        http
                .headers(SecurityConfig::hardenHeaders)
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
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/logout",
                                "/api/auth/verify-email", "/api/auth/verify-email/resend",
                                "/api/auth/password/forgot", "/api/auth/password/reset",
                                "/api/auth/mfa/verify").permitAll()
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

    /**
     * Swagger UI needs inline styles and scripts to render, which the API's policy forbids. It gets
     * its own chain, ahead of the main one, so relaxing the policy for the documentation never
     * relaxes it for the API. In production this interface is not exposed at all.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> {
                    hardenHeaders(headers);
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                    + "script-src 'self' 'unsafe-inline'; frame-ancestors 'none'"));
                });
        return http.build();
    }

    /**
     * Headers every response carries. Each one closes a specific attack, and the comments say which,
     * because a list of headers copied from a blog post is impossible to maintain.
     */
    private static void hardenHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers
                // Nothing may load, and nothing may frame us. The API answers JSON: it needs none of it.
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                // A browser that has seen this header refuses plain HTTP for a year, which removes the
                // first request an attacker on the network could downgrade.
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(true)
                        .maxAgeInSeconds(Duration.ofDays(365).toSeconds()))
                // Order ids and tokens can end up in a path; this stops them travelling in a Referer.
                .referrerPolicy(referrer -> referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // The store asks for none of these, so no dependency can ask on its behalf.
                .permissionsPolicyHeader(permissions -> permissions.policy(
                        "geolocation=(), camera=(), microphone=(), payment=(), usb=(), interest-cohort=()"))
                .frameOptions(frame -> frame.deny())
                // Isolates our window from anything that opens it, and stops other origins embedding
                // our responses, which is what makes cross-origin leaks possible.
                .crossOriginOpenerPolicy(coop -> coop.policy(
                        CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
                .crossOriginResourcePolicy(corp -> corp.policy(
                        CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_ORIGIN));
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
