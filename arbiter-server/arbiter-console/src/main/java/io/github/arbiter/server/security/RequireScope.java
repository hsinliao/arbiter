package io.github.arbiter.server.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires a valid bearer token. An empty value accepts any valid token;
 * otherwise the token scope must match one of the listed scopes.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireScope {
    String[] value() default {};
}
