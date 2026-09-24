package co.gamestore.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Makes sure the browser actually receives the CSRF token cookie.
 *
 * <p>Spring Security loads the token lazily: the cookie is only written when something reads the
 * token, which for a single page application never happens on the first GET. The client would then
 * have no token to send with its first POST. Touching the token here forces the repository to write
 * the cookie on every response, so the page always has one ready.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // "_csrf" is the request attribute Spring Security fills with a token that resolves lazily.
        Object token = request.getAttribute("_csrf");
        if (token instanceof CsrfToken csrfToken) {
            csrfToken.getToken();
        }
        chain.doFilter(request, response);
    }
}
