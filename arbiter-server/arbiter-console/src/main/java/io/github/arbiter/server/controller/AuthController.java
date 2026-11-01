package io.github.arbiter.server.controller;

import io.github.arbiter.api.ApiRoutes;
import io.github.arbiter.api.dto.LoginRequest;
import io.github.arbiter.api.dto.LoginResponse;
import io.github.arbiter.api.dto.WhoAmIResponse;
import io.github.arbiter.server.auth.TokenService.TokenClaims;
import io.github.arbiter.server.security.AuthAttributes;
import io.github.arbiter.server.security.PublicEndpoint;
import io.github.arbiter.server.security.RequireScope;
import io.github.arbiter.server.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(ApiRoutes.LOGIN)
    @PublicEndpoint
    LoginResponse login(@RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr());
    }

    @GetMapping(ApiRoutes.WHO_AM_I)
    @RequireScope
    WhoAmIResponse whoAmI(
            @RequestAttribute(AuthAttributes.CLAIMS) TokenClaims claims) {
        return authService.whoAmI(claims);
    }
}
