package co.gamestore.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tags every response with the instance that served it, making load balancing visible:
 * behind Nginx consecutive requests alternate between replicas.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InstanceHeaderFilter extends OncePerRequestFilter {

    private final String instanceId = resolveInstanceId();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Served-By", instanceId);
        chain.doFilter(request, response);
    }

    private static String resolveInstanceId() {
        try {
            // Inside Docker the hostname is the container id.
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
