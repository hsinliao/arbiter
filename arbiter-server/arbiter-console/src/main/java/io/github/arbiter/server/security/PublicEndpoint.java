package io.github.arbiter.server.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicitly allows an API endpoint to be called without a token. Every other
 * endpoint requires at least a valid token unless it is annotated with
 * {@link RequireScope} for a narrower scope.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}
