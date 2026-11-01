package io.github.arbiter.server.security;

import io.github.arbiter.server.config.ServerConfig;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public final class ApiResponseHeaderFilter extends OncePerRequestFilter {
    private final ServerConfig config;

    public ApiResponseHeaderFilter(ServerConfig config) {
        this.config = config;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String prefix = config.apiPrefix();
        if ("/".equals(prefix)) {
            return false;
        }
        String path = request.getRequestURI();
        return !path.equals(prefix) && !path.startsWith(prefix + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        filterChain.doFilter(request, response);
    }
}
