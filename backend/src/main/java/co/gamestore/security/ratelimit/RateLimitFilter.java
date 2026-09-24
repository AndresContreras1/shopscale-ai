package co.gamestore.security.ratelimit;

import co.gamestore.common.ProblemType;
import co.gamestore.common.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Protects the API from abuse (scraping, brute force on login, runaway clients).
 * Login has a much stricter limit than the rest of the API.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final Problems problems;
    private final int apiLimit;
    private final int loginLimit;

    public RateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper, Problems problems,
                           int apiLimit, int loginLimit) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.problems = problems;
        this.apiLimit = apiLimit;
        this.loginLimit = loginLimit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean login = request.getRequestURI().startsWith("/api/auth/login");
        int limit = login ? loginLimit : apiLimit;
        String key = "rl:" + (login ? "login:" : "api:") + clientIp(request);

        long remaining = rateLimiter.tryAcquire(key, limit, WINDOW);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(remaining, 0)));
        if (remaining < 0) {
            response.setStatus(ProblemType.RATE_LIMITED.status().value());
            response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), problems.of(ProblemType.RATE_LIMITED,
                    "Rate limit exceeded, try again later", request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Behind the Nginx load balancer the real client address comes in X-Forwarded-For. The header is
     * trusted only because Nginx overwrites it with the connection address; the API must not be
     * reachable directly in production.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
