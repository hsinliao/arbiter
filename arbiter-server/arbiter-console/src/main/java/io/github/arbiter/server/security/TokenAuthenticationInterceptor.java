package io.github.arbiter.server.security;

import io.github.arbiter.api.ApiHeaders;
import io.github.arbiter.server.auth.TokenService.TokenClaims;
import io.github.arbiter.server.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public final class TokenAuthenticationInterceptor implements HandlerInterceptor {
    private final AuthService authService;

    public TokenAuthenticationInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireScope requirement = method.getMethodAnnotation(RequireScope.class);
        if (requirement == null) {
            requirement = method.getBeanType().getAnnotation(RequireScope.class);
        }
        if (requirement == null) {
            if (isPublic(method)) {
                return true;
            }
            // Fail closed: an unannotated endpoint still requires a valid token.
            TokenClaims claims = authService.requireToken(
                    request.getHeader(ApiHeaders.ARBITER_TOKEN));
            request.setAttribute(AuthAttributes.CLAIMS, claims);
            return true;
        }
        TokenClaims claims = authService.requireScopes(
                request.getHeader(ApiHeaders.ARBITER_TOKEN), requirement.value());
        request.setAttribute(AuthAttributes.CLAIMS, claims);
        return true;
    }

    private static boolean isPublic(HandlerMethod method) {
        return method.getMethodAnnotation(PublicEndpoint.class) != null
                || method.getBeanType().getAnnotation(PublicEndpoint.class) != null;
    }
}
