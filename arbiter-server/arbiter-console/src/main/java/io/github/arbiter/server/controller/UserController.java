package io.github.arbiter.server.controller;

import io.github.arbiter.api.ApiRoutes;
import io.github.arbiter.api.dto.UserCreateRequest;
import io.github.arbiter.api.dto.UserListResponse;
import io.github.arbiter.api.dto.UserResponse;
import io.github.arbiter.api.dto.UserUpdateRequest;
import io.github.arbiter.server.auth.TokenService.TokenClaims;
import io.github.arbiter.server.security.AuthAttributes;
import io.github.arbiter.server.security.RequireScope;
import io.github.arbiter.server.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiRoutes.USERS)
@RequireScope("admin")
public final class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    UserListResponse list() {
        return userService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse create(@RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @GetMapping("/{username}")
    UserResponse get(@PathVariable String username) {
        return userService.get(username);
    }

    @PutMapping("/{username}")
    UserResponse update(@PathVariable String username,
                        @RequestBody UserUpdateRequest request,
                        @RequestAttribute(AuthAttributes.CLAIMS) TokenClaims claims) {
        return userService.update(username, request, claims.subject());
    }

    @DeleteMapping("/{username}")
    UserResponse delete(@PathVariable String username,
                        @RequestAttribute(AuthAttributes.CLAIMS) TokenClaims claims) {
        return userService.delete(username, claims.subject());
    }
}
