package io.github.arbiter.server.security;

import io.github.arbiter.api.ApiHeaders;
import io.github.arbiter.server.error.ApiException;
import io.github.arbiter.server.service.AuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Metrics are useful operationally but can expose internals. Keep health and
 * info public for probes, and require an admin token for the metrics endpoint.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class ManagementMetricsSecurityFilter extends OncePerRequestFilter {
    private final AuthService authService;

    public ManagementMetricsSecurityFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/actuator/metrics");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            authService.requireScopes(request.getHeader(ApiHeaders.ARBITER_TOKEN),
                    "admin");
            filterChain.doFilter(request, response);
        } catch (ApiException error) {
            response.setStatus(error.status());
            response.setContentType("application/json; charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            String message = error.safeMessage().replace("\\", "\\\\")
                    .replace("\"", "\\\"");
            response.getOutputStream().write(("{\"code\":\""
                    + error.code() + "\",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
            response.flushBuffer();
        }
    }
}
